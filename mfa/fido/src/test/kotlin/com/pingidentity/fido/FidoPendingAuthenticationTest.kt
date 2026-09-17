/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.fido

import android.app.Activity
import android.content.Context
import android.os.Build
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import com.pingidentity.android.ContextProvider
import com.pingidentity.logger.Logger
import com.pingidentity.logger.NONE
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * SDKS-4574 Phase 2 tests: verifies [FidoClient.pendingAuthenticate] and the
 * [FidoPendingAuthentication] coroutine bridge — View-attachable request, await/cancel
 * semantics, forced Credential-Manager routing (GMS static never invoked), the unsupported-OS
 * fast-fail, and the API <= 33 missing-bridge warning.
 *
 * Delivery tests run on API 35 (the OS gate is met there); the API <= 33 tests pin
 * `@Config(sdk = [33])` per method.
 */
@RunWith(RobolectricTestRunner::class) // Build.VERSION / CredentialManager use Android API
@Config(sdk = [35]) // API 35: the OS gate for conditional mediation is met
class FidoPendingAuthenticationTest {

    private lateinit var mockContext: Context
    private lateinit var mockActivity: Activity
    private lateinit var mockCredentialManager: CredentialManager
    private lateinit var capturingLogger: CapturingLogger

    /** Sentinel thrown when the GMS path is reached although the test forbids it. */
    private val gmsForbidden = RuntimeException("GMS FIDO2 path must not be reached")

    @BeforeTest
    fun setUp() {
        mockContext = mockk<Context>(relaxed = true)
        mockActivity = mockk<Activity>(relaxed = true)
        mockCredentialManager = mockk<CredentialManager>(relaxed = true)

        mockkObject(ContextProvider)
        every { ContextProvider.context } returns mockContext
        every { ContextProvider.currentActivity } returns mockActivity

        mockkObject(CredentialManager.Companion)
        every { CredentialManager.create(any()) } returns mockCredentialManager

        mockkStatic("com.pingidentity.fido.FidoNonDiscoverableKt")
    }

    @AfterTest
    fun tearDown() {
        unmockkAll()
    }

    /** Logger capture mirroring the repo's TestLogger pattern (mfa/commons). */
    private class CapturingLogger : Logger {
        val warnings = mutableListOf<String>()

        override fun d(message: String) = Unit
        override fun i(message: String) = Unit
        override fun w(message: String, throwable: Throwable?) {
            warnings += message
        }

        override fun e(message: String, throwable: Throwable?) = Unit
    }

    private fun client(logger: Logger = Logger.NONE) = FidoClient {
        this.logger = logger
    }

    private fun inputJson() = buildJsonObject {
        put(Constants.FIELD_CHALLENGE, "dGVzdC1jaGFsbGVuZ2U") // "test-challenge" base64
        put(Constants.FIELD_RP_ID, "example.com")
    }

    /** A final androidx response carrying a public-key credential with the given assertion JSON. */
    private fun responseWith(assertionJson: String): GetCredentialResponse {
        val mockPublicKeyCredential = mockk<PublicKeyCredential> {
            every { authenticationResponseJson } returns assertionJson
        }
        return mockk<GetCredentialResponse> {
            every { credential } returns mockPublicKeyCredential
        }
    }

    @Test
    fun `pending request delivers the assertion to await when the callback fires`() = runTest {
        // Given - a pending request built by the client, no ceremony started
        val assertionJson =
            """{"id":"pending-id","rawId":"pending-raw","response":{"authenticatorData":"pd","signature":"ps","clientDataJSON":"pc"}}"""
        val pending = client().pendingAuthenticate(inputJson()).getOrThrow()

        // When - the androidx callback delivers the final response (as the View would)
        pending.request.callback(responseWith(assertionJson))
        val result = pending.await()

        // Then - the assertion JsonObject is byte-identical to the modal path's unwrapping
        assertTrue(result.isSuccess)
        assertEquals(Json.parseToJsonElement(assertionJson).jsonObject, result.getOrThrow())
        assertEquals("pending-id", result.getOrThrow()["id"]?.jsonPrimitive?.content)

        // And no credential ceremony was ever started
        coVerify(exactly = 0) {
            mockCredentialManager.getCredential(any(), any() as GetCredentialRequest)
        }
    }

    @Test
    fun `await stays suspended when no callback fires`() = runTest {
        // Given - a pending request that never receives a callback (user dismissed suggestions)
        val pending = client().pendingAuthenticate(inputJson()).getOrThrow()

        var completed = false
        backgroundScope.launch {
            pending.await()
            completed = true
        }
        runCurrent()

        // Then - await has not returned: non-completion is the "fall back to modal" signal
        assertFalse(completed)
        assertNotNull(pending.request)
    }

    @Test
    fun `cancel completes await with CancellationException and re-await is a no-op failure`() =
        runTest {
            // Given - a pending request with a suspended awaiter
            val pending = client().pendingAuthenticate(inputJson()).getOrThrow()
            var awaited: Result<kotlinx.serialization.json.JsonObject>? = null
            backgroundScope.launch { awaited = pending.await() }
            runCurrent()
            assertNull(awaited)

            // When - the request is cancelled (e.g. collector close)
            pending.cancel()
            runCurrent()

            // Then - the awaiter completes with a CancellationException-bearing failure
            assertTrue(awaited!!.isFailure)
            assertTrue(awaited!!.exceptionOrNull() is CancellationException)

            // And re-await after cancel is a no-op returning the same failure
            val again = pending.await()
            assertTrue(again.isFailure)
            assertTrue(again.exceptionOrNull() is CancellationException)
        }

    @Test
    fun `cancel before await completes a later await with CancellationException`() = runTest {
        // Given - cancel is called before any awaiter registers (abandoned request)
        val pending = client().pendingAuthenticate(inputJson()).getOrThrow()
        pending.cancel()

        // Then - a later await does not hang; it returns the cancellation failure
        val result = pending.await()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is CancellationException)
    }

    @Test
    fun `cancel is a no-op when the response was already delivered`() = runTest {
        // Given - a pending request whose callback already delivered the assertion
        val assertionJson = """{"id":"delivered-id","response":{}}"""
        val pending = client().pendingAuthenticate(inputJson()).getOrThrow()
        pending.request.callback(responseWith(assertionJson))

        // When - cancel arrives after delivery
        pending.cancel()

        // Then - the delivered response wins over the cancellation
        val result = pending.await()
        assertTrue(result.isSuccess)
        assertEquals("delivered-id", result.getOrThrow()["id"]?.jsonPrimitive?.content)
    }

    @Test
    fun `unexpected credential type fails await with IllegalStateException`() = runTest {
        // Given - a callback delivering a non-public-key credential
        val pending = client().pendingAuthenticate(inputJson()).getOrThrow()
        val unexpectedCredential = mockk<androidx.credentials.Credential>()
        pending.request.callback(
            mockk<GetCredentialResponse> { every { credential } returns unexpectedCredential }
        )

        // Then - the conversion failure surfaces to awaiters, not into the androidx machinery
        val result = pending.await()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun `GMS static is never invoked on the pending path`() = runTest {
        // Given - GMS stubbed with a sentinel; the auto-detected default routing would pick GMS
        coEvery { getPublicKeyCredential(any(), any()) } throws gmsForbidden

        // When - pendingAuthenticate ignores the routing knob and always uses Credential Manager
        val result = client().pendingAuthenticate(inputJson())

        // Then - success via the pending request; GMS and the credential ceremony never invoked
        assertTrue(result.isSuccess)
        assertNotNull(result.getOrThrow().request)
        coVerify(exactly = 0) { getPublicKeyCredential(any(), any()) }
        coVerify(exactly = 0) {
            mockCredentialManager.getCredential(any(), any() as GetCredentialRequest)
        }
    }

    @Test
    fun `pending request carries the GetCredentialRequest customizer output`() = runTest {
        // Given - the same request-level hook the modal path uses
        val pending = client().pendingAuthenticate(inputJson()) {
            useFido2ApiClient = false
            onGetCredentialRequest { request ->
                GetCredentialRequest(
                    request.credentialOptions,
                    preferImmediatelyAvailableCredentials = true
                )
            }
        }.getOrThrow()

        // Then - the pending request wraps the customized GetCredentialRequest
        assertTrue(pending.request.request.preferImmediatelyAvailableCredentials)
        val option = pending.request.request.credentialOptions.single()
        assertTrue(option is GetPublicKeyCredentialOption)
        assertTrue(option.requestJson.contains("example.com"))
    }

    @Test
    fun `warning is logged when resolved routing would have selected GMS`() = runTest {
        // Given - the auto-detected default (GMS present on the test classpath) resolves true
        val logger = CapturingLogger()

        // When
        val result = client(logger).pendingAuthenticate(inputJson())

        // Then - the call still succeeds, and the force is explained in a warning
        assertTrue(result.isSuccess)
        assertTrue(
            logger.warnings.any { it.contains("forces the Android Credential Manager API") },
            "Expected a GMS-force warning, got: ${logger.warnings}"
        )
    }

    @Test
    fun `no routing warning when the customizer already opted into Credential Manager`() =
        runTest {
            // Given - explicit useFido2ApiClient = false (no force needed)
            val logger = CapturingLogger()

            // When
            val result = client(logger).pendingAuthenticate(inputJson()) {
                useFido2ApiClient = false
            }

            // Then - no GMS-force warning was logged
            assertTrue(result.isSuccess)
            assertFalse(
                logger.warnings.any { it.contains("forces the Android Credential Manager API") },
                "Unexpected GMS-force warning: ${logger.warnings}"
            )
        }

    @Test
    @Config(sdk = [33])
    fun `unsupported OS returns failure with GetCredentialUnsupportedException`() = runTest {
        // When - the OS gate is unmet on API 33 (before any request object exists)
        val result = client().pendingAuthenticate(inputJson())

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is GetCredentialUnsupportedException)
    }

    @Test
    @Config(sdk = [33])
    fun `warning is logged on the API 33 route when the bridge is not bundled`() = runTest {
        // Given - the credentials-play-services-auth bridge is absent from the test classpath,
        // so the Class.forName probe fails
        val logger = CapturingLogger()

        // When
        val result = client(logger).pendingAuthenticate(inputJson())

        // Then - the missing bridge is warned (not an exception), and the call still
        // fast-fails on the OS gate
        assertTrue(result.isFailure)
        assertTrue(
            logger.warnings.any { it.contains("credentials-play-services-auth") },
            "Expected a missing-bridge warning, got: ${logger.warnings}"
        )
    }

    @Test
    fun `no bridge warning on API 35`() = runTest {
        // Given - the bridge is only consulted on API <= 33
        val logger = CapturingLogger()

        // When
        val result = client(logger).pendingAuthenticate(inputJson())

        // Then
        assertTrue(result.isSuccess)
        assertFalse(
            logger.warnings.any { it.contains("credentials-play-services-auth") },
            "Unexpected missing-bridge warning on API 35: ${logger.warnings}"
        )
    }
}

/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.fido.davinci

import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.PublicKeyCredential
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import com.pingidentity.android.ContextProvider
import com.pingidentity.davinci.plugin.DaVinci
import com.pingidentity.fido.Constants
import com.pingidentity.fido.FidoClient
import com.pingidentity.fido.getPublicKeyCredential
import com.pingidentity.logger.CONSOLE
import com.pingidentity.logger.Logger
import com.pingidentity.orchestrate.WorkflowConfig
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * SDKS-4574 Phase 3 tests: verifies [FidoAuthenticationCollector.pendingAuthenticate] end-to-end
 * through the real [FidoClient] with a mocked [CredentialManager] — payload delivery without
 * await (fire-and-forget), the unsupported-OS fast-fail mapping, and close() cancellation.
 *
 * Delivery tests run on API 35 (the OS gate is met there); the unsupported-OS test pins
 * `@Config(sdk = [33])` per method.
 */
@RunWith(RobolectricTestRunner::class) // Build.VERSION / CredentialManager use Android API
@Config(sdk = [35]) // API 35: the OS gate for conditional mediation is met
class FidoAuthenticationCollectorPendingTest {

    private lateinit var mockContext: Context
    private lateinit var mockActivity: Activity
    private lateinit var mockCredentialManager: CredentialManager
    private lateinit var collector: FidoAuthenticationCollector

    /** Sentinel thrown if the GMS FIDO2 path is ever reached — the pending path must not. */
    private val gmsForbidden = RuntimeException("GMS FIDO2 path must not be reached")

    @BeforeTest
    fun setup() {
        mockContext = mockk<Context>(relaxed = true)
        mockActivity = mockk<Activity>(relaxed = true)
        mockCredentialManager = mockk(relaxed = true)

        mockkObject(ContextProvider)
        every { ContextProvider.context } returns mockContext
        every { ContextProvider.currentActivity } returns mockActivity

        mockkObject(CredentialManager.Companion)
        every { CredentialManager.create(any()) } returns mockCredentialManager
        mockkStatic("com.pingidentity.fido.FidoNonDiscoverableKt")

        val daVinci = mockk<DaVinci>()
        val config = mockk<WorkflowConfig>()
        every { daVinci.config } returns config
        every { config.logger } returns Logger.CONSOLE

        collector = FidoAuthenticationCollector()
        collector.davinci = daVinci
        collector.init(getInput())
    }

    @AfterTest
    fun tearDown() {
        unmockkAll()
    }

    private fun getInput(): JsonObject = buildJsonObject {
        put("type", JsonPrimitive("FIDO2"))
        put("key", JsonPrimitive("fido2"))
        put("label", JsonPrimitive("Continue"))
        put(Constants.FIELD_PUBLIC_KEY_CREDENTIAL_REQUEST_OPTIONS, buildJsonObject {
            put(Constants.FIELD_CHALLENGE, JsonArray(listOf(
                JsonPrimitive(1), JsonPrimitive(2), JsonPrimitive(3)
            )))
            put(Constants.FIELD_TIMEOUT, JsonPrimitive(120000))
            put(Constants.FIELD_RP_ID, JsonPrimitive("idc.petrov.ca"))
            put(Constants.FIELD_ALLOW_CREDENTIALS, JsonArray(listOf(
                buildJsonObject {
                    put("type", JsonPrimitive("public-key"))
                    put(Constants.FIELD_ID, JsonArray(listOf(
                        JsonPrimitive(10), JsonPrimitive(20), JsonPrimitive(30)
                    )))
                }
            )))
            put(Constants.FIELD_USER_VERIFICATION, JsonPrimitive("preferred"))
        })
        put("action", JsonPrimitive("AUTHENTICATE"))
        put("trigger", JsonPrimitive("BUTTON"))
        put("required", JsonPrimitive(true))
    }

    /**
     * A final androidx response carrying a public-key credential with the given assertion JSON,
     * as the View would deliver when the user picks a suggestion.
     */
    private fun responseWith(assertionJson: String): GetCredentialResponse {
        val mockPublicKeyCredential = mockk<PublicKeyCredential> {
            every { authenticationResponseJson } returns assertionJson
        }
        return mockk<GetCredentialResponse> {
            every { credential } returns mockPublicKeyCredential
        }
    }

    @Test
    fun `pendingAuthenticate delivers the assertion to payload without await`() = runTest {
        // Given - a pending request created through the real client; no ceremony started
        val assertionJson =
            """{"id":"pending-collector-id","rawId":"pending-raw","response":{"authenticatorData":"auth","signature":"sig","clientDataJSON":"data"}}"""
        coEvery { getPublicKeyCredential(any(), any()) } throws gmsForbidden

        // When - the androidx callback delivers the final response (as the View would),
        // and the app never calls await()
        val pending = collector.pendingAuthenticate().getOrThrow()
        assertNull(collector.payload(), "payload must be null before delivery")
        pending.request.callback(responseWith(assertionJson))

        // Then - the collector observed the assertion and payload() matches the modal shape
        val payload = collector.payload()
        assertNotNull(payload)
        assertEquals(
            "pending-collector-id",
            payload[Constants.FIELD_ASSERTION_VALUE]?.jsonObject
                ?.get(Constants.FIELD_ID)?.jsonPrimitive?.content
        )
        assertEquals("submit", collector.eventType())

        // And no credential ceremony was ever started on either API
        coVerify(exactly = 0) {
            mockCredentialManager.getCredential(any(), any() as GetCredentialRequest)
        }
        coVerify(exactly = 0) { getPublicKeyCredential(any(), any()) }
    }

    @Test
    fun `payload is visible immediately after await returns success`() = runTest {
        // Given - a pending request created through the real client, awaited by the app
        // (e.g. the sample app's LaunchedEffect awaiting and then driving next())
        coEvery { getPublicKeyCredential(any(), any()) } throws gmsForbidden
        val pending = collector.pendingAuthenticate().getOrThrow()

        // State captured at the moment await() resumes — i.e. with no synchronization beyond
        // the resumption itself.
        var awaitedId: String? = null
        var payloadAtAwait: JsonObject? = null
        backgroundScope.launch {
            val result = pending.await()
            // The observer's write to assertionValue must already be visible when await()
            // resumes: FidoPendingAuthentication.complete() publishes the result and runs the
            // observers BEFORE completing the deferred, so observer writes happen-before the
            // awaiter resumes per the JMM. A regression to completing the deferred first
            // would leave this read racing the androidx-callback-thread write — per the JMM
            // a nondeterministically stale/null payload (a data race this test pins the
            // contract against; it cannot deterministically fail on the old ordering).
            payloadAtAwait = collector.payload()
            awaitedId = result.getOrNull()?.get(Constants.FIELD_ID)?.jsonPrimitive?.content
        }
        runCurrent()
        pending.request.callback(
            responseWith("""{"id":"await-visible-id","rawId":"raw","response":{}}""")
        )
        runCurrent()

        // Then - await() resumed with the delivered assertion...
        assertEquals("await-visible-id", awaitedId)
        // ...and the payload already held it, with no further synchronization
        val payloadAtAwaitValue = payloadAtAwait
        assertNotNull(payloadAtAwaitValue, "payload must be visible when await() returns")
        assertEquals(
            "await-visible-id",
            payloadAtAwaitValue[Constants.FIELD_ASSERTION_VALUE]?.jsonObject
                ?.get(Constants.FIELD_ID)?.jsonPrimitive?.content,
            "payload must already hold the assertion await() returned"
        )
        // And the state also survives to a later reader (next()-thread visibility)
        assertEquals(
            "await-visible-id",
            collector.payload()?.get(Constants.FIELD_ASSERTION_VALUE)
                ?.jsonObject?.get(Constants.FIELD_ID)?.jsonPrimitive?.content
        )
    }

    @Test
    fun `pendingAuthenticate clears stale assertion like the modal path`() = runTest {
        // Given - a delivered assertion already in the payload
        val first = collector.pendingAuthenticate().getOrThrow()
        first.request.callback(
            responseWith("""{"id":"first-id","response":{}}""")
        )
        assertNotNull(collector.payload())

        // When - a new pending ceremony is issued
        val second = collector.pendingAuthenticate().getOrThrow()

        // Then - the stale assertion is gone until the new ceremony delivers
        assertNull(collector.payload())
        assertNull(collector.errorCode)
        second.request.callback(responseWith("""{"id":"second-id","response":{}}"""))
        assertEquals(
            "second-id",
            collector.payload()?.get(Constants.FIELD_ASSERTION_VALUE)
                ?.jsonObject?.get(Constants.FIELD_ID)?.jsonPrimitive?.content
        )
    }

    @Test
    @Config(sdk = [33])
    fun `unsupported OS maps to NotSupportedError errorCode`() = runTest {
        // When - the OS gate is unmet on API 33 (fast-fail before any request exists)
        val result = collector.pendingAuthenticate()

        // Then - the failure routes through handleError into the DaVinci error path
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is GetCredentialUnsupportedException)
        assertEquals("NotSupportedError", collector.errorCode)
        assertEquals("action", collector.eventType())

        // And the error payload is the non-null empty sentinel so actionKey fires
        val payload = collector.payload()
        assertNotNull(payload)
        assertTrue(payload.isEmpty())
    }

    @Test
    fun `close cancels the in-flight pending request`() = runTest {
        // Given - a pending request created but never delivered (user has not focused the View)
        val pending = collector.pendingAuthenticate().getOrThrow()
        assertNull(collector.payload())

        // When - the workflow closes the collector (Node.close)
        collector.close()

        // Then - the abandoned awaiter is released with a CancellationException failure,
        // not left suspended forever
        val result = pending.await()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is CancellationException)

        // And cancellation is teardown, not a ceremony failure: no errorCode latch set
        assertNull(collector.errorCode)
        assertNull(collector.payload())
        assertEquals("submit", collector.eventType())
    }

    @Test
    fun `close after delivery keeps no payload like the modal close`() = runTest {
        // Given - a delivered assertion in the payload
        val pending = collector.pendingAuthenticate().getOrThrow()
        pending.request.callback(
            responseWith("""{"id":"delivered-id","response":{}}""")
        )
        assertNotNull(collector.payload())

        // When
        collector.close()

        // Then - payload cleared as usual; the delivered request's cancel is a no-op
        assertNull(collector.payload())
        assertNull(collector.errorCode)
    }
}

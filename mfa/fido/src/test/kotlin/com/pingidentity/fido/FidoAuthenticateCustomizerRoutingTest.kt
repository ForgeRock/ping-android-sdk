/*
 * Copyright (c) 2025 - 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.fido

import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import com.google.android.gms.fido.fido2.api.common.AuthenticatorAssertionResponse
import com.google.android.gms.fido.fido2.api.common.PublicKeyCredentialRequestOptions
import com.pingidentity.android.ContextProvider
import io.mockk.CapturingSlot
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import com.google.android.gms.fido.fido2.api.common.PublicKeyCredential as GmsPublicKeyCredential

/**
 * Spike tests for SDKS-5411: verifies that [FidoAuthenticateCustomizer.useFido2ApiClient] is the
 * single source of truth for API selection on an `authenticate` call — default auto-detected
 * from GMS presence (`true` on any GMS device, `false` otherwise), with explicit per-call
 * overrides for either path.
 */
@RunWith(RobolectricTestRunner::class) // CredentialManager uses Android API
class FidoAuthenticateCustomizerRoutingTest {

    private lateinit var mockContext: Context
    private lateinit var mockActivity: Activity
    private lateinit var mockCredentialManager: CredentialManager

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

    private fun inputJson() = buildJsonObject {
        put(Constants.FIELD_CHALLENGE, "dGVzdC1jaGFsbGVuZ2U") // "test-challenge" base64
        put(Constants.FIELD_RP_ID, "example.com")
    }

    private fun stubCredentialManagerSuccess(): CapturingSlot<GetCredentialRequest> {
        val expectedResponse =
            """{"id":"cm-id","rawId":"cm-raw-id","response":{"authenticatorData":"cm-auth-data","signature":"cm-signature","clientDataJSON":"cm-client-data"}}"""
        val mockPublicKeyCredential = mockk<PublicKeyCredential> {
            every { authenticationResponseJson } returns expectedResponse
        }
        val mockGetResponse = mockk<GetCredentialResponse> {
            every { credential } returns mockPublicKeyCredential
        }
        val requestSlot = slot<GetCredentialRequest>()
        coEvery {
            mockCredentialManager.getCredential(
                context = mockActivity,
                request = capture(requestSlot)
            )
        } returns mockGetResponse
        return requestSlot
    }

    private fun stubGmsSuccess(): CapturingSlot<PublicKeyCredentialRequestOptions> {
        val mockCredential = mockk<GmsPublicKeyCredential>()
        val mockResponse = mockk<AuthenticatorAssertionResponse>()
        every { mockCredential.id } returns "gms-id"
        every { mockCredential.rawId } returns "gms-raw-id".toByteArray()
        every { mockCredential.type } returns "public-key"
        every { mockCredential.authenticatorAttachment } returns "platform"
        every { mockCredential.response } returns mockResponse
        every { mockResponse.authenticatorData } returns "gms-auth".toByteArray()
        every { mockResponse.clientDataJSON } returns "gms-client".toByteArray()
        every { mockResponse.signature } returns "gms-sig".toByteArray()
        every { mockResponse.userHandle } returns null

        val optionsSlot = slot<PublicKeyCredentialRequestOptions>()
        coEvery { getPublicKeyCredential(any(), capture(optionsSlot)) } returns mockCredential
        return optionsSlot
    }

    @Test
    fun `customizer useFido2ApiClient auto-detects GMS by default`() {
        // The single source of truth defaults to runtime GMS detection (preserves
        // existing behaviour). In the unit-test env play-services-fido is on the
        // classpath, so the default is true — the same value a GMS device resolves.
        assertTrue(FidoAuthenticateCustomizer().useFido2ApiClient)
    }

    @Test
    fun `explicit false should route to Credential Manager`() = runTest {
        // Given - explicit opt-in to the Credential Manager path
        val requestSlot = stubCredentialManagerSuccess()

        // When
        val result = FidoClient().authenticate(inputJson()) {
            useFido2ApiClient = false
        }

        // Then - Credential Manager path taken, GMS path never invoked
        assertTrue(result.isSuccess)
        assertEquals("cm-id", result.getOrThrow()["id"]?.jsonPrimitive?.content)
        assertTrue(requestSlot.captured.credentialOptions.first() is GetPublicKeyCredentialOption)
        coVerify(exactly = 1) {
            mockCredentialManager.getCredential(any(), any() as GetCredentialRequest)
        }
        coVerify(exactly = 0) { getPublicKeyCredential(any(), any()) }
    }

    @Test
    fun `default routing should follow GMS auto-detection`() = runTest {
        // Given - no override; the auto-detected default (GMS present in test env) routes to GMS
        val optionsSlot = stubGmsSuccess()

        // When
        val result = FidoClient().authenticate(inputJson())

        // Then - GMS path taken (auto-detected default, unchanged behaviour)
        assertTrue(result.isSuccess)
        assertEquals("gms-id", result.getOrThrow()["id"]?.jsonPrimitive?.content)
        assertEquals("example.com", optionsSlot.captured.rpId)
        coVerify(exactly = 1) { getPublicKeyCredential(any(), any()) }
        coVerify(exactly = 0) {
            mockCredentialManager.getCredential(any(), any() as GetCredentialRequest)
        }
    }

    @Test
    fun `opting into GMS should route to the GMS path`() = runTest {
        // Given - explicit opt-in for this call
        val optionsSlot = stubGmsSuccess()
        coEvery {
            mockCredentialManager.getCredential(any(), any() as GetCredentialRequest)
        } throws gmsForbidden

        // When
        val result = FidoClient().authenticate(inputJson()) {
            useFido2ApiClient = true
        }

        // Then - GMS path taken, Credential Manager never invoked
        assertTrue(result.isSuccess)
        assertEquals("gms-id", result.getOrThrow()["id"]?.jsonPrimitive?.content)
        assertEquals("example.com", optionsSlot.captured.rpId)
        coVerify(exactly = 1) { getPublicKeyCredential(any(), any()) }
        coVerify(exactly = 0) {
            mockCredentialManager.getCredential(any(), any() as GetCredentialRequest)
        }
    }

    @Test
    fun `Credential Manager path should still apply the option customizer`() = runTest {
        // Given - Credential Manager opt-in; option customizer must still run
        val requestSlot = stubCredentialManagerSuccess()
        coEvery { getPublicKeyCredential(any(), any()) } throws gmsForbidden

        // When
        val result = FidoClient().authenticate(inputJson()) {
            useFido2ApiClient = false
            onGetPublicKeyCredentialOption { option ->
                GetPublicKeyCredentialOption(option.requestJson.replace("example.com", "customized.example.com"))
            }
        }

        // Then - the customizer output (not the original option) reached Credential Manager
        assertTrue(result.isSuccess)
        val option = requestSlot.captured.credentialOptions.first() as GetPublicKeyCredentialOption
        assertTrue(option.requestJson.contains("customized.example.com"))
        coVerify(exactly = 0) { getPublicKeyCredential(any(), any()) }
    }

    @Test
    fun `opt-in path should still apply the GMS options customizer`() = runTest {
        // Given - GMS opt-in; request options customizer must still run
        val optionsSlot = stubGmsSuccess()
        coEvery {
            mockCredentialManager.getCredential(any(), any() as GetCredentialRequest)
        } throws gmsForbidden

        // When
        val result = FidoClient().authenticate(inputJson()) {
            useFido2ApiClient = true
            onPublicKeyCredentialRequestOptions { options ->
                PublicKeyCredentialRequestOptions.Builder()
                    .setRpId("modified-${options.rpId}")
                    .setChallenge(options.challenge)
                    .setAllowList(options.allowList)
                    .build()
            }
        }

        // Then
        assertTrue(result.isSuccess)
        assertEquals("modified-example.com", optionsSlot.captured.rpId)
        coVerify(exactly = 0) {
            mockCredentialManager.getCredential(any(), any() as GetCredentialRequest)
        }
    }
}

/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
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
 * SDKS-4574 widening tests: verifies that [FidoAuthenticateCustomizer.onGetCredentialRequest]
 * transforms the [GetCredentialRequest] handed to the Credential Manager — restoring access to
 * request-level preferences such as `preferImmediatelyAvailableCredentials`, which moved from
 * `GetPublicKeyCredentialOption` to `GetCredentialRequest` in androidx.credentials 1.5.0.
 */
@RunWith(RobolectricTestRunner::class) // CredentialManager uses Android API
class FidoAuthenticateCustomizerWideningTest {

    private lateinit var mockContext: Context
    private lateinit var mockActivity: Activity
    private lateinit var mockCredentialManager: CredentialManager

    /** Sentinel thrown when the GetCredentialRequest hook is applied although forbidden. */
    private val hookForbidden = RuntimeException("GetCredentialRequest hook must not be applied")

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
    fun `onGetCredentialRequest hook applies preferImmediatelyAvailableCredentials to the captured request`() =
        runTest {
            // Given - Credential Manager path with a request-level hook
            val requestSlot = stubCredentialManagerSuccess()

            // When
            val result = FidoClient().authenticate(inputJson()) {
                useFido2ApiClient = false
                onGetCredentialRequest { request ->
                    GetCredentialRequest(
                        request.credentialOptions,
                        preferImmediatelyAvailableCredentials = true
                    )
                }
            }

            // Then - the captured request carries the preference, and the option is preserved
            assertTrue(result.isSuccess)
            assertTrue(requestSlot.captured.preferImmediatelyAvailableCredentials)
            val option = requestSlot.captured.credentialOptions.single()
            assertTrue(option is GetPublicKeyCredentialOption)
            coVerify(exactly = 1) {
                mockCredentialManager.getCredential(any(), any() as GetCredentialRequest)
            }
            coVerify(exactly = 0) { getPublicKeyCredential(any(), any()) }
        }

    @Test
    fun `default without the hook keeps the library default preferImmediatelyAvailableCredentials false`() =
        runTest {
            // Given - Credential Manager path, no GetCredentialRequest hook
            val requestSlot = stubCredentialManagerSuccess()

            // When
            val result = FidoClient().authenticate(inputJson()) {
                useFido2ApiClient = false
            }

            // Then - the request reaches Credential Manager unchanged
            assertTrue(result.isSuccess)
            assertEquals(false, requestSlot.captured.preferImmediatelyAvailableCredentials)
        }

    @Test
    fun `onGetCredentialRequest composes with the option customizer`() = runTest {
        // Given - both Credential Manager customizers set
        val requestSlot = stubCredentialManagerSuccess()

        // When
        val result = FidoClient().authenticate(inputJson()) {
            useFido2ApiClient = false
            onGetPublicKeyCredentialOption { option ->
                GetPublicKeyCredentialOption(option.requestJson.replace("example.com", "customized.example.com"))
            }
            onGetCredentialRequest { request ->
                GetCredentialRequest(
                    request.credentialOptions,
                    preferImmediatelyAvailableCredentials = true
                )
            }
        }

        // Then - option customizer output wrapped by the request customizer output
        assertTrue(result.isSuccess)
        assertTrue(requestSlot.captured.preferImmediatelyAvailableCredentials)
        val option = requestSlot.captured.credentialOptions.single() as GetPublicKeyCredentialOption
        assertTrue(option.requestJson.contains("customized.example.com"))
    }

    @Test
    fun `onGetCredentialRequest is not applied on the GMS path`() = runTest {
        // Given - GMS opt-in with a successful GMS stub; a hook that would throw if the
        // Credential Manager path were (wrongly) taken
        val optionsSlot = stubGmsSuccess()
        coEvery {
            mockCredentialManager.getCredential(any(), any() as GetCredentialRequest)
        } throws hookForbidden

        // When
        val result = FidoClient().authenticate(inputJson()) {
            useFido2ApiClient = true
            onGetCredentialRequest { _ -> throw hookForbidden }
        }

        // Then - modal GMS behaviour unchanged: success via GMS, hook never applied,
        // Credential Manager never invoked
        assertTrue(result.isSuccess)
        assertEquals("gms-id", result.getOrThrow()["id"]?.jsonPrimitive?.content)
        assertEquals("example.com", optionsSlot.captured.rpId)
        coVerify(exactly = 0) {
            mockCredentialManager.getCredential(any(), any() as GetCredentialRequest)
        }
        coVerify(exactly = 1) { getPublicKeyCredential(any(), any()) }
    }
}

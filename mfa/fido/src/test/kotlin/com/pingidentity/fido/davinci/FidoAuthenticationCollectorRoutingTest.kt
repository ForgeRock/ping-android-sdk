/*
 * Copyright (c) 2025 - 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.fido.davinci

import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import com.google.android.gms.fido.fido2.api.common.AuthenticatorAssertionResponse
import com.google.android.gms.fido.fido2.api.common.PublicKeyCredentialRequestOptions
import com.google.android.gms.fido.fido2.api.common.PublicKeyCredential as GmsPublicKeyCredential
import com.pingidentity.android.ContextProvider
import com.pingidentity.davinci.plugin.DaVinci
import com.pingidentity.fido.Constants
import com.pingidentity.fido.getPublicKeyCredential
import com.pingidentity.logger.CONSOLE
import com.pingidentity.logger.Logger
import com.pingidentity.orchestrate.WorkflowConfig
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Spike test for SDKS-5411: verifies that the `authenticate { }` block exposed by
 * [FidoAuthenticationCollector] forwards the [com.pingidentity.fido.FidoAuthenticateCustomizer.useFido2ApiClient]
 * override end-to-end to the real [com.pingidentity.fido.FidoClient] branch decision —
 * i.e. the collector itself needs no changes for the override to work.
 */
@RunWith(RobolectricTestRunner::class) // CredentialManager uses Android API
class FidoAuthenticationCollectorRoutingTest {

    private lateinit var mockContext: Context
    private lateinit var mockActivity: Activity
    private lateinit var mockCredentialManager: CredentialManager
    private lateinit var collector: FidoAuthenticationCollector

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

    private fun stubCredentialManagerSuccess(): CapturingSlot<GetCredentialRequest> {
        val expectedResponse =
            """{"id":"collector-cm-id","rawId":"raw","response":{"authenticatorData":"auth","signature":"sig","clientDataJSON":"data"}}"""
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

    @Test
    fun `authenticate block override false should force Credential Manager through the collector`() = runTest {
        // Given - explicit opt-in to the Credential Manager path via the collector block
        val requestSlot = stubCredentialManagerSuccess()
        coEvery { getPublicKeyCredential(any(), any()) } throws gmsForbidden

        // When - block forwarded end-to-end: collector -> FidoClient.authenticate -> branch
        val result = collector.authenticate {
            useFido2ApiClient = false
        }

        // Then - Credential Manager path reached
        assertTrue(result.isSuccess)
        assertEquals("collector-cm-id", result.getOrThrow()["id"]?.jsonPrimitive?.content)
        assertTrue(requestSlot.captured.credentialOptions.first() is GetPublicKeyCredentialOption)
        coVerify(exactly = 1) {
            mockCredentialManager.getCredential(any(), any() as GetCredentialRequest)
        }
        coVerify(exactly = 0) { getPublicKeyCredential(any(), any()) }

        // And the assertion is stored in the collector payload as usual
        assertEquals(
            result.getOrThrow(),
            collector.payload()?.get(Constants.FIELD_ASSERTION_VALUE)?.jsonObject
        )
    }

    @Test
    fun `authenticate without override should follow GMS auto-detection through the collector`() = runTest {
        // Given - no override; the auto-detected default (GMS present in test env) routes to GMS
        val optionsSlot = stubGmsSuccess()

        // When
        val result = collector.authenticate()

        // Then - GMS path taken by default (behaviour existing apps see today), CM never invoked
        assertTrue(result.isSuccess)
        assertEquals("collector-gms-id", result.getOrThrow()["id"]?.jsonPrimitive?.content)
        assertEquals("idc.petrov.ca", optionsSlot.captured.rpId)
        coVerify(exactly = 1) { getPublicKeyCredential(any(), any()) }
        coVerify(exactly = 0) {
            mockCredentialManager.getCredential(any(), any() as GetCredentialRequest)
        }
    }

    private fun stubGmsSuccess(): CapturingSlot<PublicKeyCredentialRequestOptions> {
        val mockCredential = mockk<GmsPublicKeyCredential>()
        val mockResponse = mockk<AuthenticatorAssertionResponse>()
        every { mockCredential.id } returns "collector-gms-id"
        every { mockCredential.rawId } returns "raw".toByteArray()
        every { mockCredential.type } returns "public-key"
        every { mockCredential.authenticatorAttachment } returns "platform"
        every { mockCredential.response } returns mockResponse
        every { mockResponse.authenticatorData } returns "auth".toByteArray()
        every { mockResponse.clientDataJSON } returns "data".toByteArray()
        every { mockResponse.signature } returns "sig".toByteArray()
        every { mockResponse.userHandle } returns null

        val optionsSlot = slot<PublicKeyCredentialRequestOptions>()
        coEvery { getPublicKeyCredential(any(), capture(optionsSlot)) } returns mockCredential
        return optionsSlot
    }
}

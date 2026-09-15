/*
 * Copyright (c) 2025 - 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.fido.journey

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
import com.pingidentity.fido.Constants
import com.pingidentity.fido.getPublicKeyCredential
import com.pingidentity.journey.plugin.Callback
import com.pingidentity.journey.plugin.ValueCallback
import com.pingidentity.logger.CONSOLE
import com.pingidentity.logger.Logger
import com.pingidentity.network.ktor.KtorHttpRequest
import com.pingidentity.orchestrate.ContinueNode
import com.pingidentity.orchestrate.FlowContext
import com.pingidentity.orchestrate.SharedContext
import com.pingidentity.orchestrate.Workflow
import com.pingidentity.orchestrate.WorkflowConfig
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import com.pingidentity.network.HttpRequest as Request

/**
 * Spike test for SDKS-5411: verifies that the `authenticate { }` block exposed by
 * [FidoAuthenticationCallback] forwards the [com.pingidentity.fido.FidoAuthenticateCustomizer.useFido2ApiClient]
 * override end-to-end to the real [com.pingidentity.fido.FidoClient] branch decision —
 * i.e. the callback itself needs no changes for the override to work.
 */
class FidoAuthenticationCallbackRoutingTest {

    private lateinit var continueNode: ContinueNode
    private lateinit var mockWorkflow: Workflow
    private lateinit var mockWorkflowConfig: WorkflowConfig
    private lateinit var valueCallback: ValueCallback
    private lateinit var mockContext: Context
    private lateinit var mockActivity: Activity
    private lateinit var mockCredentialManager: CredentialManager

    private val gmsForbidden = RuntimeException("GMS FIDO2 path must not be reached")

    @kotlin.test.BeforeTest
    fun setUp() {
        mockWorkflow = mockk<Workflow>()
        mockWorkflowConfig = mockk<WorkflowConfig>()
        valueCallback = object : ValueCallback {
            override val id: String = Constants.WEB_AUTHN_OUTCOME
            override var value: String = ""

            override fun init(jsonObject: JsonObject): Callback {
                return this
            }

            override fun payload(): JsonObject {
                return buildJsonObject { }
            }
        }

        continueNode = object : ContinueNode(
            FlowContext(SharedContext(mutableMapOf())),
            mockWorkflow,
            buildJsonObject { },
            listOf(valueCallback)
        ) {
            override fun asRequest(): Request {
                return KtorHttpRequest()
            }
        }

        every { mockWorkflow.config } returns mockWorkflowConfig
        every { mockWorkflowConfig.logger } returns Logger.CONSOLE

        mockContext = mockk<Context>(relaxed = true)
        mockActivity = mockk<Activity>(relaxed = true)
        mockCredentialManager = mockk(relaxed = true)

        mockkObject(ContextProvider)
        every { ContextProvider.context } returns mockContext
        every { ContextProvider.currentActivity } returns mockActivity

        mockkObject(CredentialManager.Companion)
        every { CredentialManager.create(any()) } returns mockCredentialManager
        mockkStatic("com.pingidentity.fido.FidoNonDiscoverableKt")
    }

    @kotlin.test.AfterTest
    fun tearDown() {
        unmockkAll()
    }

    private fun initCallback(): FidoAuthenticationCallback {
        val sampleJson = buildJsonObject {
            put("type", "MetadataCallback")
            putJsonArray("output") {
                addJsonObject {
                    put("name", "data")
                    putJsonObject("value") {
                        put("_action", "webauthn_authentication")
                        put("challenge", "IrmRP2U3shw3plwrICzAkw/yupRI60s2dnGhfwExd/o=")
                        put("allowCredentials", "")
                        putJsonArray("_allowCredentials") { }
                        put("timeout", "60000")
                        put("userVerification", "required")
                        put("_relyingPartyId", "idc.petrov.ca")
                        putJsonObject("extensions") { }
                        put("_type", "WebAuthn")
                        put("supportsJsonResponse", false)
                    }
                }
            }
        }
        val callback = FidoAuthenticationCallback()
        callback.continueNode = continueNode
        callback.journey = mockWorkflow
        callback.init(sampleJson)
        return callback
    }

    @kotlin.test.Test
    fun `authenticate block override false should force Credential Manager through the callback`() = runTest {
        // Given - explicit opt-in to the Credential Manager path via the callback block
        val expectedResponse =
            """{"id":"callback-cm-id","rawId":"raw","response":{"authenticatorData":"YXV0aA","signature":"c2ln","clientDataJSON":"e30","userHandle":"dXNlcg"}}"""
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
        coEvery { getPublicKeyCredential(any(), any()) } throws gmsForbidden

        val callback = initCallback()

        // When - block forwarded end-to-end: callback -> FidoClient.authenticate -> branch
        val result = callback.authenticate {
            useFido2ApiClient = false
        }

        // Then - Credential Manager path reached
        assertTrue(result.isSuccess)
        assertEquals("callback-cm-id", result.getOrThrow()["id"]?.jsonPrimitive?.content)
        assertTrue(requestSlot.captured.credentialOptions.first() is GetPublicKeyCredentialOption)
        coVerify(exactly = 1) {
            mockCredentialManager.getCredential(any(), any() as GetCredentialRequest)
        }
        coVerify(exactly = 0) { getPublicKeyCredential(any(), any()) }
    }

    @kotlin.test.Test
    fun `authenticate without override should follow GMS auto-detection through the callback`() = runTest {
        // Given - no override; the auto-detected default (GMS present in test env) routes to GMS
        val mockCredential = mockk<GmsPublicKeyCredential>()
        val mockResponse = mockk<AuthenticatorAssertionResponse>()
        every { mockCredential.id } returns "callback-gms-id"
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

        val callback = initCallback()

        // When
        val result = callback.authenticate()

        // Then - GMS path taken by default (behaviour existing apps see today), CM never invoked
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { getPublicKeyCredential(any(), any()) }
        coVerify(exactly = 0) {
            mockCredentialManager.getCredential(any(), any() as GetCredentialRequest)
        }
    }
}

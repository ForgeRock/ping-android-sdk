/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.fido.journey

import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.PublicKeyCredential
import androidx.credentials.exceptions.GetCredentialUnsupportedException
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
import io.mockk.unmockkAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import com.pingidentity.network.HttpRequest as Request

/**
 * SDKS-4574 Phase 3 tests: verifies [FidoAuthenticationCallback.pendingAuthenticate] end-to-end
 * through the real [FidoClient] with a mocked [CredentialManager] — outcome delivery without
 * await (fire-and-forget), the unsupported-OS fast-fail mapping, and cancellation semantics.
 *
 * Delivery tests run on API 35 (the OS gate is met there); the unsupported-OS test pins
 * `@Config(sdk = [33])` per method.
 */
@RunWith(RobolectricTestRunner::class) // Build.VERSION / CredentialManager use Android API
@Config(sdk = [35]) // API 35: the OS gate for conditional mediation is met
class FidoAuthenticationCallbackPendingTest {

    private lateinit var continueNode: ContinueNode
    private lateinit var mockWorkflow: Workflow
    private lateinit var mockWorkflowConfig: WorkflowConfig
    private lateinit var valueCallback: ValueCallback
    private lateinit var mockContext: Context
    private lateinit var mockActivity: Activity
    private lateinit var mockCredentialManager: CredentialManager

    /** Sentinel thrown if the GMS FIDO2 path is ever reached — the pending path must not. */
    private val gmsForbidden = RuntimeException("GMS FIDO2 path must not be reached")

    @BeforeTest
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

    @AfterTest
    fun tearDown() {
        unmockkAll()
    }

    private fun initCallback(supportsJsonResponse: Boolean = false): FidoAuthenticationCallback {
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
                        put("supportsJsonResponse", supportsJsonResponse)
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

    /**
     * A final androidx response carrying a public-key credential with the given assertion JSON,
     * as the View would deliver when the user picks a suggestion. The assertion mirrors the
     * modal tests' fake response so the expected data string is identical.
     */
    private fun responseWith(assertionJson: String): GetCredentialResponse {
        val mockPublicKeyCredential = mockk<PublicKeyCredential> {
            every { authenticationResponseJson } returns assertionJson
        }
        return mockk<GetCredentialResponse> {
            every { credential } returns mockPublicKeyCredential
        }
    }

    /** The modal tests' fake assertion response, reused so payload mapping is comparable. */
    private val modalFakeAssertion =
        """{"id":"rawId","rawId":"rawId","response":{"clientDataJSON":""" +
            """"eyJ0eXBlIjoid2ViYXV0aG4uZ2V0IiwiY2hhbGxlbmdlIjoiSjlDVmcxRkl6REhhd3BCLS0yeTZGc2pyX2RLTEtzTGNGcGRKanp0ZFBydyIsIm9yaWdpbiI6ImFuZHJvaWQ6YXBrLWtleS1oYXNoOlp2Rm5reUJBbTZFUHZNNTBGQUZVRDZ1MUduN3ZaaGd0OGpjcXNjb25fY28iLCJhbmRyb2lkUGFja2FnZU5hbWUiOiJjb20ucGluZ2lkZW50aXR5LnNhbXBsZXMuam91cm5leWFwcCJ9",""" +
            """"authenticatorData":"N2_Q5-0Y8GIS3KdDwe8960U5Hls64HVj4KuW_PJGdQIdAAAAAA",""" +
            """"signature":"MEUCIES2SaVu-5e_A-PQ0caU2yd1gXR8zI-_gTMMgUSTTk2rAiEAqkHPuUcc1I1cicdWXLKwZE6bGi7uy3PjAP9U93CqesI",""" +
            """"userHandle":"MThhYzY4OWUtNjNlOC00ODcxLTg1ZWEtMzU4MzIzNjRiNDgx"}}"""

    /** The modal test's expected legacy data string, verbatim. */
    private val expectedLegacyDataString =
        "{\"type\":\"webauthn.get\",\"challenge\":\"J9CVg1FIzDHawpB--2y6Fsjr_dKLKsLcFpdJjztdPrw\",\"origin\":\"android:apk-key-hash:ZvFnkyBAm6EPvM50FAFUD6u1Gn7vZhgt8jcqscon_co\",\"androidPackageName\":\"com.pingidentity.samples.journeyapp\"}::55,111,-48,-25,-19,24,-16,98,18,-36,-89,67,-63,-17,61,-21,69,57,30,91,58,-32,117,99,-32,-85,-106,-4,-14,70,117,2,29,0,0,0,0::48,69,2,32,68,-74,73,-91,110,-5,-105,-65,3,-29,-48,-47,-58,-108,-37,39,117,-127,116,124,-52,-113,-65,-127,51,12,-127,68,-109,78,77,-85,2,33,0,-86,65,-49,-71,71,28,-44,-115,92,-119,-57,86,92,-78,-80,100,78,-101,26,46,-18,-53,115,-29,0,-1,84,-9,112,-86,122,-62::rawId::18ac689e-63e8-4871-85ea-35832364b481"

    /** The modal test's expected JSON-format outcome, verbatim (quote-escaped legacyData). */
    private val expectedJsonDataString =
        "{\"authenticatorAttachment\":\"platform\",\"legacyData\":\"{\\\"type\\\":\\\"webauthn.get\\\",\\\"challenge\\\":\\\"J9CVg1FIzDHawpB--2y6Fsjr_dKLKsLcFpdJjztdPrw\\\",\\\"origin\\\":\\\"android:apk-key-hash:ZvFnkyBAm6EPvM50FAFUD6u1Gn7vZhgt8jcqscon_co\\\",\\\"androidPackageName\\\":\\\"com.pingidentity.samples.journeyapp\\\"}::55,111,-48,-25,-19,24,-16,98,18,-36,-89,67,-63,-17,61,-21,69,57,30,91,58,-32,117,99,-32,-85,-106,-4,-14,70,117,2,29,0,0,0,0::48,69,2,32,68,-74,73,-91,110,-5,-105,-65,3,-29,-48,-47,-58,-108,-37,39,117,-127,116,124,-52,-113,-65,-127,51,12,-127,68,-109,78,77,-85,2,33,0,-86,65,-49,-71,71,28,-44,-115,92,-119,-57,86,92,-78,-80,100,78,-101,26,46,-18,-53,115,-29,0,-1,84,-9,112,-86,122,-62::rawId::18ac689e-63e8-4871-85ea-35832364b481\"}"

    @Test
    fun `pendingAuthenticate delivers the data string to valueCallback without await`() = runTest {
        // Given - a pending request created through the real client; no ceremony started
        coEvery { getPublicKeyCredential(any(), any()) } throws gmsForbidden
        val callback = initCallback(supportsJsonResponse = false)

        // When - the androidx callback delivers the final response (as the View would),
        // and the app never calls await()
        val pending = callback.pendingAuthenticate().getOrThrow()
        assertEquals("", valueCallback.value, "no outcome before delivery")
        pending.request.callback(responseWith(modalFakeAssertion))

        // Then - the callback observed the assertion and shaped it exactly as the modal path
        assertEquals(expectedLegacyDataString, valueCallback.value)

        // And no credential ceremony was ever started on either API
        coVerify(exactly = 0) {
            mockCredentialManager.getCredential(any(), any() as GetCredentialRequest)
        }
        coVerify(exactly = 0) { getPublicKeyCredential(any(), any()) }
    }

    @Test
    fun `valueCallback data is visible immediately after await returns success`() = runTest {
        // Given - a pending request created through the real client and awaited by the app
        // (e.g. the sample app awaiting and then calling node.next())
        coEvery { getPublicKeyCredential(any(), any()) } throws gmsForbidden
        val callback = initCallback(supportsJsonResponse = false)
        val pending = callback.pendingAuthenticate().getOrThrow()

        // State captured at the moment await() resumes — i.e. with no synchronization beyond
        // the resumption itself.
        var awaitedOutcome: String? = null
        var awaitedSuccessfully = false
        backgroundScope.launch {
            val result = pending.await()
            // The observer's run (which shaped the outcome and wrote valueCallback.value) must
            // already have happened when await() resumes: FidoPendingAuthentication publishes
            // the result and notifies observers BEFORE completing the deferred, so those
            // writes happen-before the awaiter resumes per the JMM. A regression to completing
            // the deferred first would make this read race the androidx-callback-thread write
            // — per the JMM a nondeterministically empty outcome (a data race this test pins
            // the contract against; it cannot deterministically fail on the old ordering).
            awaitedSuccessfully = result.isSuccess
            awaitedOutcome = valueCallback.value
        }
        runCurrent()
        pending.request.callback(responseWith(modalFakeAssertion))
        runCurrent()

        // Then - await() resumed successfully and the shaped outcome was already populated,
        // with no further synchronization
        assertTrue(awaitedSuccessfully, "await() must have resumed successfully")
        assertEquals(
            expectedLegacyDataString,
            awaitedOutcome,
            "valueCallback.value must be populated when await() returns"
        )
    }

    @Test
    fun `pendingAuthenticate shapes the JSON response when supportsJsonResponse is true`() =
        runTest {
            // Given - the callback initialized with supportsJsonResponse = true
            val callback = initCallback(supportsJsonResponse = true)

            // When - the assertion is delivered fire-and-forget
            val pending = callback.pendingAuthenticate().getOrThrow()
            pending.request.callback(responseWith(modalFakeAssertion))

            // Then - the outcome uses the JSON format with metadata, byte-identical to the
            // modal path's JSON-format outcome
            assertEquals(expectedJsonDataString, valueCallback.value)
        }

    @Test
    fun `authenticate cancels a superseded pending request before the modal ceremony`() = runTest {
        // Given - a pending request created but never delivered
        coEvery { getPublicKeyCredential(any(), any()) } throws gmsForbidden
        val callback = initCallback(supportsJsonResponse = false)
        val pending = callback.pendingAuthenticate().getOrThrow()
        assertEquals("", valueCallback.value, "no outcome before delivery")

        // When - the modal ceremony runs and delivers an assertion
        val mockPublicKeyCredential = mockk<PublicKeyCredential> {
            every { authenticationResponseJson } returns modalFakeAssertion
        }
        coEvery {
            mockCredentialManager.getCredential(any(), any() as GetCredentialRequest)
        } returns mockk {
            every { credential } returns mockPublicKeyCredential
        }
        val result = callback.authenticate { useFido2ApiClient = false }

        // Then - the modal outcome reached the valueCallback exactly once, shaped as usual
        assertTrue(result.isSuccess)
        assertEquals(expectedLegacyDataString, valueCallback.value)

        // And the abandoned awaiter was released with a cancellation failure, not left
        // suspended (both paths completing would submit the callback twice)
        val awaited = pending.await()
        assertTrue(awaited.isFailure)
        assertTrue(awaited.exceptionOrNull() is CancellationException)

        // And a late delivery from the cancelled request is ignored — the modal outcome wins
        pending.request.callback(responseWith(modalFakeAssertion))
        assertEquals(expectedLegacyDataString, valueCallback.value)
    }

    @Test
    @Config(sdk = [33])
    fun `unsupported OS maps to the ERROR_UNSUPPORTED valueCallback path`() = runTest {
        // Given
        val callback = initCallback()

        // When - the OS gate is unmet on API 33 (fast-fail before any request exists)
        val result = callback.pendingAuthenticate()

        // Then - the failure routes through handleError into the Journey error path
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is GetCredentialUnsupportedException)
        assertEquals(Constants.ERROR_UNSUPPORTED, valueCallback.value)
    }

    @Test
    fun `close-driven cancel is teardown and does not set an error outcome`() = runTest {
        // Given - a pending request created but never delivered
        val callback = initCallback()
        val pending = callback.pendingAuthenticate().getOrThrow()

        // When - the request is cancelled (e.g. lifecycle teardown) before delivery
        pending.cancel()

        // Then - no error outcome is submitted: the androidx pending path never propagates
        // errors, and cancellation is not a ceremony failure
        assertEquals("", valueCallback.value)

        // And the abandoned awaiter is released, not left suspended forever
        val awaited = pending.await()
        assertTrue(awaited.isFailure)
        assertTrue(awaited.exceptionOrNull() is CancellationException)
    }
}

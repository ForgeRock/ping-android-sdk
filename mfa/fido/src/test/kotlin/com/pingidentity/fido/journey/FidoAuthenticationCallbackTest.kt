/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.fido.journey

import androidx.credentials.exceptions.GetCredentialCancellationException
import com.pingidentity.fido.Constants
import com.pingidentity.fido.FidoClient
import com.pingidentity.journey.plugin.Callback
import com.pingidentity.journey.plugin.ValueCallback
import com.pingidentity.logger.CONSOLE
import com.pingidentity.logger.Logger
import com.pingidentity.orchestrate.ContinueNode
import com.pingidentity.orchestrate.FlowContext
import com.pingidentity.orchestrate.Request
import com.pingidentity.orchestrate.SharedContext
import com.pingidentity.orchestrate.Workflow
import com.pingidentity.orchestrate.WorkflowConfig
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class FidoAuthenticationCallbackTest {

    private lateinit var continueNode: ContinueNode
    private lateinit var mockWorkflow: Workflow
    private lateinit var mockWorkflowConfig: WorkflowConfig
    private lateinit var valueCallback: ValueCallback
    private lateinit var mockFidoClient: FidoClient
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
                return Request()
            }
        }

        every { mockWorkflow.config } returns mockWorkflowConfig
        every { mockWorkflowConfig.logger } returns Logger.CONSOLE


        mockFidoClient = mockk()
        mockkObject(FidoClient.Companion)
        every { FidoClient.invoke(any()) } returns mockFidoClient
    }

    @AfterTest
    fun tearDown() {
        unmockkObject(FidoClient.Companion)
    }

    @Test
    fun `init should parse publicKeyCredentialRequestOptions correctly`() {
        // Sample JSON as provided
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
                        put("relyingPartyId", "rpId: \"idc.petrov.ca\",")
                        put("_relyingPartyId", "idc.petrov.ca")
                        putJsonObject("extensions") { }
                        put("_type", "WebAuthn")
                        put("supportsJsonResponse", true)
                    }
                }
            }
        }

        val callback = FidoAuthenticationCallback()
        callback.journey = mockWorkflow
        callback.init(sampleJson)

        // Assert that the publicKeyCredentialRequestOptions is initialized and contains expected fields
        val options = callback.publicKeyCredentialRequestOptions
        assertEquals(
            "IrmRP2U3shw3plwrICzAkw_yupRI60s2dnGhfwExd_o",
            options["challenge"]?.jsonPrimitive?.content
        )
        assertEquals(60000, options["timeout"]?.jsonPrimitive?.int)
        assertEquals("required", options["userVerification"]?.jsonPrimitive?.content)
        assertEquals("idc.petrov.ca", options["rpId"]?.jsonPrimitive?.content)
        assertTrue(options["allowCredentials"]?.jsonArray?.isEmpty() == true)
    }

    @Test
    fun `init should parse publicKeyCredentialRequestOptions with multiple allowCredentials`() {
        val sampleJson = buildJsonObject {
            put("type", "MetadataCallback")
            putJsonArray("output") {
                addJsonObject {
                    put("name", "data")
                    putJsonObject("value") {
                        put("_action", "webauthn_authentication")
                        put("challenge", "8KzvMeaDpWr/SMOz6QSFaIsdlWdUyCEwRijF0HBA0nk=")
                        put(
                            "allowCredentials",
                            "allowCredentials: [{ \"type\": \"public-key\", \"id\": new Int8Array([-26, -52, 96, 28, 18, -70, -54, -114, 41, -46, -27, 45, -87, -125, 111, -36]).buffer },{ \"type\": \"public-key\", \"id\": new Int8Array([1, 51, -83, -20, 75, 95, 57, 33, 40, 72, -112, -69, 123, 71, 12, -43, -68, -38, -58, -96, 55, -126, 96, 111, 46, 4, 84, -119, 51, -19, -14, -93, 38, 72, 3, -103, 90, -25, -69, -31, -56, 24, -46, -38, -52, 109, -92, 104, 5, 32, -105, -95, -53, 61, 48, 28, -117, 80, 43, -14, 75, -67, 90, 1, 115]).buffer },{ \"type\": \"public-key\", \"id\": new Int8Array([-44, -116, -38, -20, 79, 86, 15, -98, 93, 57, 72, 4, -124, 75, -122, -73]).buffer },{ \"type\": \"public-key\", \"id\": new Int8Array([1, 71, 102, 103, 124, -107, 35, 120, 100, -94, -78, 101, 96, 87, -54, 23, -5, 122, 44, -1, 58, -40, -58, 108, 127, -93, -120, 102, 15, 36, -103, 101, -16, -47, -107, -111, -42, 36, -83, -123, -12, 109, 30, -3, 34, -103, 54, -106, 70, 80, 19, 30, -111, -6, 69, 18, 46, 114, 85, -47, -25, 19, 116, 37, -97]).buffer }]"
                        )
                        putJsonArray("_allowCredentials") {
                            addJsonObject {
                                put("type", "public-key")
                                putJsonArray("id") {
                                    listOf(
                                        -26,
                                        -52,
                                        96,
                                        28,
                                        18,
                                        -70,
                                        -54,
                                        -114,
                                        41,
                                        -46,
                                        -27,
                                        45,
                                        -87,
                                        -125,
                                        111,
                                        -36
                                    ).forEach { add(JsonPrimitive(it)) }
                                }
                            }
                            addJsonObject {
                                put("type", "public-key")
                                putJsonArray("id") {
                                    listOf(
                                        1,
                                        51,
                                        -83,
                                        -20,
                                        75,
                                        95,
                                        57,
                                        33,
                                        40,
                                        72,
                                        -112,
                                        -69,
                                        123,
                                        71,
                                        12,
                                        -43,
                                        -68,
                                        -38,
                                        -58,
                                        -96,
                                        55,
                                        -126,
                                        96,
                                        111,
                                        46,
                                        4,
                                        84,
                                        -119,
                                        51,
                                        -19,
                                        -14,
                                        -93,
                                        38,
                                        72,
                                        3,
                                        -103,
                                        90,
                                        -25,
                                        -69,
                                        -31,
                                        -56,
                                        24,
                                        -46,
                                        -38,
                                        -52,
                                        109,
                                        -92,
                                        104,
                                        5,
                                        32,
                                        -105,
                                        -95,
                                        -53,
                                        61,
                                        48,
                                        28,
                                        -117,
                                        80,
                                        43,
                                        -14,
                                        75,
                                        -67,
                                        90,
                                        1,
                                        115
                                    ).forEach { add(JsonPrimitive(it)) }
                                }
                            }
                            addJsonObject {
                                put("type", "public-key")
                                putJsonArray("id") {
                                    listOf(
                                        -44,
                                        -116,
                                        -38,
                                        -20,
                                        79,
                                        86,
                                        15,
                                        -98,
                                        93,
                                        57,
                                        72,
                                        4,
                                        -124,
                                        75,
                                        -122,
                                        -73
                                    ).forEach { add(JsonPrimitive(it)) }
                                }
                            }
                            addJsonObject {
                                put("type", "public-key")
                                putJsonArray("id") {
                                    listOf(
                                        1,
                                        71,
                                        102,
                                        103,
                                        124,
                                        -107,
                                        35,
                                        120,
                                        100,
                                        -94,
                                        -78,
                                        101,
                                        96,
                                        87,
                                        -54,
                                        23,
                                        -5,
                                        122,
                                        44,
                                        -1,
                                        58,
                                        -40,
                                        -58,
                                        108,
                                        127,
                                        -93,
                                        -120,
                                        102,
                                        15,
                                        36,
                                        -103,
                                        101,
                                        -16,
                                        -47,
                                        -107,
                                        -111,
                                        -42,
                                        36,
                                        -83,
                                        -123,
                                        -12,
                                        109,
                                        30,
                                        -3,
                                        34,
                                        -103,
                                        54,
                                        -106,
                                        70,
                                        80,
                                        19,
                                        30,
                                        -111,
                                        -6,
                                        69,
                                        18,
                                        46,
                                        114,
                                        85,
                                        -47,
                                        -25,
                                        19,
                                        116,
                                        37,
                                        -97
                                    ).forEach { add(JsonPrimitive(it)) }
                                }
                            }
                        }
                        put("timeout", "60000")
                        put("userVerification", "required")
                        put("relyingPartyId", "rpId: \"idc.petrov.ca\",")
                        put("_relyingPartyId", "idc.petrov.ca")
                        putJsonObject("extensions") { }
                        put("_type", "WebAuthn")
                        put("supportsJsonResponse", true)
                    }
                }
            }
        }

        val callback = FidoAuthenticationCallback()
        callback.journey = mockWorkflow
        callback.init(sampleJson)

        val options = callback.publicKeyCredentialRequestOptions
        assertEquals(
            "8KzvMeaDpWr_SMOz6QSFaIsdlWdUyCEwRijF0HBA0nk",
            options["challenge"]?.jsonPrimitive?.content
        )
        assertEquals(60000, options["timeout"]?.jsonPrimitive?.int)
        assertEquals("required", options["userVerification"]?.jsonPrimitive?.content)
        assertEquals("idc.petrov.ca", options["rpId"]?.jsonPrimitive?.content)
        val allowCredentials = options["allowCredentials"]?.jsonArray
        assertNotNull(allowCredentials)
        assertEquals(4, allowCredentials!!.size)
        allowCredentials.forEach { cred ->
            val obj = cred.jsonObject
            assertEquals("public-key", obj["type"]?.jsonPrimitive?.content)
            // The id should be a base64 string
            assertTrue(obj["id"]?.jsonPrimitive?.content?.isNotEmpty() == true)
        }
    }

    @Test
    fun `authenticate should return success and call valueCallback`() = runTest {
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
                        put("relyingPartyId", "rpId: \"idc.petrov.ca\",")
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

        // Mock Fido2.authenticate to always succeed
        val fakeResponse = buildJsonObject {
            put("response", buildJsonObject {
                put(
                    "clientDataJSON",
                    "eyJ0eXBlIjoid2ViYXV0aG4uZ2V0IiwiY2hhbGxlbmdlIjoiSjlDVmcxRkl6REhhd3BCLS0yeTZGc2pyX2RLTEtzTGNGcGRKanp0ZFBydyIsIm9yaWdpbiI6ImFuZHJvaWQ6YXBrLWtleS1oYXNoOlp2Rm5reUJBbTZFUHZNNTBGQUZVRDZ1MUduN3ZaaGd0OGpjcXNjb25fY28iLCJhbmRyb2lkUGFja2FnZU5hbWUiOiJjb20ucGluZ2lkZW50aXR5LnNhbXBsZXMuam91cm5leWFwcCJ9"
                )
                put("authenticatorData", "N2_Q5-0Y8GIS3KdDwe8960U5Hls64HVj4KuW_PJGdQIdAAAAAA")
                put(
                    "signature",
                    "MEUCIES2SaVu-5e_A-PQ0caU2yd1gXR8zI-_gTMMgUSTTk2rAiEAqkHPuUcc1I1cicdWXLKwZE6bGi7uy3PjAP9U93CqesI"
                )
                put("userHandle", "MThhYzY4OWUtNjNlOC00ODcxLTg1ZWEtMzU4MzIzNjRiNDgx")
            })
            put("rawId", "rawId")
        }

        coEvery { mockFidoClient.authenticate(any(), any()) } returns Result.success(fakeResponse)

        val result = callback.authenticate()
        assertTrue(result.isSuccess)
        val jsonResult = result.getOrThrow()
        assertNotNull(jsonResult)
        assertTrue(jsonResult.contains("response"))
        val valueCallbackString = valueCallback.value
        assertEquals(
            "{\"type\":\"webauthn.get\",\"challenge\":\"J9CVg1FIzDHawpB--2y6Fsjr_dKLKsLcFpdJjztdPrw\",\"origin\":\"android:apk-key-hash:ZvFnkyBAm6EPvM50FAFUD6u1Gn7vZhgt8jcqscon_co\",\"androidPackageName\":\"com.pingidentity.samples.journeyapp\"}::55,111,-48,-25,-19,24,-16,98,18,-36,-89,67,-63,-17,61,-21,69,57,30,91,58,-32,117,99,-32,-85,-106,-4,-14,70,117,2,29,0,0,0,0::48,69,2,32,68,-74,73,-91,110,-5,-105,-65,3,-29,-48,-47,-58,-108,-37,39,117,-127,116,124,-52,-113,-65,-127,51,12,-127,68,-109,78,77,-85,2,33,0,-86,65,-49,-71,71,28,-44,-115,92,-119,-57,86,92,-78,-80,100,78,-101,26,46,-18,-53,115,-29,0,-1,84,-9,112,-86,122,-62::rawId::18ac689e-63e8-4871-85ea-35832364b481",
            valueCallbackString
        )
    }

    @Test
    fun `authenticate should return success and call valueCallback with json response`() = runTest {
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
                        put("relyingPartyId", "rpId: \"idc.petrov.ca\",")
                        put("_relyingPartyId", "idc.petrov.ca")
                        putJsonObject("extensions") { }
                        put("_type", "WebAuthn")
                        put("supportsJsonResponse", true)
                    }
                }
            }
        }

        val callback = FidoAuthenticationCallback()
        callback.continueNode = continueNode
        callback.journey = mockWorkflow
        callback.init(sampleJson)

        // Mock Fido2.authenticate to always succeed
        val fakeResponse = buildJsonObject {
            put("response", buildJsonObject {
                put(
                    "clientDataJSON",
                    "eyJ0eXBlIjoid2ViYXV0aG4uZ2V0IiwiY2hhbGxlbmdlIjoiSjlDVmcxRkl6REhhd3BCLS0yeTZGc2pyX2RLTEtzTGNGcGRKanp0ZFBydyIsIm9yaWdpbiI6ImFuZHJvaWQ6YXBrLWtleS1oYXNoOlp2Rm5reUJBbTZFUHZNNTBGQUZVRDZ1MUduN3ZaaGd0OGpjcXNjb25fY28iLCJhbmRyb2lkUGFja2FnZU5hbWUiOiJjb20ucGluZ2lkZW50aXR5LnNhbXBsZXMuam91cm5leWFwcCJ9"
                )
                put("authenticatorData", "N2_Q5-0Y8GIS3KdDwe8960U5Hls64HVj4KuW_PJGdQIdAAAAAA")
                put(
                    "signature",
                    "MEUCIES2SaVu-5e_A-PQ0caU2yd1gXR8zI-_gTMMgUSTTk2rAiEAqkHPuUcc1I1cicdWXLKwZE6bGi7uy3PjAP9U93CqesI"
                )
                put("userHandle", "MThhYzY4OWUtNjNlOC00ODcxLTg1ZWEtMzU4MzIzNjRiNDgx")
            })
            put("rawId", "rawId")
        }

        coEvery { mockFidoClient.authenticate(any(), any()) } returns Result.success(fakeResponse)

        val result = callback.authenticate()
        assertTrue(result.isSuccess)
        val jsonResult = result.getOrThrow()
        assertNotNull(jsonResult)
        assertTrue(jsonResult.contains("response"))
        val valueCallbackString = valueCallback.value
        assertEquals(
            "{\"authenticatorAttachment\":\"platform\",\"legacyData\":\"{\\\"type\\\":\\\"webauthn.get\\\",\\\"challenge\\\":\\\"J9CVg1FIzDHawpB--2y6Fsjr_dKLKsLcFpdJjztdPrw\\\",\\\"origin\\\":\\\"android:apk-key-hash:ZvFnkyBAm6EPvM50FAFUD6u1Gn7vZhgt8jcqscon_co\\\",\\\"androidPackageName\\\":\\\"com.pingidentity.samples.journeyapp\\\"}::55,111,-48,-25,-19,24,-16,98,18,-36,-89,67,-63,-17,61,-21,69,57,30,91,58,-32,117,99,-32,-85,-106,-4,-14,70,117,2,29,0,0,0,0::48,69,2,32,68,-74,73,-91,110,-5,-105,-65,3,-29,-48,-47,-58,-108,-37,39,117,-127,116,124,-52,-113,-65,-127,51,12,-127,68,-109,78,77,-85,2,33,0,-86,65,-49,-71,71,28,-44,-115,92,-119,-57,86,92,-78,-80,100,78,-101,26,46,-18,-53,115,-29,0,-1,84,-9,112,-86,122,-62::rawId::18ac689e-63e8-4871-85ea-35832364b481\"}",
            valueCallbackString
        )
    }

    @Test
    fun `authenticate should return failure and call handleError`() = runTest {
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
                        put("relyingPartyId", "rpId: \"idc.petrov.ca\",")
                        put("_relyingPartyId", "idc.petrov.ca")
                        putJsonObject("extensions") { }
                        put("_type", "WebAuthn")
                        put("supportsJsonResponse", true)
                    }
                }
            }
        }

        val callback = FidoAuthenticationCallback()
        callback.continueNode = continueNode
        callback.journey = mockWorkflow
        callback.init(sampleJson)

        // Mock Fido2.authenticate to always fail
        coEvery { mockFidoClient.authenticate(any(), any()) } returns Result.failure(
            GetCredentialCancellationException("auth error")
        )

        val result = callback.authenticate()
        assertTrue(result.isFailure)
        val e = result.exceptionOrNull()
        assertTrue(e is GetCredentialCancellationException)
    }
}

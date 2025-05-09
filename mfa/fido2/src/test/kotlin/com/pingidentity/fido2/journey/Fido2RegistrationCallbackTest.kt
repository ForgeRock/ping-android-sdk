/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.fido2.journey

import androidx.credentials.exceptions.CreateCredentialCancellationException
import com.pingidentity.fido2.Constants
import com.pingidentity.fido2.Fido2
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
import kotlinx.serialization.json.boolean
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@RunWith(RobolectricTestRunner::class)
class Fido2RegistrationCallbackTest {

    private lateinit var continueNode: ContinueNode
    private lateinit var mockWorkflow: Workflow
    private lateinit var mockWorkflowConfig: WorkflowConfig
    private lateinit var valueCallback: ValueCallback

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

        mockkObject(Fido2)
    }

    @AfterTest
    fun tearDown() {
        unmockkObject(Fido2)
    }

    @Test
    fun `init should parse publicKeyCredentialCreationOptions correctly`() {
        val sampleJson = buildJsonObject {
            put("type", "MetadataCallback")
            putJsonArray("output") {
                addJsonObject {
                    put("name", "data")
                    putJsonObject("value") {
                        put("_action", "webauthn_registration")
                        put("challenge", "Y2hhbGxlbmdl")
                        put("timeout", "60000")
                        put("attestationPreference", "direct")
                        put("relyingPartyName", "Test RP")
                        put("_relyingPartyId", "test.example.com")
                        put("userId", "dXNlcklk")
                        put("userName", "testuser")
                        put("displayName", "Test User")
                        putJsonArray("_pubKeyCredParams") {
                            addJsonObject {
                                put("type", "public-key")
                                put("alg", -7)
                            }
                        }
                        putJsonArray("_excludeCredentials") {
                            addJsonObject {
                                put("type", "public-key")
                                putJsonArray("id") {
                                    listOf(1, 2, 3, 4).forEach { add(JsonPrimitive(it)) }
                                }
                            }
                        }
                        putJsonObject("_authenticatorSelection") {
                            put("authenticatorAttachment", "platform")
                            put("requireResidentKey", true)
                            put("userVerification", "required")
                        }
                        put("supportsJsonResponse", true)
                    }
                }
            }
        }

        val callback = Fido2RegistrationCallback()
        callback.continueNode = continueNode
        callback.journey = mockWorkflow
        callback.init(sampleJson)

        val options = callback.publicKeyCredentialCreationOptions
        assertEquals("Y2hhbGxlbmdl", options["challenge"]?.jsonPrimitive?.content)
        assertEquals(60000, options["timeout"]?.jsonPrimitive?.int)
        assertEquals("direct", options["attestation"]?.jsonPrimitive?.content)
        assertEquals("Test RP", options["rp"]?.jsonObject?.get("name")?.jsonPrimitive?.content)
        assertEquals(
            "test.example.com",
            options["rp"]?.jsonObject?.get("id")?.jsonPrimitive?.content
        )
        assertEquals("dXNlcklk", options["user"]?.jsonObject?.get("id")?.jsonPrimitive?.content)
        assertEquals("testuser", options["user"]?.jsonObject?.get("name")?.jsonPrimitive?.content)
        assertEquals(
            "Test User",
            options["user"]?.jsonObject?.get("displayName")?.jsonPrimitive?.content
        )
        assertEquals(1, options["pubKeyCredParams"]?.jsonArray?.size)
        assertEquals(1, options["excludeCredentials"]?.jsonArray?.size)
        assertEquals(
            "platform",
            options["authenticatorSelection"]?.jsonObject?.get("authenticatorAttachment")?.jsonPrimitive?.content
        )
        assertEquals(
            true,
            options["authenticatorSelection"]?.jsonObject?.get("requireResidentKey")?.jsonPrimitive?.boolean
        )
        assertEquals(
            "required",
            options["authenticatorSelection"]?.jsonObject?.get("userVerification")?.jsonPrimitive?.content
        )
    }

    @Test
    fun `register should return success and call valueCallback`() = runTest {
        val sampleJson = buildJsonObject {
            put("type", "MetadataCallback")
            putJsonArray("output") {
                addJsonObject {
                    put("name", "data")
                    putJsonObject("value") {
                        put("_action", "webauthn_registration")
                        put("challenge", "Y2hhbGxlbmdl")
                        put("timeout", "60000")
                        put("attestationPreference", "direct")
                        put("relyingPartyName", "Test RP")
                        put("_relyingPartyId", "test.example.com")
                        put("userId", "dXNlcklk")
                        put("userName", "testuser")
                        put("displayName", "Test User")
                        putJsonArray("_pubKeyCredParams") {
                            addJsonObject {
                                put("type", "public-key")
                                put("alg", -7)
                            }
                        }
                        putJsonArray("_excludeCredentials") { }
                        putJsonObject("_authenticatorSelection") {
                            put("authenticatorAttachment", "platform")
                            put("requireResidentKey", false)
                            put("userVerification", "required")
                        }
                        put("supportsJsonResponse", false)
                    }
                }
            }
        }

        val callback = Fido2RegistrationCallback()
        callback.continueNode = continueNode
        callback.journey = mockWorkflow
        callback.init(sampleJson)

        // Mock Fido2.register to always succeed
        val fakeResponse = buildJsonObject {
            put("response", buildJsonObject {
                put(
                    "clientDataJSON",
                    "eyJ0eXBlIjoid2ViYXV0aG4uY3JlYXRlIiwiY2hhbGxlbmdlIjoiWTJoaGJHeGxibWRsIiwib3JpZ2luIjoiYW5kcm9pZDphcGsta2V5LWhhc2g6WnZGbmt5QkFtNkVQdk01MEZBRIZENU7NuMQ3ZVaWhndDjIlcXNjaYjl-X2NvIiwiYW5kcm9pZFBhY2thZ2VOYW1lIjoiY29tLnBpbmdpZGVudGl0eS5zYW1wbGVzLmpvdXJuZXlhcHAifQ"
                )
                put(
                    "attestationObject",
                    "o2NmbXRkbm9uZWdhdHRTdG10oGhhdXRoRGF0YVikN2_Q5-0Y8GIS3KdDwe8960U5Hls64HVj4KuW_PJGdQIdQQAAAAAAAAAAAAAAAAAAAAAAABAQNJ7WyKh_7C_vOb9N6hwkqaUBAgMmIAEhWCDMmfOGx_FQXNYayT_M6k20-g9N8TGZBvZKKhQLFYCzrOQ4pCFYIJGi3LjDNKYGYLr3bP1Ep_tT4Z2Th2qv_b0JdOOm6lH0"
                )
            })
            put("rawId", "EDSe1siof-wv7zm_TeocJKml")
        }

        coEvery { Fido2.register(any()) } returns Result.success(fakeResponse)

        val result = callback.register("Test Device")
        assertTrue(result.isSuccess)
        val jsonResult = result.getOrThrow()
        assertNotNull(jsonResult)
        assertTrue(jsonResult.contains("response"))
        val valueCallbackString = valueCallback.value
        assertTrue(valueCallbackString.contains("Test Device"))
        assertTrue(valueCallbackString.contains("::"))
    }

    @Test
    fun `register should return success with JSON response format`() = runTest {
        val sampleJson = buildJsonObject {
            put("type", "MetadataCallback")
            putJsonArray("output") {
                addJsonObject {
                    put("name", "data")
                    putJsonObject("value") {
                        put("_action", "webauthn_registration")
                        put("challenge", "Y2hhbGxlbmdl")
                        put("timeout", "60000")
                        put("attestationPreference", "direct")
                        put("relyingPartyName", "Test RP")
                        put("_relyingPartyId", "test.example.com")
                        put("userId", "dXNlcklk")
                        put("userName", "testuser")
                        put("displayName", "Test User")
                        putJsonArray("_pubKeyCredParams") {
                            addJsonObject {
                                put("type", "public-key")
                                put("alg", -7)
                            }
                        }
                        putJsonArray("_excludeCredentials") { }
                        putJsonObject("_authenticatorSelection") {
                            put("authenticatorAttachment", "platform")
                            put("requireResidentKey", false)
                            put("userVerification", "required")
                        }
                        put("supportsJsonResponse", true)
                    }
                }
            }
        }

        val callback = Fido2RegistrationCallback()
        callback.continueNode = continueNode
        callback.journey = mockWorkflow
        callback.init(sampleJson)

        val fakeResponse = buildJsonObject {
            put("response", buildJsonObject {
                put("clientDataJSON", "eyJ0eXBlIjoid2ViYXV0aG4uY3JlYXRlIn0")
                put("attestationObject", "o2NmbXRkbm9uZQ")
            })
            put("rawId", "rawId")
        }

        coEvery { Fido2.register(any()) } returns Result.success(fakeResponse)

        val result = callback.register()
        assertTrue(result.isSuccess)
        val valueCallbackString = valueCallback.value
        assertEquals("{\"authenticatorAttachment\":\"platform\",\"legacyData\":\"{\\\"type\\\":\\\"webauthn.create\\\"}::-93,99,102,109,116,100,110,111,110,101::rawId\"}", valueCallbackString)
    }

    @Test
    fun `register should return failure and call handleError`() = runTest {
        val sampleJson = buildJsonObject {
            put("type", "MetadataCallback")
            putJsonArray("output") {
                addJsonObject {
                    put("name", "data")
                    putJsonObject("value") {
                        put("_action", "webauthn_registration")
                        put("challenge", "Y2hhbGxlbmdl")
                        put("timeout", "60000")
                        put("attestationPreference", "direct")
                        put("relyingPartyName", "Test RP")
                        put("_relyingPartyId", "test.example.com")
                        put("userId", "dXNlcklk")
                        put("userName", "testuser")
                        put("displayName", "Test User")
                        putJsonArray("_pubKeyCredParams") {
                            addJsonObject {
                                put("type", "public-key")
                                put("alg", -7)
                            }
                        }
                        putJsonArray("_excludeCredentials") { }
                        putJsonObject("_authenticatorSelection") {
                            put("authenticatorAttachment", "platform")
                            put("requireResidentKey", false)
                            put("userVerification", "required")
                        }
                        put("supportsJsonResponse", false)
                    }
                }
            }
        }

        val callback = Fido2RegistrationCallback()
        callback.journey = mockWorkflow
        callback.continueNode = continueNode
        callback.init(sampleJson)

        // Mock Fido2.register to always fail
        coEvery { Fido2.register(any()) } returns Result.failure(
            CreateCredentialCancellationException("registration error")
        )

        val result = callback.register()
        assertTrue(result.isFailure)
        val e = result.exceptionOrNull()
        assertTrue(e is CreateCredentialCancellationException)
    }
}


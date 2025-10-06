/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.fido2.davinci

import com.pingidentity.davinci.plugin.DaVinci
import com.pingidentity.fido2.Constants
import com.pingidentity.fido2.Fido2Client
import com.pingidentity.logger.CONSOLE
import com.pingidentity.logger.Logger
import com.pingidentity.orchestrate.WorkflowConfig
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@RunWith(RobolectricTestRunner::class)
class Fido2RegistrationCollectorTest {

    private lateinit var collector: Fido2RegistrationCollector
    private lateinit var mockFido2Client: Fido2Client

    @BeforeTest
    fun setup() {
        val daVinci = mockk<DaVinci>()
        val config = mockk<WorkflowConfig>()
        val logger = Logger.CONSOLE
        every { daVinci.config } returns config
        every { config.logger } returns logger

        collector = Fido2RegistrationCollector()
        collector.davinci = daVinci

        mockFido2Client = mockk()
        mockkObject(Fido2Client.Companion)
        every { Fido2Client.invoke(any()) } returns mockFido2Client
    }

    @AfterTest
    fun tearDown() {
        unmockkObject(Fido2Client.Companion)
    }


    private fun getRegistrationInput(): JsonObject = buildJsonObject {
        put("type", JsonPrimitive("FIDO2"))
        put("key", JsonPrimitive("fido2"))
        put("label", JsonPrimitive("Continue"))
        put(Constants.FIELD_PUBLIC_KEY_CREDENTIAL_CREATION_OPTIONS, buildJsonObject {
            put("rp", buildJsonObject {
                put("id", JsonPrimitive("idc.petrov.ca"))
                put("name", JsonPrimitive("pingone.ca"))
            })
            put("user", buildJsonObject {
                put(
                    "id", JsonArray(
                        listOf(
                            JsonPrimitive(24), JsonPrimitive(-76), JsonPrimitive(-64)
                        )
                    )
                )
                put("displayName", JsonPrimitive("test@example.com"))
                put("name", JsonPrimitive("test@example.com"))
            })
            put(
                "challenge", JsonArray(
                    listOf(
                        JsonPrimitive(-68), JsonPrimitive(87), JsonPrimitive(-120)
                    )
                )
            )
            put(
                "pubKeyCredParams", JsonArray(
                listOf(
                buildJsonObject {
                    put("type", JsonPrimitive("public-key"))
                    put("alg", JsonPrimitive("-7"))
                }
            )))
            put("timeout", JsonPrimitive(120000))
            put("attestation", JsonPrimitive("none"))
        })
        put("action", JsonPrimitive("REGISTER"))
        put("trigger", JsonPrimitive("BUTTON"))
        put("required", JsonPrimitive(true))
    }


    @Test
    fun `init should parse and transform input correctly`() {
        collector.init(getRegistrationInput())
        val options = collector.publicKeyCredentialCreationOptions

        // Check basic fields
        assertEquals("idc.petrov.ca", options["rp"]?.jsonObject?.get("id")?.jsonPrimitive?.content)
        assertEquals("pingone.ca", options["rp"]?.jsonObject?.get("name")?.jsonPrimitive?.content)

        // Check user id is base64url encoded
        val userId = options["user"]?.jsonObject?.get("id")?.jsonPrimitive?.content
        assertEquals("GLTA", userId) // Base64UrlSafe encoded [24, -76, -64]

        // Check challenge is base64url encoded
        val challenge = options["challenge"]?.jsonPrimitive?.content
        assertEquals("vFeI", challenge) // Base64UrlSafe encoded [-68, 87, -120]
    }

    @Test
    fun `payload should return null if not registered`() {
        collector.init(getRegistrationInput())
        assertNull(collector.payload())
    }

    @Test
    fun `payload should return attestationValue after register`() = runTest {
        collector.init(getRegistrationInput())
        val attestation = buildJsonObject { put("test", JsonPrimitive("value")) }

        coEvery { mockFido2Client.register(any(), any()) } returns Result.success(attestation)

        collector.register()
        val payload = collector.payload()

        assertNotNull(payload)
        assertEquals(attestation, payload?.get(Constants.FIELD_ATTESTATION_VALUE)?.jsonObject)
    }

    @Test
    fun `register should propagate failure`() = runTest {
        collector.init(getRegistrationInput())

        val exception = Exception("Registration failed")
        coEvery { mockFido2Client.register(any(), any()) } returns Result.failure(exception)

        val result = collector.register()

        assertTrue(result.isFailure)
        assertEquals(exception, result.exceptionOrNull())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `init should throw exception when missing creation options`() {
        val invalidInput = buildJsonObject {
            put("type", JsonPrimitive("FIDO2"))
            put("action", JsonPrimitive("REGISTER"))
        }
        collector.init(invalidInput)
    }

    @Test
    fun `transform should handle excludeCredentials with byte array id`() {
        val credentialIdBytes = byteArrayOf(-127, 88, 77, -67, -80, -80, 119, -108, -58, 18, -55, 81, -108, -121, 18, 5)

        val inputWithExcludeCredentials = buildJsonObject {
            put("type", JsonPrimitive("FIDO2"))
            put("key", JsonPrimitive("fido2"))
            put("label", JsonPrimitive("Continue"))
            put(Constants.FIELD_PUBLIC_KEY_CREDENTIAL_CREATION_OPTIONS, buildJsonObject {
                put("rp", buildJsonObject {
                    put("id", JsonPrimitive("idc.petrov.ca"))
                    put("name", JsonPrimitive("pingone.ca"))
                })
                put("user", buildJsonObject {
                    put("id", JsonArray(listOf(JsonPrimitive(24), JsonPrimitive(-76), JsonPrimitive(-64))))
                    put("displayName", JsonPrimitive("test@example.com"))
                    put("name", JsonPrimitive("test@example.com"))
                })
                put("challenge", JsonArray(listOf(JsonPrimitive(-68), JsonPrimitive(87), JsonPrimitive(-120))))
                put(Constants.FIELD_EXCLUDE_CREDENTIALS, JsonArray(listOf(
                    buildJsonObject {
                        put(Constants.FIELD_TYPE, JsonPrimitive("public-key"))
                        put(Constants.FIELD_ID, JsonArray(credentialIdBytes.map { JsonPrimitive(it.toInt()) }))
                    }
                )))
            })
            put("action", JsonPrimitive("REGISTER"))
        }

        collector.init(inputWithExcludeCredentials)
        val options = collector.publicKeyCredentialCreationOptions

        val excludeCredentials = options[Constants.FIELD_EXCLUDE_CREDENTIALS]?.let { it as? JsonArray }
        assertNotNull(excludeCredentials)
        assertEquals(1, excludeCredentials!!.size)

        val credential = excludeCredentials[0].jsonObject
        assertEquals("public-key", credential[Constants.FIELD_TYPE]?.jsonPrimitive?.content)

        // Verify the ID is now base64 encoded
        val credentialId = credential[Constants.FIELD_ID]?.jsonPrimitive?.content
        assertNotNull(credentialId)
        // The byte array should be converted to base64
        assertTrue(credentialId!!.isNotEmpty())
    }

    @Test
    fun `transform should handle multiple excludeCredentials`() {
        val credentialId1 = byteArrayOf(-127, 88, 77, -67, -80, -80, 119, -108)
        val credentialId2 = byteArrayOf(18, -55, 81, -108, -121, 18, 5, 42)

        val inputWithMultipleExcludeCredentials = buildJsonObject {
            put("type", JsonPrimitive("FIDO2"))
            put("key", JsonPrimitive("fido2"))
            put(Constants.FIELD_PUBLIC_KEY_CREDENTIAL_CREATION_OPTIONS, buildJsonObject {
                put("rp", buildJsonObject {
                    put("id", JsonPrimitive("idc.petrov.ca"))
                })
                put("user", buildJsonObject {
                    put("id", JsonArray(listOf(JsonPrimitive(24))))
                    put("name", JsonPrimitive("test"))
                })
                put("challenge", JsonArray(listOf(JsonPrimitive(-68))))
                put(Constants.FIELD_EXCLUDE_CREDENTIALS, JsonArray(listOf(
                    buildJsonObject {
                        put(Constants.FIELD_TYPE, JsonPrimitive("public-key"))
                        put(Constants.FIELD_ID, JsonArray(credentialId1.map { JsonPrimitive(it.toInt()) }))
                    },
                    buildJsonObject {
                        put(Constants.FIELD_TYPE, JsonPrimitive("public-key"))
                        put(Constants.FIELD_ID, JsonArray(credentialId2.map { JsonPrimitive(it.toInt()) }))
                    }
                )))
            })
            put("action", JsonPrimitive("REGISTER"))
        }

        collector.init(inputWithMultipleExcludeCredentials)
        val options = collector.publicKeyCredentialCreationOptions

        val excludeCredentials = options[Constants.FIELD_EXCLUDE_CREDENTIALS]?.let { it as? JsonArray }
        assertNotNull(excludeCredentials)
        assertEquals(2, excludeCredentials!!.size)

        // Verify both credentials are transformed
        val credential1 = excludeCredentials[0].jsonObject
        val credential2 = excludeCredentials[1].jsonObject

        assertNotNull(credential1[Constants.FIELD_ID]?.jsonPrimitive?.content)
        assertNotNull(credential2[Constants.FIELD_ID]?.jsonPrimitive?.content)
    }

    @Test
    fun `transform should handle excludeCredentials with already encoded id`() {
        val base64Id = "gVhNvbC0d5TGEs1RlIcSBQ=="

        val inputWithBase64ExcludeCredentials = buildJsonObject {
            put("type", JsonPrimitive("FIDO2"))
            put("key", JsonPrimitive("fido2"))
            put(Constants.FIELD_PUBLIC_KEY_CREDENTIAL_CREATION_OPTIONS, buildJsonObject {
                put("rp", buildJsonObject {
                    put("id", JsonPrimitive("idc.petrov.ca"))
                })
                put("user", buildJsonObject {
                    put("id", JsonArray(listOf(JsonPrimitive(24))))
                    put("name", JsonPrimitive("test"))
                })
                put("challenge", JsonArray(listOf(JsonPrimitive(-68))))
                put(Constants.FIELD_EXCLUDE_CREDENTIALS, JsonArray(listOf(
                    buildJsonObject {
                        put(Constants.FIELD_TYPE, JsonPrimitive("public-key"))
                        put(Constants.FIELD_ID, JsonPrimitive(base64Id))
                    }
                )))
            })
            put("action", JsonPrimitive("REGISTER"))
        }

        collector.init(inputWithBase64ExcludeCredentials)
        val options = collector.publicKeyCredentialCreationOptions

        val excludeCredentials = options[Constants.FIELD_EXCLUDE_CREDENTIALS]?.let { it as? JsonArray }
        assertNotNull(excludeCredentials)
        assertEquals(1, excludeCredentials!!.size)

        val credential = excludeCredentials[0].jsonObject
        assertEquals(base64Id, credential[Constants.FIELD_ID]?.jsonPrimitive?.content)
    }

    @Test
    fun `transform should handle empty excludeCredentials array`() {
        val inputWithEmptyExcludeCredentials = buildJsonObject {
            put("type", JsonPrimitive("FIDO2"))
            put("key", JsonPrimitive("fido2"))
            put(Constants.FIELD_PUBLIC_KEY_CREDENTIAL_CREATION_OPTIONS, buildJsonObject {
                put("rp", buildJsonObject {
                    put("id", JsonPrimitive("idc.petrov.ca"))
                })
                put("user", buildJsonObject {
                    put("id", JsonArray(listOf(JsonPrimitive(24))))
                    put("name", JsonPrimitive("test"))
                })
                put("challenge", JsonArray(listOf(JsonPrimitive(-68))))
                put(Constants.FIELD_EXCLUDE_CREDENTIALS, JsonArray(emptyList()))
            })
            put("action", JsonPrimitive("REGISTER"))
        }

        collector.init(inputWithEmptyExcludeCredentials)
        val options = collector.publicKeyCredentialCreationOptions

        val excludeCredentials = options[Constants.FIELD_EXCLUDE_CREDENTIALS]?.let { it as? JsonArray }
        assertNotNull(excludeCredentials)
        assertEquals(0, excludeCredentials!!.size)
    }

    @Test
    fun `transform should work without excludeCredentials field`() {
        val inputWithoutExcludeCredentials = buildJsonObject {
            put("type", JsonPrimitive("FIDO2"))
            put("key", JsonPrimitive("fido2"))
            put(Constants.FIELD_PUBLIC_KEY_CREDENTIAL_CREATION_OPTIONS, buildJsonObject {
                put("rp", buildJsonObject {
                    put("id", JsonPrimitive("idc.petrov.ca"))
                })
                put("user", buildJsonObject {
                    put("id", JsonArray(listOf(JsonPrimitive(24))))
                    put("name", JsonPrimitive("test"))
                })
                put("challenge", JsonArray(listOf(JsonPrimitive(-68))))
            })
            put("action", JsonPrimitive("REGISTER"))
        }

        collector.init(inputWithoutExcludeCredentials)
        val options = collector.publicKeyCredentialCreationOptions

        assertNull(options[Constants.FIELD_EXCLUDE_CREDENTIALS])
    }

    @Test
    fun `transform should preserve other fields in excludeCredentials`() {
        val credentialIdBytes = byteArrayOf(1, 2, 3, 4)

        val inputWithExtraFields = buildJsonObject {
            put("type", JsonPrimitive("FIDO2"))
            put("key", JsonPrimitive("fido2"))
            put(Constants.FIELD_PUBLIC_KEY_CREDENTIAL_CREATION_OPTIONS, buildJsonObject {
                put("rp", buildJsonObject {
                    put("id", JsonPrimitive("idc.petrov.ca"))
                })
                put("user", buildJsonObject {
                    put("id", JsonArray(listOf(JsonPrimitive(24))))
                    put("name", JsonPrimitive("test"))
                })
                put("challenge", JsonArray(listOf(JsonPrimitive(-68))))
                put(Constants.FIELD_EXCLUDE_CREDENTIALS, JsonArray(listOf(
                    buildJsonObject {
                        put(Constants.FIELD_TYPE, JsonPrimitive("public-key"))
                        put(Constants.FIELD_ID, JsonArray(credentialIdBytes.map { JsonPrimitive(it.toInt()) }))
                        put("transports", JsonArray(listOf(JsonPrimitive("usb"), JsonPrimitive("nfc"))))
                    }
                )))
            })
            put("action", JsonPrimitive("REGISTER"))
        }

        collector.init(inputWithExtraFields)
        val options = collector.publicKeyCredentialCreationOptions

        val excludeCredentials = options[Constants.FIELD_EXCLUDE_CREDENTIALS]?.let { it as? JsonArray }
        assertNotNull(excludeCredentials)
        assertEquals(1, excludeCredentials!!.size)

        val credential = excludeCredentials[0].jsonObject
        assertEquals("public-key", credential[Constants.FIELD_TYPE]?.jsonPrimitive?.content)
        assertNotNull(credential[Constants.FIELD_ID]?.jsonPrimitive?.content)

        // Verify transports field is preserved
        val transports = credential["transports"]?.let { it as? JsonArray }
        assertNotNull(transports)
        assertEquals(2, transports!!.size)
        assertEquals("usb", transports[0].jsonPrimitive.content)
        assertEquals("nfc", transports[1].jsonPrimitive.content)
    }
}

/*
 * Copyright (c) 2025 - 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.recognize.journey

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.junit.Assert.assertTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RecognizeCallbackTest {

    @Test
    fun `returns PingOneRecognizeEnrollCallback for ENROLL operationType`() {
        val result = RecognizeCallback().init(buildCallbackJson("ENROLL"))

        assertTrue(result is PingOneRecognizeEnrollCallback)
    }

    @Test
    fun `returns PingOneRecognizeAuthenticateCallback for AUTHENTICATE operationType`() {
        val result = RecognizeCallback().init(buildCallbackJson("AUTHENTICATE"))

        assertTrue(result is PingOneRecognizeAuthenticateCallback)
    }

    @Test
    fun `returned enroll callback has common fields populated from server output`() {
        val result = RecognizeCallback().init(buildCallbackJson("ENROLL")) as PingOneRecognizeEnrollCallback

        assertEquals("https://auth.example.com", result.websocketURL)
        assertEquals("Acme Corp", result.customerName)
        assertEquals("pub-key-abc", result.imageEncryptionPublicKey)
        assertEquals("key-id-123", result.imageEncryptionKeyId)
        assertEquals("https://host.example.com", result.host)
        assertEquals("api-key-xyz", result.apiKey)
        assertEquals("john.doe", result.username)
        assertEquals("tx-data-payload", result.transactionData)
        assertEquals("true", result.generateClientState)
        assertEquals("existing-client-state", result.clientState)
    }

    @Test
    fun `returned authenticate callback has common fields populated from server output`() {
        val result = RecognizeCallback().init(buildCallbackJson("AUTHENTICATE")) as PingOneRecognizeAuthenticateCallback

        assertEquals("https://auth.example.com", result.websocketURL)
        assertEquals("Acme Corp", result.customerName)
        assertEquals("https://host.example.com", result.host)
        assertEquals("api-key-xyz", result.apiKey)
        assertEquals("john.doe", result.username)
        assertEquals("tx-data-payload", result.transactionData)
    }

    @Test
    fun `returned callback payload matches the original server JSON`() {
        val json = buildCallbackJson("ENROLL")
        val result = RecognizeCallback().init(json)

        assertEquals(json, result.payload())
    }

    @Test
    fun `throws IllegalArgumentException when operationType is missing from output`() {
        val json = buildJsonObject {
            put("type", "PingOneRecognizeCallback")
            putJsonArray("output") {
                addJsonObject {
                    put("name", "host")
                    put("value", "https://host.example.com")
                }
            }
        }

        assertFailsWith<IllegalArgumentException> {
            RecognizeCallback().init(json)
        }
    }

    @Test
    fun `throws IllegalArgumentException for unsupported operationType`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            RecognizeCallback().init(buildCallbackJson("REGISTER"))
        }
        assertTrue(exception.message!!.contains("REGISTER"))
        assertTrue(exception.message!!.contains("not supported"))
    }

    @Test
    fun `error message for missing operationType mentions the field name`() {
        val json = buildJsonObject {
            put("type", "PingOneRecognizeCallback")
            putJsonArray("output") { }
        }

        val exception = assertFailsWith<IllegalArgumentException> {
            RecognizeCallback().init(json)
        }
        assertTrue(exception.message!!.contains("operationType"))
    }

    @Test
    fun `mobileSDKOptions JsonObject is parsed into the returned callback`() {
        val json = buildJsonObject {
            put("type", "PingOneRecognizeCallback")
            putJsonArray("output") {
                addJsonObject {
                    put("name", "operationType")
                    put("value", "ENROLL")
                }
                addJsonObject {
                    put("name", "mobileSDKOptions")
                    put("value", buildJsonObject {
                        put("livenessConfiguration", "LEVEL_1")
                        put("cameraDelaySeconds", "2")
                        put("showSuccessFeedback", "true")
                    })
                }
            }
        }

        val result = RecognizeCallback().init(json) as PingOneRecognizeEnrollCallback

        assertEquals("LEVEL_1", result.mobileSDKOptions["livenessConfiguration"]?.toString()?.trim('"'))
    }

    @Test
    fun `webSDKOptions JsonObject is parsed into the returned callback`() {
        val json = buildJsonObject {
            put("type", "PingOneRecognizeCallback")
            putJsonArray("output") {
                addJsonObject {
                    put("name", "operationType")
                    put("value", "AUTHENTICATE")
                }
                addJsonObject {
                    put("name", "webSDKOptions")
                    put("value", buildJsonObject {
                        put("authorizationToken", "token-abc")
                        put("operationId", "op-123")
                    })
                }
            }
        }

        val result = RecognizeCallback().init(json) as PingOneRecognizeAuthenticateCallback

        assertEquals("token-abc", result.webSDKOptions["authorizationToken"]?.toString()?.trim('"'))
    }

    @Test
    fun `output fields not in the schema are silently ignored`() {
        val json = buildJsonObject {
            put("type", "PingOneRecognizeCallback")
            putJsonArray("output") {
                addJsonObject {
                    put("name", "operationType")
                    put("value", "ENROLL")
                }
                addJsonObject {
                    put("name", "unknownFutureField")
                    put("value", "some-value")
                }
                addJsonObject {
                    put("name", "apiKey")
                    put("value", "key-123")
                }
            }
        }

        val result = RecognizeCallback().init(json) as PingOneRecognizeEnrollCallback

        assertEquals("key-123", result.apiKey)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildCallbackJson(operationType: String): JsonObject = buildJsonObject {
        put("type", "PingOneRecognizeCallback")
        putJsonArray("output") {
            addJsonObject { put("name", "operationType");              put("value", operationType) }
            addJsonObject { put("name", "websocketURL");               put("value", "https://auth.example.com") }
            addJsonObject { put("name", "customerName");               put("value", "Acme Corp") }
            addJsonObject { put("name", "imageEncryptionPublicKey");   put("value", "pub-key-abc") }
            addJsonObject { put("name", "imageEncryptionKeyId");       put("value", "key-id-123") }
            addJsonObject { put("name", "host");                       put("value", "https://host.example.com") }
            addJsonObject { put("name", "apiKey");                     put("value", "api-key-xyz") }
            addJsonObject { put("name", "username");                   put("value", "john.doe") }
            addJsonObject { put("name", "transactionData");            put("value", "tx-data-payload") }
            addJsonObject { put("name", "generateClientState");        put("value", "true") }
            addJsonObject { put("name", "clientState");                put("value", "existing-client-state") }
            addJsonObject {
                put("name", "mobileSDKOptions")
                put("value", buildJsonObject {
                    put("livenessConfiguration", "LEVEL_1")
                    put("operationInfoId", "op-id-001")
                })
            }
            addJsonObject {
                put("name", "webSDKOptions")
                put("value", buildJsonObject {
                    put("authorizationToken", "web-token")
                })
            }
        }
        putJsonArray("input") {
            addJsonObject { put("name", "IDToken1signedJwt");       put("value", "") }
            addJsonObject { put("name", "IDToken1clientState");     put("value", "") }
            addJsonObject { put("name", "IDToken1keylessId");       put("value", "") }
            addJsonObject { put("name", "IDToken1clientError");     put("value", "") }
            addJsonObject { put("name", "IDToken1clientErrorCode"); put("value", "") }
        }
    }
}

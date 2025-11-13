/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.fido.davinci

import com.pingidentity.davinci.plugin.DaVinci
import com.pingidentity.fido.Constants
import com.pingidentity.logger.CONSOLE
import com.pingidentity.logger.Logger
import com.pingidentity.orchestrate.WorkflowConfig
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFailsWith

@RunWith(RobolectricTestRunner::class)
class FidoCollectorTest {

    private lateinit var mockDaVinci: DaVinci
    private lateinit var mockConfig: WorkflowConfig

    @BeforeTest
    fun setUp() {
        mockDaVinci = mockk()
        mockConfig = mockk()

        every { mockDaVinci.config } returns mockConfig
        every { mockConfig.logger } returns Logger.CONSOLE
    }

    @Test
    fun `init should return Fido2RegistrationCollector for REGISTER action`() {
        val input = getRegistrationInput()

        val collector = FidoCollector()
        collector.davinci = mockDaVinci
        val result = collector.init(input)

        assertTrue(result is FidoRegistrationCollector)
    }

    @Test
    fun `init should return Fido2AuthenticationCollector for AUTHENTICATE action`() {
        val input = getAuthenticationInput()

        val collector = FidoCollector()
        collector.davinci = mockDaVinci
        val result = collector.init(input)

        assertTrue(result is FidoAuthenticationCollector)
    }

    @Test
    fun `init should throw IllegalArgumentException when action is missing`() {
        val input = buildJsonObject {
            put(Constants.FIELD_KEY, "test_key")
        }

        val collector = FidoCollector()

        val exception = assertFailsWith<IllegalArgumentException> {
            collector.init(input)
        }
        assertTrue(exception.message!!.contains("action is required"))
    }

    @Test
    fun `init should throw IllegalArgumentException for unsupported action`() {
        val input = buildJsonObject {
            put(Constants.FIELD_ACTION, "UNSUPPORTED_ACTION")
            put(Constants.FIELD_KEY, "test_key")
        }

        val collector = FidoCollector()

        val exception = assertFailsWith<IllegalArgumentException> {
            collector.init(input)
        }
        assertTrue(exception.message!!.contains("UNSUPPORTED_ACTION is not supported"))
    }

    @Test
    fun `init should properly initialize the returned collector`() {
        val input = getRegistrationInput()

        val collector = FidoCollector()
        collector.davinci = mockDaVinci
        val result = collector.init(input) as FidoRegistrationCollector

        // Verify that the collector was properly initialized
        assertTrue(result.key == "fido2")
        assertTrue(result.label == "Continue")
        assertTrue(result.required)
    }

    @Test
    fun `init should handle complete FIDO2 authentication input`() {

        val collector = FidoCollector()
        collector.davinci = mockDaVinci
        val result = collector.init(getAuthenticationInput())

        assertTrue(result is FidoAuthenticationCollector)
        val authCollector = result as FidoAuthenticationCollector
        assertTrue(authCollector.key == "fido2")
        assertTrue(authCollector.label == "Continue")
        assertTrue(authCollector.trigger == "BUTTON")
        assertTrue(authCollector.required)
    }

    private fun getAuthenticationInput() : JsonObject {
        return buildJsonObject {
            put("type", "FIDO2")
            put("key", "fido2")
            put("label", "Continue")
            putJsonObject("publicKeyCredentialRequestOptions") {
                putJsonArray("challenge") {
                    listOf(17, 116, -82, 108, -2, 49, 91, 116, 125, 116, 11, -91, -4, -87, 47, -68, 120, -51, 15, 113, 84, 118, -35, 0, -107, -58, 98, 7, 113, -82, 91, 67).forEach { add(JsonPrimitive(it)) }
                }
                put("timeout", 120000)
                put("rpId", "idc.petrov.ca")
                putJsonArray("allowCredentials") {
                    addJsonObject {
                        put("type", "public-key")
                        putJsonArray("id") {
                            listOf(-85, -103, 57, -107, -18, 112, 125, 8, 36, 101, -109, -98, 6, -56, -76, 59).forEach { add(JsonPrimitive(it)) }
                        }
                    }
                    addJsonObject {
                        put("type", "public-key")
                        putJsonArray("id") {
                            listOf(-35, -5, 23, 87, 107, 71, -32, -105, 14, 1, -76, -32, 49, -79, 6, -43).forEach { add(JsonPrimitive(it)) }
                        }
                    }
                }
                put("userVerification", "preferred")
            }
            put("action", "AUTHENTICATE")
            put("trigger", "BUTTON")
            put("required", true)
        }
    }

    private fun getRegistrationInput(): JsonObject {
        return buildJsonObject {
            put("type", "FIDO2")
            put("key", "fido2")
            put("label", "Continue")
            putJsonObject("publicKeyCredentialCreationOptions") {
                putJsonObject("rp") {
                    put("id", "idc.petrov.ca")
                    put("name", "pingone.ca")
                }
                putJsonObject("user") {
                    putJsonArray("id") {
                        listOf(24, -76, -64, -123, 11, 13, -6, 111, 112, 94, -48, 94, -118, -102, -102, 97, -94, 59, 27, 105, -39, -122, -35, 67, -73, -18, 109, -78, -3, -14, -44, 44).forEach { add(JsonPrimitive(it)) }
                    }
                    put("displayName", "Andy.witrisna+02@pingidentity.com")
                    put("name", "Andy.witrisna+02@pingidentity.com")
                }
                putJsonArray("challenge") {
                    listOf(-68, 87, -120, -123, 127, 48, 125, 75, 99, 32, 115, -117, -77, -13, -9, -32, 44, -97, -13, 112, 39, 111, 107, -82, 118, -122, 125, -94, 7, 34, 40, -29).forEach { add(JsonPrimitive(it)) }
                }
                putJsonArray("pubKeyCredParams") {
                    addJsonObject {
                        put("type", "public-key")
                        put("alg", "-7")
                    }
                    addJsonObject {
                        put("type", "public-key")
                        put("alg", "-37")
                    }
                    addJsonObject {
                        put("type", "public-key")
                        put("alg", "-257")
                    }
                }
                put("timeout", 120000)
                putJsonArray("excludeCredentials") {
                    addJsonObject {
                        put("type", "public-key")
                        putJsonArray("id") {
                            listOf(-35, -5, 23, 87, 107, 71, -32, -105, 14, 1, -76, -32, 49, -79, 6, -43).forEach { add(JsonPrimitive(it)) }
                        }
                    }
                    addJsonObject {
                        put("type", "public-key")
                        putJsonArray("id") {
                            listOf(-85, -103, 57, -107, -18, 112, 125, 8, 36, 101, -109, -98, 6, -56, -76, 59).forEach { add(JsonPrimitive(it)) }
                        }
                    }
                }
                putJsonObject("authenticatorSelection") {
                    put("residentKey", "required")
                    put("requireResidentKey", true)
                    put("userVerification", "required")
                }
                put("attestation", "none")
                putJsonObject("extensions") {
                    put("credProps", true)
                    put("hmacCreateSecret", true)
                }
            }
            put("action", "REGISTER")
            put("trigger", "BUTTON")
            put("required", true)
        }
    }
}

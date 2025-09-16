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
import kotlinx.coroutines.runBlocking
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
import kotlin.test.BeforeTest
import kotlin.test.Test

@RunWith(RobolectricTestRunner::class)
class Fido2RegistrationCollectorTest {

    private lateinit var collector: Fido2RegistrationCollector

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
                put("id", JsonArray(listOf(
                    JsonPrimitive(24), JsonPrimitive(-76), JsonPrimitive(-64)
                )))
                put("displayName", JsonPrimitive("test@example.com"))
                put("name", JsonPrimitive("test@example.com"))
            })
            put("challenge", JsonArray(listOf(
                JsonPrimitive(-68), JsonPrimitive(87), JsonPrimitive(-120)
            )))
            put("pubKeyCredParams", JsonArray(listOf(
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

    @BeforeTest
    fun setup() {
        val daVinci = mockk<DaVinci>()
        val config = mockk<WorkflowConfig>()
        val logger = Logger.CONSOLE
        every { daVinci.config } returns config
        every { config.logger } returns logger

        collector = Fido2RegistrationCollector()
        collector.davinci = daVinci
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
    fun `payload should return attestationValue after register`() = runBlocking {
        collector.init(getRegistrationInput())
        val attestation = buildJsonObject { put("test", JsonPrimitive("value")) }

        mockkObject(Fido2Client)
        coEvery { Fido2Client.register(any()) } returns Result.success(attestation)

        collector.register()
        val payload = collector.payload()

        assertNotNull(payload)
        assertEquals(attestation, payload?.get(Constants.FIELD_ATTESTATION_VALUE)?.jsonObject)
    }

    @Test
    fun `register should propagate failure`() = runBlocking {
        collector.init(getRegistrationInput())

        mockkObject(Fido2Client)
        val exception = Exception("Registration failed")
        coEvery { Fido2Client.register(any()) } returns Result.failure(exception)

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
}


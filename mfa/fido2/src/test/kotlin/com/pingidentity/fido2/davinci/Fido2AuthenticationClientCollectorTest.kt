/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.fido2.davinci

import com.google.android.gms.fido.fido2.api.common.PublicKeyCredentialRequestOptions
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
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals

@RunWith(RobolectricTestRunner::class)
class Fido2AuthenticationClientCollectorTest {

    private lateinit var collector: Fido2AuthenticationClientCollector

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

    @BeforeTest
    fun setup() {
        val daVinci = mockk<DaVinci>()
        val config = mockk<WorkflowConfig>()
        val logger = Logger.CONSOLE
        every { daVinci.config } returns config
        every { config.logger } returns logger

        collector = Fido2AuthenticationClientCollector()
        collector.davinci = daVinci

    }

    @Test
    fun `init should parse and transform input correctly`() {
        collector.init(getInput())
        val options = collector.publicKeyCredentialRequestOptions
        assertEquals("idc.petrov.ca", options.rpId)
        // Challenge should be base64url encoded
        assertContentEquals(byteArrayOf(1, 2, 3), options.challenge)

        // AllowCredentials id should be base64url encoded
        val allowCredentials = options.allowList
        assertNotNull(allowCredentials)
        assertEquals(1, allowCredentials?.size)
        allowCredentials?.forEach { cred ->
            // The id should be a base64 string
            assertTrue(cred.id.isNotEmpty())
        }
    }

    @Test
    fun `payload should return null if not authenticated`() {
        collector.init(getInput())
        assertNull(collector.payload())
    }

    @Test
    fun `payload should return assertionValue after authenticate`() = runTest {
        collector.init(getInput())
        val assertion = buildJsonObject { put("test", JsonPrimitive("value")) }
        mockkObject(Fido2Client)
        coEvery { Fido2Client.authenticate(any<PublicKeyCredentialRequestOptions>()) } returns Result.success(assertion)
        collector.authenticate()
        val payload = collector.payload()
        assertNotNull(payload)
        assertEquals(assertion, payload?.get(Constants.FIELD_ASSERTION_VALUE)?.jsonObject)
    }

    @Test
    fun `authenticate should propagate failure`() = runTest {
        collector.init(getInput())
        mockkObject(Fido2Client)
        val exception = Exception("fail")
        coEvery { Fido2Client.authenticate(any<PublicKeyCredentialRequestOptions>()) } returns Result.failure(exception)
        val result = collector.authenticate()
        assertTrue(result.isFailure)
        assertEquals(exception, result.exceptionOrNull())
    }
}


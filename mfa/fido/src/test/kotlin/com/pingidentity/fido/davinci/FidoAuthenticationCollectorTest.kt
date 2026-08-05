/*
 * Copyright (c) 2025 - 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.fido.davinci

import com.pingidentity.davinci.plugin.DaVinci
import com.pingidentity.fido.Constants
import kotlinx.coroutines.CancellationException
import com.pingidentity.fido.FidoClient
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
import kotlinx.serialization.json.jsonArray
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
class FidoAuthenticationCollectorTest {

    private lateinit var collector: FidoAuthenticationCollector
    private lateinit var mockFidoClient: FidoClient

    @BeforeTest
    fun setup() {
        val daVinci = mockk<DaVinci>()
        val config = mockk<WorkflowConfig>()
        val logger = Logger.CONSOLE
        every { daVinci.config } returns config
        every { config.logger } returns logger

        collector = FidoAuthenticationCollector()
        collector.davinci = daVinci

        mockFidoClient = mockk()
        mockkObject(FidoClient.Companion)
        every { FidoClient.invoke(any()) } returns mockFidoClient

    }

    @AfterTest
    fun tearDown() {
        unmockkObject(FidoClient.Companion)
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



    @Test
    fun `init should parse and transform input correctly`() {
        collector.init(getInput())
        val options = collector.publicKeyCredentialRequestOptions
        assertEquals("idc.petrov.ca", options[Constants.FIELD_RP_ID]?.jsonPrimitive?.content)
        assertEquals("preferred", options[Constants.FIELD_USER_VERIFICATION]?.jsonPrimitive?.content)
        // Challenge should be base64url encoded
        assertEquals("AQID", options[Constants.FIELD_CHALLENGE]?.jsonPrimitive?.content)
        // AllowCredentials id should be base64url encoded
        val allowCreds = options[Constants.FIELD_ALLOW_CREDENTIALS]?.jsonArray
        assertEquals("ChQe", allowCreds?.firstOrNull()?.jsonObject?.get(Constants.FIELD_ID)?.jsonPrimitive?.content)
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
        coEvery { mockFidoClient.authenticate(any(), any()) } returns Result.success(assertion)
        collector.authenticate()
        val payload = collector.payload()
        assertNotNull(payload)
        assertEquals(assertion, payload?.get(Constants.FIELD_ASSERTION_VALUE)?.jsonObject)
    }

    @Test
    fun `authenticate should propagate failure`() = runTest {
        collector.init(getInput())
        val exception = Exception("fail")
        coEvery { mockFidoClient.authenticate(any(), any()) } returns Result.failure(exception)
        val result = collector.authenticate()
        assertTrue(result.isFailure)
        assertEquals(exception, result.exceptionOrNull())
    }

    @Test
    fun `close should clear assertionValue`() = runTest {
        collector.init(getInput())
        val assertion = buildJsonObject { put("test", JsonPrimitive("value")) }

        coEvery { mockFidoClient.authenticate(any(), any()) } returns Result.success(assertion)

        // Authenticate and verify payload is not null
        collector.authenticate()
        assertNotNull(collector.payload())

        // Close the collector
        collector.close()

        // Verify payload is now null
        assertNull(collector.payload())
    }

    @Test
    fun `close should allow reuse after clearing`() = runTest {
        collector.init(getInput())
        val assertion1 = buildJsonObject { put("test", JsonPrimitive("value1")) }
        val assertion2 = buildJsonObject { put("test", JsonPrimitive("value2")) }

        // First authentication
        coEvery { mockFidoClient.authenticate(any(), any()) } returns Result.success(assertion1)
        collector.authenticate()
        val payload1 = collector.payload()
        assertNotNull(payload1)
        assertEquals(assertion1, payload1?.get(Constants.FIELD_ASSERTION_VALUE)?.jsonObject)

        // Close and re-authenticate
        collector.close()
        assertNull(collector.payload())

        // Second authentication
        coEvery { mockFidoClient.authenticate(any(), any()) } returns Result.success(assertion2)
        collector.authenticate()
        val payload2 = collector.payload()
        assertNotNull(payload2)
        assertEquals(assertion2, payload2?.get(Constants.FIELD_ASSERTION_VALUE)?.jsonObject)
    }

    @Test
    fun `authenticate should call handleError and set errorCode on failure`() = runTest {
        collector.init(getInput())
        val exception = mockk<androidx.credentials.exceptions.GetCredentialCancellationException>(relaxed = true)
        every { exception.message } returns "User cancelled"
        coEvery { mockFidoClient.authenticate(any(), any()) } returns Result.failure(exception)

        val result = collector.authenticate()

        assertTrue(result.isFailure)
        assertEquals("NotAllowedError", collector.errorCode)
    }

    @Test
    fun `payload should be non-null empty object on failure so actionKey fires`() = runTest {
        collector.init(getInput())
        val exception = mockk<androidx.credentials.exceptions.GetCredentialCancellationException>(relaxed = true)
        every { exception.message } returns "User cancelled"
        coEvery { mockFidoClient.authenticate(any(), any()) } returns Result.failure(exception)

        collector.authenticate()

        val payload = collector.payload()
        assertNotNull(payload)
        assertTrue(payload!!.isEmpty())
    }

    @Test
    fun `actionKey should match errorCode after failure`() = runTest {
        collector.init(getInput())
        val exception = mockk<androidx.credentials.exceptions.GetCredentialCancellationException>(relaxed = true)
        every { exception.message } returns "User cancelled"
        coEvery { mockFidoClient.authenticate(any(), any()) } returns Result.failure(exception)

        collector.authenticate()

        assertEquals("NotAllowedError", collector.actionKey)
    }

    @Test
    fun `close should reset errorCode latch`() = runTest {
        collector.init(getInput())
        val exception = mockk<androidx.credentials.exceptions.GetCredentialCancellationException>(relaxed = true)
        every { exception.message } returns "User cancelled"
        coEvery { mockFidoClient.authenticate(any(), any()) } returns Result.failure(exception)

        collector.authenticate()
        assertEquals("NotAllowedError", collector.errorCode)
        assertEquals("action", collector.eventType())

        collector.close()

        assertNull(collector.errorCode)
        assertEquals("submit", collector.eventType())
    }

    @Test
    fun `init should reset errorCode latch after failure`() = runTest {
        collector.init(getInput())
        val exception = mockk<androidx.credentials.exceptions.GetCredentialCancellationException>(relaxed = true)
        every { exception.message } returns "User cancelled"
        coEvery { mockFidoClient.authenticate(any(), any()) } returns Result.failure(exception)

        collector.authenticate()
        assertEquals("NotAllowedError", collector.errorCode)

        collector.init(getInput())

        assertNull(collector.errorCode)
        assertEquals("submit", collector.eventType())
    }

    @Test
    fun `authenticate should rethrow CancellationException without setting errorCode`() = runTest {
        collector.init(getInput())
        coEvery { mockFidoClient.authenticate(any(), any()) } returns Result.failure(CancellationException("cancelled"))

        var threw = false
        try {
            collector.authenticate()
        } catch (e: CancellationException) {
            threw = true
        }

        assertTrue(threw)
        assertNull(collector.errorCode)
    }

    @Test
    fun `authenticate should clear stale assertion before retry`() = runTest {
        collector.init(getInput())
        val assertion = buildJsonObject { put("test", JsonPrimitive("value")) }
        coEvery { mockFidoClient.authenticate(any(), any()) } returns Result.success(assertion)
        collector.authenticate()
        assertNotNull(collector.payload())

        // Second call fails — stale assertion must not survive
        val exception = mockk<androidx.credentials.exceptions.GetCredentialCancellationException>(relaxed = true)
        every { exception.message } returns "User cancelled"
        coEvery { mockFidoClient.authenticate(any(), any()) } returns Result.failure(exception)
        collector.authenticate()

        // payload() returns empty sentinel (errorCode set), not the stale success payload
        val payload = collector.payload()
        assertNotNull(payload)
        assertTrue(payload!!.isEmpty())
        assertEquals("NotAllowedError", collector.errorCode)
    }
}


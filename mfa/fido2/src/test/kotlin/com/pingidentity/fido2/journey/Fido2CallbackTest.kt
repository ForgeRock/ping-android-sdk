/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.fido2.journey

import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.CreateCredentialUnsupportedException
import androidx.credentials.exceptions.domerrors.AbortError
import androidx.credentials.exceptions.publickeycredential.CreatePublicKeyCredentialDomException
import com.pingidentity.fido2.Constants
import com.pingidentity.journey.plugin.ValueCallback
import com.pingidentity.logger.CONSOLE
import com.pingidentity.logger.Logger
import com.pingidentity.orchestrate.ContinueNode
import com.pingidentity.orchestrate.FlowContext
import com.pingidentity.orchestrate.Request
import com.pingidentity.orchestrate.SharedContext
import com.pingidentity.orchestrate.Workflow
import com.pingidentity.orchestrate.WorkflowConfig
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlin.io.encoding.Base64
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class Fido2CallbackTest {

    private lateinit var testCallback: TestFido2Callback
    private lateinit var mockContinueNode: ContinueNode
    private lateinit var mockWorkflow: Workflow
    private lateinit var mockWorkflowConfig: WorkflowConfig
    private lateinit var mockValueCallback: ValueCallback

    private inner class TestFido2Callback : Fido2Callback() {
        override fun init(name: String, value: JsonElement) {
            // Test implementation
        }
    }

    @BeforeTest
    fun setUp() {
        mockWorkflow = mockk<Workflow>()
        mockWorkflowConfig = mockk<WorkflowConfig>()
        mockValueCallback = mockk<ValueCallback>(relaxed = true)

        mockContinueNode = object: ContinueNode(
            FlowContext(SharedContext(mutableMapOf())),
            mockWorkflow,
            buildJsonObject {  },
            listOf(mockValueCallback)
        ) {
            override fun asRequest(): Request {
                return Request()
            }
        }

        every { mockWorkflow.config } returns mockWorkflowConfig
        every { mockWorkflowConfig.logger } returns Logger.CONSOLE
        every { mockValueCallback.id } returns Constants.WEB_AUTHN_OUTCOME

        testCallback = TestFido2Callback()
        testCallback.continueNode = mockContinueNode
        testCallback.journey = mockWorkflow
    }

    @Test
    fun `valueCallback should set value to WebAuthn outcome callback`() {
        // Given
        val testValue = "test-value"
        val valueSlot = slot<String>()
        every { mockValueCallback.value = capture(valueSlot) } returns Unit

        // When
        testCallback.valueCallback(testValue)

        // Then
        verify { mockValueCallback.value = testValue }
        assertEquals(testValue, valueSlot.captured)
    }

    @Test
    fun `handleError should handle CreateCredentialUnsupportedException`() {
        // Given
        val error = CreateCredentialUnsupportedException("Unsupported")
        val valueSlot = slot<String>()
        every { mockValueCallback.value = capture(valueSlot) } returns Unit

        // When
        testCallback.handleError(error)

        // Then
        verify { mockValueCallback.value = Constants.ERROR_UNSUPPORTED }
        assertEquals(Constants.ERROR_UNSUPPORTED, valueSlot.captured)
    }

    @Test
    fun `handleError should handle CreateCredentialCancellationException`() {
        // Given
        val errorMessage = "User cancelled"
        val error = CreateCredentialCancellationException(errorMessage)
        val valueSlot = slot<String>()
        every { mockValueCallback.value = capture(valueSlot) } returns Unit

        // When
        testCallback.handleError(error)

        // Then
        val expectedValue = "${Constants.ERROR_PREFIX}${Constants.ERROR_NOT_ALLOWED}:$errorMessage"
        verify { mockValueCallback.value = expectedValue }
        assertEquals(expectedValue, valueSlot.captured)
    }

    @Test
    fun `handleError should handle CreatePublicKeyCredentialDomException`() {
        // Given
        val errorMessage = "DOM error"
        val error = CreatePublicKeyCredentialDomException(AbortError(), errorMessage)

        val valueSlot = slot<String>()
        every { mockValueCallback.value = capture(valueSlot) } returns Unit

        // When
        testCallback.handleError(error)

        // Then
        val expectedValue = "${Constants.ERROR_PREFIX}AbortError:$errorMessage"
        verify { mockValueCallback.value = expectedValue }
        assertEquals(expectedValue, valueSlot.captured)
    }

    @Test
    fun `handleError should handle unknown exceptions`() {
        // Given
        val errorMessage = "Unknown error"
        val error = RuntimeException(errorMessage)
        val valueSlot = slot<String>()
        every { mockValueCallback.value = capture(valueSlot) } returns Unit

        // When
        testCallback.handleError(error)

        // Then
        val expectedValue = "${Constants.ERROR_PREFIX}${Constants.ERROR_UNKNOWN}:$errorMessage"
        verify { mockValueCallback.value = expectedValue }
        assertEquals(expectedValue, valueSlot.captured)
    }

    @Test
    fun `base64ToJson should decode base64 to JSON string`() {
        // Given
        val jsonString = """{"test":"value"}"""
        val base64String =
            Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(jsonString.toByteArray())

        // When
        val result = with(testCallback) { base64String.base64ToJson() }

        // Then
        assertEquals(jsonString, result)
    }

    @Test
    fun `base64ToIntStr should convert base64 to comma-separated integers`() {
        // Given
        val bytes = byteArrayOf(1, 2, 3, 255.toByte())
        val base64String = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(bytes)

        // When
        val result = with(testCallback) { base64String.base64ToIntStr() }

        // Then
        assertEquals("1,2,3,-1", result)
    }

    @Test
    fun `base64DefaultToUrlSafe should convert standard base64 to URL-safe`() {
        // Given
        val originalData = "test data with special chars +/="
        val standardBase64 = Base64.encode(originalData.toByteArray())

        // When
        val result = with(testCallback) { standardBase64.base64DefaultToUrlSafe() }

        // Then
        val expectedUrlSafe = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
            .encode(originalData.toByteArray())
        assertEquals(expectedUrlSafe, result)
    }

    @Test
    fun `toBase64 should convert ByteArray to URL-safe base64`() {
        // Given
        val bytes = byteArrayOf(1, 2, 3, 255.toByte())

        // When
        val result = with(testCallback) { bytes.toBase64() }

        // Then
        val expected = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(bytes)
        assertEquals(expected, result)
    }

}


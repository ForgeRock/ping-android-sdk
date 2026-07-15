/*
 * Copyright (c) 2025 - 2026 Ping Identity Corporation. All rights reserved.
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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import kotlin.test.BeforeTest
import kotlin.test.Test

class AbstractFidoCollectorTest {

    private lateinit var mockDaVinci: DaVinci
    private lateinit var mockConfig: WorkflowConfig
    private lateinit var collector: TestFidoCollector

    @BeforeTest
    fun setUp() {
        mockDaVinci = mockk()
        mockConfig = mockk()

        every { mockDaVinci.config } returns mockConfig
        every { mockConfig.logger } returns Logger.CONSOLE

        collector = TestFidoCollector()
        collector.davinci = mockDaVinci
    }

    @Test
    fun `init should set all properties correctly`() {
        val input = buildJsonObject {
            put(Constants.FIELD_KEY, "test_key")
            put(Constants.FIELD_LABEL, "Test Label")
            put(Constants.FIELD_TRIGGER, "test_trigger")
            put(Constants.FIELD_REQUIRED, true)
        }

        collector.init(input)

        assertEquals("test_key", collector.key)
        assertEquals("Test Label", collector.label)
        assertEquals("test_trigger", collector.trigger)
        assertTrue(collector.required)
    }

    @Test
    fun `init should handle missing optional fields`() {
        val input = buildJsonObject {
            put(Constants.FIELD_KEY, "test_key")
        }

        collector.init(input)

        assertEquals("test_key", collector.key)
        assertEquals("", collector.label)
        assertEquals("", collector.trigger)
        assertFalse(collector.required)
    }

    @Test
    fun `eventType should return submit`() {
        assertEquals(Constants.EVENT_TYPE_SUBMIT, collector.eventType())
    }

    @Test
    fun `id should return key value`() {
        val input = buildJsonObject {
            put(Constants.FIELD_KEY, "unique_id")
        }

        collector.init(input)

        assertEquals("unique_id", collector.id())
    }

    @Test
    fun `logger should be accessible`() {
        // Access the logger property to ensure it's properly initialized
        val logger = collector.logger
        assertEquals(Logger.CONSOLE, logger)
    }

    @Test
    fun `error should return null by default`() {
        assertEquals(null, collector.error())
    }

    @Test
    fun `error should return error message when set`() {
        collector.error = "Test error message"
        assertEquals("Test error message", collector.error())
    }

    @Test
    fun `error should be mutable`() {
        // Initially null
        assertEquals(null, collector.error())

        // Set error
        collector.error = "First error"
        assertEquals("First error", collector.error())

        // Update error
        collector.error = "Second error"
        assertEquals("Second error", collector.error())

        // Clear error
        collector.error = null
        assertEquals(null, collector.error())
    }

    @Test
    fun `eventType should return submit when error is null`() {
        collector.error = null
        assertEquals(Constants.EVENT_TYPE_SUBMIT, collector.eventType())
    }

    @Test
    fun `eventType should return action when error is set`() {
        collector.error = "Some error occurred"
        assertEquals("action", collector.eventType())
    }

    @Test
    fun `eventType should return action when error is empty string`() {
        collector.error = ""
        assertEquals("action", collector.eventType())
    }

    @Test
    fun `handleError should set NotSupportedError for CreateCredentialUnsupportedException`() {
        val exception = mockk<androidx.credentials.exceptions.CreateCredentialUnsupportedException>(relaxed = true)
        every { exception.message } returns "Create credential not supported"

        collector.handleError(exception)

        assertEquals("NotSupportedError", collector.error)
    }

    @Test
    fun `handleError should set NotSupportedError for GetCredentialUnsupportedException`() {
        val exception = mockk<androidx.credentials.exceptions.GetCredentialUnsupportedException>(relaxed = true)
        every { exception.message } returns "Get credential not supported"

        collector.handleError(exception)

        assertEquals("NotSupportedError", collector.error)
    }

    @Test
    fun `handleError should set NotAllowedError for CreateCredentialCancellationException`() {
        val exception = mockk<androidx.credentials.exceptions.CreateCredentialCancellationException>(relaxed = true)
        every { exception.message } returns "User cancelled creation"

        collector.handleError(exception)

        assertEquals("NotAllowedError", collector.error)
    }

    @Test
    fun `handleError should set NotAllowedError for GetCredentialCancellationException`() {
        val exception = mockk<androidx.credentials.exceptions.GetCredentialCancellationException>(relaxed = true)
        every { exception.message } returns "User cancelled get"

        collector.handleError(exception)

        assertEquals("NotAllowedError", collector.error)
    }

    @Test
    fun `handleError should set DOM error name for CreatePublicKeyCredentialDomException`() {
        val domError = mockk<androidx.credentials.exceptions.domerrors.NotAllowedError>(relaxed = true)
        val exception = mockk<androidx.credentials.exceptions.publickeycredential.CreatePublicKeyCredentialDomException>(relaxed = true)
        every { exception.domError } returns domError
        every { exception.message } returns "DOM error occurred"

        collector.handleError(exception)

        assertEquals("NotAllowedError", collector.error)
    }

    @Test
    fun `handleError should set DOM error name for GetPublicKeyCredentialDomException`() {
        val domError = mockk<androidx.credentials.exceptions.domerrors.NotSupportedError>(relaxed = true)
        val exception = mockk<androidx.credentials.exceptions.publickeycredential.GetPublicKeyCredentialDomException>(relaxed = true)
        every { exception.domError } returns domError
        every { exception.message } returns "DOM error occurred"

        collector.handleError(exception)

        assertEquals("NotSupportedError", collector.error)
    }

    @Test
    fun `handleError should set UnknownError for unknown exception types`() {
        val exception = RuntimeException("Some random error")

        collector.handleError(exception)

        assertEquals("UnknownError", collector.error)
    }

    @Test
    fun `handleError should set UnknownError for generic Exception`() {
        val exception = Exception("Generic exception")

        collector.handleError(exception)

        assertEquals("UnknownError", collector.error)
    }

    @Test
    fun `handleError should set UnknownError for IllegalStateException`() {
        val exception = IllegalStateException("Invalid state")

        collector.handleError(exception)

        assertEquals("UnknownError", collector.error)
    }

    @Test
    fun `handleError should override previous error`() {
        // Set an initial error
        collector.error = "PreviousError"
        assertEquals("PreviousError", collector.error)

        // Handle a new error
        val exception = mockk<androidx.credentials.exceptions.CreateCredentialCancellationException>(relaxed = true)
        every { exception.message } returns "User cancelled"
        collector.handleError(exception)

        // Verify the error was updated
        assertEquals("NotAllowedError", collector.error)
    }

    // Test implementation of AbstractFido2Collector for testing purposes
    private class TestFidoCollector : AbstractFidoCollector()
}


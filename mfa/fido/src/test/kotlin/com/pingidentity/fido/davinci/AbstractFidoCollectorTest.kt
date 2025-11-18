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

    // Test implementation of AbstractFido2Collector for testing purposes
    private class TestFidoCollector : AbstractFidoCollector()
}


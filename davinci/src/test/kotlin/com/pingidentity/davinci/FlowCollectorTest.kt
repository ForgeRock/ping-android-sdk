/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

import com.pingidentity.davinci.collector.FlowCollector
import com.pingidentity.testrail.TestRailCase
import com.pingidentity.testrail.TestRailWatcher
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.rules.TestWatcher
import kotlin.test.Test
import kotlin.test.assertEquals

class FlowCollectorTest {

    @JvmField
    @Rule
    val watcher: TestWatcher = TestRailWatcher

    @TestRailCase(21258)
    @Test
    fun testInitialization() {
        val flowCollector = FlowCollector()
        assertNotNull(flowCollector)
    }

    @TestRailCase(22146)
    @Test
    fun `should initialize key and label from JsonObject`() {
        val flowCollector = FlowCollector()
        val jsonObject = buildJsonObject {
            put("key", "testKey")
            put("label", "testLabel")
            put("type", "testType")
        }

        flowCollector.init(jsonObject)

        assertEquals("testKey", flowCollector.key)
        assertEquals("testLabel", flowCollector.label)
        assertEquals("testType", flowCollector.type)
    }

    @TestRailCase(22147)
    @Test
    fun `should return value when value is set`() {
        val flowCollector = FlowCollector()
        flowCollector.value = "test"
        assertEquals("test", flowCollector.value)
    }

    @Test
    fun `should initialize default value`() {
        val flowCollector = FlowCollector()
        val jsonObject = buildJsonObject {
            put("key", "testKey")
            put("label", "testLabel")
            put("type", "testType")
        }

        flowCollector.init(jsonObject)

        assertEquals("testKey", flowCollector.key)
        assertEquals("testLabel", flowCollector.label)
        assertEquals("testType", flowCollector.type)
    }

    @Test
    fun `close should clear value`() {
        val flowCollector = FlowCollector()
        flowCollector.value = "testValue"

        // Verify value is set
        assertEquals("testValue", flowCollector.value)
        assertEquals("testValue", flowCollector.payload())

        // Close the collector
        flowCollector.close()

        // Verify value is cleared
        assertEquals("", flowCollector.value)
        assertNull(flowCollector.payload())
    }

    @Test
    fun `close should allow reuse after clearing`() {
        val flowCollector = FlowCollector()

        // First value
        flowCollector.value = "value1"
        assertEquals("value1", flowCollector.payload())

        // Close and set new value
        flowCollector.close()
        assertEquals("", flowCollector.value)

        // Second value
        flowCollector.value = "value2"
        assertEquals("value2", flowCollector.payload())
    }
}
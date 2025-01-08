/*
 * Copyright (c) 2024 - 2025 Ping Identity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

import com.pingidentity.davinci.collector.TextCollector
import com.pingidentity.testrail.TestRailCase
import com.pingidentity.testrail.TestRailWatcher
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Test
import org.junit.Assert.*
import org.junit.Rule
import org.junit.rules.TestWatcher
import kotlin.test.assertEquals

class TextCollectorTest {

    @JvmField
    @Rule
    val watcher: TestWatcher = TestRailWatcher

    @TestRailCase(22555)
    @Test
    fun testInitialization() {
        val textCollector = TextCollector()
        assertNotNull(textCollector)
    }

    @TestRailCase(22557)
    @Test
    fun `should initialize key and label from JsonObject`() {
        val textCollector = TextCollector()
        val jsonObject = buildJsonObject {
            put("key", "testKey")
            put("label", "testLabel")
        }

        textCollector.init(jsonObject)

        assertEquals("testKey", textCollector.key)
        assertEquals("testLabel", textCollector.label)
    }

    @TestRailCase(22558)
    @Test
    fun `should return value when value is set`() {
        val textCollector = TextCollector()
        textCollector.value = "test"
        assertEquals("test", textCollector.value)
    }

    @Test
    fun `should initialize default value`() {
        val input = JsonPrimitive("test")
        val textCollector = TextCollector()
        textCollector.init(input)
        assertEquals("test", textCollector.value)
    }
}
/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

import com.pingidentity.davinci.collector.FieldCollector
import com.pingidentity.davinci.collector.SingleValueCollector
import com.pingidentity.testrail.TestRailCase
import com.pingidentity.testrail.TestRailWatcher
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Rule
import org.junit.rules.TestWatcher
import kotlin.test.Test
import kotlin.test.assertEquals

class FieldCollectorTest {
    @JvmField
    @Rule
    val watcher: TestWatcher = TestRailWatcher

    @TestRailCase(21257)
    @Test
    fun `should initialize key and label from JsonObject`() {
        val fieldCollector = object : FieldCollector() {}
        val jsonObject = buildJsonObject {
            put("type", "testType")
            put("key", "testKey")
            put("label", "testLabel")
        }

        fieldCollector.init(jsonObject)

        assertEquals("testType", fieldCollector.type)
        assertEquals("testKey", fieldCollector.key)
        assertEquals("testLabel", fieldCollector.label)
    }

    @TestRailCase(21281)
    @Test
    fun `should return value when value is set`() {
        val fieldCollector = object : SingleValueCollector() {}
        fieldCollector.value = "test"
        assertEquals("test", fieldCollector.value)
    }
}
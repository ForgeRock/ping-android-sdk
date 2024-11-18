/*
 * Copyright (c) 2024. PingIdentity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

import com.pingidentity.davinci.collector.TextCollector
import com.pingidentity.testrail.TestRailCase
import com.pingidentity.testrail.TestRailWatcher
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Test
import org.junit.Assert.*
import org.junit.Rule
import org.junit.rules.TestWatcher

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

        kotlin.test.assertEquals("testKey", textCollector.key)
        kotlin.test.assertEquals("testLabel", textCollector.label)
    }

    @TestRailCase(22558)
    @Test
    fun `should return value when value is set`() {
        val textCollector = TextCollector()
        textCollector.value = "test"
        kotlin.test.assertEquals("test", textCollector.value)
    }
}
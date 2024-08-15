/*
 * Copyright (c) 2024. PingIdentity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

import com.pingidentity.testrail.TestRailCase
import com.pingidentity.davinci.collector.FieldCollector
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals

class FieldCollectorTest {

    @TestRailCase(21257)
    @Test
    fun `should initialize key and label from JsonObject`() {
        val fieldCollector = object : FieldCollector() {}
        val jsonObject = buildJsonObject {
            put("key", "testKey")
            put("label", "testLabel")
        }

        fieldCollector.init(jsonObject)

        assertEquals("testKey", fieldCollector.key)
        assertEquals("testLabel", fieldCollector.label)
    }

    @TestRailCase(21281)
    @Test
    fun `should return value when value is set`() {
        val fieldCollector = object : FieldCollector() {}
        fieldCollector.value = "test"
        assertEquals("test", fieldCollector.value)
    }
}
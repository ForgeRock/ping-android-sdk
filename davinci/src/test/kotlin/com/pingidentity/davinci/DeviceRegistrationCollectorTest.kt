/*
 * Copyright (c) 2025 Ping Identity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci
import com.pingidentity.davinci.collector.DeviceRegistrationCollector
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DeviceRegistrationCollectorTest {

    @Test
    fun `init with valid input initializes devices list`() {
        val input = buildJsonObject {
            put("devices", buildJsonArray {
                addJsonObject {
                    put("type", "EMAIL")
                    put("title", "Email")
                    put("description", "Email Description")
                    put("iconSrc", "Email icon")
                }
            })
        }
        val collector = DeviceRegistrationCollector()
        collector.init(input)

        assertEquals(1, collector.devices.size)
        assertEquals("EMAIL", collector.devices[0].type)
        assertEquals("Email", collector.devices[0].title)
        assertEquals("Email Description", collector.devices[0].description)
        assertEquals("Email icon", collector.devices[0].iconSrc)
    }

    @Test
    fun `init with empty input creates empty devices list`() {
        val input = buildJsonObject {}
        val collector = DeviceRegistrationCollector()
        collector.init(input)

        assertTrue(collector.devices.isEmpty())
    }

    @Test
    fun `init with malformed input throws JsonException`() {
        val input = buildJsonObject {
            put("devices", "invalid")
        }
        val collector = DeviceRegistrationCollector()

        assertFailsWith<IllegalArgumentException> {
            collector.init(input)
        }
    }
}
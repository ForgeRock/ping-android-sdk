/*
 * Copyright (c) 2025 Ping Identity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci
import com.pingidentity.davinci.collector.Device
import com.pingidentity.davinci.collector.DeviceAuthenticationCollector
import com.pingidentity.davinci.collector.Required
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeviceAuthenticationCollectorTest {

    private lateinit var collector: DeviceAuthenticationCollector

    @BeforeTest
    fun setup() {
        collector = DeviceAuthenticationCollector()
    }

    @Test
    fun `init sets devices and required flag from input`() {
        val input = buildJsonObject {
            put("devices", buildJsonArray {
                addJsonObject {
                    put("id", "device1")
                    put("type", "EMAIL")
                    put("title", "Email")
                    put("description", "Email Description")
                    put("iconSrc", "Email icon")
                    put("value", "Email Value")
                }
            })
            put("required", true)
        }
        collector.init(input)

        assertTrue(collector.required)
        assertEquals(1, collector.devices.size)
        assertEquals("device1", collector.devices[0].id)
        assertEquals(false, collector.devices[0].default)
        assertEquals("EMAIL", collector.devices[0].type)
        assertEquals("Email", collector.devices[0].title)
        assertEquals("Email Description", collector.devices[0].description)
        assertEquals("Email icon", collector.devices[0].iconSrc)
        assertEquals("Email Value", collector.devices[0].value)

    }
    @Test
    fun `init with required false sets flag accordingly`() {
        val input = buildJsonObject {
            put("required", false)
        }

        collector.init(input)
        assertFalse(collector.required)
    }

    @Test
    fun `value returns null when no device selected`() {
        assertNull(collector.value)

        val result = collector.payload()

        assertTrue(result.isNullOrEmpty())
    }

    @Test
    fun `value returns device details when device selected`() {
        collector.value = Device(id = "device1", type = "fingerprint", value = "someValue")

        val result = collector.payload()

        assertEquals("fingerprint", result?.get("type")?.toString()?.trim('"'))
        assertEquals("device1", result?.get("id")?.toString()?.trim('"'))
        assertEquals("someValue", result?.get("value")?.toString()?.trim('"'))
    }

    @Test
    fun `validate returns Required error when required and no device selected`() {
        val input = buildJsonObject {
            put("required", true)
        }
        collector.init(input)

        val errors = collector.validate()

        assertEquals(1, errors.size)
        assertEquals(Required, errors[0])
    }

    @Test
    fun `validate returns no errors when required and device selected`() {
        val input = buildJsonObject {
            put("required", true)
        }
        collector.init(input)
        collector.value = Device("device1", "fingerprint")

        val errors = collector.validate()

        assertTrue(errors.isEmpty())
    }

    @Test
    fun `validate returns no errors when not required and no device selected`() {
        val input = buildJsonObject {
            put("required", false)
        }
        collector.init(input)

        val errors = collector.validate()

        assertTrue(errors.isEmpty())
    }

}
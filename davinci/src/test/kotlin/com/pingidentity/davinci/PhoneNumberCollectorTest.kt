/*
 * Copyright (c) 2025 Ping Identity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci


import com.pingidentity.davinci.collector.PhoneNumberCollector
import com.pingidentity.davinci.collector.Required
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PhoneNumberCollectorTest {

    @Test
    fun `init with required true sets required flag`() {
        val input = buildJsonObject {
            put("required", true)
        }
        val collector = PhoneNumberCollector()

        collector.init(input)

        assertTrue(collector.required)
    }

    @Test
    fun `init with required false sets required flag`() {
        val input = buildJsonObject {
            put("required", false)
        }
        val collector = PhoneNumberCollector()

        collector.init(input)

        assertFalse(collector.required)
    }

    @Test
    fun `init without required defaults to false`() {
        val input = buildJsonObject {}
        val collector = PhoneNumberCollector()

        collector.init(input)

        assertFalse(collector.required)
    }

    @Test
    fun `init with null defaultCountryCode uses empty string`() {
        val input = buildJsonObject {
        }
        val collector = PhoneNumberCollector()

        collector.init(input)

        assertEquals("", collector.defaultCountryCode)
    }

    @Test
    fun `value returns properly formatted phone number`() {
        val collector = PhoneNumberCollector().apply {
            countryCode = "US"
            phoneNumber = "5555555555"
        }

        val result = collector.payload()

        assertEquals("US", result?.get("countryCode")?.toString()?.trim('"'))
        assertEquals("5555555555", result?.get("phoneNumber")?.toString()?.trim('"'))
    }

    @Test
    fun `validate returns Required error when required and fields are empty`() {
        val collector = PhoneNumberCollector()
        collector.init(buildJsonObject { put("required", true) })

        val errors = collector.validate()

        assertEquals(1, errors.size)
        assertEquals(Required, errors[0])
    }

    @Test
    fun `validate returns Required error when required and some fields are empty`() {
        val collector = PhoneNumberCollector().apply {
            countryCode = "US"
        }
        collector.init(buildJsonObject { put("required", true) })

        val errors = collector.validate()

        assertEquals(1, errors.size)
        assertEquals(Required, errors[0])
    }

    @Test
    fun `validate returns no errors when required and all fields are filled`() {
        val collector = PhoneNumberCollector().apply {
            countryCode = "US"
            phoneNumber = "5555555555"
        }
        collector.init(buildJsonObject { put("required", true) })

        val errors = collector.validate()

        assertTrue(errors.isEmpty())
    }

    @Test
    fun `validate returns no errors when not required and fields are empty`() {
        val collector = PhoneNumberCollector()
        collector.init(buildJsonObject { put("required", false) })

        val errors = collector.validate()

        assertTrue(errors.isEmpty())
    }
}
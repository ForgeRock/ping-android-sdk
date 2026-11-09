/*
 * Copyright (c) 2025 Ping Identity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci


import com.pingidentity.davinci.collector.PhoneNumberCollector
import com.pingidentity.davinci.collector.Required
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
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
    fun `init with validatePhoneNumber`() {
        val input = buildJsonObject {
            put("validatePhoneNumber", true)
        }
        val collector = PhoneNumberCollector()

        collector.init(input)

        assertTrue(collector.validatePhoneNumber)
    }

    @Test
    fun `init with defaultCountryCode`() {
        val input = buildJsonObject {
            put("defaultCountryCode", "CA")
        }
        val collector = PhoneNumberCollector()

        collector.init(input)

        assertEquals("CA", collector.defaultCountryCode)
    }

    @Test
    fun `default with  Phone Number`() {
        val input = JsonPrimitive("1234567")
        val collector = PhoneNumberCollector()

        collector.init(input)

        assertEquals("1234567", collector.phoneNumber)
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

    @Test
    fun `init with new structure sets phoneNumber and countryCode`() {
        val input = buildJsonObject {
            put("phoneNumber", "CA-1-+17783186380-7783186380")
            put("countryCode", "CA")
        }
        val collector = PhoneNumberCollector()

        collector.init(input as JsonElement)

        assertEquals("CA-1-+17783186380-7783186380", collector.phoneNumber)
        assertEquals("CA", collector.countryCode)
    }

    @Test
    fun `init with phone number and extension`() {
        val input = buildJsonObject {
            put("phoneNumber", "CA-1-+17783186380-7783186380")
            put("countryCode", "CA")
            put("extension", "1234")
            put("extensionLabel", "Extension")
        }
        val collector = PhoneNumberCollector()
        collector.init(input as JsonElement)

        assertEquals("CA-1-+17783186380-7783186380", collector.phoneNumber)
        assertEquals("CA", collector.countryCode)
        assertEquals("1234", collector.extension)
        assertEquals("Extension", collector.extensionLabel)
    }

    @Test
    fun `init with phone number but no extension label`() {
        val input = buildJsonObject {
            put("phoneNumber", "CA-1-+17783186380-7783186380")
            put("countryCode", "CA")
            put("extension", "1234")
        }
        val collector = PhoneNumberCollector()
        collector.init(input as JsonElement)

        assertEquals("CA-1-+17783186380-7783186380", collector.phoneNumber)
        assertEquals("CA", collector.countryCode)
        assertEquals("1234", collector.extension)
        assertEquals("", collector.extensionLabel)
    }

    @Test
    fun `init with formData value does not overwrite extension label from field definition`() {
        val field = buildJsonObject {
            put("type", "PHONE_NUMBER")
            put("key", "phone-field")
            put("label", "Phone Collector")
            put("defaultCountryCode", "BF")
            put("validatePhoneNumber", true)
            put("showExtension", true)
            put("extensionLabel", "Extension")
        }
        val formDataValue = buildJsonObject {
            put("phoneNumber", "(555)555-1234")
            put("countryCode", "GB")
            put("extension", "4321")
        }
        val collector = PhoneNumberCollector()
        // Form.parse order: field definition first, then the prefilled value
        collector.init(field)
        collector.init(formDataValue as JsonElement)

        assertEquals("(555)555-1234", collector.phoneNumber)
        assertEquals("GB", collector.countryCode)
        assertEquals("4321", collector.extension)
        assertEquals("Extension", collector.extensionLabel)
    }
}
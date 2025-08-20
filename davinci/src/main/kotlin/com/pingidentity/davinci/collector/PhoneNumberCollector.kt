/*
 * Copyright (c) 2025 Ping Identity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci.collector

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

private const val COUNTRY_CODE = "countryCode"
private const val PHONE_NUMBER = "phoneNumber"

/**
 * A collector for phone number.
 */
class PhoneNumberCollector : FieldCollector<JsonObject>(), Validator {

    // default country code
    lateinit var defaultCountryCode: String
        private set

    var validatePhoneNumber: Boolean = false
        private set

    // country code
    var countryCode: String = ""
    // phone number
    var phoneNumber: String = ""

    override fun init(input: JsonObject) {
        super.init(input)
        defaultCountryCode = input["defaultCountryCode"]?.jsonPrimitive?.content ?: ""
        validatePhoneNumber = input["validatePhoneNumber"]?.jsonPrimitive?.boolean ?: false
    }

    override fun init(input: JsonElement) {
        if (input is JsonObject) {
            // New structure with phoneNumber and countryCode
            phoneNumber = input[PHONE_NUMBER]?.jsonPrimitive?.content ?: ""
            countryCode = input[COUNTRY_CODE]?.jsonPrimitive?.content ?: ""
        } else {
            // Legacy structure - simple string
            phoneNumber = input.jsonPrimitive.content
        }
    }

    override fun payload(): JsonObject? {
        return if (countryCode.isEmpty() || phoneNumber.isEmpty()) {
            null
        } else {
            buildJsonObject {
                put(COUNTRY_CODE, countryCode)
                put(PHONE_NUMBER, phoneNumber)
            }
        }
    }
}

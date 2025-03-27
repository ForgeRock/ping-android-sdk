/*
 * Copyright (c) 2025 Ping Identity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci.collector

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * A collector for phone number.
 */
class PhoneNumberCollector : FieldCollector<JsonObject?>(), Validator {

    // default country code
    lateinit var defaultCountryCode: String
        private set

    // country code
    var countryCode: String? = null
    // phone number
    var phoneNumber: String? = null

    override fun init(input: JsonObject) {
        super.init(input)
        defaultCountryCode = input["defaultCountryCode"]?.jsonPrimitive?.content ?: ""
    }

    override fun payload(): JsonObject? {
        return if (countryCode.isNullOrEmpty() || phoneNumber.isNullOrEmpty()) {
            null
        } else {
            buildJsonObject {
                put("countryCode", countryCode)
                put("phoneNumber", phoneNumber)
            }
        }
    }
}

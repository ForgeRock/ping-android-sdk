/*
 * Copyright (c) 2025 - 2026 Ping Identity Corporation. All rights reserved.
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
private const val EXTENSION = "extension"

private const val DEFAULT_COUNTRY_CODE = "defaultCountryCode"
private const val VALIDATE_PHONE_NUMBER = "validatePhoneNumber"
private const val SHOW_EXTENSION = "showExtension"
private const val EXTENSION_LABEL = "extensionLabel"

/**
 * A collector for phone number.
 */
class PhoneNumberCollector : FieldCollector<JsonObject>(), Validator {

    // default country code
    lateinit var defaultCountryCode: String
        private set

    var validatePhoneNumber: Boolean = false
        private set
    var showExtension: Boolean = false
        private set
    var extensionLabel: String = ""
        private set

    // country code
    var countryCode: String = ""
    // phone number
    var phoneNumber: String = ""
    // extension
    var extension: String = ""

    override fun init(input: JsonObject) : PhoneNumberCollector {
        super.init(input)
        defaultCountryCode = input[DEFAULT_COUNTRY_CODE]?.jsonPrimitive?.content ?: ""
        validatePhoneNumber = input[VALIDATE_PHONE_NUMBER]?.jsonPrimitive?.boolean ?: false
        showExtension = input[SHOW_EXTENSION]?.jsonPrimitive?.boolean ?: false
        extensionLabel = input[EXTENSION_LABEL]?.jsonPrimitive?.content ?: ""
        return this
    }

    override fun init(input: JsonElement) {
        if (input is JsonObject) {
            // New structure with phoneNumber and countryCode
            phoneNumber = input[PHONE_NUMBER]?.jsonPrimitive?.content ?: ""
            countryCode = input[COUNTRY_CODE]?.jsonPrimitive?.content ?: ""
            extension = input[EXTENSION]?.jsonPrimitive?.content ?: ""
            extensionLabel = input[EXTENSION_LABEL]?.jsonPrimitive?.content ?: ""
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
                put(EXTENSION, extension)
            }
        }
    }
}

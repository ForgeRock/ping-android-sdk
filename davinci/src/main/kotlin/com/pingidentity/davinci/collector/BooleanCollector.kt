/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci.collector

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * A Collector implementation for a single checkbox field.
 *
 * ```json
 * {
 *    "type": "SINGLE_CHECKBOX",
 *    "key": "single-checkbox-field",
 *    "label": "This is a sample checkbox test. ",
 *    "required": true,
 *    "inputType": "BOOLEAN",
 *    "appearance": "CHECKBOX", # Optional, defaults to CHECKBOX. Can be either CHECKBOX or SWITCH.
 *    "errorMessage": "Select the checkbox to continue.",
 *    "richContent": {
 *        "content": "This is a sample checkbox test. "
 *    }
 * }
 *
 */
class BooleanCollector: FieldCollector<Boolean>() {
    var appearance: SingleCheckboxAppearance = SingleCheckboxAppearance.CHECKBOX
        private set

    var errorMessage: String = ""
        private set

    var value: Boolean = false
    var richContent: String? = null
        private set

    override fun init(input: JsonObject): BooleanCollector {
        super.init(input)
        appearance = when (input["appearance"]?.jsonPrimitive?.contentOrNull ?: SingleCheckboxAppearance.CHECKBOX.value) {
            SingleCheckboxAppearance.CHECKBOX.value -> SingleCheckboxAppearance.CHECKBOX
            SingleCheckboxAppearance.SWITCH.value -> SingleCheckboxAppearance.SWITCH
            else -> SingleCheckboxAppearance.CHECKBOX
        }
        errorMessage = input["errorMessage"]?.jsonPrimitive?.contentOrNull ?: "This field is required."
        richContent = input["richContent"]?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull
        return this
    }

    override fun validate(): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()
        val result = super.validate()
        errors.addAll(result)
        if (required && !value) {
            errors.add(SingleCheckboxRequiredError(key, errorMessage))
        }
        return errors
    }

    override fun payload(): Boolean {
        return value
    }
}

enum class SingleCheckboxAppearance(val value: String) {
    CHECKBOX("CHECKBOX"),
    SWITCH("SWITCH")
}

class SingleCheckboxRequiredError(val key: String, val message: String) : ValidationError() {
    override fun toString(): String {
        return "SingleCheckboxRequiredError(key='$key', message='$message')"
    }
}
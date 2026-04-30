/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci.collector

import com.pingidentity.davinci.HREF
import com.pingidentity.davinci.REPLACEMENTS
import com.pingidentity.davinci.RICH_CONTENT
import com.pingidentity.davinci.RichContent
import com.pingidentity.davinci.RichContentReplacement
import com.pingidentity.davinci.TARGET
import com.pingidentity.davinci.TYPE
import com.pingidentity.davinci.VALUE
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
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
    var richContent: RichContent? = null
        private set

    override fun init(input: JsonObject): BooleanCollector {
        super.init(input)
        appearance = when (input["appearance"]?.jsonPrimitive?.contentOrNull ?: SingleCheckboxAppearance.CHECKBOX.value) {
            SingleCheckboxAppearance.CHECKBOX.value -> SingleCheckboxAppearance.CHECKBOX
            SingleCheckboxAppearance.SWITCH.value -> SingleCheckboxAppearance.SWITCH
            else -> SingleCheckboxAppearance.CHECKBOX
        }
        errorMessage = input["errorMessage"]?.jsonPrimitive?.contentOrNull ?: "This field is required."
        val richContentJson = input[RICH_CONTENT] as? JsonObject
        richContentJson?.let {
            richContent = RichContent(
                richText = it["content"]?.jsonPrimitive?.contentOrNull ?: label,
                replacements = (it[REPLACEMENTS] as? JsonObject)
                    ?.mapValues { (_, element) ->
                        val obj = element as? JsonObject ?: return@mapValues RichContentReplacement()
                        RichContentReplacement(
                            value = (obj[VALUE] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                            href = (obj[HREF] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                            type = (obj[TYPE] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                            target = (obj[TARGET] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                        )
                    } ?: emptyMap()
            )
        }
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
/*
 * Copyright (c) 2025 Ping Identity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci.collector

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive

/**
 * Class representing CHECKBOX, COMBOBOX type with MULTI_SELECT inputType.
 *
 * This class inherits from the FieldCollector class and implements the Collector interface.
 * It is used to collect multiple values from a list of options.
 *
 * @constructor Creates a new MultiSelectCollector with the given input.
 */
open class MultiSelectCollector : FieldCollector() {
    lateinit var options: List<Option>
        private set
    var required = false
        private set


    var value: MutableList<String> = mutableListOf()

    override fun init(input: JsonObject) {
        super.init(input)
        required = input["required"]?.jsonPrimitive?.boolean ?: false
        options = input(input)
    }

    override fun init(input: JsonElement) {
        super.init(input)
        if (input is JsonArray) {
            input.forEach {
                value.add(it.jsonPrimitive.content)
            }
        }
    }

    /**
     * Function to validate the MultiSelectCollector.
     * @return A list of Validation Error
     */
    open fun validate(): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()
        if (required && value.isEmpty()) {
            errors.add(Required)
        }
        return errors.toList()
    }

}
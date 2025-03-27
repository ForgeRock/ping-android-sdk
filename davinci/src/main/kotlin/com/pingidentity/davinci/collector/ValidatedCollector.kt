/*
 * Copyright (c) 2025 Ping Identity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci.collector

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Abstract class representing a validated collector.
 *
 * @property validation The validation object.
 * @property required The required flag.
 */
abstract class ValidatedCollector : SingleValueCollector() {

    var validation: Validation? = null
        private set


    override fun init(input: JsonObject) {
        super.init(input)
        validation = input["validation"]?.jsonObject?.let {
            Validation(
                Regex(it["regex"]?.jsonPrimitive?.content ?: ""),
                it["errorMessage"]?.jsonPrimitive?.content ?: ""
            )
        }
    }

    /**
     * Function to validate the collector.
     * @return A list of validation errors, if any.
     */
    override fun validate(): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()
        errors.addAll(super.validate())
        if (validation?.regex?.matches(value) == false) {
            errors.add(RegexError(validation?.errorMessage ?: ""))
        }
        return errors.toList()
    }

}
/*
 * Copyright (c) 2024 - 2025 Ping Identity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci.collector

import com.pingidentity.davinci.plugin.Collector
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Abstract class representing a fields from the form.
 *
 * @property type The type of the field collector.
 * @property key The key of the field collector.
 * @property label The label of the field collector.
 *
 */
abstract class FieldCollector : Collector {
    var type = ""
        private set
    var key = ""
        private set
    var label = ""
        private set

    /**
     * Function to initialize the field collector.
     * @param input The input JSON object to parse.
     */
    override fun init(input: JsonObject) {
        type = input["type"]?.jsonPrimitive?.content ?: ""
        key = input["key"]?.jsonPrimitive?.content ?: ""
        label = input["label"]?.jsonPrimitive?.content ?: ""
    }
}

/**
 * Data class representing the validation of the field collector.
 *
 * @property regex The regex of the validation.
 * @property errorMessage The error message of the validation.
 */
data class Validation(val regex: Regex, val errorMessage: String)
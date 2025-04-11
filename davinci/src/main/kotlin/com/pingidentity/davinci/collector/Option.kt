/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci.collector

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Data class representing an option.
 * @property label The label of the option.
 * @property value The value of the option.
 */
data class Option(val label: String, val value: String)

/**
 * Function to parse the input JSON object and return a list of options.
 * @param input The input JSON object.
 * @return A list of options.
 */
fun input(input: JsonObject): List<Option> {
    return input["options"]?.jsonArray?.map { jsonElement ->
        val jsonObject = jsonElement.jsonObject
        val key = jsonObject["label"]?.jsonPrimitive?.content ?: ""
        val value = jsonObject["value"]?.jsonPrimitive?.content ?: ""
        Option(key, value)
    } ?: emptyList()
}

/*
 * Copyright (c) 2025 Ping Identity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci.collector

import com.pingidentity.davinci.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray

/**
 * Data class representing an option.
 * @property label The label of the option.
 * @property value The value of the option.
 */
@Serializable
data class Option(val label: String, val value: String) {

    companion object {
        /**
         * Function to parse the input JSON object and return a list of options.
         * @param input The input JSON object.
         * @return A list of options.
         */
        fun options(input: JsonObject): List<Option> {
            return input["options"]?.jsonArray?.map { jsonElement ->
                json.decodeFromJsonElement<Option>(jsonElement)
            } ?: emptyList()
        }
    }
}

/*
 * Copyright (c) 2024 PingIdentity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci.collector

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class Option(val label: String, val value: String)

fun input(input: JsonObject): List<Option> {
    return input["options"]?.jsonArray?.map { jsonElement ->
        val jsonObject = jsonElement.jsonObject
        val key = jsonObject["label"]?.jsonPrimitive?.content ?: ""
        val value = jsonObject["value"]?.jsonPrimitive?.content ?: ""
        Option(key, value)
    } ?: emptyList()
}

/*
 * Copyright (c) 2024 Ping Identity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci.collector

import com.pingidentity.davinci.plugin.Collectors
import com.pingidentity.davinci.plugin.RequestAdapter
import com.pingidentity.orchestrate.FlowContext
import com.pingidentity.orchestrate.Request
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal fun Collectors.eventType(): String? {
    forEach {
        when (it) {
            is SubmitCollector, is FlowCollector -> {
                if ((it as SingleValueCollector).value.isNotEmpty()) {
                    return it.value
                }
            }

            else -> {}
        }
    }
    return null
}

/**
 * Find any collectors that override the request
 */
internal fun Collectors.request(context: FlowContext, request: Request): Request {
    var result = request
    forEach { collector ->
        if (collector is RequestAdapter) {
            result = collector.request(context, result)
        }
    }
    return result
}

/**
 * Represents a list of collectors as a JSON object for posting to the server.
 *
 * This function takes a list of collectors and represents it as a JSON object. It iterates over the list of collectors,
 * adding each collector's key and value to the JSON object if the collector's value is not empty.
 *
 * @return A JSON object representing the list of collectors.
 */
internal fun Collectors.asJson(): JsonObject {
    return buildJsonObject {
        forEach {
            when (it) {
                is SubmitCollector, is FlowCollector -> {
                    if ((it as SingleValueCollector).value.isNotEmpty()) {
                        put("actionKey", it.key)
                    }
                }

                else -> {}
            }
        }
        val map = mutableMapOf<String, Any>()
        forEach {
            when (it) {
                is TextCollector, is PasswordCollector -> {
                    if ((it as SingleValueCollector).value.isNotEmpty()) {
                        toMap(map, it.key, it.value)
                    }
                }

                is SingleSelectCollector -> {
                    if (it.value.isNotEmpty()) {
                        toMap(map, it.key, it.value)
                    }
                }

                is MultiSelectCollector -> {
                    if (it.value.isNotEmpty()) {
                        toMap(map, it.key, it.value)
                    }
                }
            }
        }
        put("formData", mapToJsonObject(map))
    }
}

@Suppress("UNCHECKED_CAST")
private fun toMap(map: MutableMap<String, Any>, key: String, value: Any) {
    var currentMap = map

    //TODO Remove this when the nested key is removed.
    val keys = key.split(".")

    for (i in keys.indices) {
        val part = keys[i]
        if (i == keys.size - 1) {
            currentMap[part] = value
        } else {
            currentMap =
                currentMap.getOrPut(part) { mutableMapOf<String, Any>() } as MutableMap<String, Any>
        }
    }
}

@Suppress("UNCHECKED_CAST")
fun mapToJsonObject(map: Map<String, Any>): JsonObject {
    return JsonObject(map.mapValues { (_, value) ->
        when (value) {
            is Map<*, *> -> mapToJsonObject(value as Map<String, Any>) // Recursive for nested maps
            is List<*> -> JsonArray(value.map { JsonPrimitive(it.toString()) }) // Convert List to JsonArray
            is String -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            else -> {
                JsonPrimitive(value.toString())
            }
        }
    })
}
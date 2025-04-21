/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci.collector

import com.pingidentity.davinci.plugin.Collectors
import com.pingidentity.davinci.plugin.RequestInterceptor
import com.pingidentity.davinci.plugin.Submittable
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
            is Submittable -> {
                val eventType = it.eventType()
                it.payload()?.let {
                    return eventType
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
        if (collector is RequestInterceptor) {
            result = collector.intercept(context, result)
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
        val map = mutableMapOf<String, Any>()
        forEach {
            if (it is SubmitCollector || it is FlowCollector) {
                it.payload()?.let { _ ->
                    put("actionKey", it.id())
                }
            } else {
                it.payload()?.let { payload ->
                    map[it.id()] = payload
                }
            }
        }
        put("formData", mapToJsonObject(map))
    }
}

/**
 * Converts a map to a JSON object.
 *
 *  This function takes a map of string keys and any values, and converts it to a JSON object.
 *  It recursively converts nested maps to JSON objects, lists to JSON arrays, and other values to JSON primitives.
 *
 *  @param map The map to convert.
 *  @return A JSON object representing the map.
 */
@Suppress("UNCHECKED_CAST")
private fun mapToJsonObject(map: Map<String, Any>): JsonObject {
    return JsonObject(map.mapValues { (_, value) ->
        when (value) {
            is JsonObject -> value
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
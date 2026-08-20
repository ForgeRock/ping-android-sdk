/*
 * Copyright (c) 2024 - 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci.collector

import com.pingidentity.davinci.plugin.ActionKeyProvider
import com.pingidentity.davinci.plugin.Collectors
import com.pingidentity.davinci.plugin.Submittable
import com.pingidentity.orchestrate.FlowContext
import com.pingidentity.orchestrate.RequestInterceptor
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import com.pingidentity.network.HttpRequest as Request

/**
 * Returns the event type for this list of collectors.
 *
 * Uses a two-pass strategy to ensure explicit user actions always take precedence:
 * - First pass: returns the event type of the first [SubmitCollector] or [FlowCollector] whose
 *   [ActionKeyProvider.actionKey] is non-null (i.e. the user has selected that button).
 * - Second pass: falls back to the first [Submittable] whose `payload()` is non-null
 *   (e.g. a FIDO collector reporting a WebAuthn error).
 *
 * This ordering prevents a concurrently-failed collector (e.g. FIDO) from shadowing an explicit
 * user action in the same node, regardless of its position in the list.
 *
 * @return the event type string, or null if no matching collector is found.
 */
internal fun Collectors.eventType(): String? {
    // First pass: honor explicit Submit/Flow actions.
    forEach {
        if ((it is SubmitCollector || it is FlowCollector) && it.actionKey != null) {
            return (it as Submittable).eventType()
        }
    }
    // Second pass: fall back to any Submittable with a payload (e.g. FIDO errors).
    forEach {
        if (it is Submittable && it.payload() != null) {
            return it.eventType()
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
 * Serialises this list of collectors into a JSON object for posting to the DaVinci server.
 *
 * The `actionKey` field is populated with strict priority:
 * 1. [SubmitCollector] / [FlowCollector] — always set `actionKey` when their
 *    [ActionKeyProvider.actionKey] is non-null (explicit user action).
 * 2. [MetadataCollector] — sets `actionKey` and adds its payload to `formData` only when
 *    both [ActionKeyProvider.actionKey] and `payload()` are non-null.
 * 3. Any other [ActionKeyProvider] (e.g. a FIDO error collector) — sets `actionKey` only if
 *    it has not already been set by a higher-priority collector; payload is always added to `formData`.
 * 4. All other collectors — payload is added to `formData` under the collector's id.
 *
 * @return a [JsonObject] with an `actionKey` field and a `formData` object.
 */
internal fun Collectors.asJson(): JsonObject {
    return buildJsonObject {
        val map = mutableMapOf<String, Any>()
        var actionKeySet = false
        forEach {
            when {
                (it is SubmitCollector || it is FlowCollector) -> {
                    it.actionKey?.let { key ->
                        put("actionKey", key)
                        actionKeySet = true
                    }
                }
                it is MetadataCollector -> {
                    val key = it.actionKey
                    val payload = it.payload()
                    if (key != null && !payload.isNullOrEmpty()) {
                        put("actionKey", key)
                        actionKeySet = true
                        map[key] = payload
                    }
                }
                it is ActionKeyProvider -> {
                    val key = it.actionKey
                    if (key != null) {
                        if (!actionKeySet) {
                            put("actionKey", key)
                            actionKeySet = true
                        }
                        it.payload()?.let { payload ->
                            if (payload is JsonObject && payload.isNotEmpty()) map[key] = payload
                        }
                    } else {
                        it.payload()?.let { payload -> map[it.id()] = payload }
                    }
                }
                else -> {
                    it.payload()?.let { payload -> map[it.id()] = payload }
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
/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.journey.plugin

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

abstract class AbstractCallback : Callback {

    lateinit var json: JsonObject
        protected set

    protected abstract fun onAttribute(name: String, value: JsonElement)

    override fun init(jsonObject: JsonObject) {
        this.json = jsonObject
        jsonObject["output"]?.jsonArray?.forEach { outputItem ->
            val outputObject = outputItem.jsonObject
            outputObject["name"]?.jsonPrimitive?.content?.let { name ->
                outputObject["value"]?.let { value ->
                    onAttribute(name, value)
                }
            }
        }
    }

    fun input(vararg value: Any): JsonObject {
        val orig = json["input"]?.jsonArray

        val updated = buildJsonArray {
            value.forEachIndexed { index, element ->
                val inputName =
                    orig?.get(index)?.jsonObject?.get("name")?.jsonPrimitive?.content ?: ""
                add(buildJsonObject {
                    put("name", inputName)
                    when (value[index]) {
                        is Int -> put("value", element as Int)
                        is String -> put("value", element as String)
                        is Boolean -> put("value", element as Boolean)
                        is Double -> put("value", element as Double)
                    }
                })
            }
        }

        // Convert the JsonObject to a mutable map
        val mutableMap = json.toMutableMap()

        // Modify the map
        mutableMap["input"] = updated

        // Convert the map back to a JsonObject
        json = buildJsonObject {
            mutableMap.forEach { (key, value) ->
                put(key, value)
            }
        }
        return update(updated)
    }

    /*
    fun input(suffix: String, value: Any): JsonObject {
        val orig = json["input"]?.jsonArray

        val updated = buildJsonArray {
            orig?.forEach {
                val inputName = it.jsonObject["name"]?.jsonPrimitive?.content ?: ""
                if (inputName.endsWith(suffix)) {
                    add(buildJsonObject {
                        put("name", inputName)
                        when (value) {
                            is Int -> put("value", value)
                            is String -> put("value", value)
                            is Boolean -> put("value", value)
                            is Double -> put("value", value)
                        }
                    })
                } else {
                    add(it)
                }
            }
        }
        return update(updated)
    }
     */

    private fun update(input: JsonArray): JsonObject {
        // Convert the JsonObject to a mutable map
        val mutableMap = json.toMutableMap()

        // Modify the map
        mutableMap["input"] = input

        // Convert the map back to a JsonObject
        json = buildJsonObject {
            mutableMap.forEach { (key, value) ->
                put(key, value)
            }
        }
        return json
    }

    override fun asJson(): JsonObject = json
}
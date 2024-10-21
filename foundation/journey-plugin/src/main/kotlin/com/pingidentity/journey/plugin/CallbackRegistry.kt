/*
 * Copyright (c) 2024. PingIdentity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.journey.plugin

import com.pingidentity.orchestrate.ContinueNode
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object CallbackRegistry {
    private val callbacks: MutableMap<String, () -> Callback> = HashMap()
    val derivedCallbacks: MutableList<(JsonObject) -> String?> = mutableListOf()

    fun register(type: String, block: () -> Callback) {
        callbacks[type] = block
    }

    fun registerDerived(block: (JsonObject) -> String?) {
        derivedCallbacks.add(block)
    }

    /**
     * Injects the DaVinci and ContinueNode instances into the collectors.
     * @param davinci The DaVinci instance to be injected.
     * @param continueNode The ContinueNode instance to be injected.
     */
    fun inject(journey: Journey,  continueNode: ContinueNode) {
        continueNode.callbacks.forEach { callback ->
            if (callback is JourneyAware) {
                callback.journey = journey
            }
        }
    }


    fun callback(array: JsonArray): List<Callback> {
        val list = mutableListOf<Callback>()
        array.forEach { item ->
            val jsonObject = item.jsonObject
            val type = jsonObject["type"]?.jsonPrimitive?.content
            callbacks[type]?.let { list.add(it().apply { init(jsonObject) }) }
        }
        return list
    }

}
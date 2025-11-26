/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.journey.plugin

import com.pingidentity.orchestrate.ContinueNode
import com.pingidentity.orchestrate.ContinueNodeAware
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * CallbackRegistry is responsible for managing the registration and retrieval of callbacks.
 * It allows for the registration of callbacks by type and provides a method to retrieve
 * callbacks based on a JSON array.
 */
object CallbackRegistry {
    private val callbacks: MutableMap<String, () -> Callback> = mutableMapOf()

    /**
     * Returns a map of registered callbacks.
     */
    fun callbacks() = callbacks.toMap()

    /**
     * Registers a callback with the specified type.
     * @param type The type of the callback.
     * @param block A lambda function that returns an instance of the callback.
     */
    fun register(type: String, block: () -> Callback) {
        callbacks[type] = block
    }

    /**
     * Injects the Journey instances into the callbacks.
     * @param continueNode The ContinueNode instance.
     */
    fun inject(continueNode: ContinueNode) {
        continueNode.callbacks.forEach { callback ->
            if (callback is ContinueNodeAware) {
                callback.continueNode = continueNode
            }
        }
    }

    /**
     * Retrieves a list of callbacks based on the provided JSON array.
     * @param journey The Journey instance to be injected.
     * @param array The JSON array containing the callback types.
     *   * @return A list of initialized Callback instances.
     */
    fun callback(journey: Journey, array: JsonArray): List<Callback> {
        val list = mutableListOf<Callback>()
        array.forEach { item ->
            val jsonObject = item.jsonObject
            val type = jsonObject["type"]?.jsonPrimitive?.content
            callbacks[type]?.let {
                val callback = it()
                //We want to inject Journey before init function, so that it can access attribute from Journey
                if (callback is JourneyAware) {
                    callback.journey = journey
                }
                //callback.init may return a different callback, parents callback is responsible
                //to init the child callback and inject required dependency
                list.add(callback.init(jsonObject))
            }
        }
        return list
    }

}
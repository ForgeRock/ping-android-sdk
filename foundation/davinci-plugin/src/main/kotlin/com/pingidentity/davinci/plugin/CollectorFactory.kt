/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci.plugin

import com.pingidentity.orchestrate.ContinueNode
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The CollectorFactory object is responsible for creating and managing Collector instances.
 * It maintains a map of collector creation functions, keyed by type.
 * It also provides functions to register new types of collectors and to create collectors from a JsonArray.
 */
object CollectorFactory {
    // A mutable map to hold the collector creation functions.
    private val collectors: MutableMap<String, () -> Collector<*>> = HashMap()

    /**
     * Registers a new type of Collector.
     * @param type The type of the Collector.
     * @param block A function that creates a new instance of the Collector.
     */
    fun register(type: String, block: () -> Collector<*>) {
        collectors[type] = block
    }

    /**
     * Creates a list of Collector instances from a JsonArray.
     * Each JsonObject in the array should have a "type" field that matches a registered Collector type.
     * @param array The JsonArray to create the Collectors from.
     * @return A list of Collector instances.
     */
    fun collector(array: JsonArray): List<Collector<*>> {
        val list = mutableListOf<Collector<*>>()
        array.forEach { item ->
            val jsonObject = item.jsonObject
            val type = jsonObject["inputType"]?.jsonPrimitive?.content ?: jsonObject["type"]?.jsonPrimitive?.content
            collectors[type]?.let {
                list.add(it().apply {
                    init(jsonObject)
                })
            }
        }
        return list
    }

    /**
     * Injects the DaVinci and ContinueNode instances into the collectors.
     * @param davinci The DaVinci instance to be injected.
     * @param continueNode The ContinueNode instance to be injected.
     */
    fun inject(davinci: DaVinci, continueNode: ContinueNode) {
        continueNode.collectors.forEach { collector ->
            if (collector is ContinueNodeAware) {
                collector.continueNode = continueNode
            }
            if (collector is DaVinciAware) {
                collector.davinci = davinci
            }
        }
    }

    /**
     * Resets the CollectorFactory by clearing all registered collectors.
     */
    fun reset() {
        collectors.clear()
    }
}
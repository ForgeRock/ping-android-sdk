/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci.plugin

import com.pingidentity.orchestrate.ContinueNode
import com.pingidentity.orchestrate.ContinueNodeAware
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
    private val collectors: MutableMap<String, () -> Collector<*>> = mutableMapOf()

    /**
     * Returns a map of registered collectors.
     */
    fun collectors() = collectors.toMap()

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
     *
     * @param daVinci The DaVinci instance to be injected.
     * @param array The JsonArray to create the Collectors from.
     * @return A list of Collector instances.
     */
    fun collector(
        daVinci: DaVinci,
        array: JsonArray,
    ): List<Collector<*>> {

        val list = mutableListOf<Collector<*>>()
        array.forEach { item ->
            val jsonObject = item.jsonObject
            val type = jsonObject["inputType"]?.jsonPrimitive?.content ?: jsonObject["type"]?.jsonPrimitive?.content
            collectors[type]?.let {
                val collector = it()
                //We want to inject davinci before init function, so that it can access attribute from davinci
                if (collector is DaVinciAware) {
                    collector.davinci = daVinci
                }
                //collector.init may return a different collector, parents collector is responsible
                //to init the child collector and inject required dependency
                list.add(collector.init(jsonObject))
            }
        }
        return list
    }


    /**
     * Injects the DaVinci and ContinueNode instances into the collectors.
     * @param continueNode The ContinueNode instance to be injected.
     */
    fun inject(continueNode: ContinueNode) {
        continueNode.collectors.forEach { collector ->
            if (collector is ContinueNodeAware) {
                collector.continueNode = continueNode
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
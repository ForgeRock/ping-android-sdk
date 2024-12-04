/*
 * Copyright (c) 2024 Ping Identity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci.plugin

import com.pingidentity.orchestrate.Action
import com.pingidentity.orchestrate.ContinueNode
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Interface representing a Collector.
 * A Collector is a type of Action that can be initialized with a JsonObject.
 */
interface Collector : Action {
    /**
     * Initializes the Collector with the given input.
     *
     * @param input The JsonObject used to initialize the Collector.
     */
    fun init(input: JsonObject)

    /**
     * Initializes the default values of the Collector with the given input.
     * The formData.value.{key} is used to lookup the default value.
     */
    fun init(input: JsonElement) {
        //Default implementation for init with JsonElement
    }
}

/**
 * Extension property for Connector class to get a list of collectors.
 *
 * @return A list of Collector instances.
 */
val ContinueNode.collectors: List<Collector>
    get() = this.actions.filterIsInstance<Collector>()

/**
 * Type alias for a list of collectors.
 */
typealias Collectors = List<Collector>
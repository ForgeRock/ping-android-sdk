/*
 * Copyright (c) 2024. PingIdentity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.journey.plugin

import com.pingidentity.orchestrate.Action
import com.pingidentity.orchestrate.ContinueNode
import kotlinx.serialization.json.JsonObject

interface Callback : Action {
    fun init(jsonObject: JsonObject)

    //Callback is more self-contained, it created its own json without depending on other Callback
    fun asJson(): JsonObject
}

/**
 * Extension property for Connector class to get a list of collectors.
 *
 * @return A list of Collector instances.
 */
val ContinueNode.callbacks: List<Callback>
    get() = this.actions.filterIsInstance<Callback>()


/**
 * Type alias for a list of collectors.
 */
typealias Callbacks = List<Callback>
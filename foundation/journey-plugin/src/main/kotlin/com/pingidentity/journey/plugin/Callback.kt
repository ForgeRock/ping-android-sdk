/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.journey.plugin

import com.pingidentity.orchestrate.Action
import kotlinx.serialization.json.JsonObject

/**
 * Callback interface for handling actions in the journey plugin.
 */
interface Callback : Action {
    /**
     * Initializes the callback with the provided JSON object.
     * @param jsonObject The JSON object containing the callback configuration.
     * @return The initialized Callback instance.
     */
    fun init(jsonObject: JsonObject): Callback

    /**
     * Returns the payload of the callback.
     */
    fun payload(): JsonObject
}



/**
 * Type alias for a list of collectors.
 */
typealias Callbacks = List<Callback>
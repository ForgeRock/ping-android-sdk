/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.journey.callback

import com.pingidentity.journey.plugin.AbstractCallback
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive

/**
 * A callback for polling the user for a response.
 *
 * @property waitTime The period of time in milliseconds that the client should wait before replying to this callback.
 * @property message The message which should be displayed to the user
 */
class PollingWaitCallback : AbstractCallback() {

    var waitTime: Int = 0
        private set

    var message: String = ""
        private set

    override fun init(name: String, value: JsonElement) {
        when (name) {
            "message" -> this.message = value.jsonPrimitive.content
            "waitTime" -> this.waitTime = value.jsonPrimitive.int
        }
    }

}
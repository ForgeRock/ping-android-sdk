/*
 * Copyright (c) 2024 Ping Identity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.journey.callback

import com.pingidentity.journey.plugin.AbstractCallback
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive

class PollingWaitCallback : AbstractCallback() {

    /**
     * The period of time in milliseconds that the client should wait before replying to this callback.
     */
    var waitTime: Int = 0
        private set

    /**
     * The message which should be displayed to the user
     */
    var message: String = ""
        private set

    override fun onAttribute(name: String, value: JsonElement) {
        when (name) {
            "message" -> this.message = value.jsonPrimitive.content
            "waitTime" -> this.waitTime = value.jsonPrimitive.int
        }
    }

}
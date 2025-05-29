/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.journey.callback

import com.pingidentity.journey.plugin.AbstractCallback
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive

/**
 * A callback for displaying text output.
 *
 * @property messageType The type of message (e.g., information, warning, error).
 * @property message The message to be displayed.
 */
open class TextOutputCallback : AbstractCallback() {
    var messageType = 0
        private set

    var message: String = ""
        private set

    override fun init(name: String, value: JsonElement) {
        when (name) {
            "messageType" -> this.messageType = value.jsonPrimitive.int
            "message" -> this.message = value.jsonPrimitive.content
        }
    }

    override fun payload(): JsonObject {
        return if (messageType == 4) {
            buildJsonObject {};
        } else {
            super.payload();
        }
    }

    companion object {
        /**
         * Information message.
         */
        const val INFORMATION: Int = 0

        /**
         * Warning message.
         */
        const val WARNING: Int = 1

        /**
         * Error message.
         */
        const val ERROR: Int = 2
    }

}
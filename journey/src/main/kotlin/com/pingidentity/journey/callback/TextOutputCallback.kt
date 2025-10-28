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
 * @property messageType The type of message TextOutputCallbackMessageType (information, warning, error, script).
 * @property message The message to be displayed.
 */
open class TextOutputCallback : AbstractCallback() {
    var messageType = TextOutputCallbackMessageType.UNKNOWN
        private set

    var message: String = ""
        private set

    override fun init(name: String, value: JsonElement) {
        when (name) {
            "messageType" -> this.messageType = fromType(value.jsonPrimitive.int)
            "message" -> this.message = value.jsonPrimitive.content
        }
    }

    override fun payload(): JsonObject {
        return if (messageType == TextOutputCallbackMessageType.SCRIPT ||
            messageType == TextOutputCallbackMessageType.UNKNOWN) {
            buildJsonObject {}
        } else {
            super.payload()
        }
    }

    companion object {
        private fun fromType(type: Int): TextOutputCallbackMessageType =
            TextOutputCallbackMessageType.entries.find { it.type == type } ?: TextOutputCallbackMessageType.SCRIPT
    }
}

/**
 * Enum representing the type of message for TextOutputCallback.
 */
enum class TextOutputCallbackMessageType(val type: Int) {
    INFORMATION(0),
    WARNING(1),
    ERROR(2),
    SCRIPT(4),
    UNKNOWN(-1)
}
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
import kotlinx.serialization.json.jsonPrimitive

open class TextOutputCallback : AbstractCallback() {
    /**
     * The message type
     */
    var messageType = 0

    /**
     * The message
     */
    var message: String? = null

    override fun onAttribute(name: String, value: JsonElement) {
        when (name) {
            "messageType" -> this.messageType = value.jsonPrimitive.content.toInt()
            "message" -> this.message = value.jsonPrimitive.content
        }
    }

    //TODO Make sure not to post back javascript
    override fun asJson(): JsonObject {
        if (messageType == 4) {
            return buildJsonObject {};
        } else {
            return super.asJson();
        }
    }

    companion object {
        //Message Type
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
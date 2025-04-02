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
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * Callback to retrieve the selected option from a list of options
 */
class ConfirmationCallback : AbstractCallback() {

    var prompt: String = ""
        private set

    /**
     * Get the list of options.
     * @return the list of options.
     */
    var options: List<String> = emptyList()
        private set

    var defaultOption = 0
        private set

    var optionType = 0
        private set

    var messageType = 0
        private set

    var selectedIndex = 0

    override fun onAttribute(name: String, value: JsonElement) {
        when (name) {
            "prompt" -> this.prompt = value.jsonPrimitive.content ?: ""
            "optionType" -> this.optionType = value.jsonPrimitive.int
            "defaultOption" -> {
                this.defaultOption = value.jsonPrimitive.int
                this.selectedIndex = this.defaultOption
            }

            "messageType" -> this.messageType = value.jsonPrimitive.int
            "options" -> this.options = value.jsonArray.map {
                it.jsonPrimitive.content
            }
        }
    }

    override fun asJson(): JsonObject {
        return input(selectedIndex)
    }


    companion object {
        //Option Type
        const val UNSPECIFIED_OPTION: Int = -1
        const val YES_NO_OPTION: Int = 0
        const val YES_NO_CANCEL_OPTION: Int = 1
        const val OK_CANCEL_OPTION: Int = 2

        //Option
        const val YES: Int = 0
        const val NO: Int = 1
        const val CANCEL: Int = 2
        const val OK: Int = 3

        //Message Type
        const val INFORMATION: Int = 0
        const val WARNING: Int = 1
        const val ERROR: Int = 2
    }
}
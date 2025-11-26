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
 * A callback for collecting a confirmation from a user.
 *
 * @property prompt The prompt message.
 * @property options The list of options.
 * @property defaultOption The default option index.
 * @property optionType The type of options.
 * @property messageType The type of message.
 * @property selectedIndex The index of the selected option.
 */
class ConfirmationCallback : AbstractCallback() {

    var prompt: String = ""
        private set

    var options: List<String> = emptyList()
        private set

    var defaultOption = ConfirmationCallbackSelection.NO
        private set

    var optionType = ConfirmationCallbackOptionType.YES_NO_OPTION
        private set

    var messageType = ConfirmationCallbackMessageType.INFORMATION
        private set

    lateinit var selectedIndex: Number

    override fun init(name: String, value: JsonElement) {
        when (name) {
            "prompt" -> this.prompt = value.jsonPrimitive.content
            "optionType" -> this.optionType = optionTypeFromType(value.jsonPrimitive.int)
            "defaultOption" -> this.defaultOption = selectionFromIndex(value.jsonPrimitive.int)
            "messageType" -> this.messageType = messageTypeFromType(value.jsonPrimitive.int)
            "options" -> this.options = value.jsonArray.map {
                it.jsonPrimitive.content
            }
        }
    }

    override fun payload(): JsonObject {
        return if (::selectedIndex.isInitialized) {
            input(selectedIndex.toInt())
        } else {
            json
        }
    }


    companion object {
        /**
         * Maps an integer type to a ConfirmationCallbackOptionType enum.
         *
         * @param type The integer type.
         * @return The corresponding ConfirmationCallbackOptionType.
         */
        private fun optionTypeFromType(type: Int): ConfirmationCallbackOptionType =
            ConfirmationCallbackOptionType.entries.find { it.type == type }
                ?: ConfirmationCallbackOptionType.UNSPECIFIED_OPTION

        /**
         * Maps an integer type to a ConfirmationCallbackMessageType enum.
         *
         * @param type The integer type.
         * @return The corresponding ConfirmationCallbackMessageType.
         */
        private fun messageTypeFromType(type: Int): ConfirmationCallbackMessageType =
            ConfirmationCallbackMessageType.entries.find { it.messageType == type }
                ?: ConfirmationCallbackMessageType.INFORMATION

        /**
         * Maps an integer index to a ConfirmationCallbackSelection enum.
         */
        private fun selectionFromIndex(index: Int): ConfirmationCallbackSelection =
            ConfirmationCallbackSelection.entries.find { it.selection == index }
                ?: ConfirmationCallbackSelection.CANCEL
    }
}

/**
 * Enum representing the types of confirmation callback options.
 */
enum class ConfirmationCallbackOptionType(val type: Int) {
    UNSPECIFIED_OPTION(-1),
    YES_NO_OPTION(0),
    YES_NO_CANCEL_OPTION(1),
    OK_CANCEL_OPTION(2),
}

/**
 * Enum representing the selection made in a confirmation callback.
 */
enum class ConfirmationCallbackSelection(val selection: Int) {
    YES(0),
    NO(1),
    CANCEL(2),
    OK(3),
}

/**
 * Enum representing the types of confirmation callback messages.
 */
enum class ConfirmationCallbackMessageType(val messageType: Int) {
    INFORMATION(0),
    WARNING(1),
    ERROR(2),
}
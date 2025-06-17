/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.journey.callback

import com.pingidentity.journey.plugin.AbstractCallback

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonPrimitive

/**
 * A callback for collecting text input.
 *
 * @property prompt The prompt for the text input.
 * @property defaultText The default text to display in the input field.
 * @property text The text input by the user.
 */
class TextInputCallback : AbstractCallback() {

    var prompt: String = ""
        private set

    var defaultText: String = ""
        private set

    var text: String = ""

    override fun init(name: String, value: JsonElement) {
        when (name) {
            "prompt" -> prompt = value.jsonPrimitive.content
            "defaultText" -> {
                defaultText = value.jsonPrimitive.content
                text = defaultText
            }

        }
    }

    override fun payload() = input(text)

}
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

class TextInputCallback : AbstractCallback() {

    var prompt: String = ""
        private set

    /**
     * The text to be used as the default text displayed with the prompt.
     */
    var defaultText: String = ""
        private set

    var text: String = ""

    override fun onAttribute(name: String, value: JsonElement) {
        when (name) {
            "prompt" -> prompt = value.jsonPrimitive.content
            "defaultText" -> defaultText = value.jsonPrimitive.content
        }
    }

    override fun asJson() = input(text)

}
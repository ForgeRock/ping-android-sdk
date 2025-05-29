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
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * A callback for collecting a choice from a user.
 * @property choices The list of choices.
 * @property defaultChoice The default choice index.
 * @property prompt The prompt message.
 * @property selectedIndex The index of the selected choice.
 */
class ChoiceCallback : AbstractCallback() {

    var choices: List<String> = listOf()
        private set

    var defaultChoice = 0
        private set

    var prompt: String = ""
        private set

    var selectedIndex: Int = 0

    override fun init(name: String, value: JsonElement) {
        when (name) {
            "prompt" -> this.prompt = value.jsonPrimitive.content ?: ""
            "defaultChoice" -> {
                this.defaultChoice = value.jsonPrimitive.int
                selectedIndex = defaultChoice
            }
            "choices" -> this.choices = value.jsonArray.map {
                it.jsonPrimitive.content
            }
        }
    }

    override fun payload() = input(selectedIndex)

}
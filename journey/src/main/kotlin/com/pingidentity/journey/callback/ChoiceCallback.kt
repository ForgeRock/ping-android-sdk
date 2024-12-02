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
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

class ChoiceCallback : AbstractCallback() {

    var choices: List<String> = listOf()
        private set

    var defaultChoice = 0
        private set

    var prompt: String = ""
        private set

    //Input
    var selectIndex: Int = 0

    override fun onAttribute(name: String, value: JsonElement) {
        when (name) {
            "prompt" -> this.prompt = value.jsonPrimitive.content ?: ""
            "defaultChoice" -> this.defaultChoice = value.jsonPrimitive.int
            "choices" -> this.choices = value.jsonArray.map {
                it.jsonPrimitive.content
            }
        }
    }

    override fun asJson() = input(selectIndex)

}
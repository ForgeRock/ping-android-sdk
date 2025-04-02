/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.journey.callback

import com.pingidentity.journey.plugin.AbstractCallback
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

class KbaCreateCallback: AbstractCallback() {

    var predefinedQuestions: List<String> = emptyList()
        private set

    var selectedQuestion = ""
    var selectedAnswer = ""

    override fun onAttribute(name: String, value: JsonElement) {
        if ("predefinedQuestions" == name) {
            prepareQuestions(value.jsonArray)
        }
    }

    private fun prepareQuestions(array: JsonArray) {
        predefinedQuestions = array.map {
            it.jsonPrimitive.content
        }
    }

    override fun asJson() = input(selectedQuestion, selectedAnswer)
}
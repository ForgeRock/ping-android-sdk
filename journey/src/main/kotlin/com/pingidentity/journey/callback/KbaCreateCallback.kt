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
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * A callback for collecting knowledge-based authentication (KBA) information from a user.
 *
 * @property prompt The prompt message.
 * @property predefinedQuestions The list of predefined questions.
 * @property selectedQuestion The selected question.
 * @property selectedAnswer The answer to the selected question.
 */
class KbaCreateCallback : AbstractCallback() {

    var prompt: String = ""
        private set
    var predefinedQuestions: List<String> = emptyList()
        private set

    var selectedQuestion = ""
    var selectedAnswer = ""
    var allowUserDefinedQuestions = false

    override fun init(name: String, value: JsonElement) {
        when (name) {
            "prompt" -> prompt = value.jsonPrimitive.content
            "predefinedQuestions" -> prepareQuestions(value.jsonArray)
            "allowUserDefinedQuestions" -> allowUserDefinedQuestions = value.jsonPrimitive.boolean
        }
    }

    private fun prepareQuestions(array: JsonArray) {
        predefinedQuestions = array.map {
            it.jsonPrimitive.content
        }
    }

    override fun payload() = input(selectedQuestion, selectedAnswer)
}
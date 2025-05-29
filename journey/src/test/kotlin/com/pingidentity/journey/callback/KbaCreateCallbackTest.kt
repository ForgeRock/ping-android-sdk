/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.journey.callback

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class KbaCreateCallbackTest {

    private lateinit var jsonObject: JsonObject

    @BeforeTest
    fun setUp() {
        jsonObject = Json.parseToJsonElement(
            """
           {
              "type": "KbaCreateCallback",
              "output": [
                {
                  "name": "prompt",
                  "value": "Purpose Message"
                },
                {
                  "name": "predefinedQuestions",
                  "value": [
                    "What's your favorite color?",
                    "what's your favorite place?",
                    "Who was your first employer?"
                  ]
                },
                {
                  "name": "allowUserDefinedQuestions",
                  "value": true
                }
              ],
              "input": [
                {
                  "name": "IDToken1question",
                  "value": ""
                },
                {
                  "name": "IDToken1answer",
                  "value": ""
                }
              ]
            }                       
                    """
        ) as JsonObject
    }

    @Test
    fun initializesCorrectly() {
        val callback = KbaCreateCallback()
        callback.init(jsonObject)

        assertEquals("Purpose Message", callback.prompt)
        assertEquals(
            listOf(
                "What's your favorite color?",
                "what's your favorite place?",
                "Who was your first employer?"
            ),
            callback.predefinedQuestions
        )
        assertEquals("", callback.selectedQuestion)
        assertEquals("", callback.selectedAnswer)
    }

    @Test
    fun payloadReturnsCorrectly() {
        val callback = KbaCreateCallback()
        callback.init(jsonObject)
        callback.selectedQuestion = "What's your favorite color?"
        callback.selectedAnswer = "Blue"

        val payload = callback.payload()
        assertEquals(
            "What's your favorite color?",
            payload["input"]?.jsonArray?.get(0)?.jsonObject?.get("value")?.jsonPrimitive?.content
        )
        assertEquals(
            "Blue",
            payload["input"]?.jsonArray?.get(1)?.jsonObject?.get("value")?.jsonPrimitive?.content
        )
    }

}
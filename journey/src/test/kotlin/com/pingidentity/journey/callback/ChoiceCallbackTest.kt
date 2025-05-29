/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.journey.callback

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ChoiceCallbackTest {

    private lateinit var jsonObject: JsonObject

    @BeforeTest
    fun setUp() {
        jsonObject = Json.parseToJsonElement(
            """{
                    "type": "ChoiceCallback",
                    "output": [
                      {
                        "name": "prompt",
                        "value": "Choose an option"
                      },
                      {
                        "name": "choices",
                        "value": [
                          "Option 1",
                          "Option 2",
                          "Option 3"
                        ]
                      },
                      {
                        "name": "defaultChoice",
                        "value": 1
                      }
                    ],
                    "input": [
                      {
                        "name": "IDToken1",
                        "value": 1
                      }
                    ]
                  }"""
        ) as JsonObject
    }

    @Test
    fun initializesPromptAndChoicesCorrectly() {
        val callback = ChoiceCallback()
        callback.init(jsonObject)
        assertEquals(1, callback.defaultChoice)
        assertEquals(1, callback.selectedIndex)
        assertEquals("Choose an option", callback.prompt)
        assertEquals(listOf("Option 1", "Option 2", "Option 3"), callback.choices)
    }

    @Test
    fun payloadReturnsSelectedChoiceIndexCorrectly() {
        val callback = ChoiceCallback()
        callback.init(jsonObject)
        callback.selectedIndex = 2
        assertEquals(
            2,
            callback.payload()["input"]?.jsonArray?.get(0)?.jsonObject?.get("value")?.jsonPrimitive?.int
        )
    }

}
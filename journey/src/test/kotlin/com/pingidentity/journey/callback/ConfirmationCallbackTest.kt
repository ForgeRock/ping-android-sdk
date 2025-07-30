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

class ConfirmationCallbackTest {

    private lateinit var jsonObject: JsonObject

    @BeforeTest
    fun setUp() {
        jsonObject = Json.parseToJsonElement(
            """
            {
              "type": "ConfirmationCallback",
              "output": [
                {
                  "name": "prompt",
                  "value": "Please confirm your choice"
                },
                {
                  "name": "messageType",
                  "value": 0
                },
                {
                  "name": "options",
                  "value": [
                    "Yes",
                    "No"
                  ]
                },
                {
                  "name": "optionType",
                  "value": -1
                },
                {
                  "name": "defaultOption",
                  "value": 1
                }
              ],
              "input": [
                {
                  "name": "IDToken2",
                  "value": 100
                }
              ]
            }
            """
        ) as JsonObject
    }

    @Test
    fun initializesCorrectly() {
        val callback = ConfirmationCallback()
        callback.init(jsonObject)

        assertEquals("Please confirm your choice", callback.prompt)
        assertEquals(0, callback.messageType)
        assertEquals(listOf("Yes", "No"), callback.options)
        assertEquals(-1, callback.optionType)
        assertEquals(1, callback.defaultOption)
    }

    @Test
    fun payloadReturnsCorrectly() {
        val callback = ConfirmationCallback()
        callback.init(jsonObject)
        callback.selectedIndex = 1

        val payload = callback.payload()
        assertEquals(
            1,
            payload["input"]?.jsonArray?.get(0)?.jsonObject?.get("value")?.jsonPrimitive?.int
        )
    }

    @Test
    fun `payload not explicitly set`() {
        val callback = ConfirmationCallback()
        callback.init(jsonObject)

        val payload = callback.payload()
        assertEquals(
            100,
            payload["input"]?.jsonArray?.get(0)?.jsonObject?.get("value")?.jsonPrimitive?.int
        )
    }


}
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

class HiddenValueCallbackTest {

    private lateinit var jsonObject: JsonObject

    @BeforeTest
    fun setUp() {
        jsonObject = Json.parseToJsonElement(
            """
           {
              "type": "HiddenValueCallback",
              "output": [
                {
                  "name": "value",
                  "value": "false"
                },
                {
                  "name": "id",
                  "value": "webAuthnOutcome"
                }
              ],
              "input": [
                {
                  "name": "IDToken2",
                  "value": "webAuthnOutcome"
                }
              ]
            }        
            """
        ) as JsonObject
    }

    @Test
    fun initializesCorrectly() {
        val callback = HiddenValueCallback()
        callback.init(jsonObject)

        assertEquals("false", callback.value)
        assertEquals("webAuthnOutcome", callback.id)
    }

    @Test
    fun payloadReturnsCorrectly() {
        val callback = HiddenValueCallback()
        callback.init(jsonObject)

        val payload = callback.payload()
        assertEquals(
            "false",
            payload["input"]?.jsonArray?.get(0)?.jsonObject?.get("value")?.jsonPrimitive?.content
        )
    }

}
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

class SuspendedTextOutputCallbackTest {

    private lateinit var jsonObject: JsonObject

    @BeforeTest
    fun setUp() {
        jsonObject = Json.parseToJsonElement(
            """
            {
              "type": "SuspendedTextOutputCallback",
              "output": [
                {
                  "name": "message",
                  "value": "An email has been sent to the address you entered. Click the link in that email to proceed."
                },
                {
                  "name": "messageType",
                  "value": "0"
                }
              ]
            }
                    """
        ) as JsonObject
    }

    @Test
    fun initializesCorrectly() {
        val callback = SuspendedTextOutputCallback()
        callback.init(jsonObject)

        assertEquals(
            "An email has been sent to the address you entered. Click the link in that email to proceed.",
            callback.message
        )
        assertEquals(0, callback.messageType)
    }

    @Test
    fun payloadReturnsCorrectly() {
        val callback = SuspendedTextOutputCallback()
        callback.init(jsonObject)

        val payload = callback.payload()
        assertEquals(
            "SuspendedTextOutputCallback",
            payload["type"]?.jsonPrimitive?.content
        )
        assertEquals(
            "An email has been sent to the address you entered. Click the link in that email to proceed.",
            payload["output"]?.jsonArray?.get(0)?.jsonObject?.get("value")?.jsonPrimitive?.content
        )
        assertEquals(
            0,
            payload["output"]?.jsonArray?.get(1)?.jsonObject?.get("value")?.jsonPrimitive?.int
        )
    }

}
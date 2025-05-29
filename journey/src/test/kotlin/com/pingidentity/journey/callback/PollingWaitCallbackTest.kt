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

class PollingWaitCallbackTest {

    private lateinit var jsonObject: JsonObject

    @BeforeTest
    fun setUp() {
        jsonObject = Json.parseToJsonElement(
            """
            {
              "type": "PollingWaitCallback",
              "output": [
                {
                  "name": "waitTime",
                  "value": "8000"
                },
                {
                  "name": "message",
                  "value": "Waiting"
                }
              ]
            }               
                    """
        ) as JsonObject
    }

    @Test
    fun initializesCorrectly() {
        val callback = PollingWaitCallback()
        callback.init(jsonObject)

        assertEquals(8000, callback.waitTime)
        assertEquals("Waiting", callback.message)
    }

    @Test
    fun payloadReturnsCorrectly() {
        val callback = PollingWaitCallback()
        callback.init(jsonObject)

        val payload = callback.payload()
        assertEquals(
            "PollingWaitCallback",
            payload["type"]?.jsonPrimitive?.content
        )
    }

}
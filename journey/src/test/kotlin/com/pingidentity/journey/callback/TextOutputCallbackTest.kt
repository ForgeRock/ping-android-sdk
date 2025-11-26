/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.journey.callback

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class TextOutputCallbackTest {

    private lateinit var jsonObject: JsonObject

    @BeforeTest
    fun setUp() {
        jsonObject = Json.parseToJsonElement(
      """
            {
              "type": "TextOutputCallback",
              "output": [
                {
                  "name": "message",
                  "value": "Test"
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
        val callback = TextOutputCallback()
        callback.init(jsonObject)

        assertEquals("Test", callback.message)
        assertEquals(TextOutputCallbackMessageType.INFORMATION, callback.messageType)
    }

    @Test
    fun `payload returns super payload when messageType is not 4`() {
        jsonObject = Json.parseToJsonElement(
            """
        {
          "type": "TextOutputCallback",
          "output": [
            {
              "name": "message",
              "value": "Test"
            },
            {
              "name": "messageType",
              "value": "1"
            }
          ]
        }
        """
        ) as JsonObject
        val callback = TextOutputCallback()
        callback.init(jsonObject)

        val payload = callback.payload()

        assertEquals("Test", callback.message)
        assertEquals(TextOutputCallbackMessageType.WARNING, callback.messageType)
        assertEquals(2, payload.size, "Payload should not be empty when messageType is not 4")
    }

    @Test
    fun `payload returns empty JSON when messageType is 4`() {
        jsonObject = Json.parseToJsonElement(
            """
            {
              "type": "TextOutputCallback",
              "output": [
                {
                  "name": "message",
                  "value": "Test"
                },
                {
                  "name": "messageType",
                  "value": "4"
                }
              ]
            }
            """
        ) as JsonObject
        val callback = TextOutputCallback()
        callback.init(jsonObject)

        val payload = callback.payload()

        assertEquals(0, payload.size, "Payload should be empty when messageType is 4")
    }

}
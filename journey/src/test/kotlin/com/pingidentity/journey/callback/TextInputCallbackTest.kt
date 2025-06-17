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

class TextInputCallbackTest {

    private lateinit var jsonObject: JsonObject

    @BeforeTest
    fun setUp() {
        jsonObject = Json.parseToJsonElement(
            """
            {
                "type": "TextInputCallback",
                "output": [
                    {
                        "name": "prompt",
                        "value": "One Time Pin"
                    },
                    {
                        "name": "defaultText",
                        "value": "default"
                    }
                ],
                "input": [
                    {
                        "name": "IDToken1",
                        "value": ""
                    }
                ],
                "_id": 0
            }
                """
        ) as JsonObject
    }

    @Test
    fun initializesCorrectly() {
        val callback = TextInputCallback()
        callback.init(jsonObject)

        assertEquals("One Time Pin", callback.prompt)
        assertEquals("default", callback.defaultText)
        assertEquals("default", callback.text)
    }

    @Test
    fun payloadReturnsCorrectly() {
        val callback = TextInputCallback()
        callback.init(jsonObject)
        callback.text = "test"

        val payload = callback.payload()
        assertEquals(
            "test",
            payload["input"]?.jsonArray?.get(0)?.jsonObject?.get("value")?.jsonPrimitive?.content
        )
    }

}
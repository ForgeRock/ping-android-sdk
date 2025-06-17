/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.journey.callback

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class NameCallbackTest {

    private lateinit var jsonObject: JsonObject

    @BeforeTest
    fun setUp() {
        jsonObject = buildJsonObject {
            put("output", buildJsonArray {
                add(buildJsonObject {
                    put("name", JsonPrimitive("prompt"))
                    put("value", JsonPrimitive("Enter your name"))
                })
            })
            put("input", buildJsonArray {
                add(buildJsonObject {
                    put("name", JsonPrimitive("IDToken1"))
                    put("value", JsonPrimitive(""))
                })
            })
        }
    }

    @Test
    fun initializesPromptWhenJsonElementContainsPrompt() {
        val callback = NameCallback()
        callback.init(jsonObject)
        assertEquals("Enter your name", callback.prompt)
    }

    @Test
    fun payloadReturnsInputNameCorrectly() {
        val callback = NameCallback()
        callback.init(jsonObject)
        callback.name = "John Doe"
        assertEquals(
            "John Doe",
            callback.payload()["input"]?.jsonArray?.get(0)?.jsonObject?.get("value")?.jsonPrimitive?.content
        )
    }

}
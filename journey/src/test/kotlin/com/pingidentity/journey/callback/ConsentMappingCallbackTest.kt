/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.journey.callback

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ConsentMappingCallbackTest {

    private lateinit var jsonObject: JsonObject

    @BeforeTest
    fun setUp() {
        jsonObject = Json.parseToJsonElement(
            """
            {
              "type": "ConsentMappingCallback",
              "output": [
                {
                  "name": "name",
                  "value": "managedUser_managedUser"
                },
                {
                  "name": "displayName",
                  "value": "Identity Mapping"
                },
                {
                  "name": "icon",
                  "value": ""
                },
                {
                  "name": "accessLevel",
                  "value": "Actual Profile"
                },
                {
                  "name": "isRequired",
                  "value": false
                },
                {
                  "name": "message",
                  "value": "This is Consent"
                },
                {
                  "name": "fields",
                  "value": ["a", "b"]
                }
              ],
              "input": [
                {
                  "name": "IDToken1",
                  "value": false
                }
              ]
            }
                """
        ) as JsonObject
    }

    @Test
    fun initializesCorrectly() {
        val callback = ConsentMappingCallback()
        callback.init(jsonObject)

        assertEquals("managedUser_managedUser", callback.name)
        assertEquals("Identity Mapping", callback.displayName)
        assertEquals("", callback.icon)
        assertEquals("Actual Profile", callback.accessLevel)
        assertEquals(false, callback.isRequired)
        assertEquals("This is Consent", callback.message)
        assertEquals(listOf("a", "b"), callback.fields)
    }

    @Test
    fun payloadReturnsCorrectly() {
        val callback = ConsentMappingCallback()
        callback.init(jsonObject)
        callback.accepted = true

        val payload = callback.payload()
        assertEquals(
            true,
            payload["input"]?.jsonArray?.get(0)?.jsonObject?.get("value")?.jsonPrimitive?.boolean
        )
    }

}
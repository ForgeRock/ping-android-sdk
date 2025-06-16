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

class TermsAndConditionsCallbackTest {

    private lateinit var jsonObject: JsonObject

    @BeforeTest
    fun setUp() {
        jsonObject = Json.parseToJsonElement(
            """
            {
              "type": "TermsAndConditionsCallback",
              "output": [
                {
                  "name": "version",
                  "value": "1.0"
                },
                {
                  "name": "terms",
                  "value": "This is a demo for Terms & Conditions"
                },
                {
                  "name": "createDate",
                  "value": "2019-07-11T22:23:55.737Z"
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
        val callback = TermsAndConditionsCallback()
        callback.init(jsonObject)

        assertEquals("1.0", callback.version)
        assertEquals("This is a demo for Terms & Conditions", callback.terms)
        assertEquals("2019-07-11T22:23:55.737Z", callback.createDate)
        assertEquals(false, callback.accept)
    }

    @Test
    fun payloadReturnsCorrectly() {
        val callback = TermsAndConditionsCallback()
        callback.init(jsonObject)
        callback.accept = true

        val payload = callback.payload()
        assertEquals(
            true,
            payload["input"]?.jsonArray?.get(0)?.jsonObject?.get("value")?.jsonPrimitive?.boolean
        )
    }

}
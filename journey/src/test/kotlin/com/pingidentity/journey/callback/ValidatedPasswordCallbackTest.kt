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

class ValidatedPasswordCallbackTest {

    private lateinit var jsonObject: JsonObject

    @BeforeTest
    fun setUp() {
        jsonObject = Json.parseToJsonElement(
            """
            {
              "type": "ValidatedCreatePasswordCallback",
              "output": [
                {
                  "name": "echoOn",
                  "value": false
                },
                {
                  "name": "policies",
                  "value": {
                    "policyRequirements": [
                      "VALID_TYPE"
                    ],
                    "fallbackPolicies": null,
                    "name": "password",
                    "policies": [
                      {
                        "policyRequirements": [
                          "VALID_TYPE"
                        ],
                        "policyId": "valid-type",
                        "params": {
                          "types": [
                            "string"
                          ]
                        }
                      }
                    ],
                    "conditionalPolicies": null
                  }
                },
                {
                  "name": "failedPolicies",
                  "value": []
                },
                {
                  "name": "validateOnly",
                  "value": false
                },
                {
                  "name": "prompt",
                  "value": "Password"
                }
              ],
              "input": [
                {
                  "name": "IDToken1",
                  "value": ""
                },
                {
                  "name": "IDToken1validateOnly",
                  "value": false
                }
              ]
            }
                            """
        ) as JsonObject
    }

    @Test
    fun initializesCorrectly() {
        val callback = ValidatedPasswordCallback()
        callback.init(jsonObject)

        assertEquals("Password", callback.prompt)
        assertEquals(false, callback.echoOn)
        assertEquals("", callback.password)
    }

    @Test
    fun payloadReturnsCorrectly() {
        val callback = ValidatedPasswordCallback()
        callback.init(jsonObject)
        callback.password = "password"

        val payload = callback.payload()
        assertEquals(
            "password",
            payload["input"]?.jsonArray?.get(0)?.jsonObject?.get("value")?.jsonPrimitive?.content
        )
    }

}
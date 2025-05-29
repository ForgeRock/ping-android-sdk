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

class ValidatedUsernameCallbackTest {

    private lateinit var jsonObject: JsonObject

    @BeforeTest
    fun setUp() {
        jsonObject = Json.parseToJsonElement(
            """
            {
              "type": "ValidatedCreateUsernameCallback",
              "output": [
                {
                  "name": "policies",
                  "value": {
                    "policyRequirements": [
                      "REQUIRED",
                      "VALID_TYPE",
                      "VALID_USERNAME",
                      "CANNOT_CONTAIN_CHARACTERS",
                      "MIN_LENGTH",
                      "MAX_LENGTH"
                    ],
                    "fallbackPolicies": null,
                    "name": "userName",
                    "policies": [
                      {
                        "policyRequirements": [
                          "REQUIRED"
                        ],
                        "policyId": "required"
                      },
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
                      },
                      {
                        "policyId": "valid-username",
                        "policyRequirements": [
                          "VALID_USERNAME"
                        ]
                      },
                      {
                        "params": {
                          "forbiddenChars": [
                            "/"
                          ]
                        },
                        "policyId": "cannot-contain-characters",
                        "policyRequirements": [
                          "CANNOT_CONTAIN_CHARACTERS"
                        ]
                      },
                      {
                        "params": {
                          "minLength": 1
                        },
                        "policyId": "minimum-length",
                        "policyRequirements": [
                          "MIN_LENGTH"
                        ]
                      },
                      {
                        "params": {
                          "maxLength": 255
                        },
                        "policyId": "maximum-length",
                        "policyRequirements": [
                          "MAX_LENGTH"
                        ]
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
                  "value": "Username"
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
        val callback = ValidatedUsernameCallback()
        callback.init(jsonObject)

        assertEquals("Username", callback.prompt)
        assertEquals("", callback.username)
    }
    @Test
    fun payloadReturnsCorrectly() {
        val callback = ValidatedUsernameCallback()
        callback.init(jsonObject)
        callback.username = "testUser"

        val payload = callback.payload()
        assertEquals(
            "testUser",
            payload["input"]?.jsonArray?.get(0)?.jsonObject?.get("value")?.jsonPrimitive?.content
        )
    }

}
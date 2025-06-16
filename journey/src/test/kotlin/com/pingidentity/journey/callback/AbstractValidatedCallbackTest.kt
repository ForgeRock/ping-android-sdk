/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.journey.callback

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class AbstractValidatedCallbackTest {

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
                  "value": [
                    "{ \"params\": { \"minLength\": 3 }, \"policyRequirement\": \"MIN_LENGTH\" }"
                  ]
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
            """.trimIndent()
        ) as JsonObject
    }

    @Test
    fun `test init with policies from sample data`() {
        val callback = object : AbstractValidatedCallback() {}
        callback.init(jsonObject)
        assertNotNull(callback.policies)

        assertEquals(1, callback.failedPolicies.size)

        val policy = callback.failedPolicies[0]
        assertEquals("MIN_LENGTH", policy.policyRequirement)
        assertEquals(3, policy.params["minLength"]?.jsonPrimitive?.int)

        assertFalse(callback.validateOnly)
        // Additional assertions
        val policyRequirements = callback.policies["policyRequirements"]?.jsonArray
        assertNotNull(policyRequirements)
        assertEquals(6, policyRequirements.size)
        assertEquals("REQUIRED", policyRequirements[0].jsonPrimitive.content)
        assertEquals("MAX_LENGTH", policyRequirements[5].jsonPrimitive.content)

        val policiesArray = callback.policies["policies"]?.jsonArray
        assertNotNull(policiesArray)
        assertEquals(6, policiesArray.size)
        val firstPolicy = policiesArray[0].jsonObject
        assertEquals("required", firstPolicy["policyId"]?.jsonPrimitive?.content)
        assertEquals(
            "REQUIRED",
            firstPolicy["policyRequirements"]?.jsonArray?.first()?.jsonPrimitive?.content
        )
    }

}
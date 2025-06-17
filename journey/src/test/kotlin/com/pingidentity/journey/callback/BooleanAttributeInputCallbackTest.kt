package com.pingidentity.journey.callback

import junit.framework.TestCase.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class BooleanAttributeInputCallbackTest {

    private lateinit var jsonObject: JsonObject

    @BeforeTest
    fun setUp() {

        jsonObject = Json.parseToJsonElement(
            """
            {
              "type": "BooleanAttributeInputCallback",
              "output": [
                {
                  "name": "name",
                  "value": "custom_dummy"
                },
                {
                  "name": "prompt",
                  "value": "Dummy"
                },
                {
                  "name": "required",
                  "value": true
                },
                {
                  "name": "policies",
                  "value": {
                    "policyRequirements": [
                      "VALID_TYPE"
                    ],
                    "fallbackPolicies": null,
                    "name": "custom_dummy",
                    "policies": [
                      {
                        "policyRequirements": [
                          "VALID_TYPE"
                        ],
                        "policyId": "valid-type",
                        "params": {
                          "types": [
                            "boolean"
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
                  "name": "value",
                  "value": true
                }
              ],
              "input": [
                {
                  "name": "IDToken4",
                  "value": true
                },
                {
                  "name": "IDToken4validateOnly",
                  "value": false
                }
              ]
            }
            """.trimIndent()
        ) as JsonObject
    }

    @Test
    fun booleanAttributeInputCallbackInitializesValueCorrectlyFromJson() {
        val callback = BooleanAttributeInputCallback()
        callback.init(jsonObject)

        //Default
        assertTrue(callback.value)
    }

    @Test
    fun payloadReturnsCorrectly() {
        val callback = BooleanAttributeInputCallback()
        callback.init(jsonObject)
        callback.value = false
        callback.validateOnly = true
        assertEquals(
            false,
            callback.payload()["input"]?.jsonArray?.get(0)?.jsonObject?.get("value")?.jsonPrimitive?.boolean
        )
        assertEquals(
            true,
            callback.payload()["input"]?.jsonArray?.get(1)?.jsonObject?.get("value")?.jsonPrimitive?.boolean
        )
    }
}
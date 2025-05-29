package com.pingidentity.journey.callback

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class NumberAttributeInputCallbackTest {

    private lateinit var jsonObject: JsonObject

    @BeforeTest
    fun setUp() {

        jsonObject = Json.parseToJsonElement(
            """
            {
              "type": "NumberAttributeInputCallback",
              "output": [
                {
                  "name": "name",
                  "value": "custom_age"
                },
                {
                  "name": "prompt",
                  "value": "How old are you?"
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
                    "name": "custom_age",
                    "policies": [
                      {
                        "policyRequirements": [
                          "VALID_TYPE"
                        ],
                        "policyId": "valid-type",
                        "params": {
                          "types": [
                            "number"
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
                  "value": 30.0
                }
              ],
              "input": [
                {
                  "name": "IDToken3",
                  "value": 30.0
                },
                {
                  "name": "IDToken3validateOnly",
                  "value": false
                }
              ]
            }
            """.trimIndent()
        ) as JsonObject
    }

    @Test
    fun numberAttributeInputCallbackInitializesValueCorrectlyFromJson() {
        val callback = NumberAttributeInputCallback()
        callback.init(jsonObject)

        //Default
        assertEquals(30.0, callback.value)
    }

    @Test
    fun payloadReturnsCorrectly() {
        val callback = NumberAttributeInputCallback()
        callback.init(jsonObject)
        callback.validateOnly = true
        assertEquals(
            30.0,
            callback.payload()["input"]?.jsonArray?.get(0)?.jsonObject?.get("value")?.jsonPrimitive?.double
        )
        assertEquals(
            true,
            callback.payload()["input"]?.jsonArray?.get(1)?.jsonObject?.get("value")?.jsonPrimitive?.boolean
        )

        callback.value = 20.0
        assertEquals(
            20.0,
            callback.payload()["input"]?.jsonArray?.get(0)?.jsonObject?.get("value")?.jsonPrimitive?.double
        )


    }


}
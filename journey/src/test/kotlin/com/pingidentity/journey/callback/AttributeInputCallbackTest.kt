package com.pingidentity.journey.callback

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AttributeInputCallbackTest {

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
    fun attributeInputCallbackCorrectlyFromJson() {
        val callback = object : AttributeInputCallback() {}
        callback.init(jsonObject)

        assertEquals("custom_dummy", callback.name)
        assertEquals("Dummy", callback.prompt)
        assertEquals(true, callback.required)
    }

}
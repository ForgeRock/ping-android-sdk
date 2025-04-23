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

class StringAttributeInputCallbackTest {

    private lateinit var jsonObject: JsonObject

    @BeforeTest
    fun setUp() {

        jsonObject = Json.parseToJsonElement(
            """
            {
              "type": "StringAttributeInputCallback",
              "output": [
                {
                  "name": "name",
                  "value": "mail"
                },
                {
                  "name": "prompt",
                  "value": "Email Address"
                },
                {
                  "name": "required",
                  "value": true
                },
                {
                  "name": "policies",
                  "value": {
                    "policyRequirements": [
                      "REQUIRED",
                      "VALID_TYPE",
                      "VALID_EMAIL_ADDRESS_FORMAT"
                    ],
                    "fallbackPolicies": null,
                    "name": "mail",
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
                        "policyId": "valid-email-address-format",
                        "policyRequirements": [
                          "VALID_EMAIL_ADDRESS_FORMAT"
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
                  "name": "value",
                  "value": "original text"
                }
              ],
              "input": [
                {
                  "name": "IDToken2",
                  "value": "original text"
                },
                {
                  "name": "IDToken2validateOnly",
                  "value": false
                }
              ]
            }
                """.trimIndent()
        ) as JsonObject
    }

    @Test
    fun stringttributeInputCallbackInitializesValueCorrectlyFromJson() {
        val callback = StringAttributeInputCallback()
        callback.init(jsonObject)

        //Default
        assertEquals("original text", callback.value)
    }

    @Test
    fun payloadReturnsCorrectly() {
        val callback = StringAttributeInputCallback()
        callback.init(jsonObject)
        callback.validateOnly = true
        assertEquals(
            "original text",
            callback.payload()["input"]?.jsonArray?.get(0)?.jsonObject?.get("value")?.jsonPrimitive?.content
        )
        assertEquals(
            true,
            callback.payload()["input"]?.jsonArray?.get(1)?.jsonObject?.get("value")?.jsonPrimitive?.boolean
        )

        callback.value = "new text"
        assertEquals(
            "new text",
            callback.payload()["input"]?.jsonArray?.get(0)?.jsonObject?.get("value")?.jsonPrimitive?.content
        )


    }


}
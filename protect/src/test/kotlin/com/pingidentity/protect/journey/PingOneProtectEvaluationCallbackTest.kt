/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.protect.journey

import com.pingidentity.journey.plugin.Callback
import com.pingidentity.journey.plugin.ValueCallback
import com.pingidentity.orchestrate.ContinueNode
import com.pingidentity.protect.Protect
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PingOneProtectEvaluationCallbackTest {
    private lateinit var jsonObject: JsonObject

    @BeforeTest
    fun setUp() {
        mockkObject(Protect)
        every { Protect.config(any()) } just runs
        every { Protect.resumeBehavioralData() } just runs
        every { Protect.pauseBehavioralData() } just runs
        coEvery { Protect.initialize() } just runs
        coEvery { Protect.data() } returns "deviceSignals"
        jsonObject = Json.parseToJsonElement(
            """
          {
              "type": "PingOneProtectEvaluationCallback",
              "output": [
                {
                  "name": "pauseBehavioralData",
                  "value": true
                }
              ],
              "input": [
                {
                  "name": "IDToken1signals",
                  "value": ""
                },
                {
                  "name": "IDToken1clientError",
                  "value": ""
                }
              ]
          }
                            """
        ) as JsonObject
    }

    @AfterTest
    fun tearDown() {
        unmockkObject(Protect)
    }


    @Test
    fun initializesAllPropertiesWithPauseBehavioralDataFalse() = runTest {
        jsonObject = Json.parseToJsonElement(
            """
          {
              "type": "PingOneProtectEvaluationCallback",
              "output": [
                {
                  "name": "pauseBehavioralData",
                  "value": false
                }
              ],
              "input": [
                {
                  "name": "IDToken1signals",
                  "value": ""
                },
                {
                  "name": "IDToken1clientError",
                  "value": ""
                }
              ]
          }
                            """
        ) as JsonObject
        val callback = PingOneProtectEvaluationCallback().apply { init(jsonObject) }

        assertFalse(callback.pauseBehavioralData)

        assertTrue(callback.collect().isSuccess)
        verify(exactly = 0) { Protect.pauseBehavioralData() }
        val payload = callback.payload()
        assertEquals(
            "deviceSignals",
            payload["input"]?.jsonArray?.get(0)?.jsonObject?.get("value")?.jsonPrimitive?.content
        )
    }

    @Test
    fun initializesAllPropertiesCorrectlyFromValidJson() = runTest {
        val callback = PingOneProtectEvaluationCallback().apply { init(jsonObject) }

        assertTrue(callback.pauseBehavioralData)

        assertTrue(callback.collect().isSuccess)
        verify(exactly = 1) { Protect.pauseBehavioralData() }
        val payload = callback.payload()
        assertEquals(
            "deviceSignals",
            payload["input"]?.jsonArray?.get(0)?.jsonObject?.get("value")?.jsonPrimitive?.content
        )
    }

    @Test
    fun startReturnsFailureResultWhenProtectThrowsException() = runTest {
        mockkObject(Protect)
        coEvery { Protect.data() } throws IOException("Collect data failed")
        every { Protect.config(any()) } returns Unit
        every { Protect.resumeBehavioralData() } returns Unit
        every { Protect.pauseBehavioralData() } returns Unit
        val callback = PingOneProtectEvaluationCallback().apply { init(jsonObject) }

        val result = callback.collect()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IOException)
        val payload = callback.payload()
        assertEquals(
            "Collect data failed",
            payload["input"]?.jsonArray?.get(1)?.jsonObject?.get("value")?.jsonPrimitive?.content
        )

    }


    @Test
    fun initializesWithMissingOptionalFields_setsDefaults() {
        val jsonObject = Json.parseToJsonElement(
            """
          {
              "type": "PingOneProtectEvaluationCallback",
              "output": [
              ],
              "input": [
                {
                  "name": "IDToken1signals",
                  "value": ""
                },
                {
                  "name": "IDToken1clientError",
                  "value": ""
                }
              ]
          }
                            """
        ) as JsonObject
        val callback = PingOneProtectEvaluationCallback().apply { init(jsonObject) }

        assertFalse(callback.pauseBehavioralData)
    }

    @Test
    fun `test init with protect evaluation`() = runTest {
        jsonObject = Json.parseToJsonElement(
            """
        {   
            "type": "MetadataCallback",
            "output": [
                {
                    "name": "data",
                    "value": {
                        "_type": "PingOneProtect",  
                        "_action": "protect_risk_evaluation",
                        "envId" : "some_id",
                        "pauseBehavioralData" : true
                     }
                }
            ],
            "_id": 0
        }
                    """
        ) as JsonObject
        val callback = PingOneProtectEvaluationCallback()
        val actualCallback = callback.init(jsonObject)
        assertTrue(actualCallback is PingOneProtectEvaluationCallback)
        assertTrue(actualCallback.pauseBehavioralData)
        val continueNode = mockk<ContinueNode>()
        val hiddenValueCallback1 = object : ValueCallback {
            override val id: String = "IDToken1pingone_risk_evaluation_signals"
            override var value: String = ""
            override fun init(jsonObject: JsonObject): Callback {
                return this
            }

            override fun payload(): JsonObject {
                return buildJsonObject { }
            }
        }
        val hiddenValueCallback2 = object : ValueCallback {
            override val id: String = "IDToken1clientError"
            override var value: String = ""
            override fun init(jsonObject: JsonObject): Callback {
                return this
            }

            override fun payload(): JsonObject {
                return buildJsonObject { }
            }
        }

        every { continueNode.actions } returns listOf(hiddenValueCallback1, hiddenValueCallback2)
        callback.continueNode = continueNode
        callback.collect()
        //Since there is no error
        assertEquals("deviceSignals", hiddenValueCallback1.value)
        assertTrue(hiddenValueCallback2.value.isEmpty())

    }

    @Test
    fun `test init with protect evaluation with Error`() = runTest {
        jsonObject = Json.parseToJsonElement(
            """
        {   
            "type": "MetadataCallback",
            "output": [
                {
                    "name": "data",
                    "value": {
                        "_type": "PingOneProtect",  
                        "_action": "protect_risk_evaluation",
                        "envId" : "some_id",
                        "pauseBehavioralData" : true
                     }
                }
            ],
            "_id": 0
        }
                    """
        ) as JsonObject
        val callback = PingOneProtectEvaluationCallback()
        val actualCallback = callback.init(jsonObject)
        assertTrue(actualCallback is PingOneProtectEvaluationCallback)
        assertTrue(actualCallback.pauseBehavioralData)
        val continueNode = mockk<ContinueNode>()
        val hiddenValueCallback1 = object : ValueCallback {
            override val id: String = "IDToken1pingone_risk_evaluation_signals"
            override var value: String = ""
            override fun init(jsonObject: JsonObject): Callback {
                return this
            }

            override fun payload(): JsonObject {
                return buildJsonObject { }
            }
        }
        val hiddenValueCallback2 = object : ValueCallback {
            override val id: String = "IDToken1clientError"
            override var value: String = ""
            override fun init(jsonObject: JsonObject): Callback {
                return this
            }

            override fun payload(): JsonObject {
                return buildJsonObject { }
            }
        }

        every { continueNode.actions } returns listOf(hiddenValueCallback1, hiddenValueCallback2)
        callback.continueNode = continueNode
        coEvery { Protect.data() } throws IOException("Collect data failed")
        callback.collect()
        assertTrue(hiddenValueCallback1.value.isEmpty())
        assertEquals("Collect data failed", hiddenValueCallback2.value)
    }

}


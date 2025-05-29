/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.protect.journey

import com.pingidentity.journey.plugin.Callback
import com.pingidentity.journey.plugin.ValueCallback
import com.pingidentity.journey.plugin.callbacks
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
import kotlinx.serialization.json.int
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

class PingOneProtectInitializeCallbackTest {

    private lateinit var jsonObject: JsonObject

    @BeforeTest
    fun setUp() {
        mockkObject(Protect)
        every { Protect.resumeBehavioralData() } just runs
        every { Protect.pauseBehavioralData() } just runs
        coEvery { Protect.init() } just runs
        jsonObject = Json.parseToJsonElement(
            """
        {
              "type": "PingOneProtectInitializeCallback",
              "output": [
                {
                  "name": "envId",
                  "value": "02fb4743-189a-4bc7-9d6c-a919edfe6447"
                },
                {
                  "name": "consoleLogEnabled",
                  "value": true
                },
                {
                  "name": "deviceAttributesToIgnore",
                  "value": ["attr1", "attr2"]
                },
                {
                  "name": "customHost",
                  "value": "host.example.com"
                },
                {
                  "name": "lazyMetadata",
                  "value": true
                },
                {
                  "name": "behavioralDataCollection",
                  "value": true
                },
                {
                  "name": "deviceKeyRsyncIntervals",
                  "value": 14
                },
                {
                  "name": "enableTrust",
                  "value": false
                },
                {
                  "name": "disableTags",
                  "value": false
                },
                {
                  "name": "disableHub",
                  "value": false
                }
              ],
              "input": [
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
    fun initializesAllPropertiesCorrectlyFromValidJson() = runTest {
        val callback = PingOneProtectInitializeCallback().apply { init(jsonObject) }

        assertEquals("02fb4743-189a-4bc7-9d6c-a919edfe6447", callback.envId)
        assertTrue(callback.behavioralDataCollection)
        assertTrue(callback.consoleLogEnabled)
        assertTrue(callback.lazyMetadata)
        assertEquals("host.example.com", callback.customHost)
        assertEquals(listOf("attr1", "attr2"), callback.deviceAttributesToIgnore)

        assertTrue(callback.start().isSuccess)
        verify(exactly = 1) { Protect.resumeBehavioralData() }
        val payload = callback.payload()
        assertTrue(
            payload["input"]?.jsonArray?.get(0)?.jsonObject?.get("value")?.jsonPrimitive?.content?.isEmpty() == true
        )
    }

    @Test
    fun startReturnsFailureResultWhenProtectThrowsException() = runTest {
        mockkObject(Protect)
        coEvery { Protect.init() } throws IOException("Initialization failed")
        every { Protect.config(any()) } returns Unit
        every { Protect.resumeBehavioralData() } returns Unit
        every { Protect.pauseBehavioralData() } returns Unit
        val callback = PingOneProtectInitializeCallback().apply { init(jsonObject) }

        val result = callback.start()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IOException)
        val payload = callback.payload()
        assertEquals(
            "Initialization failed",
            payload["input"]?.jsonArray?.get(0)?.jsonObject?.get("value")?.jsonPrimitive?.content
        )

    }


    @Test
    fun initializesWithMissingOptionalFields_setsDefaults() {
        val jsonObject = Json.parseToJsonElement(
            """
        {
              "type": "PingOneProtectInitializeCallback",
              "output": [
                {
                  "name": "envId",
                  "value": "02fb4743-189a-4bc7-9d6c-a919edfe6447"
                }
              ],
              "input": [
                {
                  "name": "IDToken1clientError",
                  "value": ""
                }
              ]
        }
                    """
        ) as JsonObject
        val callback = PingOneProtectInitializeCallback().apply { init(jsonObject) }

        assertEquals("02fb4743-189a-4bc7-9d6c-a919edfe6447", callback.envId)
        assertFalse(callback.behavioralDataCollection)
        assertFalse(callback.consoleLogEnabled)
        assertFalse(callback.lazyMetadata)
        assertEquals("", callback.customHost)
        assertTrue(callback.deviceAttributesToIgnore.isEmpty())
    }

    @Test
    fun `test init with MetadataCallback`() = runTest {
        jsonObject = Json.parseToJsonElement(
            """
        {
            "type": "MetadataCallback",
            "output": [
                {
                    "name": "data",
                    "value": {
                        "_type": "PingOneProtect",  
                        "_action": "protect_initialize",
                        "envId" : "02fb4743-189a-4bc7-9d6c-a919edfe6447",
                        "consoleLogEnabled" : true,
                        "deviceAttributesToIgnore" : [],
                        "customHost" : "",
                        "lazyMetadata" : true,
                        "behavioralDataCollection" : true,
                        "disableHub" : true,
                        "deviceKeyRsyncIntervals" : 10,
                        "enableTrust" : true,
                        "disableTags" : true
                     }
                }
            ],
            "_id": 0
        }
                    """
        ) as JsonObject
        val callback = PingOneProtectInitializeCallback()
        val actualCallback = callback.init(jsonObject)
        assertTrue(actualCallback is PingOneProtectInitializeCallback)
        assertTrue("02fb4743-189a-4bc7-9d6c-a919edfe6447" == actualCallback.envId)
        assertTrue(actualCallback.consoleLogEnabled)
        assertTrue(actualCallback.lazyMetadata)
        assertTrue(actualCallback.behavioralDataCollection)
        assertEquals("", actualCallback.customHost)
        assertTrue(actualCallback.deviceAttributesToIgnore.isEmpty())

        val continueNode = mockk<ContinueNode>()
        val hiddenValueCallback = object : ValueCallback {
            override val id: String = "IDToken1clientError"
            override var value: String = ""
            override fun init(jsonObject: JsonObject): Callback {
                return this
            }
            override fun payload(): JsonObject {
                return buildJsonObject {  }
            }
        }
        every { continueNode.callbacks } returns listOf(hiddenValueCallback)
        callback.continueNode = continueNode
        callback.start()
        //Since there is no error
        assertTrue(hiddenValueCallback.value.isEmpty())
    }

    @Test
    fun `test init with MetadataCallback and clientError`() = runTest {
        jsonObject = Json.parseToJsonElement(
            """
        {
            "type": "MetadataCallback",
            "output": [
                {
                    "name": "data",
                    "value": {
                        "_type": "PingOneProtect",  
                        "_action": "protect_initialize",
                        "envId" : "02fb4743-189a-4bc7-9d6c-a919edfe6447",
                        "consoleLogEnabled" : true,
                        "deviceAttributesToIgnore" : [],
                        "customHost" : "",
                        "lazyMetadata" : true,
                        "behavioralDataCollection" : true,
                        "disableHub" : true,
                        "deviceKeyRsyncIntervals" : 10,
                        "enableTrust" : true,
                        "disableTags" : true
                     }
                }
            ],
            "_id": 0
        }
                    """
        ) as JsonObject
        var callback = PingOneProtectInitializeCallback()
        callback = callback.init(jsonObject) as PingOneProtectInitializeCallback

        val continueNode = mockk<ContinueNode>()
        val hiddenValueCallback = object : ValueCallback {
            override val id: String = "IDToken1clientError"
            override var value: String = ""
            override fun init(jsonObject: JsonObject): Callback {
                return this
            }
            override fun payload(): JsonObject {
                return buildJsonObject {  }
            }
        }
        every { continueNode.actions } returns listOf(hiddenValueCallback)
        callback.continueNode = continueNode
        coEvery { Protect.init() } throws IOException("Initialization failed")
        callback.start()
        //Since there is no error
        assertEquals("Initialization failed", hiddenValueCallback.value)
    }

}

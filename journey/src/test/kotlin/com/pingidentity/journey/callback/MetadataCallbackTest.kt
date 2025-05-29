/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.journey.callback

import com.pingidentity.protect.journey.CallbackInitializer
import com.pingidentity.protect.journey.PingOneProtectEvaluationCallback
import com.pingidentity.protect.journey.PingOneProtectInitializeCallback
import io.mockk.mockk
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MetadataCallbackTest {

    private lateinit var jsonObject: JsonObject

    @BeforeTest
    fun setUp() {
        jsonObject = Json.parseToJsonElement(
            """
            {
              "type": "MetadataCallback",
              "output": [
                {
                  "name": "data",
                  "value": {
                    "_action": "webauthn_authentication",
                    "challenge": "qnMsxgya8h6mUc6OyRu8jJ6Oq16tHV3cgE7juXGMDbg=",
                    "allowCredentials": "",
                    "_allowCredentials": [],
                    "timeout": "60000",
                    "userVerification": "preferred",
                    "relyingPartyId": "rpId: \"humorous-cuddly-carrot.glitch.me\",",
                    "_relyingPartyId": "humorous-cuddly-carrot.glitch.me",
                    "_type": "WebAuthn"
                  }
                }
              ]
            }
                    """
        ) as JsonObject
    }

    @Test
    fun initializesCorrectly() {
        val callback = MetadataCallback()
        callback.init(jsonObject)

        assertEquals("webauthn_authentication", callback.value["_action"]?.jsonPrimitive?.content)
        assertEquals("qnMsxgya8h6mUc6OyRu8jJ6Oq16tHV3cgE7juXGMDbg=", callback.value["challenge"]?.jsonPrimitive?.content)
        assertEquals("", callback.value["allowCredentials"]?.jsonPrimitive?.content)
        assertEquals(0, callback.value["_allowCredentials"]?.jsonArray?.size)
        assertEquals("60000", callback.value["timeout"]?.jsonPrimitive?.content)
        assertEquals("preferred", callback.value["userVerification"]?.jsonPrimitive?.content)
        assertEquals("rpId: \"humorous-cuddly-carrot.glitch.me\",", callback.value["relyingPartyId"]?.jsonPrimitive?.content)
        assertEquals("humorous-cuddly-carrot.glitch.me", callback.value["_relyingPartyId"]?.jsonPrimitive?.content)
        assertEquals("WebAuthn", callback.value["_type"]?.jsonPrimitive?.content)
    }

    @Test
    fun payloadReturnsCorrectly() {
        val callback = MetadataCallback()
        callback.init(jsonObject)

        val payload = callback.payload()
        assertEquals("webauthn_authentication", payload["output"]?.jsonArray?.get(0)?.jsonObject?.get("value")?.jsonObject?.get("_action")?.jsonPrimitive?.content)
        assertEquals("qnMsxgya8h6mUc6OyRu8jJ6Oq16tHV3cgE7juXGMDbg=", payload["output"]?.jsonArray?.get(0)?.jsonObject?.get("value")?.jsonObject?.get("challenge")?.jsonPrimitive?.content)
    }

    @Test
    fun `test init with protect initialize`() {
        CallbackInitializer().create(mockk())
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
        val callback = MetadataCallback()
        val actualCallback = callback.init(jsonObject)
        assertTrue(actualCallback is PingOneProtectInitializeCallback)
        assertTrue("02fb4743-189a-4bc7-9d6c-a919edfe6447" == actualCallback.envId)
        assertTrue(actualCallback.consoleLogEnabled)
        assertTrue(actualCallback.lazyMetadata)
        assertTrue(actualCallback.behavioralDataCollection)
        assertEquals("", actualCallback.customHost)
        assertTrue(actualCallback.deviceAttributesToIgnore.isEmpty())
    }

    @Test
    fun `test init with protect evaluation`() {
        CallbackInitializer().create(mockk())
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
        val callback = MetadataCallback()
        val actualCallback = callback.init(jsonObject)
        assertTrue(actualCallback is PingOneProtectEvaluationCallback)
        assertTrue(actualCallback.pauseBehavioralData)

    }
}
/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.journey.callback

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
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
}
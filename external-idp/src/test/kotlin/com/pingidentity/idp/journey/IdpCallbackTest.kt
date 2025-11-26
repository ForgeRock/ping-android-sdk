/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.idp.journey

import com.pingidentity.idp.IdpClient
import com.pingidentity.idp.IdpHandler
import com.pingidentity.idp.IdpResult
import com.pingidentity.journey.plugin.Journey
import com.pingidentity.logger.CONSOLE
import com.pingidentity.logger.Logger
import com.pingidentity.orchestrate.WorkflowConfig
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.putJsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class IdpCallbackTest {

    private lateinit var idpCallback: IdpCallback
    private lateinit var jsonObject: JsonObject
    private val journey: Journey = mockk()

    @Before
    fun setup() {
        idpCallback = IdpCallback()
        idpCallback.journey = journey
        val config: WorkflowConfig = mockk()
        every { config.logger }.returns(Logger.CONSOLE)
        every { journey.config }.returns(config)


        jsonObject = buildJsonObject {
            putJsonArray("output") {
                addJsonObject {
                    put("name", JsonPrimitive("provider"))
                    put("value", JsonPrimitive("apple-android"))
                }
                addJsonObject {
                    put("name", JsonPrimitive("clientId"))
                    put("value", JsonPrimitive("com.forgerock.ios.sdk.social.service"))
                }
                addJsonObject {
                    put("name", JsonPrimitive("redirectUri"))
                    put(
                        "value",
                        JsonPrimitive("https://openam-sdks.forgeblocks.com/am/oauth2/alpha/client/form_post/apple-android")
                    )
                }
                addJsonObject {
                    put("name", JsonPrimitive("scopes"))
                    putJsonArray("value") {
                        add(JsonPrimitive("name"))
                        add(JsonPrimitive("email"))
                    }
                }
                addJsonObject {
                    put("name", JsonPrimitive("nonce"))
                    put("value", JsonPrimitive("ka7bjbyu86p6yhlg07rekhv0hm6itf6"))
                }
                addJsonObject {
                    put("name", JsonPrimitive("acrValues"))
                    putJsonArray("value") {}
                }
                addJsonObject {
                    put("name", JsonPrimitive("request"))
                    put("value", JsonPrimitive(""))
                }
                addJsonObject {
                    put("name", JsonPrimitive("acceptsJSON"))
                    put("value", JsonPrimitive(true))
                }
                addJsonObject {
                    put("name", JsonPrimitive("requestUri"))
                    put("value", JsonPrimitive(""))
                }
            }
            put("input", buildJsonArray {
                add(buildJsonObject {
                    put("name", JsonPrimitive("IDToken1token"))
                    put("value", JsonPrimitive(""))
                })
                add(buildJsonObject {
                    put("name", JsonPrimitive("IDToken1tokenType"))
                    put("value", JsonPrimitive(""))
                })
            })
        }
    }

    @Test
    fun `test init with output format`() {
        // Given a callback with output data
        // When initialized with the jsonObject
        idpCallback.init(jsonObject)

        // Then properties are correctly set from the output array
        assertEquals("apple-android", idpCallback.provider)
        assertEquals("com.forgerock.ios.sdk.social.service", idpCallback.clientId)
        assertEquals(
            "https://openam-sdks.forgeblocks.com/am/oauth2/alpha/client/form_post/apple-android",
            idpCallback.redirectUri
        )
        assertEquals(listOf("name", "email"), idpCallback.scopes)
        assertEquals("ka7bjbyu86p6yhlg07rekhv0hm6itf6", idpCallback.nonce)
        assertTrue(idpCallback.acrValues.isEmpty())
        assertEquals("", idpCallback.request)
        assertEquals("", idpCallback.requestUri)
    }

    @Test
    fun `test payload returns correct input values`() = runTest {
        idpCallback.init(jsonObject)
        // Given a callback with token and tokenType
        val token = "test-token"
        val tokenType = "Bearer"

        idpCallback.authorize(idpHandler = object : IdpHandler {
            override var tokenType: String = tokenType

            override suspend fun authorize(idpClient: IdpClient): IdpResult {
                return IdpResult(token, emptyMap())
            }
        })

        // When generating payload
        val payload = idpCallback.payload()

        // Then the input array contains correct values
        val inputArray = payload["input"]?.jsonArray
        assertTrue(inputArray != null && inputArray.size == 2)
        assertEquals(token, inputArray?.get(0)?.jsonObject?.get("value")?.jsonPrimitive?.content)
        assertEquals(
            tokenType,
            inputArray?.get(1)?.jsonObject?.get("value")?.jsonPrimitive?.content
        )
    }

    @Test
    fun `test when authorize failed`() = runTest {
        idpCallback.init(jsonObject)
        // Given a callback with token and tokenType
        val tokenType = "Bearer"

        val result = idpCallback.authorize(idpHandler = object : IdpHandler {
            override var tokenType: String = tokenType

            override suspend fun authorize(idpClient: IdpClient): IdpResult {
                throw IllegalStateException("Authorization failed")
            }
        })

        assertTrue(result.isFailure)
        assertThrows(IllegalStateException::class.java) {
            result.getOrThrow()
        }
    }


    @Test
    fun `test init with empty json`() {
        // When initializing with empty JSON
        idpCallback.init(buildJsonObject {})

        // Then default values are maintained
        assertEquals("", idpCallback.provider)
        assertEquals("", idpCallback.clientId)
        assertEquals("", idpCallback.redirectUri)
        assertTrue(idpCallback.scopes.isEmpty())
        assertEquals("", idpCallback.nonce)
        assertTrue(idpCallback.acrValues.isEmpty())
        assertEquals("", idpCallback.request)
        assertEquals("", idpCallback.requestUri)
    }
}


/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.idp.journey

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SelectIdpCallbackTest {

    private lateinit var selectIdpCallback: SelectIdpCallback
    private lateinit var jsonObject: JsonObject

    @Before
    fun setup() {
        selectIdpCallback = SelectIdpCallback()

        jsonObject = buildJsonObject {
            putJsonArray("output") {
                // First output object - providers
                addJsonObject {
                    put("name", "providers")
                    putJsonArray("value") {
                        // Google provider
                        addJsonObject {
                            put("provider", "google-andy")
                            putJsonObject("uiConfig") {
                                put(
                                    "buttonCustomStyle",
                                    "background-color: #fff; color: #757575; border-color: #ddd;"
                                )
                                put("buttonImage", "images/g-logo.png")
                                put("buttonClass", "")
                                put(
                                    "buttonCustomStyleHover",
                                    "color: #6d6d6d; background-color: #eee; border-color: #ccc;"
                                )
                                put("buttonDisplayName", "Google")
                                put("iconClass", "fa-google")
                                put("iconFontColor", "white")
                                put("iconBackground", "#4184f3")
                            }
                        }
                        // Facebook provider
                        addJsonObject {
                            put("provider", "Facebook-Ping-Andy")
                            putJsonObject("uiConfig") {
                                put(
                                    "buttonCustomStyle",
                                    "background-color: #3b5998;border-color: #3b5998; color: white;"
                                )
                                put("buttonImage", "")
                                put("buttonClass", "fa-facebook-official")
                                put(
                                    "buttonCustomStyleHover",
                                    "background-color: #334b7d;border-color: #334b7d; color: white;"
                                )
                                put("buttonDisplayName", "Facebook")
                            }
                        }
                        // Apple provider
                        addJsonObject {
                            put("provider", "apple-android")
                            putJsonObject("uiConfig") {
                                put("buttonImage", "images/apple-logo.png")
                                put(
                                    "buttonCustomStyle",
                                    "background-color: #000000; color: #ffffff; border-color: #000000;"
                                )
                                put("buttonClass", "")
                                put(
                                    "buttonCustomStyleHover",
                                    "background-color: #000000; color: #ffffff; border-color: #000000;"
                                )
                                put("buttonDisplayName", "Apple")
                                put("iconClass", "fa-apple")
                                put("iconFontColor", "white")
                                put("iconBackground", "#000000")
                            }
                        }
                        // Local authentication provider
                        addJsonObject {
                            put("provider", "localAuthentication")
                        }
                    }
                }
                // Second output object - value
                addJsonObject {
                    put("name", "value")
                    put("value", "")
                }
            }
            putJsonArray("input") {
                addJsonObject {
                    put("name", "IDToken1")
                    put("value", "")
                }
            }
            put("_id", 0)
        }
    }

    @Test
    fun `test init with providers`() {
        // When
        selectIdpCallback.init(jsonObject)

        // Then
        assertEquals(4, selectIdpCallback.providers.size)
        assertEquals("google-andy", selectIdpCallback.providers[0].provider)
        assertEquals("Facebook-Ping-Andy", selectIdpCallback.providers[1].provider)
        assertEquals("apple-android", selectIdpCallback.providers[2].provider)
        assertEquals("apple-android", selectIdpCallback.providers[2].provider)
        assertEquals("images/apple-logo.png", selectIdpCallback.providers[2].uiConfig["buttonImage"]?.jsonPrimitive?.content)
        assertEquals("localAuthentication", selectIdpCallback.providers[3].provider)
    }

    @Test
    fun `test init with empty providers`() {
        // When
        selectIdpCallback.init(buildJsonObject {})

        // Then
        assertTrue(selectIdpCallback.providers.isEmpty())
    }

    @Test
    fun `test payload returns correct json with value set`() {
        // Given
        selectIdpCallback.init(jsonObject)
        val testValue = "test-provider"
        selectIdpCallback.value = testValue

        // When
        val payload = selectIdpCallback.payload()

        // Then
        assertEquals(
            testValue,
            selectIdpCallback.payload()["input"]?.jsonArray?.get(0)?.jsonObject?.get("value")?.jsonPrimitive?.content
        )
    }

    @Test
    fun `test payload returns empty json when value is empty`() {
        // Given
        selectIdpCallback.init(jsonObject)
        selectIdpCallback.value = ""

        // When
        val payload = selectIdpCallback.payload()

        // Then
        assertEquals("",
            selectIdpCallback.payload()["input"]?.jsonArray?.get(0)?.jsonObject?.get("value")?.jsonPrimitive?.content
        )

    }

}

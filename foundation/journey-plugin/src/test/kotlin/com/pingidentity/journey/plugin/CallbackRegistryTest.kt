/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.journey.plugin

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CallbackRegistryTest {

    @BeforeTest
    fun setup() {
        CallbackRegistry.register("type1", ::DummyCallback)
        CallbackRegistry.register("type2", ::Dummy2Callback)
    }

    @Test
    fun `Should return list of callbacks when valid types are provided`() {
        val jsonArray = buildJsonArray {
            add(buildJsonObject { put("type", "type1") })
            add(buildJsonObject { put("type", "type2") })
        }

        val callbacks = CallbackRegistry.callback(jsonArray)

        assertEquals(2, callbacks.size)
    }

    @Test
    fun `Should return empty list when no valid types are provided`() {
        val jsonArray = buildJsonArray {
            add(buildJsonObject { put("type", "invalidType") })
        }

        val callbacks = CallbackRegistry.callback(jsonArray)

        assertTrue(callbacks.isEmpty())
    }

    @Test
    fun `Should override existing callback when same type is registered again`() {
        CallbackRegistry.register("type1", ::Dummy2Callback)

        val jsonArray = buildJsonArray {
            add(buildJsonObject { put("type", "type1") })
        }

        val callbacks = CallbackRegistry.callback(jsonArray)

        assertEquals(1, callbacks.size)
        assertTrue(callbacks.first() is Dummy2Callback)
    }

    @Test
    fun `Should return empty list when jsonArray is empty`() {
        val jsonArray = buildJsonArray { }

        val callbacks = CallbackRegistry.callback(jsonArray)

        assertTrue(callbacks.isEmpty())
    }
}

class DummyCallback : Callback {
    override fun init(jsonObject: JsonObject): Callback {
        return this
    }

    override fun payload(): JsonObject {
        return buildJsonObject { }
    }
}
class Dummy2Callback : Callback {
    override fun init(jsonObject: JsonObject): Callback {
        return this
    }

    override fun payload(): JsonObject {
        return buildJsonObject { }
    }
}
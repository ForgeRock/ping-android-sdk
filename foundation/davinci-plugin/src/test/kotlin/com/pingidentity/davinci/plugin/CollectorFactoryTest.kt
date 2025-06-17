/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci.plugin

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CollectorFactoryTest {

    @BeforeTest
    fun setup() {
        CollectorFactory.register("type1", ::DummyCallback)
        CollectorFactory.register("type2", ::Dummy2Callback)
    }

    @Test
    fun `Should return list of collectors when valid types are provided`() {
        val jsonArray = buildJsonArray {
            add(buildJsonObject { put("type", "type1") })
            add(buildJsonObject { put("type", "type2") })
        }

        val callbacks = CollectorFactory.collector(jsonArray)
        assertEquals("dummy", (callbacks[0] as DummyCallback).value)
        assertEquals("dummy2", (callbacks[1] as Dummy2Callback).value)

        assertEquals(2, callbacks.size)
    }

    @Test
    fun `Should return empty list when no valid types are provided`() {
        val jsonArray = buildJsonArray {
            add(buildJsonObject { put("type", "invalidType") })
        }

        val callbacks = CollectorFactory.collector(jsonArray)

        assertTrue(callbacks.isEmpty())
    }

    @Test
    fun `Should return empty list when jsonArray is empty`() {
        val jsonArray = buildJsonArray { }

        val callbacks = CollectorFactory.collector(jsonArray)

        assertTrue(callbacks.isEmpty())
    }
}

class DummyCallback : Collector<Nothing> {
    lateinit var value : String
    override fun init(input: JsonObject) {
        value = "dummy"
    }
}
class Dummy2Callback : Collector<Nothing> {
    lateinit var value : String
    override fun init(input: JsonObject) {
        value = "dummy2"
    }
}
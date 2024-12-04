/*
 * Copyright (c) 2024 Ping Identity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci

import com.pingidentity.davinci.collector.Option
import com.pingidentity.davinci.collector.SingleSelectCollector
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlin.test.Test
import kotlin.test.assertEquals

class SingleSelectCollectorTest {

    @Test
    fun initializesOptionsWithProvidedValue() {
        val input = buildJsonObject {
            put("options", buildJsonArray {
                add(buildJsonObject {
                    put("label", "Option 1")
                    put("value", "Option 1 Value")
                })
                add(buildJsonObject {
                    put("label", "Option 2")
                    put("value", "Option 2 Value")
                })
            })
        }
        val collector = SingleSelectCollector()
        collector.init(input)
        assertEquals(listOf("Option 1", "Option 2"), collector.options.map { it.label })
        assertEquals(listOf("Option 1 Value", "Option 2 Value"), collector.options.map { it.value })
    }

    @Test
    fun initializesValueWithProvidedJsonElement() {
        val input = JsonPrimitive("Selected Option")
        val collector = SingleSelectCollector()
        collector.init(input)
        assertEquals("Selected Option", collector.value)
    }

    @Test
    fun initializesOptionsWithEmptyListWhenNoValueProvided() {
        val input = buildJsonObject {}
        val collector = SingleSelectCollector()
        collector.init(input)
        assertEquals(emptyList(), collector.options)
    }
}

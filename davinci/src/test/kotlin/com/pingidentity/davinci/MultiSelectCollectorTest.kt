/*
 * Copyright (c) 2024 Ping Identity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci

import com.pingidentity.davinci.collector.MultiSelectCollector
import com.pingidentity.davinci.collector.Required
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals

class MultiSelectCollectorTest {

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
        val collector = MultiSelectCollector()
        collector.init(input)
        assertEquals(listOf("Option 1", "Option 2"), collector.options.map { it.label })
        assertEquals(listOf("Option 1 Value", "Option 2 Value"), collector.options.map { it.value })
    }

    @Test
    fun initializesValueWithProvidedJsonElement() {
        val input = JsonPrimitive("Selected Option")
        val collector = MultiSelectCollector()
        collector.init(input)
        assertEquals(listOf("Selected Option"), collector.value)
    }

    @Test
    fun initializesOptionsWithEmptyListWhenNoValueProvided() {
        val input = buildJsonObject {}
        val collector = MultiSelectCollector()
        collector.init(input)
        assertEquals(emptyList(), collector.options)
    }

    @Test
    fun addsRequiredErrorWhenValueIsEmptyAndRequired() {
        val input = buildJsonObject {
            put("required", true)
        }
        val collector = MultiSelectCollector()
        collector.init(input)
        assertEquals(listOf(Required), collector.validate())
    }

    @Test
    fun doesNotAddRequiredErrorWhenValueIsNotEmptyAndRequired() {
        val input = buildJsonObject {
            put("required", true)
        }
        val inputDefault = JsonPrimitive("Selected Option")
        val collector = MultiSelectCollector()
        collector.init(input)
        collector.init(inputDefault)
        assertEquals(emptyList(), collector.validate())
    }
}

/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci

import com.pingidentity.davinci.collector.LabelCollector
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals

class LabelCollectorTest {

    @Test
    fun initializesContentWithProvidedValue() {
        val input = buildJsonObject {
            put("content", "Test Content")
        }
        val collector = LabelCollector()
        collector.init(input)
        assertEquals("Test Content", collector.content)
    }

    @Test
    fun initializesContentWithEmptyStringWhenNoValueProvided() {
        val input = JsonObject(emptyMap())
        val collector = LabelCollector()
        collector.init(input)
        assertEquals("", collector.content)
    }

}
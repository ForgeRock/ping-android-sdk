/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci

import com.pingidentity.davinci.collector.MetadataCollector
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MetadataCollectorTest {

    private fun buildFullMetadataJson(): JsonObject = buildJsonObject {
        put("type", "METADATA")
        put("key", "sdkMetadata")
        put("payload", buildJsonObject {
            put("testkey", "testValue")
        })
    }

    @Test
    fun initializesKeyFromJson() {
        val collector = MetadataCollector().apply { init(buildFullMetadataJson()) }
        assertEquals("sdkMetadata", collector.key)
    }

    @Test
    fun initializesTypeFromJson() {
        val collector = MetadataCollector().apply { init(buildFullMetadataJson()) }
        assertEquals("METADATA", collector.type)
    }

    @Test
    fun initializesMetadataFromPayloadField() {
        val collector = MetadataCollector().apply { init(buildFullMetadataJson()) }
        assertEquals("testValue", collector.metadata["testkey"]?.jsonPrimitive?.content)
    }

    @Test
    fun idReturnsKey() {
        val collector = MetadataCollector().apply { init(buildFullMetadataJson()) }
        assertEquals("sdkMetadata", collector.id())
    }

    @Test
    fun payloadReturnsNull() {
        val collector = MetadataCollector().apply { init(buildFullMetadataJson()) }
        assertNull(collector.payload())
    }

    @Test
    fun outputIsNullByDefault() {
        val collector = MetadataCollector().apply { init(buildFullMetadataJson()) }
        assertNull(collector.output)
    }

    @Test
    fun errorIsNullByDefault() {
        val collector = MetadataCollector().apply { init(buildFullMetadataJson()) }
        assertNull(collector.error)
    }

    @Test
    fun initReturnsCollectorInstanceForMethodChaining() {
        val collector = MetadataCollector()
        val result = collector.init(buildFullMetadataJson())
        assertEquals(collector, result)
    }

    @Test
    fun initializesWithDefaultsWhenJsonIsEmpty() {
        val collector = MetadataCollector().apply { init(JsonObject(emptyMap())) }
        assertEquals("", collector.key)
        assertEquals("", collector.type)
        assertTrue(collector.metadata.isEmpty())
    }

    @Test
    fun idReturnsEmptyStringWhenKeyAbsent() {
        val collector = MetadataCollector().apply { init(JsonObject(emptyMap())) }
        assertEquals("", collector.id())
    }

    @Test
    fun metadataIsEmptyWhenPayloadAbsent() {
        val input = buildJsonObject {
            put("type", "METADATA")
            put("key", "sdkMetadata")
        }
        val collector = MetadataCollector().apply { init(input) }
        assertTrue(collector.metadata.isEmpty())
    }

    @Test
    fun outputCanBeSet() {
        val collector = MetadataCollector().apply { init(buildFullMetadataJson()) }
        val output = buildJsonObject {
            put("verification", "successful")
            put("status", true)
        }
        collector.output = output
        assertEquals(output, collector.output)
        assertNull(collector.error)
    }

    @Test
    fun errorCanBeSet() {
        val collector = MetadataCollector().apply { init(buildFullMetadataJson()) }
        val error = buildJsonObject {
            put("code", "SOME_ERROR_CODE")
            put("message", "User cancelled the operation")
        }
        collector.error = error
        assertEquals(error, collector.error)
    }

    @Test
    fun outputAndErrorCanBeSetIndependently() {
        val collector = MetadataCollector().apply { init(buildFullMetadataJson()) }
        val output = buildJsonObject { put("status", "success") }
        val error = buildJsonObject {
            put("code", "SOME_ERROR_CODE")
            put("message", "User cancelled the operation")
        }

        collector.output = output
        collector.error = error

        assertEquals(output, collector.output)
        assertEquals(error, collector.error)
    }
}

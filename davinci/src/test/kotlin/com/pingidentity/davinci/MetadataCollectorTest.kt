/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci

import com.pingidentity.davinci.collector.MetadataCollector
import com.pingidentity.davinci.collector.asJson
import com.pingidentity.davinci.collector.eventType
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MetadataCollectorTest {

    private fun buildFullMetadataJson(): JsonObject = buildJsonObject {
        put("type", "METADATA")
        put("key", "sdkMetadata")
        put("payload", buildJsonObject {
            put("sdk", "PROTECT")
            put("action", "INITIALIZE")
        })
    }

    // --- init ---

    @Test
    fun initializesKeyTypeAndMetadata() {
        val collector = MetadataCollector().apply { init(buildFullMetadataJson()) }
        assertEquals("sdkMetadata", collector.key)
        assertEquals("METADATA", collector.type)
        assertEquals("PROTECT", collector.metadata["sdk"]?.jsonPrimitive?.content)
        assertEquals("INITIALIZE", collector.metadata["action"]?.jsonPrimitive?.content)
    }

    @Test
    fun idEqualsKey() {
        val collector = MetadataCollector().apply { init(buildFullMetadataJson()) }
        assertEquals(collector.key, collector.id())
    }

    @Test
    fun defaultsWhenJsonEmpty() {
        val collector = MetadataCollector().apply { init(JsonObject(emptyMap())) }
        assertEquals("", collector.key)
        assertEquals("", collector.type)
        assertTrue(collector.metadata.isEmpty())
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
    fun initReturnsInstanceForChaining() {
        val collector = MetadataCollector()
        assertEquals(collector, collector.init(buildFullMetadataJson()))
    }

    // --- payload / setResult ---

    @Test
    fun payloadIsNullBeforeSetResultOrSetError() {
        val collector = MetadataCollector().apply { init(buildFullMetadataJson()) }
        assertNull(collector.payload())
    }

    @Test
    fun setResultProducesPayload() {
        val collector = MetadataCollector().apply { init(buildFullMetadataJson()) }
        val result = buildJsonObject { put("verified", true) }
        collector.setResult(result)
        assertEquals(result, collector.payload())
    }

    // --- setError ---

    @Test
    fun setErrorProducesErrorEnvelope() {
        val collector = MetadataCollector().apply { init(buildFullMetadataJson()) }
        collector.setError(errorCode = "USER_CANCELLED", message = "User cancelled the operation")

        val error = collector.payload()!!["error"]?.jsonObject
        assertNotNull(error)
        assertEquals("USER_CANCELLED", error["code"]?.jsonPrimitive?.content)
        assertEquals("User cancelled the operation", error["message"]?.jsonPrimitive?.content)
    }

    // --- eventType ---

    @Test
    fun eventTypeIsAction() {
        assertEquals("action", MetadataCollector().eventType())
    }

    // --- validate ---

    @Test
    fun validateRequiresResultBeforeSet() {
        val collector = MetadataCollector().apply { init(buildFullMetadataJson()) }
        assertTrue(collector.validate().isNotEmpty())
    }

    @Test
    fun validatePassesAfterSetResult() {
        val collector = MetadataCollector().apply { init(buildFullMetadataJson()) }
        collector.setResult(buildJsonObject { put("ok", true) })
        assertTrue(collector.validate().isEmpty())
    }

    @Test
    fun validatePassesAfterSetError() {
        val collector = MetadataCollector().apply { init(buildFullMetadataJson()) }
        collector.setError(errorCode = "E", message = "m")
        assertTrue(collector.validate().isEmpty())
    }

    // --- close ---

    @Test
    fun closeClearsResult() {
        val collector = MetadataCollector().apply { init(buildFullMetadataJson()) }
        collector.setResult(buildJsonObject { put("ok", true) })
        collector.close()
        assertNull(collector.payload())
    }

    // --- Collectors pipeline integration ---

    @Test
    fun collectorsAsJsonSetsActionKeyAndFormDataWhenResultPresent() {
        val collector = MetadataCollector().apply {
            init(buildFullMetadataJson())
            setResult(buildJsonObject { put("verified", true) })
        }
        val json = listOf(collector).asJson()
        assertEquals("sdkMetadata", json["actionKey"]?.jsonPrimitive?.content)
        val sdkMetadata = json["formData"]?.jsonObject?.get("sdkMetadata")?.jsonObject
        assertNotNull(sdkMetadata)
        assertEquals(true, sdkMetadata["verified"]?.jsonPrimitive?.boolean)
    }

    @Test
    fun collectorsEventTypeIsActionWhenResultPresent() {
        val collector = MetadataCollector().apply {
            init(buildFullMetadataJson())
            setResult(buildJsonObject { put("ok", true) })
        }
        assertEquals("action", listOf(collector).eventType())
    }

    @Test
    fun collectorsAsJsonOmitsActionKeyWhenNoResult() {
        val collector = MetadataCollector().apply { init(buildFullMetadataJson()) }
        val json = listOf(collector).asJson()
        assertNull(json["actionKey"])
        assertTrue(json["formData"]?.jsonObject?.isEmpty() ?: true)
    }

    @Test
    fun collectorsEventTypeIsNullWhenNoResult() {
        val collector = MetadataCollector().apply { init(buildFullMetadataJson()) }
        assertNull(listOf(collector).eventType())
    }
}

/*
 * Copyright (c) 2024 - 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci

import com.pingidentity.davinci.collector.ReadOnlyTextCollector
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReadOnlyTextCollectorTest {

    // Full JSON as returned by the DaVinci server
    private fun buildFullAgreementJson(): JsonObject = buildJsonObject {
        put("type", "AGREEMENT")
        put("inputType", "READ_ONLY_TEXT")
        put("key", "agreement")
        put("content", "This is example agreement text, you can edit this text in the agreements section.")
        put("titleEnabled", true)
        put("title", "Terms of Service Agreement")
        putJsonObject("agreement") {
            put("id", "6ff30c9e-cd98-4fe5-85ca-01111ca20702")
            put("useDynamicAgreement", false)
        }
        put("enabled", true)
    }

    @Test
    fun initializesKeyFromJson() {
        val collector = ReadOnlyTextCollector().apply { init(buildFullAgreementJson()) }
        assertEquals("agreement", collector.key)
    }

    @Test
    fun initializesTypeFromJson() {
        val collector = ReadOnlyTextCollector().apply { init(buildFullAgreementJson()) }
        assertEquals("AGREEMENT", collector.type)
    }

    @Test
    fun initializesContentFromJson() {
        val collector = ReadOnlyTextCollector().apply { init(buildFullAgreementJson()) }
        assertEquals(
            "This is example agreement text, you can edit this text in the agreements section.",
            collector.content
        )
    }

    @Test
    fun initializesTitleFromJson() {
        val collector = ReadOnlyTextCollector().apply { init(buildFullAgreementJson()) }
        assertEquals("Terms of Service Agreement", collector.title)
    }

    @Test
    fun initializesTitleEnabledFromJson() {
        val collector = ReadOnlyTextCollector().apply { init(buildFullAgreementJson()) }
        assertTrue(collector.titleEnabled)
    }

    @Test
    fun initializesEnabledFromJson() {
        val collector = ReadOnlyTextCollector().apply { init(buildFullAgreementJson()) }
        assertTrue(collector.enabled)
    }

    @Test
    fun initializesAgreementIdFromJson() {
        val collector = ReadOnlyTextCollector().apply { init(buildFullAgreementJson()) }
        assertEquals("6ff30c9e-cd98-4fe5-85ca-01111ca20702", collector.agreementId)
    }

    @Test
    fun initializesUseDynamicAgreementFromJson() {
        val collector = ReadOnlyTextCollector().apply { init(buildFullAgreementJson()) }
        assertFalse(collector.useDynamicAgreement)
    }

    @Test
    fun idReturnsKey() {
        val collector = ReadOnlyTextCollector().apply { init(buildFullAgreementJson()) }
        assertEquals("agreement", collector.id())
    }

    @Test
    fun initializesWithDefaultsWhenJsonIsEmpty() {
        val collector = ReadOnlyTextCollector().apply { init(JsonObject(emptyMap())) }
        assertEquals("", collector.key)
        assertEquals("", collector.type)
        assertEquals("", collector.content)
        assertEquals("", collector.title)
        assertFalse(collector.titleEnabled)
        assertTrue(collector.enabled)   // defaults to true
        assertEquals("", collector.agreementId)
        assertFalse(collector.useDynamicAgreement)
    }

    @Test
    fun titleEnabledFalseWhenNotProvided() {
        val input = buildJsonObject { put("key", "agreement") }
        val collector = ReadOnlyTextCollector().apply { init(input) }
        assertFalse(collector.titleEnabled)
    }

}
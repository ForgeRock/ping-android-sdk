/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci

import com.pingidentity.davinci.collector.ImageCollector
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ImageCollectorTest {

    private fun buildFullImageJson(): JsonObject = buildJsonObject {
        put("type", "IMAGE")
        put("key", "image")
        put("description", "New Image")
        put("imageUrl", "https://travel.destinationcanada.com/_next/image?url=https%3A%2F%2Fadmin.destinationcanada.com%2Fsites%2Fdefault%2Ffiles%2F2023-06%2FQC-Montreal-Skyline_hero.jpg&w=1920&q=75")
        put("hyperlinkUrl", "https://www.pingidentity.com/en.html")
    }

    @Test
    fun initializesKeyFromJson() {
        val collector = ImageCollector().apply { init(buildFullImageJson()) }
        assertEquals("image", collector.key)
    }

    @Test
    fun initializesDescriptionFromJson() {
        val collector = ImageCollector().apply { init(buildFullImageJson()) }
        assertEquals("New Image", collector.description)
    }

    @Test
    fun initializesImageUrlFromJson() {
        val collector = ImageCollector().apply { init(buildFullImageJson()) }
        assertEquals(
            "https://travel.destinationcanada.com/_next/image?url=https%3A%2F%2Fadmin.destinationcanada.com%2Fsites%2Fdefault%2Ffiles%2F2023-06%2FQC-Montreal-Skyline_hero.jpg&w=1920&q=75",
            collector.imageUrl
        )
    }

    @Test
    fun initializesHyperlinkUrlFromJson() {
        val collector = ImageCollector().apply { init(buildFullImageJson()) }
        assertEquals("https://www.pingidentity.com/en.html", collector.hyperlinkUrl)
    }

    @Test
    fun idReturnsKey() {
        val collector = ImageCollector().apply { init(buildFullImageJson()) }
        assertEquals("image", collector.id())
    }

    @Test
    fun initReturnsCollectorInstanceForMethodChaining() {
        val collector = ImageCollector()
        val result = collector.init(buildFullImageJson())
        assertEquals(collector, result)
    }

    @Test
    fun hyperlinkUrlIsNullWhenAbsent() {
        val input = buildJsonObject {
            put("type", "IMAGE")
            put("key", "image")
            put("description", "No link")
            put("imageUrl", "https://example.com/image.png")
        }
        val collector = ImageCollector().apply { init(input) }
        assertNull(collector.hyperlinkUrl)
    }

    @Test
    fun initializesWithDefaultsWhenJsonIsEmpty() {
        val collector = ImageCollector().apply { init(JsonObject(emptyMap())) }
        assertEquals("", collector.key)
        assertEquals("", collector.description)
        assertEquals("", collector.imageUrl)
        assertNull(collector.hyperlinkUrl)
    }

    @Test
    fun idReturnsEmptyStringWhenKeyAbsent() {
        val collector = ImageCollector().apply { init(JsonObject(emptyMap())) }
        assertEquals("", collector.id())
    }

    @Test
    fun initializesWithEmptyImageUrlWhenImageUrlAbsent() {
        val input = buildJsonObject {
            put("key", "image")
            put("description", "Missing imageUrl")
        }
        val collector = ImageCollector().apply { init(input) }
        assertEquals("", collector.imageUrl)
    }
}

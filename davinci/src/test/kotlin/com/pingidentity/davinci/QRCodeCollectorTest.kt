/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci

import com.pingidentity.davinci.collector.QRCodeCollector
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class QRCodeCollectorTest {

    @Test
    fun initializesContentAndFallbackTextWithProvidedValues() {
        val input = buildJsonObject {
            put("content", "data:image/png;base64,iVBORw0KGgoAAAANSUhEUg")
            put("fallbackText", "Scan this QR code")
        }
        val collector = QRCodeCollector()
        collector.init(input)

        assertEquals("data:image/png;base64,iVBORw0KGgoAAAANSUhEUg", collector.content)
        assertEquals("Scan this QR code", collector.fallbackText)
    }

    @Test
    fun initializesWithEmptyStringsWhenNoValuesProvided() {
        val input = JsonObject(emptyMap())
        val collector = QRCodeCollector()
        collector.init(input)

        assertEquals("", collector.content)
        assertEquals("", collector.fallbackText)
    }

    @Test
    fun initializesContentWithEmptyStringWhenContentIsNull() {
        val input = buildJsonObject {
            put("fallbackText", "Use manual code")
        }
        val collector = QRCodeCollector()
        collector.init(input)

        assertEquals("", collector.content)
        assertEquals("Use manual code", collector.fallbackText)
    }

    @Test
    fun initializesFallbackTextWithEmptyStringWhenFallbackTextIsNull() {
        val input = buildJsonObject {
            put("content", "data:image/png;base64,abc123")
        }
        val collector = QRCodeCollector()
        collector.init(input)

        assertEquals("data:image/png;base64,abc123", collector.content)
        assertEquals("", collector.fallbackText)
    }

    @Test
    fun returnsNullForInvalidBase64Data() {
        val input = buildJsonObject {
            put("content", "data:image/png;base64,ThisIsNotValidBase64!!!")
            put("fallbackText", "Use alternative method")
        }
        val collector = QRCodeCollector()
        collector.init(input)

        val bitmap = collector.bitmap()
        assertNull(bitmap)
    }

    @Test
    fun returnsNullForValidBase64ButInvalidImageData() {
        val invalidImageBytes = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05)
        val base64Content = Base64.encode(invalidImageBytes)

        val input = buildJsonObject {
            put("content", "data:image/png;base64,$base64Content")
            put("fallbackText", "Fallback option")
        }
        val collector = QRCodeCollector()
        collector.init(input)

        val bitmap = collector.bitmap()
        assertNull(bitmap)
    }

    @Test
    fun returnsNullWhenContentDoesNotContainBase64Prefix() {
        val input = buildJsonObject {
            put("content", "iVBORw0KGgoAAAANSUhEUg")
            put("fallbackText", "No base64 prefix")
        }
        val collector = QRCodeCollector()
        collector.init(input)

        val bitmap = collector.bitmap()
        assertNull(bitmap)
    }

    @Test
    fun returnsNullForEmptyContent() {
        val input = buildJsonObject {
            put("content", "")
            put("fallbackText", "Empty content")
        }
        val collector = QRCodeCollector()
        collector.init(input)

        val bitmap = collector.bitmap()
        assertNull(bitmap)
    }

    @Test
    fun returnsNullWhenContentOnlyContainsBase64Prefix() {
        val input = buildJsonObject {
            put("content", "data:image/png;base64,")
            put("fallbackText", "Only prefix")
        }
        val collector = QRCodeCollector()
        collector.init(input)

        val bitmap = collector.bitmap()
        assertNull(bitmap)
    }

    @Test
    fun extractsBase64DataAfterBase64PrefixRegardlessOfMimeType() {
        val invalidImageBytes = byteArrayOf(0x01, 0x02, 0x03)
        val base64Content = Base64.encode(invalidImageBytes)

        val input = buildJsonObject {
            put("content", "data:image/jpeg;base64,$base64Content")
            put("fallbackText", "JPEG mime type")
        }
        val collector = QRCodeCollector()
        collector.init(input)

        val bitmap = collector.bitmap()
        assertNull(bitmap)
    }

    @Test
    fun initReturnsCollectorInstanceForMethodChaining() {
        val input = buildJsonObject {
            put("content", "data:image/png;base64,abc")
            put("fallbackText", "Test")
        }
        val collector = QRCodeCollector()
        val result = collector.init(input)

        assertEquals(collector, result)
    }
}

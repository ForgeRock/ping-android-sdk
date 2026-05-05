/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci.collector

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.pingidentity.davinci.plugin.Collector
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64

/**
 * A collector that handles QR code display in DaVinci authentication flows.
 *
 * The QRCodeCollector is used to display QR codes to users for authentication scenarios such as:
 * - Device pairing
 * - Multi-factor authentication setup
 * - Out-of-band authentication
 * - Cross-device authentication flows
 *
 * The QR code content is typically provided as a Base64-encoded image string that can be
 * decoded and displayed as a bitmap.
 *
 * ## Content Format
 *
 * The [content] field typically contains a data URI string with Base64-encoded image data:
 * ```
 * data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAA...
 * ```
 *
 * @see Collector
 * @see bitmap
 */
class QRCodeCollector : Collector<Nothing> {

    /**
     * The QR code content as a Base64-encoded data URI string.
     *
     * This typically contains the full data URI including the MIME type and Base64 prefix:
     * `data:image/png;base64,{base64-encoded-data}`
     *
     * The [bitmap] method extracts and decodes this content to create a displayable bitmap.
     */
    lateinit var content: String
        private set

    /**
     * Fallback text to display when the QR code cannot be rendered.
     *
     * This text provides an alternative way for users to complete the authentication
     * if the QR code cannot be displayed or scanned. It may contain:
     * - A manual entry code
     * - Instructions for alternative authentication methods
     * - Error or help information
     */
    lateinit var fallbackText: String
        private set

    /**
     * Initializes the QRCodeCollector with configuration from the input JSON.
     *
     * Extracts and sets the following parameters:
     * - [content]: QR code content as Base64-encoded data URI (default: "")
     * - [fallbackText]: Alternative text if QR code cannot be displayed (default: "")
     *
     * @param input JSON object containing the collector configuration with fields:
     *              - `content`: The Base64-encoded QR code image data
     *              - `fallbackText`: Alternative text for display
     * @return This QRCodeCollector instance for method chaining
     *
     * @see Collector.init
     */
    override fun init(input: JsonObject): QRCodeCollector {
        super.init(input)
        content = input["content"]?.jsonPrimitive?.content ?: ""
        fallbackText = input["fallbackText"]?.jsonPrimitive?.content ?: ""
        return this
    }

    /**
     * Converts the Base64-encoded QR code content to a displayable Bitmap.
     *
     * This method:
     * 1. Extracts the Base64 data from the [content] string (after "base64,")
     * 2. Decodes the Base64 string to a byte array
     * 3. Converts the byte array to a Bitmap using BitmapFactory
     *
     * ## Content Format
     *
     * The method expects [content] to be in data URI format:
     * ```
     * data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAA...
     * ```
     *
     * The substring after "base64," is extracted and decoded.
     *
     * @return A Bitmap representation of the QR code, or `null` if decoding fails
     *
     * @see content
     * @see fallbackText
     */
    fun bitmap(): Bitmap? {
        return try {
            // Decode Base64 content and convert to Bitmap
            val decodedBytes = Base64.decode(content.substringAfter("base64,"))
            BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        } catch (e: Exception) {
            // Return null if decoding fails
            null
        }
    }
}
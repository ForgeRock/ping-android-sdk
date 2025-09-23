/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.fido2

import kotlin.io.encoding.Base64.Default.UrlSafe
import kotlin.io.encoding.Base64.PaddingOption

/**
 * Base64 encoder/decoder configured for URL-safe encoding without padding.
 * This follows the FIDO2/WebAuthn specifications for binary data encoding.
 */
val base64UrlSafeNoPadding by lazy {
    UrlSafe.withPadding(PaddingOption.ABSENT)
}

/**
 * Converts a ByteArray to a URL-safe Base64-encoded string without padding.
 *
 * This method encodes binary data using URL-safe Base64 encoding.
 *
 * **Example:**
 * ```kotlin
 * val bytes = byteArrayOf(72, 101, 108, 108, 111) // "Hello"
 * val encoded = bytes.toBase64() // Returns "SGVsbG8"
 * ```
 *
 * @receiver ByteArray The binary data to encode
 * @return The URL-safe Base64-encoded string without padding
 */
internal fun ByteArray.toBase64(): String {
    return base64UrlSafeNoPadding.encode(this)
}

/**
 * Converts a Base64-encoded string to a JSON string.
 *
 * This method decodes a Base64 URL-safe string and converts the resulting
 * bytes to a UTF-8 string, typically containing JSON data.
 * **Example:**
 * ```kotlin
 * val base64 = "eyJ0eXBlIjoid2ViYXV0aG4uZ2V0In0" // Base64 encoded JSON
 * val json = base64.base64ToStr() // Returns {"type":"webauthn.get"}
 * ```
 *
 * @receiver String The Base64 URL-safe encoded string to decode
 * @return The decoded JSON string in UTF-8 format
 */
internal fun String.base64ToStr(): String {
    val decodedBytes = base64UrlSafeNoPadding.decode(this)
    return decodedBytes.toString(Charsets.UTF_8)
}

/**
 * Converts a Base64-encoded string to a comma-separated string of integers.
 *
 * This method decodes a Base64 string and converts each byte to its integer
 * representation (range -128 to 127), joined by commas. This format is used
 * by some Journey server configurations for binary data representation in
 * legacy compatibility modes.
 *
 * **Example:**
 * ```kotlin
 * val base64 = "SGVsbG8" // Base64 for "Hello"
 * val intStr = base64.base64ToIntStr() // Returns "72,101,108,108,111"
 * ```
 *
 * @receiver String The Base64 encoded string to decode and convert
 * @return A comma-separated string of signed integer values (-128 to 127)
 * @throws IllegalArgumentException if the input is not valid Base64
 */
internal fun String.base64ToIntStr(): String {
    return base64UrlSafeNoPadding.decode(this).joinToString(Constants.INT_SEPARATOR) { it.toInt().toString() }
}

/**
 * Converts a regular string to a Base64-encoded string.
 *
 * This is a convenience method that converts a UTF-8 string to bytes
 * and then encodes it using URL-safe Base64 without padding.
 *
 * **Example:**
 * ```kotlin
 * val text = "Hello World"
 * val encoded = text.toBase64Str() // Returns "SGVsbG8gV29ybGQ"
 * ```
 *
 * @receiver String The UTF-8 string to encode
 * @return The URL-safe Base64-encoded representation
 */
internal fun String.toBase64Str(): String {
    return this.toByteArray().toBase64()
}

/**
 * Converts a standard Base64 string to URL-safe Base64 without padding.
 *
 * This method re-encodes Base64 data to ensure it uses URL-safe characters
 * (replacing '+' with '-' and '/' with '_') and removes padding characters ('='),
 * as required by the WebAuthn specification. This is necessary when receiving
 * Base64 data from sources that use standard encoding.
 *
 * **Character Mapping:**
 * - '+' → '-'
 * - '/' → '_'
 * - '=' padding removed
 *
 * **Example:**
 * ```kotlin
 * val standard = "SGVsbG8+V29ybGQ=" // Standard Base64 with padding
 * val urlSafe = standard.base64DefaultToUrlSafe() // Returns "SGVsbG8-V29ybGQ"
 * ```
 *
 * @receiver String The standard Base64 string to convert
 * @return The URL-safe Base64-encoded string without padding
 * @throws IllegalArgumentException if the input is not valid Base64
 */
internal fun String.base64DefaultToUrlSafe(): String {
    val decoded = kotlin.io.encoding.Base64.decode(this)
    return base64UrlSafeNoPadding.encode(decoded)
}

/**
 * Decodes a URL-safe Base64 string to a byte array.
 *
 * This method decodes URL-safe Base64 strings (using '-' and '_' instead of
 * '+' and '/') without requiring padding. This is the standard format used
 * throughout FIDO2/WebAuthn implementations.
 *
 * **Example:**
 * ```kotlin
 * val urlSafeBase64 = "SGVsbG8gV29ybGQ" // URL-safe Base64 without padding
 * val bytes = urlSafeBase64.urlSafeDecode() // Returns byte array for "Hello World"
 * ```
 *
 * @receiver String The URL-safe Base64 string to decode
 * @return The decoded byte array
 */
internal fun String.urlSafeDecode(): ByteArray {
    return base64UrlSafeNoPadding.decode(this)
}

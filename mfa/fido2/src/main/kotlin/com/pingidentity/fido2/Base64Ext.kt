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
 * This method encodes binary data using URL-safe Base64 encoding as required
 * by the FIDO2/WebAuthn specifications.
 *
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
 *
 * @return The decoded JSON string
 */
internal fun String.base64ToStr(): String {
    val decodedBytes = base64UrlSafeNoPadding.decode(this)
    return decodedBytes.toString(Charsets.UTF_8)
}

/**
 * Converts a Base64-encoded string to a comma-separated string of integers.
 *
 * This method decodes a Base64 string and converts each byte to its integer
 * representation, joined by commas. This format is used by some Journey
 * server configurations.
 *
 * @return A comma-separated string of integer values
 */
internal fun String.base64ToIntStr(): String {
    return base64UrlSafeNoPadding.decode(this).joinToString(Constants.INT_SEPARATOR) { it.toInt().toString() }
}

internal fun String.toBase64Str(): String {
    return this.toByteArray().toBase64()
}

/**
 * Converts a standard Base64 string to URL-safe Base64 without padding.
 *
 * This method re-encodes Base64 data to ensure it uses URL-safe characters
 * and removes padding, as required by the WebAuthn specification.
 *
 * @return The URL-safe Base64-encoded string without padding
 */
internal fun String.base64DefaultToUrlSafe(): String {
    val decoded = kotlin.io.encoding.Base64.decode(this)
    return base64UrlSafeNoPadding.encode(decoded)
}

internal fun String.base64Default() = kotlin.io.encoding.Base64.decode(this)

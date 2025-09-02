/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.fido2.journey

import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.CreateCredentialUnsupportedException
import androidx.credentials.exceptions.publickeycredential.CreatePublicKeyCredentialDomException
import com.pingidentity.fido2.Constants
import com.pingidentity.journey.plugin.AbstractCallback
import com.pingidentity.journey.plugin.ContinueNodeAware
import com.pingidentity.journey.plugin.ValueCallback
import com.pingidentity.journey.plugin.callbacks
import com.pingidentity.logger.Logger
import com.pingidentity.orchestrate.ContinueNode
import kotlin.io.encoding.Base64.Default.UrlSafe
import kotlin.io.encoding.Base64.PaddingOption

/**
 * Abstract base class for FIDO2 callbacks in ForgeRock Journey workflows.
 *
 * This class provides common functionality for handling FIDO2 operations within
 * Journey workflows, including error handling, value setting, and Base64 encoding/decoding
 * utilities. It manages the interaction between FIDO2 operations and the Journey framework.
 *
 * The class handles the conversion between different data formats used by FIDO2
 * specifications and the Journey framework, ensuring proper encoding and error handling.
 */
abstract class Fido2Callback : ContinueNodeAware, AbstractCallback() {

    /**
     * The Journey continue node that this callback is associated with.
     * This provides access to the workflow context and other callbacks.
     */
    override lateinit var continueNode: ContinueNode

    /**
     * Logger instance for this callback, obtained from the workflow configuration.
     */
    val logger: Logger by lazy {
        continueNode.workflow.config.logger
    }

    /**
     * Base64 encoder/decoder configured for URL-safe encoding without padding.
     * This follows the FIDO2/WebAuthn specifications for binary data encoding.
     */
    private val base64UrlSafeNoPadding by lazy {
        UrlSafe.withPadding(PaddingOption.ABSENT)
    }

    /**
     * Sets a value to the ValueCallback associated with the WebAuthn outcome.
     *
     * This method finds the ValueCallback with the ID "webAuthnOutcome" and sets
     * the provided value, which will be sent back to the Journey server.
     *
     * @param value The value to set for the WebAuthn outcome
     */
    fun valueCallback(value: String) {
        continueNode.callbacks.filterIsInstance<ValueCallback>()
            .find { it.id == Constants.WEB_AUTHN_OUTCOME }
            ?.let { it.value = value }
    }

    /**
     * Handles errors that occur during FIDO2 operations.
     *
     * This method converts various types of credential exceptions into appropriate
     * error messages that the Journey server can understand and process.
     *
     * @param error The throwable error to handle and convert
     */
    fun handleError(error: Throwable) {
        when (error) {
            is CreateCredentialUnsupportedException -> valueCallback(Constants.ERROR_UNSUPPORTED)
            is CreateCredentialCancellationException -> setError(Constants.ERROR_NOT_ALLOWED, error.message)
            is CreatePublicKeyCredentialDomException -> setError(error.domError::class.simpleName, error.message)
            else -> setError(Constants.ERROR_UNKNOWN, error.message)
        }
    }

    /**
     * Sets an error message to the WebAuthn outcome callback.
     *
     * @param error The error type identifier
     * @param message The detailed error message
     */
    private fun setError(error: String?, message: String?) {
        continueNode.callbacks.filterIsInstance<ValueCallback>()
            .find { it.id == Constants.WEB_AUTHN_OUTCOME }
            ?.let { it.value = "${Constants.ERROR_PREFIX}$error:$message" }
    }

    /**
     * Converts a Base64-encoded string to a JSON string.
     *
     * This method decodes a Base64 URL-safe string and converts the resulting
     * bytes to a UTF-8 string, typically containing JSON data.
     *
     * @return The decoded JSON string
     */
    internal fun String.base64ToJson(): String {
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

}
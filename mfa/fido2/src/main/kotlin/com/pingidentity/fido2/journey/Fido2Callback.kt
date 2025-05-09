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
import com.pingidentity.journey.plugin.AbstractCallback
import com.pingidentity.journey.plugin.ContinueNodeAware
import com.pingidentity.journey.plugin.ValueCallback
import com.pingidentity.journey.plugin.callbacks
import com.pingidentity.logger.Logger
import com.pingidentity.orchestrate.ContinueNode
import kotlin.io.encoding.Base64.*
import kotlin.io.encoding.Base64.Default.UrlSafe
import kotlin.io.encoding.ExperimentalEncodingApi

private const val WEB_AUTHN_OUTCOME = "webAuthnOutcome"

/**
 * Abstract class for FIDO2 callbacks.
 * This class provides common functionality for handling FIDO2 callbacks,
 * including setting values to the associated [ValueCallback] and handling errors.
 */
@OptIn(ExperimentalEncodingApi::class)
abstract class Fido2Callback : ContinueNodeAware, AbstractCallback() {

    override lateinit var continueNode: ContinueNode
    val logger: Logger by lazy {
        continueNode.workflow.config.logger
    }

    /**
     * Base64 encoder/decoder used for encoding and decoding FIDO2 data.
     * It uses URL-safe encoding without padding, as per the FIDO2 specifications.
     */
    private val base64UrlSafeNoPadding by lazy {
        UrlSafe.withPadding(PaddingOption.ABSENT)
    }

    /**
     * Set value to the [ValueCallback] which associated with the WebAuthn
     * Callback.
     *
     * @param value The Value to set to the [ValueCallback]
     */
    fun valueCallback(value: String) {
        for (callback in continueNode.callbacks) {
            if (callback is ValueCallback) {
                if (callback.id == WEB_AUTHN_OUTCOME) {
                    callback.value = value
                }
            }
        }
    }

    /**
     * Handle errors that occur during the FIDO2 process.
     * This method will set the error message to the [ValueCallback] associated with the WebAuthn Callback.
     *
     * @param error The Throwable error to handle
     */
    fun handleError(error: Throwable) {
        when (error) {
            is CreateCredentialUnsupportedException -> valueCallback("unsupported")
            is CreateCredentialCancellationException -> error("NotAllowedError", error.message)
            is CreatePublicKeyCredentialDomException -> {
                error(error.domError::class.simpleName, error.message)
            }

            else -> error("UnknownError", error.message)
        }
    }

    private fun error(error: String?, message: String?) {
        for (callback in continueNode.callbacks) {
            if (callback is ValueCallback) {
                if (callback.id == WEB_AUTHN_OUTCOME) {
                    callback.value = "ERROR::$error:$message"
                }
            }
        }
    }

    /**
     * Convert a Base64-encoded string to a JSON string.
     * This method decodes the Base64 string and converts it to a UTF-8 string.
     *
     * @return The decoded JSON string.
     */
    protected fun String.base64ToJson(): String {
        val decodedBytes = base64UrlSafeNoPadding.decode(this)
        return decodedBytes.toString(Charsets.UTF_8)
    }

    /**
     * Convert a Base64-encoded string to a comma-separated string of integers.
     * This method decodes the Base64 string and converts each byte to an integer.
     *
     * @return The comma-separated string of integers.
     */
    protected fun String.base64ToIntStr(): String {
        return base64UrlSafeNoPadding.decode(this).joinToString(",") { it.toInt().toString() }
    }

    /**
     * Convert a Base64-encoded string to a URL-safe Base64-encoded string without padding.
     * This method decodes the Base64 string and re-encodes it using URL-safe encoding.
     *
     * @return The URL-safe Base64-encoded string without padding.
     */
    protected fun String.base64DefaultToUrlSafe(): String {
        val decoded = kotlin.io.encoding.Base64.decode(this)
        return base64UrlSafeNoPadding.encode(decoded)
    }

    /**
     * Convert a ByteArray to a URL-safe Base64-encoded string without padding.
     * This method encodes the ByteArray using URL-safe Base64 encoding.
     *
     * @return The URL-safe Base64-encoded string without padding.
     */
    protected fun ByteArray.toBase64(): String {
        return base64UrlSafeNoPadding.encode(this)
    }

}
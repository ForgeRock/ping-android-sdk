/*
 * Copyright (c) 2025 - 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.fido.davinci

import com.pingidentity.davinci.plugin.Collector
import com.pingidentity.fido.Constants
import com.pingidentity.fido.Constants.FIELD_ALLOW_CREDENTIALS
import com.pingidentity.fido.Constants.FIELD_CHALLENGE
import com.pingidentity.fido.FidoAuthenticateCustomizer
import com.pingidentity.fido.FidoClient
import com.pingidentity.fido.FidoPendingAuthentication
import com.pingidentity.fido.toBase64
import com.pingidentity.orchestrate.Closeable
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * DaVinci collector for FIDO2 authentication operations.
 *
 * This collector handles FIDO2 authentication within a DaVinci workflow. It processes
 * authentication request options from the DaVinci server, performs the authentication
 * ceremony using the device's FIDO2 capabilities, and provides the assertion response
 * back to the workflow.
 *
 * The collector automatically transforms the DaVinci-specific JSON format to the
 * standard WebAuthn format required by the Android Credential Manager API.
 *
 * @property publicKeyCredentialRequestOptions The transformed authentication request options
 * @property assertionValue The authentication response after successful authentication
 */
class FidoAuthenticationCollector : AbstractFidoCollector(), Closeable {

    lateinit var publicKeyCredentialRequestOptions: JsonObject
        private set

    /**
     * The delivered assertion, stored from the [FidoPendingAuthentication.observe] callback on
     * the androidx delivery thread and read by [payload]/[close] on other threads — hence
     * [Volatile] so the fire-and-forget path (no [FidoPendingAuthentication.await]) is still
     * visible.
     */
    @Volatile
    private var assertionValue: JsonObject? = null

    /**
     * The in-flight pending request from the most recent [pendingAuthenticate] call, if any;
     * cancelled in [close] so an abandoned conditional ceremony cannot deliver an assertion
     * into a collector the workflow has already moved past. Written on the caller's coroutine
     * thread and read/cancelled in [close] from the workflow's close path — hence [Volatile].
     */
    @Volatile
    private var pendingAuthentication: FidoPendingAuthentication? = null

    /**
     * Initializes the collector with authentication request options.
     *
     * Extracts and transforms the public key credential request options from the
     * input JSON, preparing them for use with the Android Credential Manager.
     *
     * @param input The JSON object containing collector initialization data
     * @return This collector instance for method chaining
     * @throws IllegalArgumentException if publicKeyCredentialRequestOptions is missing
     */
    override fun init(input: JsonObject): Collector<JsonObject> {
        super.init(input)
        logger.d("Initializing FIDO2 FidoAuthenticationCollector")
        publicKeyCredentialRequestOptions =
            input[Constants.FIELD_PUBLIC_KEY_CREDENTIAL_REQUEST_OPTIONS]?.jsonObject
                ?.let { transform(it) }
                ?: throw IllegalArgumentException("Missing ${Constants.FIELD_PUBLIC_KEY_CREDENTIAL_REQUEST_OPTIONS}")
        logger.d("FIDO2 authentication collector initialized with request options")
        return this
    }

    /**
     * Returns the payload to be sent back to the DaVinci workflow.
     *
     * @return A [JsonObject] containing the assertion value if authentication was successful,
     *         or null if authentication hasn't been performed yet
     */
    override fun payload(): JsonObject? {
        if (errorCode != null) {
            return buildJsonObject { }
        }
        return assertionValue?.let {
            logger.d("Returning assertion payload for FIDO2 authentication")
            buildJsonObject {
                put(Constants.FIELD_ASSERTION_VALUE, it)
            }
        }
    }

    /**
     * Performs FIDO2 authentication using the initialized request options.
     *
     * This method initiates the FIDO2 authentication ceremony using the Android
     * Credential Manager. Upon successful authentication, the assertion value
     * is stored and will be automatically included in the workflow payload.
     *
     * @param block A lambda function that transforms the public key credential request options
     * @return A [Result] containing the assertion response as a [JsonObject] on success,
     *         or an exception on failure
     */
    suspend fun authenticate(
        block: FidoAuthenticateCustomizer.() -> Unit = {}
    ): Result<JsonObject> {
        errorCode = null
        assertionValue = null
        logger.d("Starting FIDO2 authentication")
        return FidoClient { logger = this@FidoAuthenticationCollector.logger }.authenticate(
            publicKeyCredentialRequestOptions, block
        ).onSuccess {
            logger.d("FIDO2 authentication successful")
            assertionValue = it
        }.onFailure { exception ->
            logger.e("FIDO2 authentication failed", exception)
            handleError(exception)
        }
    }

    /**
     * Builds a pending (View-attachable) FIDO2 authentication request for conditional
     * mediation (autofill with passkeys).
     *
     * Unlike [authenticate], which runs the modal ceremony to completion, this method returns
     * a [FidoPendingAuthentication] whose request must be attached to the View that should
     * surface passkey suggestions (typically the username field):
     *
     * ```kotlin
     * val pending = collector.pendingAuthenticate().getOrThrow()
     * view.pendingGetCredentialRequest = pending.request
     * ```
     *
     * The collector observes the delivered assertion internally: whenever the user completes
     * the ceremony from the attached View's suggestions, [assertionValue] is stored and
     * [payload] returns the assertion exactly as after a successful [authenticate] — even if
     * the app never calls [FidoPendingAuthentication.await]. Ceremonies that never deliver
     * (user dismissed the suggestions) leave the payload untouched; keep the modal
     * [authenticate] available as the fallback.
     *
     * Fails fast with `errorCode = NotSupportedError` when the device cannot deliver pending
     * requests (OS gate unmet — see [com.pingidentity.fido.isConditionalMediationSupported]).
     * Routing always uses the Android Credential Manager (the Google Play Services FIDO2 API
     * has no conditional surface), regardless of the block's `useFido2ApiClient` setting.
     *
     * @param block A lambda function that transforms the public key credential request options
     * @return A [Result] containing the [FidoPendingAuthentication] to attach to a View on
     *         success, or an exception on failure
     */
    suspend fun pendingAuthenticate(
        block: FidoAuthenticateCustomizer.() -> Unit = {}
    ): Result<FidoPendingAuthentication> {
        // A superseded pending request must not deliver an assertion into this new ceremony
        pendingAuthentication?.cancel()
        errorCode = null
        assertionValue = null
        logger.d("Starting FIDO2 pending authentication")
        return FidoClient { logger = this@FidoAuthenticationCollector.logger }
            .pendingAuthenticate(publicKeyCredentialRequestOptions, block)
            .onSuccess { pending ->
                logger.d("FIDO2 pending authentication request created")
                pendingAuthentication = pending
                pending.observe { result ->
                    result.onSuccess { assertion ->
                        logger.d("FIDO2 pending authentication successful")
                        assertionValue = assertion
                    }.onFailure { exception ->
                        // Cancellation is teardown (close), not a ceremony failure — the
                        // androidx pending path itself never propagates errors.
                        if (exception is CancellationException) {
                            logger.d("FIDO2 pending authentication cancelled")
                        } else {
                            logger.e("FIDO2 pending authentication failed", exception)
                            handleError(exception)
                        }
                    }
                }
            }.onFailure { exception ->
                logger.e("FIDO2 pending authentication failed", exception)
                handleError(exception)
            }
    }

    /**
     * Transforms the DaVinci JSON format to WebAuthn-compatible format.
     *
     * Converts array-based binary data to proper Base64 URL-safe encoding as
     * required by the WebAuthn specification. This includes:
     * - Converting challenge from byte array to Base64 URL string
     * - Transforming allowCredentials IDs from byte arrays to Base64 URL strings
     *
     * @param inputJson The input JSON object in DaVinci format
     * @return The transformed JSON object compatible with WebAuthn standards
     */
    private fun transform(inputJson: JsonObject): JsonObject {
        logger.d("Transforming FIDO2 authentication request options")
        val map = inputJson.toMutableMap()

        // Convert challenge array to Base64 string
        (map[FIELD_CHALLENGE] as? JsonArray)?.let { challenge ->
            val byteArray = challenge.map { it.jsonPrimitive.int.toByte() }.toByteArray()
            map[FIELD_CHALLENGE] =
                JsonPrimitive(byteArray.toBase64())
        }

        // Convert allowCredentials IDs to Base64 strings
        (map[FIELD_ALLOW_CREDENTIALS] as? JsonArray)?.let { allowCredentials ->
            val updated = allowCredentials.map { credential ->
                if (credential is JsonObject && credential.containsKey(Constants.FIELD_ID)) {
                    val credentialMap = credential.toMutableMap()
                    (credentialMap[Constants.FIELD_ID] as? JsonArray)?.let { id ->
                        val byteArray = id.map { it.jsonPrimitive.int.toByte() }.toByteArray()
                        credentialMap[Constants.FIELD_ID] =
                            JsonPrimitive(byteArray.toBase64())
                    }
                    JsonObject(credentialMap)
                } else {
                    credential
                }
            }
            map[FIELD_ALLOW_CREDENTIALS] = JsonArray(updated)
        }

        logger.d("FIDO2 authentication request options transformed successfully")
        return JsonObject(map)
    }

    override fun close() {
        pendingAuthentication?.cancel()
        pendingAuthentication = null
        assertionValue = null
        errorCode = null
    }

}
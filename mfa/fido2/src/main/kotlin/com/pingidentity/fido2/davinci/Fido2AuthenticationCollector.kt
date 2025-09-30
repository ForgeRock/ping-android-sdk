/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.fido2.davinci

import com.pingidentity.davinci.plugin.Collector
import com.pingidentity.fido2.Constants
import com.pingidentity.fido2.Constants.FIELD_ALLOW_CREDENTIALS
import com.pingidentity.fido2.Constants.FIELD_CHALLENGE
import com.pingidentity.fido2.Fido2AuthenticateCustomizer
import com.pingidentity.fido2.Fido2Client
import com.pingidentity.fido2.toBase64
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
class Fido2AuthenticationCollector : AbstractFido2Collector() {

    lateinit var publicKeyCredentialRequestOptions: JsonObject
        private set

    lateinit var assertionValue: JsonObject
        private set

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
        logger.d("Initializing FIDO2 Fido2AuthenticationCollector")
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
        return if (::assertionValue.isInitialized) {
            logger.d("Returning assertion payload for FIDO2 authentication")
            buildJsonObject { put(Constants.FIELD_ASSERTION_VALUE, assertionValue) }
        } else {
            logger.d("No assertion value available, returning null payload")
            null
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
        block: Fido2AuthenticateCustomizer.() -> Unit = {}
    ): Result<JsonObject> {
        logger.d("Starting FIDO2 authentication")
        return Fido2Client { logger = this@Fido2AuthenticationCollector.logger }.authenticate(
            publicKeyCredentialRequestOptions, block
        ).onSuccess {
            logger.d("FIDO2 authentication successful")
            assertionValue = it
        }.onFailure { exception ->
            logger.e("FIDO2 authentication failed", exception)
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

}
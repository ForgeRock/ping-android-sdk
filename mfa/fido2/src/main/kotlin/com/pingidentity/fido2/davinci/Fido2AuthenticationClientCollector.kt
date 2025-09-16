/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.fido2.davinci

import com.google.android.gms.fido.common.Transport
import com.google.android.gms.fido.fido2.api.common.PublicKeyCredentialDescriptor
import com.google.android.gms.fido.fido2.api.common.PublicKeyCredentialRequestOptions
import com.google.android.gms.fido.fido2.api.common.PublicKeyCredentialType
import com.pingidentity.davinci.plugin.Collector
import com.pingidentity.fido2.Constants
import com.pingidentity.fido2.Constants.DEFAULT_TIMEOUT
import com.pingidentity.fido2.Constants.FIELD_ALLOW_CREDENTIALS
import com.pingidentity.fido2.Constants.FIELD_CHALLENGE
import com.pingidentity.fido2.Constants.FIELD_ID
import com.pingidentity.fido2.Constants.FIELD_PUBLIC_KEY_CREDENTIAL_REQUEST_OPTIONS
import com.pingidentity.fido2.Constants.FIELD_RP_ID
import com.pingidentity.fido2.Constants.FIELD_TIMEOUT
import com.pingidentity.fido2.Fido2Client
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
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
class Fido2AuthenticationClientCollector : AbstractFido2Collector(), Fido2AuthenticationCollector {

    /**
     * The Google Play Services FIDO2 request options for authentication.
     *
     * This contains all the parameters needed for the authentication ceremony in
     * Google Play Services format, including:
     * - challenge: Binary challenge data from the server
     * - timeout: Maximum time allowed for the operation (in seconds)
     * - rpId: Relying party identifier
     * - allowList: Explicit list of acceptable credentials for non-discoverable mode
     *
     * The options are built from Journey's internal format during the init() process.
     */
    lateinit var publicKeyCredentialRequestOptions: PublicKeyCredentialRequestOptions
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
        logger.d("Initializing FIDO2 Fido2AuthenticationClientCollector")
        publicKeyCredentialRequestOptions =
            input[FIELD_PUBLIC_KEY_CREDENTIAL_REQUEST_OPTIONS]?.jsonObject
                ?.let { transform(it) }
                ?: throw IllegalArgumentException("Missing $FIELD_PUBLIC_KEY_CREDENTIAL_REQUEST_OPTIONS")
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
        block: (PublicKeyCredentialRequestOptions) -> PublicKeyCredentialRequestOptions = {
            it
        }
    ): Result<JsonObject> {
        logger.d("Starting FIDO2 authentication")
        return Fido2Client.authenticate(block(publicKeyCredentialRequestOptions)).onSuccess {
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
     * @param input The input JSON object in DaVinci format
     * @return The transformed JSON object compatible with WebAuthn standards
     */
    private fun transform(input: JsonObject): PublicKeyCredentialRequestOptions {
        logger.d("Transforming FIDO2 authentication request options")

        //Parse the string into a kotlinx.serialization JsonObject
        val builder = PublicKeyCredentialRequestOptions.Builder()

        //Set Challenge
        val challengeJsonArray = input[FIELD_CHALLENGE]?.jsonArray ?: buildJsonArray {}
        builder.setChallenge(challengeJsonArray.toByteArray())

        //Set Relying Party ID (rpId)
        val rpId = input[FIELD_RP_ID]?.jsonPrimitive?.content ?: ""
        builder.setRpId(rpId)

        //Set Timeout (convert milliseconds to seconds)
        val timeoutMs = input[FIELD_TIMEOUT]?.jsonPrimitive?.doubleOrNull ?: DEFAULT_TIMEOUT
        builder.setTimeoutSeconds(timeoutMs / 1000.0)

        //Set Allow List (allowCredentials)
        val credentialsJsonArray = input[FIELD_ALLOW_CREDENTIALS]?.jsonArray
        val allowList = credentialsJsonArray?.map { credElement ->
            val credObject = credElement.jsonObject
            val idJsonArray = credObject[FIELD_ID]?.jsonArray ?: buildJsonArray {}

            PublicKeyCredentialDescriptor(
                PublicKeyCredentialType.PUBLIC_KEY.toString(),
                idJsonArray.toByteArray(),
                listOf(Transport.INTERNAL))
        } ?: emptyList()
        builder.setAllowList(allowList)

        return builder.build()

    }

    private fun JsonArray.toByteArray(): ByteArray {
        return this.map { it.jsonPrimitive.int.toByte() }.toByteArray()
    }

    override suspend fun authenticate(): Result<JsonObject> = authenticate {
        it
    }

}
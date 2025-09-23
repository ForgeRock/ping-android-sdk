/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.fido2

import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import com.google.android.gms.fido.common.Transport
import com.google.android.gms.fido.fido2.api.common.AuthenticatorAssertionResponse
import com.google.android.gms.fido.fido2.api.common.PublicKeyCredentialDescriptor
import com.google.android.gms.fido.fido2.api.common.PublicKeyCredentialRequestOptions
import com.google.android.gms.fido.fido2.api.common.PublicKeyCredentialType
import com.pingidentity.android.ContextProvider
import com.pingidentity.fido2.Constants.FIELD_ALLOW_CREDENTIALS
import com.pingidentity.fido2.Constants.FIELD_AUTHENTICATOR_ATTACHMENT
import com.pingidentity.fido2.Constants.FIELD_AUTHENTICATOR_DATA
import com.pingidentity.fido2.Constants.FIELD_CLIENT_DATA_JSON
import com.pingidentity.fido2.Constants.FIELD_ID
import com.pingidentity.fido2.Constants.FIELD_RAW_ID
import com.pingidentity.fido2.Constants.FIELD_RESPONSE
import com.pingidentity.fido2.Constants.FIELD_SIGNATURE
import com.pingidentity.fido2.Constants.FIELD_TYPE
import com.pingidentity.fido2.Constants.FIELD_USER_HANDLE
import com.pingidentity.logger.Logger
import com.pingidentity.utils.PingDsl
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.coroutines.coroutineContext

/**
 * Core FIDO2 operations handler for Android applications.
 *
 * This class provides unified FIDO2 functionality by automatically selecting the most
 * appropriate API based on device capabilities and configuration. It supports:
 * - **Android Credential Manager API**: For modern passkey experiences (Android 14+)
 * - **Google Play Services FIDO2 API**: For broader device compatibility (Android 7+)
 *
 * **Key Features:**
 * - Automatic API selection based on availability and configuration
 * - Support for both discoverable and non-discoverable credentials
 * - Unified interface for different underlying implementations
 * - Comprehensive error handling and logging
 * - Customizable request options through lambda functions
 *
 * **Usage Patterns:**
 * ```kotlin
 * // Basic usage with default configuration
 * val client = Fido2Client()
 *
 * // Custom configuration
 * val client = Fido2Client {
 *     logger = customLogger
 *     useFido2Client = true // Force to use Fido2 Library
 * }
 * ```
 *
 * @see <a href="https://developer.android.com/identity/sign-in/credential-manager">Android Credential Manager</a>
 * @see <a href="https://developers.google.com/identity/fido">Google Play Services FIDO2</a>
 */
class Fido2Client(private val config: Fido2ClientConfig) {

    /**
     * Registers a new FIDO2 credential using Android Credential Manager.
     *
     * This method creates a new FIDO2 credential (passkey) on the device using the
     * Android Credential Manager API. The credential can be stored locally on the device
     * or synced across devices depending on platform capabilities and user preferences.
     *
     * **Registration Process:**
     * 1. Validates the credential creation request
     * 2. Prompts user for authentication (biometric, PIN, etc.)
     * 3. Generates cryptographic key pair on secure hardware
     * 4. Creates attestation response with public key and metadata
     * 5. Returns formatted response compatible with WebAuthn standards
     *
     * **Credential Storage:**
     * - **Local**: Stored in device's secure hardware (TPM/Secure Enclave)
     * - **Synced**: May sync across user's devices via platform provider
     * - **Discoverable**: Can be used without explicit credential ID
     *
     * @param input The credential creation options including user info,
     *                         relying party details, and cryptographic preferences
     * @return A [Result] containing the attestation response as a [JsonObject] on success,
     *         or an exception on failure. The response includes the public key,
     *         attestation data, and client data JSON.
     */
    suspend fun register(input: JsonObject, block: Fido2RegistrationCustomizer.() -> Unit = {}): Result<JsonObject> {
        val customizer = Fido2RegistrationCustomizer().apply(block)

        try {
            val credentialManager = CredentialManager.create(ContextProvider.context)
            val credentialRequest = customizer.customizer(CreatePublicKeyCredentialRequest(input.toString()))

            val result = credentialManager.createCredential(
                context = ContextProvider.context,
                request = credentialRequest
            )
            when (result) {
                is CreatePublicKeyCredentialResponse -> {
                    val attestationValue = Json.parseToJsonElement(
                        result.registrationResponseJson
                    ).jsonObject
                    return Result.success(attestationValue)
                }

                else -> throw IllegalStateException("Unexpected result type: ${result::class.simpleName}")
            }
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            return Result.failure(e)
        }
    }

    /**
     * Authenticates using an existing FIDO2 credential with Credential Manager API.
     *
     * This method performs FIDO2 authentication using the Android Credential Manager,
     * which provides support for discoverable credentials (passkeys) and cross-device
     * synchronization on supported devices.
     *
     * **Authentication Process:**
     * 1. Presents available credentials to the user
     * 2. User selects credential and provides verification
     * 3. Device generates cryptographic assertion
     * 4. Returns signed assertion response
     *
     * **Credential Discovery:**
     * - Automatically discovers available passkeys
     * - No need for explicit allowCredentials list
     * - Supports cross-device authentication
     *
     * @param credentialOption The authentication options including challenge,
     *                        timeout, and verification requirements
     * @return A [Result] containing the assertion response as a [JsonObject] on success,
     *         or an exception on failure
     */
    private suspend fun authenticate(credentialOption: GetPublicKeyCredentialOption): Result<JsonObject> {
        val credentialManager = CredentialManager.create(ContextProvider.context)
        val credentialRequest = GetCredentialRequest(listOf(credentialOption))
        try {
            val result = credentialManager.getCredential(
                context = ContextProvider.context,
                request = credentialRequest
            )
            when (val credential = result.credential) {
                is PublicKeyCredential -> {
                    val assertionValue = Json.parseToJsonElement(
                        credential.authenticationResponseJson
                    ).jsonObject
                    return Result.success(assertionValue)
                }

                else -> throw IllegalStateException("Unexpected result type: ${result::class.simpleName}")
            }
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            return Result.failure(e)
        }
    }

    /**
     * Authenticates using an existing FIDO2 credential with Google Play Services API.
     *
     * This method performs FIDO2 authentication using Google Play Services FIDO2 API,
     * which provides broader device compatibility for non-discoverable (device-bound)
     * credentials. This is used when Google Play Services is available and configured.
     *
     * **Authentication Process:**
     * 1. Uses explicit credential descriptors (allowCredentials)
     * 2. Launches transparent activity for user interaction
     * 3. Handles platform authenticator or security key
     * 4. Converts response to standard WebAuthn format
     *
     * **Compatibility:**
     * - Works with device-bound credentials
     * - Requires explicit credential list
     * - Broader Android version support
     *
     * @param credentialOption The authentication request options with challenge,
     *                        allowed credentials, and timeout settings
     * @return A [Result] containing the assertion response as a [JsonObject] on success,
     *         or an exception on failure. Response format matches Credential Manager output.
     */
    private suspend fun authenticate(credentialOption: PublicKeyCredentialRequestOptions): Result<JsonObject> {
        try {
            val credential = getPublicKeyCredential(ContextProvider.context, credentialOption)

            // Convert GMS PublicKeyCredential to JsonObject format similar to Credential Manager response
            val response = credential.response as AuthenticatorAssertionResponse
            val assertionValue = buildJsonObject {
                put(FIELD_ID, JsonPrimitive(credential.id))
                credential.rawId?.let {
                    put(FIELD_RAW_ID, JsonPrimitive(it.toBase64()))
                }
                put(
                    FIELD_AUTHENTICATOR_ATTACHMENT,
                    JsonPrimitive(
                        credential.authenticatorAttachment ?: Constants.AUTHENTICATOR_PLATFORM
                    )
                )
                put(FIELD_TYPE, JsonPrimitive(credential.type))
                put(FIELD_RESPONSE, buildJsonObject {
                    put(
                        FIELD_AUTHENTICATOR_DATA,
                        JsonPrimitive(response.authenticatorData.toBase64())
                    )
                    put(FIELD_CLIENT_DATA_JSON, JsonPrimitive(response.clientDataJSON.toBase64()))
                    put(FIELD_SIGNATURE, JsonPrimitive(response.signature.toBase64()))
                    response.userHandle?.let {
                        put(FIELD_USER_HANDLE, JsonPrimitive(it.toBase64()))
                    }
                })
            }

            return Result.success(assertionValue)
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            return Result.failure(e)
        }
    }

    /**
     * Unified authentication method that automatically selects the appropriate API.
     *
     * This method provides a single entry point for FIDO2 authentication that automatically
     * chooses between Credential Manager and Google Play Services based on configuration
     * and availability. It supports both discoverable and non-discoverable credential modes.
     *
     * **API Selection Logic:**
     * ```
     * if (config.useFido2Client) {
     *     // Use Google Play Services FIDO2
     *     // - Better for non-discoverable credentials
     *     // - Broader device compatibility
     *     // - Explicit credential descriptors required
     * } else {
     *     // Use Android Credential Manager
     *     // - Better for discoverable credentials
     *     // - Modern passkey experience
     *     // - Automatic credential discovery
     * }
     * ```
     *
     * **Customization Options:**
     * The block parameter allows customization of the authentication request:
     * ```kotlin
     * client.authenticate(options) {
     *     // For Google Play Services
     *     onPublicKeyCredentialRequestOptions { options ->
     *         options.toBuilder()
     *             .setTimeoutSeconds(30.0)
     *             .build()
     *     }
     *
     *     // For Credential Manager
     *     onGetPublicKeyCredentialOption { option ->
     *         GetPublicKeyCredentialOption(
     *             option.requestJson,
     *             preferImmediatelyAvailableCredentials = true
     *         )
     *     }
     * }
     * ```
     *
     * @param input The WebAuthn-compatible authentication options containing challenge,
     *             timeout, rpId, and optionally allowCredentials
     * @param block A customization function that allows modification of the request
     *             options before authentication. The block receives a [Fido2AuthenticateCustomizer]
     *             with methods to customize both API types.
     * @return A [Result] containing the assertion response on success, or exception on failure.
     *         The response format is consistent regardless of underlying API used.
     */
    suspend fun authenticate(
        input: JsonObject,
        block: Fido2AuthenticateCustomizer.() -> Unit = {}
    ): Result<JsonObject> {
        try {
            val customizer = Fido2AuthenticateCustomizer().apply(block)

            if (config.useFido2Client) {
                val publicKeyCredentialRequestOptions = PublicKeyCredentialRequestOptions.Builder()
                    .setAllowList(getAllowCredentials(input))
                    .setRpId(input[Constants.FIELD_RP_ID]?.jsonPrimitive?.content ?: "")
                    .setChallenge(
                        input[Constants.FIELD_CHALLENGE]?.jsonPrimitive?.content?.urlSafeDecode()
                            ?: byteArrayOf()
                    )
                    .setTimeoutSeconds(
                        (input[Constants.FIELD_TIMEOUT]?.jsonPrimitive?.double
                            ?: Constants.DEFAULT_TIMEOUT) / 1000
                    ) // Convert milliseconds to seconds
                    .build()
                return authenticate(
                    customizer.requestOptionsCustomizer(
                        publicKeyCredentialRequestOptions
                    )
                )
            } else {
                // Create a basic GetPublicKeyCredentialOption that will be customized
                return authenticate(
                    customizer.getOptionCustomizer(
                        GetPublicKeyCredentialOption(input.toString())
                    )
                )
            }
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            return Result.failure(e)
        }
    }

    /**
     * Extracts and processes the allowed credentials list from authentication options.
     *
     * This method converts WebAuthn credential descriptors from the input JSON to
     * Google Play Services PublicKeyCredentialDescriptor objects. It handles the
     * transformation of Base64-encoded credential IDs and sets appropriate transport methods.
     *
     * **Input Format Expected:**
     * ```json
     * {
     *   "allowCredentials": [
     *     {
     *       "type": "public-key",
     *       "id": "base64-encoded-credential-id"
     *     }
     *   ]
     * }
     * ```
     *
     * **Processing Steps:**
     * 1. Extract allowCredentials array from input
     * 2. Convert each credential descriptor
     * 3. Decode Base64 credential IDs to byte arrays
     * 4. Set transport methods (INTERNAL for platform authenticators)
     * 5. Return list of PublicKeyCredentialDescriptor objects
     *
     * @param input The WebAuthn options containing allowCredentials array
     * @return List of PublicKeyCredentialDescriptor objects for Google Play Services API
     */
    private fun getAllowCredentials(input: JsonObject): List<PublicKeyCredentialDescriptor> {
        // Extract the allowed credentials array, defaulting to empty if not present
        var allowCredentials = JsonArray(emptyList())
        if (input.containsKey(FIELD_ALLOW_CREDENTIALS)) {
            allowCredentials =
                input[FIELD_ALLOW_CREDENTIALS]?.jsonArray ?: JsonArray(emptyList())
        }
        return getCredentials(allowCredentials)
    }

    /**
     * Converts JSON credential descriptors to Google Play Services format.
     *
     * This helper method processes individual credential descriptors from the allowCredentials
     * array and converts them to the format required by Google Play Services FIDO2 API.
     *
     * **Transformation Details:**
     * - Credential type: "public-key" (standard WebAuthn type)
     * - Credential ID: Base64 string → byte array
     * - Transport methods: Set to [Transport.INTERNAL] for platform authenticators
     *
     * **Error Handling:**
     * - Silently skips malformed credential descriptors
     * - Continues processing remaining valid credentials
     * - Returns empty list if no valid credentials found
     *
     * @param credentials JsonArray containing credential descriptor objects
     * @return List of PublicKeyCredentialDescriptor objects for Google Play Services
     */
    private fun getCredentials(credentials: JsonArray): List<PublicKeyCredentialDescriptor> {
        val result = mutableListOf<PublicKeyCredentialDescriptor>()
        for (element in credentials) {
            val excludeCredential = element.jsonObject

            // Extract credential type, skip if missing
            val type = excludeCredential[FIELD_TYPE]?.jsonPrimitive?.content ?: continue

            // Extract credential ID as integer array, skip if missing
            val id = excludeCredential[FIELD_ID]?.jsonPrimitive?.content ?: continue

            // Create descriptor with INTERNAL transport (platform authenticator)
            val descriptor =
                PublicKeyCredentialDescriptor(
                    PublicKeyCredentialType.fromString(type).toString(),
                    id.urlSafeDecode(),
                    listOf(Transport.INTERNAL)
                )
            result.add(descriptor)
        }
        return result
    }

    companion object {
        /**
         * Factory method to create a Fido2Client with customizable configuration.
         *
         * This factory method provides a convenient way to create and configure a Fido2Client
         * instance using a DSL-style configuration block. The configuration allows customization
         * of logging, API selection, and other client behaviors.
         *
         * **Usage Examples:**
         * ```kotlin
         * // Default configuration
         * val client = Fido2Client()
         *
         * // Custom logger
         * val client = Fido2Client {
         *     logger = Logger.CONSOLE
         * }
         *
         * // Force Google Play Services
         * val client = Fido2Client {
         *     useFido2Client = true
         * }
         *
         * // Complete customization
         * val client = Fido2Client {
         *     logger = customLogger
         *     useFido2Client = shouldUsePlayServices
         * }
         * ```
         *
         * @param block Configuration lambda that receives a [Fido2ClientConfig] instance
         *             for customization. Defaults to empty configuration.
         * @return A configured Fido2Client instance ready for use
         */
        operator fun invoke(block: Fido2ClientConfig.() -> Unit = {}): Fido2Client {
            val config = Fido2ClientConfig()
            config.apply(block)
            return Fido2Client(config)
        }
    }
}

/**
 * Customizer for FIDO2 authentication requests.
 *
 * This class provides customization hooks for both Google Play Services and Credential Manager
 * authentication requests. It allows fine-tuning of request options before the authentication
 * ceremony begins.
 *
 * **Usage in Authentication:**
 * ```kotlin
 * client.authenticate(options) {
 *     onPublicKeyCredentialRequestOptions { options ->
 *         // Customize Google Play Services request
 *         options.toBuilder()
 *             .setTimeoutSeconds(30.0)
 *             .build()
 *     }
 *
 *     onGetPublicKeyCredentialOption { option ->
 *         // Customize Credential Manager request
 *         GetPublicKeyCredentialOption(
 *             option.requestJson,
 *             preferImmediatelyAvailableCredentials = true
 *         )
 *     }
 * }
 * ```
 */

class Fido2RegistrationCustomizer {
    internal var customizer: (CreatePublicKeyCredentialRequest) -> CreatePublicKeyCredentialRequest =
        { it }

    fun onCreatePublicKeyCredentialRequest(block: (CreatePublicKeyCredentialRequest) -> CreatePublicKeyCredentialRequest) {
        customizer = block
    }
}

class Fido2AuthenticateCustomizer {
    internal var requestOptionsCustomizer: (PublicKeyCredentialRequestOptions) -> PublicKeyCredentialRequestOptions =
        { it }
    internal var getOptionCustomizer: (GetPublicKeyCredentialOption) -> GetPublicKeyCredentialOption =
        { it }

    /**
     * Customizes Google Play Services FIDO2 request options.
     *
     * This method allows modification of PublicKeyCredentialRequestOptions before
     * they are passed to the Google Play Services FIDO2 API. Common customizations
     * include timeout adjustments, extension settings, and transport preferences.
     *
     * **Common Customizations:**
     * ```kotlin
     * onPublicKeyCredentialRequestOptions { options ->
     *    PublicKeyCredentialRequestOptions.Builder()
     *         .setTimeoutSeconds(60.0)          // Extend timeout
     *         .setUserVerification("required")   // Force user verification
     *         .build()
     * }
     * ```
     *
     * @param block Transformation function that receives the original options
     *             and returns modified options for the authentication request
     */
    fun onPublicKeyCredentialRequestOptions(block: (PublicKeyCredentialRequestOptions) -> PublicKeyCredentialRequestOptions) {
        requestOptionsCustomizer = block
    }

    /**
     * Customizes Android Credential Manager request options.
     *
     * This method allows modification of GetPublicKeyCredentialOption before
     * it is passed to the Credential Manager API. This is useful for setting
     * preferences like immediate credential availability or request priorities.
     *
     * **Common Customizations:**
     * ```kotlin
     * onGetPublicKeyCredentialOption { option ->
     *     GetPublicKeyCredentialOption(
     *         option.requestJson,
     *         preferImmediatelyAvailableCredentials = true,
     *         isAutoSelectAllowed = false
     *     )
     * }
     * ```
     *
     * @param block Transformation function that receives the original option
     *             and returns modified option for the authentication request
     */
    fun onGetPublicKeyCredentialOption(block: (GetPublicKeyCredentialOption) -> GetPublicKeyCredentialOption) {
        getOptionCustomizer = block
    }
}

/**
 * Configuration class for Fido2Client instances.
 *
 * This class contains all configurable options for the Fido2Client, including
 * logging settings and API selection preferences. It uses the @PingDsl annotation
 * to provide a type-safe DSL for configuration.
 *
 * **Configuration Options:**
 * - **logger**: Controls logging output and verbosity
 * - **useFido2Client**: Determines API selection strategy
 *
 * **API Selection Logic:**
 * The `useFido2Client` property is automatically initialized based on runtime
 * detection of Google Play Services FIDO availability, but can be overridden
 * for testing or specific deployment requirements.
 */
@PingDsl
class Fido2ClientConfig {
    /**
     * Logger instance for debugging and monitoring FIDO2 operations.
     *
     * The logger is used throughout the FIDO2 client to provide detailed
     * information about authentication flows, errors, and performance metrics.
     *
     * **Default**: Uses the global Logger.logger instance
     * **Options**:
     * - Logger.CONSOLE for console output
     * - Logger.NONE for no logging
     * - Custom Logger implementations
     */
    var logger: Logger = Logger.logger

    /**
     * Determines whether to use Google Play Services FIDO2 API.
     *
     * When true, the client prefers Google Play Services FIDO2 API for broader
     * device compatibility. When false, it uses Android Credential Manager API
     * for modern passkey experiences.
     *
     * **Auto-detection Logic:**
     * The default value is determined by runtime detection of the
     * `com.google.android.gms.fido.Fido` class. If the class is found,
     * Google Play Services is assumed to be available.
     *
     * **Manual Override:**
     * This can be manually set to force a specific API choice:
     * ```kotlin
     * Fido2Client {
     *     useFido2Client = true  // Force Google Play Services
     * }
     * ```
     *
     * **Default**: Auto-detected based on Google Play Services availability
     */
    var useFido2Client = try {
        Class.forName("com.google.android.gms.fido.Fido")
        true
    } catch (e: ClassNotFoundException) {
        false
    }

}
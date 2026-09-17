/*
 * Copyright (c) 2025 - 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.fido

import android.os.Build
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import com.google.android.gms.fido.common.Transport
import com.google.android.gms.fido.fido2.api.common.AuthenticatorAssertionResponse
import com.google.android.gms.fido.fido2.api.common.PublicKeyCredentialDescriptor
import com.google.android.gms.fido.fido2.api.common.PublicKeyCredentialRequestOptions
import com.google.android.gms.fido.fido2.api.common.PublicKeyCredentialType
import com.pingidentity.android.ContextProvider
import com.pingidentity.fido.Constants.FIELD_ALLOW_CREDENTIALS
import com.pingidentity.fido.Constants.FIELD_AUTHENTICATOR_ATTACHMENT
import com.pingidentity.fido.Constants.FIELD_AUTHENTICATOR_DATA
import com.pingidentity.fido.Constants.FIELD_CLIENT_DATA_JSON
import com.pingidentity.fido.Constants.FIELD_ID
import com.pingidentity.fido.Constants.FIELD_RAW_ID
import com.pingidentity.fido.Constants.FIELD_RESPONSE
import com.pingidentity.fido.Constants.FIELD_SIGNATURE
import com.pingidentity.fido.Constants.FIELD_TYPE
import com.pingidentity.fido.Constants.FIELD_USER_HANDLE
import com.pingidentity.logger.Logger
import com.pingidentity.utils.PingDsl
import kotlinx.coroutines.currentCoroutineContext
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

/**
 * Core FIDO operations handler for Android applications.
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
 * val client = FidoClient()
 *
 * // Custom configuration
 * val client = FidoClient {
 *     logger = customLogger
 * }
 * ```
 *
 * @see <a href="https://developer.android.com/identity/sign-in/credential-manager">Android Credential Manager</a>
 * @see <a href="https://developers.google.com/identity/fido">Google Play Services FIDO2</a>
 */
class FidoClient(private val config: FidoClientConfig) {

    /**
     * Registers a new FIDO credential using Android Credential Manager.
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
    suspend fun register(input: JsonObject, block: FidoRegistrationCustomizer.() -> Unit = {}): Result<JsonObject> {
        val customizer = FidoRegistrationCustomizer().apply(block)

        try {
            val credentialManager = CredentialManager.create(ContextProvider.context)
            val credentialRequest = customizer.customizer(CreatePublicKeyCredentialRequest(input.toString()))

            val result = credentialManager.createCredential(
                context = ContextProvider.currentActivity,
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
            currentCoroutineContext().ensureActive()
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
     * @param customizer The per-call customizer supplying the [GetCredentialRequest]
     *                        transformation
     * @return A [Result] containing the assertion response as a [JsonObject] on success,
     *         or an exception on failure
     */
    private suspend fun authenticate(
        credentialOption: GetPublicKeyCredentialOption,
        customizer: FidoAuthenticateCustomizer
    ): Result<JsonObject> {
        val credentialManager = CredentialManager.create(ContextProvider.context)
        val credentialRequest =
            customizer.getCredentialRequestCustomizer(GetCredentialRequest(listOf(credentialOption)))
        try {
            val result = credentialManager.getCredential(
                context = ContextProvider.currentActivity,
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
            currentCoroutineContext().ensureActive()
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
            currentCoroutineContext().ensureActive()
            return Result.failure(e)
        }
    }

    /**
     * Unified authentication method that automatically selects the appropriate API.
     *
     * This method provides a single entry point for FIDO2 authentication that automatically
     * chooses between Credential Manager and Google Play Services based on the
     * [FidoAuthenticateCustomizer.useFido2ApiClient] setting on the `authenticate { }` block
     * (default: auto-detected — Google Play Services when GMS is present, Credential
     * Manager otherwise). It supports both discoverable and
     * non-discoverable credential modes.
     *
     * **API Selection Logic:**
     * ```
     * if (customizer.useFido2ApiClient) {
     *     // Use Google Play Services FIDO2
     *     // - Better for non-discoverable credentials
     *     // - Broader device compatibility
     *     // - Explicit credential descriptors required
     * } else {
     *     // Use Android Credential Manager
     *     // - Better for discoverable credentials
     *     // - Modern passkey experience
     *     // - Automatic credential discovery
     *     // - Required for conditional mediation (autofill with passkeys)
     * }
     * ```
     *
     * **Customization Options:**
     * The block parameter allows customization of the authentication request:
     * ```kotlin
     * client.authenticate(options) {
     *     // Optional: opt into Google Play Services for this call
     *     useFido2ApiClient = true // For non-discoverable (device-bound) credentials
     *
     *     // For Google Play Services
     *     onPublicKeyCredentialRequestOptions { options ->
     *         PublicKeyCredentialRequestOptions.Builder()
     *             .setRpId(options.rpId)
     *             .setChallenge(options.challenge)
     *             .setAllowList(options.allowList)
     *             .setTimeoutSeconds(30.0)
     *             .build()
     *     }
     *
     *     // For Credential Manager
     *     onGetPublicKeyCredentialOption { option ->
     *         GetPublicKeyCredentialOption(option.requestJson)
     *     }
     *
     *     // For Credential Manager: request-level preferences such as
     *     // preferImmediatelyAvailableCredentials (moved off GetPublicKeyCredentialOption
     *     // in androidx.credentials 1.5.0)
     *     onGetCredentialRequest { request ->
     *         GetCredentialRequest(
     *             request.credentialOptions,
     *             preferImmediatelyAvailableCredentials = true
     *         )
     *     }
     * }
     * ```
     *
     * @param input The WebAuthn-compatible authentication options containing challenge,
     *             timeout, rpId, and optionally allowCredentials
     * @param block A customization function that allows modification of the request
     *             options before authentication. The block receives a [FidoAuthenticateCustomizer]
     *             with methods to customize both API types.
     * @return A [Result] containing the assertion response on success, or exception on failure.
     *         The response format is consistent regardless of underlying API used.
     */
    suspend fun authenticate(
        input: JsonObject,
        block: FidoAuthenticateCustomizer.() -> Unit = {}
    ): Result<JsonObject> {
        try {
            val customizer = FidoAuthenticateCustomizer().apply(block)

            if (customizer.useFido2ApiClient) {
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
                    ),
                    customizer
                )
            }
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            return Result.failure(e)
        }
    }

    /**
     * Builds a pending (View-attachable) authentication request for conditional mediation
     * (autofill with passkeys), always routed through the Android Credential Manager.
     *
     * Unlike [authenticate], which runs the modal ceremony to completion, this method returns
     * a [FidoPendingAuthentication] whose [FidoPendingAuthentication.request] must be attached
     * to a View (`view.pendingGetCredentialRequest = pending.request`). The request is
     * exercised when the user focuses that View; the assertion is delivered to
     * [FidoPendingAuthentication.await].
     *
     * **Routing:** conditional mediation exists only on the Credential Manager path (the
     * Google Play Services FIDO2 API has no conditional surface), so this call always uses
     * Credential Manager. When the resolved routing — [FidoAuthenticateCustomizer.useFido2ApiClient]
     * on the block, which auto-detects GMS by default — would have selected Google Play
     * Services, a warning is logged and the call proceeds on Credential Manager anyway; a
     * pending request routed to GMS would silently never autofill.
     *
     * **Feature gate:** fails fast with `Result.failure(GetCredentialUnsupportedException)`
     * when the device cannot deliver pending requests — the same OS gate the androidx View
     * handler applies (`SDK_INT >= 35`, or API 34 with a preview SDK). Consult
     * [isConditionalMediationSupported] before calling to hide the affordance up front.
     *
     * **API 33 and older:** conditional mediation is only delivered on API 35+, but on older
     * devices the Credential Manager path still routes through the
     * `credentials-play-services-auth` bridge. When that bridge is not bundled the request can
     * never be served, so a warning is logged (not an exception — the ceremony itself fails
     * with an exception the existing error mapping already understands).
     *
     * **Error contract:** like the modal path, errors during the ceremony are not delivered
     * through this method — androidx propagates none for pending requests. Only a final
     * response, if any, reaches [FidoPendingAuthentication.await]; a dismissed suggestion
     * sheet leaves it suspended (documented there) with the modal fallback as the recovery.
     *
     * **Customization:** the block is applied with the same chain as the modal Credential
     * Manager path — [FidoAuthenticateCustomizer.onGetPublicKeyCredentialOption] customizes the
     * credential option, [FidoAuthenticateCustomizer.onGetCredentialRequest] customizes the
     * wrapping [GetCredentialRequest] (e.g. `preferImmediatelyAvailableCredentials`).
     *
     * @param input The WebAuthn-compatible authentication options containing challenge,
     *             timeout, rpId, and optionally allowCredentials
     * @param block A customization function applied to the [GetCredentialRequest] before the
     *             pending request is built
     * @return A [Result] containing the [FidoPendingAuthentication] to attach to a View, or a
     *         `Result.failure` with a [GetCredentialUnsupportedException] when the OS gate is
     *         unmet (before any request object exists)
     */
    suspend fun pendingAuthenticate(
        input: JsonObject,
        block: FidoAuthenticateCustomizer.() -> Unit = {}
    ): Result<FidoPendingAuthentication> {
        try {
            val customizer = FidoAuthenticateCustomizer().apply(block)

            // Conditional mediation has no GMS surface: force the Credential Manager path. Warn
            // when the resolved routing (the customizer field, which auto-detects GMS) would
            // have picked Google Play Services, so deliberate pins are explainable in logs.
            if (customizer.useFido2ApiClient) {
                config.logger.w(
                    "pendingAuthenticate forces the Android Credential Manager API: conditional " +
                        "mediation (autofill with passkeys) is not available through the Google " +
                        "Play Services FIDO2 API selected by useFido2ApiClient. The pending " +
                        "request proceeds on Credential Manager."
                )
            }

            // The Credential Manager path on API <= 33 depends on the optional
            // credentials-play-services-auth bridge; without it the request can never resolve.
            // Probe for the bridge's public, version-stable Service and warn when absent.
            if (Build.VERSION.SDK_INT <= 33 && !isBridgePresent()) {
                config.logger.w(
                    "Conditional mediation on Android API <= 33 requires the " +
                        "androidx.credentials:credentials-play-services-auth dependency; it was " +
                        "not found on the classpath, so the pending credential request will not " +
                        "deliver suggestions. Add it to the app's dependencies."
                )
            }

            // Fast-fail before building any request object: without the OS gate the androidx
            // View handler accepts the tag but never delivers suggestions (SDK_INT < 35).
            if (!isConditionalMediationSupported) {
                return Result.failure(
                    GetCredentialUnsupportedException(
                        "Conditional mediation (pending credential request) requires Android 15 " +
                            "(API 35); this device reports API ${Build.VERSION.SDK_INT}"
                    )
                )
            }

            // Same option-build + customizer chain as the modal Credential Manager path
            // (authenticate(credentialOption, customizer)), but the GetCredentialRequest
            // escapes into a PendingGetCredentialRequest instead of driving the ceremony.
            val credentialRequest =
                customizer.getCredentialRequestCustomizer(
                    GetCredentialRequest(
                        listOf(
                            customizer.getOptionCustomizer(
                                GetPublicKeyCredentialOption(input.toString())
                            )
                        )
                    )
                )

            return Result.success(FidoPendingAuthentication(credentialRequest))
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            return Result.failure(e)
        }
    }

    /**
     * Checks whether the `credentials-play-services-auth` bridge is bundled by probing its
     * public, version-stable `CredentialProviderMetadataHolder` Service.
     *
     * @return true when the bridge class is resolvable on the current classloader
     */
    private fun isBridgePresent(): Boolean = try {
        Class.forName(BRIDGE_PROBE_CLASS)
        true
    } catch (e: ClassNotFoundException) {
        false
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
         * The bridge class probed to detect whether `credentials-play-services-auth` is
         * bundled. It is a public Service, verified identical in bridge 1.5.0 and 1.6.0; a
         * probe failure degrades to a missed warning, never a broken flow.
         */
        private const val BRIDGE_PROBE_CLASS =
            "androidx.credentials.playservices.CredentialProviderMetadataHolder"

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
         * ```
         *
         * @param block Configuration lambda that receives a [FidoClientConfig] instance
         *             for customization. Defaults to empty configuration.
         * @return A configured Fido2Client instance ready for use
         */
        operator fun invoke(block: FidoClientConfig.() -> Unit = {}): FidoClient {
            val config = FidoClientConfig()
            config.apply(block)
            return FidoClient(config)
        }
    }
}

/**
 * Customizes registration requests before the ceremony begins.
 *
 * This class provides a customization hook for the Google Play Services / Credential Manager
 * registration request. It allows fine-tuning of the request before the registration
 * ceremony begins.
 *
 * **Usage in Registration:**
 * ```kotlin
 * client.register(options) {
 *     onCreatePublicKeyCredentialRequest { request ->
 *         // Customize the registration request
 *         CreatePublicKeyCredentialRequest(
 *             request.requestJson,
 *             preferImmediatelyAvailableCredentials = true
 *         )
 *     }
 * }
 * ```
 */
@PingDsl
class FidoRegistrationCustomizer {
    internal var customizer: (CreatePublicKeyCredentialRequest) -> CreatePublicKeyCredentialRequest =
        { it }

    fun onCreatePublicKeyCredentialRequest(block: (CreatePublicKeyCredentialRequest) -> CreatePublicKeyCredentialRequest) {
        customizer = block
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
 *         PublicKeyCredentialRequestOptions.Builder()
 *             .setRpId(options.rpId)
 *             .setChallenge(options.challenge)
 *             .setAllowList(options.allowList)
 *             .setTimeoutSeconds(30.0)
 *             .build()
 *     }
 *
 *     onGetPublicKeyCredentialOption { option ->
 *         // Customize Credential Manager request
 *         GetPublicKeyCredentialOption(option.requestJson)
 *     }
 *
 *     onGetCredentialRequest { request ->
 *         // Customize the Credential Manager request itself (request-level preferences)
 *         GetCredentialRequest(
 *             request.credentialOptions,
 *             preferImmediatelyAvailableCredentials = true
 *         )
 *     }
 * }
 * ```
 */
@PingDsl
class FidoAuthenticateCustomizer {

    /**
     * The single source of truth for FIDO2 API selection.
     *
     * **Default**: auto-detected — `true` (Google Play Services FIDO2) when GMS is
     * present on the device, `false` (Android Credential Manager) otherwise. The
     * auto-detection preserves the routing behaviour existing apps see today.
     * Set `false` to route this call through the Android Credential Manager API
     * (required for conditional mediation / autofill with passkeys), or `true`
     * to explicitly use Google Play Services (required for non-discoverable,
     * device-bound credentials).
     *
     * **Common Customizations:**
     * ```kotlin
     * client.authenticate(options) {
     *     useFido2ApiClient = false // Force Credential Manager (e.g. for conditional mediation)
     * }
     * ```
     */
    var useFido2ApiClient: Boolean = try {
        Class.forName("com.google.android.gms.fido.Fido")
        true
    } catch (e: ClassNotFoundException) {
        false
    }

    internal var requestOptionsCustomizer: (PublicKeyCredentialRequestOptions) -> PublicKeyCredentialRequestOptions =
        { it }
    internal var getOptionCustomizer: (GetPublicKeyCredentialOption) -> GetPublicKeyCredentialOption =
        { it }
    internal var getCredentialRequestCustomizer: (GetCredentialRequest) -> GetCredentialRequest =
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
     * option-level preferences such as the client data hash or auto-select
     * behaviour.
     *
     * **Common Customizations:**
     * ```kotlin
     * onGetPublicKeyCredentialOption { option ->
     *     GetPublicKeyCredentialOption(
     *         option.requestJson,
     *         clientDataHash = option.clientDataHash,
     *         allowedProviders = setOf(ComponentName("com.example", "AuthenticatorService"))
     *     )
     * }
     * ```
     *
     * Note: `preferImmediatelyAvailableCredentials` is not a constructor parameter of
     * [GetPublicKeyCredentialOption] as of androidx.credentials 1.5.0 — it lives on
     * [GetCredentialRequest]. Use [onGetCredentialRequest] to set it.
     *
     * @param block Transformation function that receives the original option
     *             and returns modified option for the authentication request
     */
    fun onGetPublicKeyCredentialOption(block: (GetPublicKeyCredentialOption) -> GetPublicKeyCredentialOption) {
        getOptionCustomizer = block
    }

    /**
     * Customizes the Android Credential Manager [GetCredentialRequest] that wraps the
     * [GetPublicKeyCredentialOption].
     *
     * This hook gives access to request-level preferences that cannot be expressed on the
     * individual credential option. The most notable is
     * `preferImmediatelyAvailableCredentials`, which controls whether the ceremony returns
     * immediately when no locally available credential exists instead of falling back to
     * discovering remote (e.g. cross-device) options — a preference that moved from
     * `GetPublicKeyCredentialOption` to `GetCredentialRequest` in androidx.credentials 1.5.0.
     *
     * **Common Customizations:**
     * ```kotlin
     * onGetCredentialRequest { request ->
     *     GetCredentialRequest(
     *         request.credentialOptions,
     *         preferImmediatelyAvailableCredentials = true
     *     )
     * }
     * ```
     *
     * @param block Transformation function that receives the original request
     *             and returns modified request for the authentication ceremony
     */
    fun onGetCredentialRequest(block: (GetCredentialRequest) -> GetCredentialRequest) {
        getCredentialRequestCustomizer = block
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
 *
 * **API Selection Logic:**
 * The FIDO2 API used for each call is selected per request via
 * [FidoAuthenticateCustomizer.useFido2ApiClient] on the `authenticate { }` block.
 * The default is auto-detected at runtime: Google Play Services FIDO2 when GMS is
 * present (virtually every device with Play Services), Android Credential Manager
 * otherwise. Set `false` explicitly to select Credential Manager.
 */
@PingDsl
class FidoClientConfig {
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
}
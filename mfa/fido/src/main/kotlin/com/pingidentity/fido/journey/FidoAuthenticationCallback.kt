/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.fido.journey

import com.pingidentity.fido.Constants
import com.pingidentity.fido.Constants.AUTHENTICATOR_PLATFORM
import com.pingidentity.fido.Constants.DEFAULT_USER_VERIFICATION
import com.pingidentity.fido.Constants.FIELD_ALLOW_CREDENTIALS
import com.pingidentity.fido.Constants.FIELD_ALLOW_CREDENTIALS_INTERNAL
import com.pingidentity.fido.Constants.FIELD_AUTHENTICATOR_ATTACHMENT
import com.pingidentity.fido.Constants.FIELD_AUTHENTICATOR_DATA
import com.pingidentity.fido.Constants.FIELD_CHALLENGE
import com.pingidentity.fido.Constants.FIELD_CLIENT_DATA_JSON
import com.pingidentity.fido.Constants.FIELD_DATA
import com.pingidentity.fido.Constants.FIELD_ID
import com.pingidentity.fido.Constants.FIELD_RAW_ID
import com.pingidentity.fido.Constants.FIELD_RELYING_PARTY_ID_INTERNAL
import com.pingidentity.fido.Constants.FIELD_RESPONSE
import com.pingidentity.fido.Constants.FIELD_RP_ID
import com.pingidentity.fido.Constants.FIELD_SIGNATURE
import com.pingidentity.fido.Constants.FIELD_TIMEOUT
import com.pingidentity.fido.Constants.FIELD_TYPE
import com.pingidentity.fido.Constants.FIELD_USER_HANDLE
import com.pingidentity.fido.Constants.FIELD_USER_VERIFICATION
import com.pingidentity.fido.FidoAuthenticateCustomizer
import com.pingidentity.fido.FidoClient
import com.pingidentity.fido.base64DefaultToUrlSafe
import com.pingidentity.fido.base64ToIntStr
import com.pingidentity.fido.base64ToStr
import com.pingidentity.fido.toBase64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * FIDO2 authentication callback using Android Credential Manager for discoverable credentials.
 *
 * This callback implementation handles FIDO2 authentication using the Android Credential Manager API,
 * which provides support for discoverable credentials (passkeys) and cross-device synchronization.
 * This implementation is used as a fallback when Google Play Services FIDO2 API is not available on the device.
 *
 * **Key Features:**
 * - Supports discoverable credentials (passkeys)
 * - Cross-device credential synchronization (when supported by the device)
 * - Modern Android Credential Manager API integration
 * - Automatic user account selection from available passkeys
 * - No need for explicit allowCredentials list (discoverable)
 *
 * **Device Requirements:**
 * - Android 14+ for full Credential Manager support
 * - Fallback support on older Android versions with compatible credential providers
 * - Device must support platform authenticators or external security keys
 *
 * **API Selection Logic:**
 * This implementation is selected when:
 * - Google Play Services FIDO (`com.google.android.gms.fido.Fido`) class is not found at runtime
 * - The device supports Android Credential Manager
 * - Provides broader compatibility across different Android versions
 *
 * The callback processes authentication request options from the server, performs the authentication
 * using discoverable credentials, and formats the response according to Journey framework expectations.
 *
 * @see FidoAuthenticationCallback
 */
class FidoAuthenticationCallback : FidoCallback() {

    /**
     * The public key credential request options for FIDO2 authentication.
     *
     * This JsonObject contains all the parameters needed for the authentication ceremony
     * in WebAuthn-compatible format, including:
     * - challenge: Base64URL-encoded challenge from the server
     * - timeout: Maximum time allowed for the operation (in milliseconds)
     * - userVerification: Level of user verification required
     * - rpId: Relying party identifier
     * - allowCredentials: List of acceptable credentials (for non-discoverable mode)
     *
     * The options are transformed from Journey's internal format to standard WebAuthn format
     * during the init() process.
     */
    lateinit var publicKeyCredentialRequestOptions: JsonObject
        private set

    /**
     * Indicates whether the server supports JSON-formatted responses.
     *
     * When true, authentication responses are wrapped in a JSON object with metadata.
     * When false, responses use the legacy string format for backward compatibility.
     * This flag is automatically detected from the server's configuration during init().
     */
    private var supportsJsonResponse: Boolean = false

    /**
     * Initializes the callback with data from the Journey workflow.
     *
     * Parses the incoming JSON data and transforms it into WebAuthn-compatible format.
     * Also detects server capabilities like JSON response support.
     *
     * @param name The field name, expected to be "data"
     * @param value The JSON object containing FIDO2 authentication parameters
     * @throws IllegalArgumentException if name is not "data" or value is not a JsonObject
     */
    override fun init(name: String, value: JsonElement) {
        logger.d("Initializing FIDO2 FidoAuthenticationCallback")
        if (name == FIELD_DATA && value is JsonObject) {
            // Store the supportsJsonResponse flag before transformation
            supportsJsonResponse =
                value[Constants.FIELD_SUPPORTS_JSON_RESPONSE]?.jsonPrimitive?.content?.toBoolean()
                    ?: false
            publicKeyCredentialRequestOptions = transform(value)
        } else {
            throw IllegalArgumentException("Expected JsonObject for 'data', got ${value::class.simpleName}")
        }
    }

    /**
     * Performs FIDO2 authentication using the initialized request options.
     *
     * This method initiates the FIDO2 authentication ceremony using Android Credential Manager,
     * prompting the user to verify their identity using available discoverable credentials (passkeys).
     * The authentication can use biometrics, PIN, or other verification methods supported by the authenticator.
     *
     * **Authentication Flow:**
     * 1. Converts the publicKeyCredentialRequestOptions to GetPublicKeyCredentialOption
     * 2. Calls the Android Credential Manager to perform authentication
     * 3. Processes the returned credential response
     * 4. Formats the response according to server expectations
     * 5. Automatically submits the response to the Journey workflow
     *
     * **Response Format:**
     * The response is formatted differently based on the supportsJsonResponse flag:
     *
     * **Legacy String Format (supportsJsonResponse = false):**
     * ```
     * {"type":"webauthn.get","challenge":"test-challenge"}::dGVzdC1hdXRoZW50aWNhdG9yLWRhdGE::dGVzdC1zaWduYXR1cmU::dGVzdC1yYXctaWQ::dGVzdC11c2VyLWhhbmRsZQ
     * ```
     *
     * **JSON Format (supportsJsonResponse = true):**
     * ```json
     * {
     *   "authenticatorType": "PLATFORM",
     *   "response": "{\"type\":\"webauthn.get\",\"challenge\":\"test-challenge\"}::dGVzdC1hdXRoZW50aWNhdG9yLWRhdGE::dGVzdC1zaWduYXR1cmU::dGVzdC1yYXctaWQ::dGVzdC11c2VyLWhhbmRsZQ"
     * }
     * ```
     *
     * **Data Components (separated by "::"):**
     * 1. Client data JSON (contains challenge, origin, and type information)
     * 2. Authenticator data (Base64 encoded, then converted to integer string representation)
     * 3. Signature (Base64 encoded, then converted to integer string representation)
     * 4. Raw credential ID (Base64 encoded credential identifier)
     * 5. User handle (Base64 encoded user identifier, optional)
     *
     * @param block A transformation function that converts JsonObject to GetPublicKeyCredentialOption.
     *              Allows customization of credential manager options like preferImmediatelyAvailableCredentials.
     * @return A [Result] containing the authentication response as a [JsonObject] on success,
     *         or an exception on failure. The response is automatically submitted to the Journey workflow.
     */
    suspend fun authenticate(
        block: FidoAuthenticateCustomizer.() -> Unit = {}): Result<JsonObject> {
        return FidoClient {
            logger = this@FidoAuthenticationCallback.logger
        }.authenticate(
            publicKeyCredentialRequestOptions, block
        ).onSuccess { response ->
            // Extract the response object from the credential
            val authResponse =
                response[FIELD_RESPONSE]?.jsonObject ?: JsonObject(emptyMap())

            // Build the response data string with components separated by "::"
            val data = listOf(
                // Client data JSON - contains challenge, origin, and type
                authResponse[FIELD_CLIENT_DATA_JSON]?.jsonPrimitive?.content?.base64ToStr()
                    ?: "",
                // Authenticator data - cryptographic proof from the authenticator
                authResponse[FIELD_AUTHENTICATOR_DATA]?.jsonPrimitive?.content?.base64ToIntStr()
                    ?: "",
                // Signature - cryptographic signature over the client data and authenticator data
                authResponse[FIELD_SIGNATURE]?.jsonPrimitive?.content?.base64ToIntStr()
                    ?: "",
                // Raw credential ID - unique identifier for the credential
                response[FIELD_RAW_ID]?.jsonPrimitive?.content ?: "",
                // User handle - optional user identifier (may be empty)
                authResponse[FIELD_USER_HANDLE]?.jsonPrimitive?.content?.base64ToStr() ?: ""
            ).joinToString(Constants.DATA_SEPARATOR)

            // Format the response based on server capabilities
            val callbackValue = if (supportsJsonResponse) {
                // New JSON format with metadata
                Json.encodeToString(
                    FidoJsonResponse(
                        response[FIELD_AUTHENTICATOR_ATTACHMENT]?.jsonPrimitive?.content
                            ?: AUTHENTICATOR_PLATFORM, data
                    )
                )
            } else {
                // Legacy string format for backward compatibility
                data
            }

            // Submit the response to the Journey workflow
            valueCallback(callbackValue)
        }.onFailure {
            // Handle authentication errors and update the Journey workflow
            handleError(it)
        }
    }

    /**
     * Transforms Journey-specific JSON format to standard WebAuthn format.
     *
     * Converts the authentication request from Journey's internal representation to the
     * format expected by the Android Credential Manager API. This transformation includes:
     * - Converting Base64 standard encoding to Base64URL encoding for the challenge
     * - Parsing timeout string to integer milliseconds
     * - Extracting relying party ID from internal field
     * - Converting byte array credential IDs to Base64 strings
     * - Setting appropriate user verification requirements
     *
     * **Input Format (Journey Internal):**
     * ```json
     * {
     *   "_action": "webauthn_authentication",
     *   "challenge": "IrmRP2U3shw3plwrICzAkw/yupRI60s2dnGhfwExd/o=",
     *   "allowCredentials": "",
     *   "_allowCredentials": [
     *     {
     *       "type": "public-key",
     *       "id": [-26, -52, 96, 28, 18, -70, -54, -114, 41, -46, -27, 45, -87, -125, 111, -36]
     *     }
     *   ],
     *   "timeout": "60000",
     *   "userVerification": "required",
     *   "_relyingPartyId": "idc.petrov.ca",
     *   "supportsJsonResponse": true
     * }
     * ```
     *
     * **Output Format (WebAuthn Compatible):**
     * ```json
     * {
     *   "challenge": "IrmRP2U3shw3plwrICzAkw_yupRI60s2dnGhfwExd_o",
     *   "timeout": 60000,
     *   "userVerification": "required",
     *   "rpId": "idc.petrov.ca",
     *   "allowCredentials": [
     *     {
     *       "type": "public-key",
     *       "id": "5sxgHBK6yo4p0uctqYNv3A=="
     *     }
     *   ]
     * }
     * ```
     *
     * **Field Mappings:**
     * - `challenge`: Standard Base64 → Base64URL encoding
     * - `timeout`: String → Integer (milliseconds)
     * - `_relyingPartyId` → `rpId`: Direct mapping
     * - `_allowCredentials` → `allowCredentials`: Byte arrays → Base64 strings
     * - `userVerification`: Direct mapping with fallback to default
     *
     * @param input The Journey-formatted JSON object
     * @return WebAuthn-compatible JSON object for Credential Manager
     */
    private fun transform(input: JsonObject): JsonObject {
        return buildJsonObject {
            // Convert challenge from Base64 standard to Base64URL encoding
            put(
                FIELD_CHALLENGE,
                input[FIELD_CHALLENGE]?.jsonPrimitive?.content?.base64DefaultToUrlSafe()
                    ?: ""
            )

            // Parse timeout string to integer, with fallback to default
            put(
                FIELD_TIMEOUT,
                input[FIELD_TIMEOUT]?.jsonPrimitive?.content?.toIntOrNull()
                    ?: Constants.DEFAULT_TIMEOUT
            )

            // Set user verification requirement with fallback to default
            put(
                FIELD_USER_VERIFICATION,
                input[FIELD_USER_VERIFICATION]?.jsonPrimitive?.content
                    ?: DEFAULT_USER_VERIFICATION
            )

            // Extract relying party ID from internal field
            put(
                FIELD_RP_ID,
                input[FIELD_RELYING_PARTY_ID_INTERNAL]?.jsonPrimitive?.content ?: ""
            )

            // Transform allowed credentials from byte arrays to Base64 strings
            putJsonArray(FIELD_ALLOW_CREDENTIALS) {
                input[FIELD_ALLOW_CREDENTIALS_INTERNAL]?.jsonArray?.forEach { element ->
                    element.jsonObject.let { credential ->
                        val type = credential[FIELD_TYPE]?.jsonPrimitive?.content
                        val idArray = credential[FIELD_ID]?.jsonArray

                        if (type != null && idArray != null) {
                            addJsonObject {
                                put(FIELD_TYPE, type)
                                // Convert byte array to Base64 string
                                val byteArray = ByteArray(idArray.size) { i ->
                                    idArray[i].jsonPrimitive.int.toByte()
                                }
                                put(FIELD_ID, JsonPrimitive(byteArray.toBase64()))
                            }
                        }
                    }
                }
            }
        }
    }
}
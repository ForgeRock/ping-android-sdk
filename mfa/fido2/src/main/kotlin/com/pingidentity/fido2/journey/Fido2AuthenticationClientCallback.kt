/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.fido2.journey

import com.google.android.gms.fido.common.Transport
import com.google.android.gms.fido.fido2.api.common.PublicKeyCredentialDescriptor
import com.google.android.gms.fido.fido2.api.common.PublicKeyCredentialRequestOptions
import com.google.android.gms.fido.fido2.api.common.PublicKeyCredentialType
import com.pingidentity.fido2.Constants
import com.pingidentity.fido2.Constants.AUTHENTICATOR_PLATFORM
import com.pingidentity.fido2.Constants.FIELD_ALLOW_CREDENTIALS_INTERNAL
import com.pingidentity.fido2.Constants.FIELD_AUTHENTICATOR_ATTACHMENT
import com.pingidentity.fido2.Fido2Client
import com.pingidentity.fido2.base64Default
import com.pingidentity.fido2.base64ToIntStr
import com.pingidentity.fido2.base64ToStr
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * FIDO2 authentication callback using Google Play Services for non-discoverable credentials.
 *
 * This callback implementation handles FIDO2 authentication using the Google Play Services FIDO2 API,
 * which provides broader device compatibility for device-bound (non-discoverable) credentials.
 * This implementation is preferred when Google Play Services FIDO is available on the device.
 *
 * **Key Features:**
 * - Device-bound (non-discoverable) credentials
 * - Broad device compatibility (Android 7+)
 * - Established Google Play Services integration
 * - Support for security keys and platform authenticators
 * - Requires explicit allowCredentials list for authentication
 *
 * **Device Requirements:**
 * - Android 7+ (API 24+)
 * - Google Play Services with FIDO2 support
 * - Compatible authenticator (built-in or external)
 *
 * **API Selection Logic:**
 * This implementation is selected when:
 * - Google Play Services FIDO (`com.google.android.gms.fido.Fido`) class is found at runtime
 * - Provides fallback for devices without full Credential Manager support
 * - Ensures broader compatibility across Android ecosystem
 *
 * The callback processes authentication request options from the server, performs the authentication
 * using device-bound credentials, and formats the response for the Journey framework. It uses a
 * transparent activity to handle the FIDO2 user interaction flow.
 *
 * @see Fido2AuthenticationCallback
 * @see Fido2AuthenticationCredentialCallback
 */
open class Fido2AuthenticationClientCallback : Fido2Callback(),
    Fido2AuthenticationCallback {

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
     * Parses the incoming JSON data and transforms it into Google Play Services FIDO2 format.
     * Also detects server capabilities like JSON response support.
     *
     * @param name The field name, expected to be "data"
     * @param value The JSON object containing FIDO2 authentication parameters
     * @throws IllegalArgumentException if name is not "data" or value is not a JsonObject
     */
    override fun init(name: String, value: JsonElement) {
        logger.d("Initializing FIDO2 Fido2AuthenticationClientCallback")
        if (name == Constants.FIELD_DATA && value is JsonObject) {
            supportsJsonResponse = value[Constants.FIELD_SUPPORTS_JSON_RESPONSE]?.jsonPrimitive?.content?.toBoolean() ?: false
            publicKeyCredentialRequestOptions = transform(value)
        } else {
            throw IllegalArgumentException("Expected JsonObject for 'data', got ${value::class.simpleName}")
        }
    }

    /**
     * Performs FIDO2 authentication using Google Play Services.
     *
     * This method initiates the FIDO2 authentication ceremony using Google Play Services FIDO2 API,
     * prompting the user to verify their identity using device-bound credentials. The authentication
     * can use biometrics, PIN, or other verification methods supported by the authenticator.
     *
     * **Authentication Flow:**
     * 1. Applies any custom transformations to the publicKeyCredentialRequestOptions
     * 2. Calls Google Play Services FIDO2 API to perform authentication
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
     *   "response": "{"type":"webauthn.get","challenge":"test-challenge"}::dGVzdC1hdXRoZW50aWNhdG9yLWRhdGE::dGVzdC1zaWduYXR1cmU::dGVzdC1yYXctaWQ::dGVzdC11c2VyLWhhbmRsZQ"
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
     * @param block A transformation function that allows customization of the request options.
     *              Can be used to modify timeout, add extensions, or adjust other parameters.
     * @return A [Result] containing the authentication response as a [JsonObject] on success,
     *         or an exception on failure. The response is automatically submitted to the Journey workflow.
     */
    suspend fun authenticate(
        block: (PublicKeyCredentialRequestOptions) -> PublicKeyCredentialRequestOptions = {
            publicKeyCredentialRequestOptions
        }
    ): Result<JsonObject> {

        return Fido2Client.authenticate(block(publicKeyCredentialRequestOptions)).onSuccess { response ->
            val authResponse =
                response[Constants.FIELD_RESPONSE]?.jsonObject ?: JsonObject(emptyMap())
            val data = listOf(
                authResponse[Constants.FIELD_CLIENT_DATA_JSON]?.jsonPrimitive?.content?.base64ToStr()
                    ?: "",
                authResponse[Constants.FIELD_AUTHENTICATOR_DATA]?.jsonPrimitive?.content?.base64ToIntStr()
                    ?: "",
                authResponse[Constants.FIELD_SIGNATURE]?.jsonPrimitive?.content?.base64ToIntStr()
                    ?: "",
                response[Constants.FIELD_RAW_ID]?.jsonPrimitive?.content ?: "",
                authResponse[Constants.FIELD_USER_HANDLE]?.jsonPrimitive?.content?.base64ToStr() ?: ""
            ).joinToString(Constants.DATA_SEPARATOR)

            val callbackValue = if (supportsJsonResponse) {
                Json.encodeToString(
                    Fido2JsonResponse(
                        response[FIELD_AUTHENTICATOR_ATTACHMENT]?.jsonPrimitive?.content
                            ?: AUTHENTICATOR_PLATFORM, data
                    ))
            } else {
                data
            }
            valueCallback(callbackValue)
        }.onFailure {
            handleError(it)
        }
    }

    /**
     * Transforms Journey-specific JSON format to Google Play Services FIDO2 format.
     *
     * Converts the authentication request from Journey's internal representation to the
     * format expected by the Google Play Services FIDO2 API. This transformation includes:
     * - Converting Base64 challenge string to binary data
     * - Parsing timeout from milliseconds string to seconds double
     * - Extracting relying party ID from internal field
     * - Converting credential descriptors from byte arrays to PublicKeyCredentialDescriptor objects
     * - Setting appropriate transport methods and credential types
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
     *   "_relyingPartyId": "idc.petrov.ca",
     *   "supportsJsonResponse": true
     * }
     * ```
     *
     * **Output Format (Google Play Services Compatible):**
     * - challenge: Binary byte array
     * - timeout: Double value in seconds
     * - rpId: String identifier
     * - allowList: List of PublicKeyCredentialDescriptor objects
     *
     * **Field Mappings:**
     * - `challenge`: Base64 string → Binary byte array
     * - `timeout`: String milliseconds → Double seconds
     * - `_relyingPartyId` → `rpId`: Direct mapping
     * - `_allowCredentials` → `allowList`: Byte arrays → PublicKeyCredentialDescriptor objects
     *
     * @param input The Journey-formatted JSON object
     * @return Google Play Services FIDO2 PublicKeyCredentialRequestOptions object
     */
    private fun transform(input: JsonObject): PublicKeyCredentialRequestOptions {
        // Extract and decode the challenge from Base64 to binary data
        val challenge =
            input[Constants.FIELD_CHALLENGE]?.jsonPrimitive?.content?.base64Default()
                ?: ByteArray(0)

        // Extract relying party ID from internal field
        val relayingPartyId = input[Constants.FIELD_RELYING_PARTY_ID_INTERNAL]?.jsonPrimitive?.content ?: ""

        // Convert timeout from milliseconds string to seconds double
        val timeout = (input[Constants.FIELD_TIMEOUT]?.jsonPrimitive?.doubleOrNull
            ?: Constants.DEFAULT_TIMEOUT)

        // Transform allowed credentials from JSON array to credential descriptors
        val allowCredentials = getAllowCredentials(input)

        // Build the Google Play Services request options
        publicKeyCredentialRequestOptions = PublicKeyCredentialRequestOptions.Builder()
            .setAllowList(allowCredentials)
            .setRpId(relayingPartyId)
            .setChallenge(challenge)
            .setTimeoutSeconds(timeout / 1000) // Convert milliseconds to seconds
            .build()

        return publicKeyCredentialRequestOptions
    }

    /**
     * Extracts and processes the allowed credentials list from the input JSON.
     *
     * This method looks for the `_allowCredentials` field in the input JSON object
     * and converts it to a list of PublicKeyCredentialDescriptor objects that
     * Google Play Services can understand.
     *
     * @param value The input JSON object containing credential information
     * @return List of PublicKeyCredentialDescriptor objects for authentication
     */
    private fun getAllowCredentials(value: JsonObject): List<PublicKeyCredentialDescriptor> {
        // Extract the allowed credentials array, defaulting to empty if not present
        var allowCredentials = JsonArray(emptyList())
        if (value.containsKey(FIELD_ALLOW_CREDENTIALS_INTERNAL)) {
            allowCredentials =
                value[FIELD_ALLOW_CREDENTIALS_INTERNAL]?.jsonArray ?: JsonArray(emptyList())
        }
        return getCredentials(allowCredentials)
    }

    /**
     * Converts JSON credential descriptors to Google Play Services PublicKeyCredentialDescriptor objects.
     *
     * This method processes each credential descriptor in the JSON array and converts it to the
     * format required by Google Play Services FIDO2 API. Each descriptor includes:
     * - Credential type (typically "public-key")
     * - Credential ID as a byte array
     * - Transport methods (set to INTERNAL for platform authenticators)
     *
     * **Input Format:**
     * ```json
     * [
     *   {
     *     "type": "public-key",
     *     "id": [-26, -52, 96, 28, 18, -70, -54, -114, 41, -46, -27, 45, -87, -125, 111, -36]
     *   }
     * ]
     * ```
     *
     * **Output:**
     * List of PublicKeyCredentialDescriptor objects with:
     * - type: "public-key"
     * - id: Binary credential identifier
     * - transports: [Transport.INTERNAL]
     *
     * @param credentials JsonArray containing credential descriptor objects
     * @return List of PublicKeyCredentialDescriptor objects for Google Play Services
     */
    private fun getCredentials(credentials: JsonArray): List<PublicKeyCredentialDescriptor> {
        val result = mutableListOf<PublicKeyCredentialDescriptor>()
        for (element in credentials) {
            val excludeCredential = element.jsonObject

            // Extract credential type, skip if missing
            val type = excludeCredential["type"]?.jsonPrimitive?.content ?: continue

            // Extract credential ID as integer array, skip if missing
            val idArray = excludeCredential["id"]?.jsonArray ?: continue

            // Convert integer array to byte array
            val bytes = ByteArray(idArray.size)
            for ((j, idElement) in idArray.withIndex()) {
                bytes[j] = idElement.jsonPrimitive.int.toByte()
            }

            // Create descriptor with INTERNAL transport (platform authenticator)
            val descriptor =
                PublicKeyCredentialDescriptor(
                    PublicKeyCredentialType.fromString(type).toString(),
                    bytes,
                    listOf(Transport.INTERNAL)
                )
            result.add(descriptor)
        }
        return result
    }

    /**
     * Performs FIDO2 authentication with default options.
     *
     * This is the interface method implementation that provides a simple authentication
     * call without custom transformation options. It uses the publicKeyCredentialRequestOptions
     * as-is for the Google Play Services FIDO2 call.
     *
     * @return A [Result] containing the authentication response on success, or an exception on failure
     */
    override suspend fun authenticate(): Result<JsonObject> = authenticate {
        it
    }
}
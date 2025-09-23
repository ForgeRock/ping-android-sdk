/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.fido2.journey

import com.pingidentity.fido2.Constants
import com.pingidentity.fido2.Constants.DATA_SEPARATOR
import com.pingidentity.fido2.Constants.DEFAULT_ATTESTATION
import com.pingidentity.fido2.Constants.DEFAULT_RELYING_PARTY_ID
import com.pingidentity.fido2.Constants.DEFAULT_RESIDENT_KEY_REQUIRED
import com.pingidentity.fido2.Constants.DEFAULT_TIMEOUT
import com.pingidentity.fido2.Constants.FIELD_ATTESTATION
import com.pingidentity.fido2.Constants.FIELD_ATTESTATION_OBJECT
import com.pingidentity.fido2.Constants.FIELD_ATTESTATION_PREFERENCE
import com.pingidentity.fido2.Constants.FIELD_AUTHENTICATOR_ATTACHMENT
import com.pingidentity.fido2.Constants.FIELD_AUTHENTICATOR_SELECTION
import com.pingidentity.fido2.Constants.FIELD_AUTHENTICATOR_SELECTION_INTERNAL
import com.pingidentity.fido2.Constants.FIELD_CHALLENGE
import com.pingidentity.fido2.Constants.FIELD_CLIENT_DATA_JSON
import com.pingidentity.fido2.Constants.FIELD_DISPLAY_NAME
import com.pingidentity.fido2.Constants.FIELD_EXCLUDE_CREDENTIALS
import com.pingidentity.fido2.Constants.FIELD_EXCLUDE_CREDENTIALS_INTERNAL
import com.pingidentity.fido2.Constants.FIELD_ID
import com.pingidentity.fido2.Constants.FIELD_NAME
import com.pingidentity.fido2.Constants.FIELD_PUB_KEY_CRED_PARAMS
import com.pingidentity.fido2.Constants.FIELD_PUB_KEY_CRED_PARAMS_INTERNAL
import com.pingidentity.fido2.Constants.FIELD_RAW_ID
import com.pingidentity.fido2.Constants.FIELD_RELYING_PARTY_ID_INTERNAL
import com.pingidentity.fido2.Constants.FIELD_RELYING_PARTY_NAME
import com.pingidentity.fido2.Constants.FIELD_REQUIRE_RESIDENT_KEY
import com.pingidentity.fido2.Constants.FIELD_RESIDENT_KEY
import com.pingidentity.fido2.Constants.FIELD_RP
import com.pingidentity.fido2.Constants.FIELD_TIMEOUT
import com.pingidentity.fido2.Constants.FIELD_USER
import com.pingidentity.fido2.Constants.FIELD_USER_ID
import com.pingidentity.fido2.Constants.FIELD_USER_NAME
import com.pingidentity.fido2.Constants.FIELD_USER_VERIFICATION
import com.pingidentity.fido2.Constants.RESIDENT_KEY_DISCOURAGED
import com.pingidentity.fido2.Fido2Client
import com.pingidentity.fido2.Fido2RegistrationCustomizer
import com.pingidentity.fido2.base64DefaultToUrlSafe
import com.pingidentity.fido2.base64ToIntStr
import com.pingidentity.fido2.base64ToStr
import com.pingidentity.fido2.toBase64
import com.pingidentity.fido2.toBase64Str
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * FIDO2 registration callback for ForgeRock Journey workflows.
 *
 * This callback handles the FIDO2 credential registration process within a Journey workflow.
 * It processes credential creation options from the server, performs the registration
 * using the device's FIDO2 capabilities, and formats the response for the Journey framework.
 *
 * The callback supports both legacy string-based and modern JSON-based response formats
 * depending on the server capabilities. It also supports optional device naming for
 * better credential management.
 */
class Fido2RegistrationCallback : Fido2Callback() {

    /**
     * The public key credential creation options for FIDO2 registration.
     * This contains all the parameters needed for the registration ceremony,
     * including user information, relying party details, and cryptographic preferences.
     */
    lateinit var publicKeyCredentialCreationOptions: JsonObject
        private set

    /**
     * Indicates whether the server supports JSON-formatted responses.
     * When true, responses are sent as JSON objects; otherwise, as legacy strings.
     */
    private var supportsJsonResponse: Boolean = false

    override fun init(name: String, value: JsonElement) {
        logger.d("Initializing FIDO2 registration callback with name: $name")
        if (name == Constants.FIELD_DATA && value is JsonObject) {
            logger.d("Processing FIDO2 registration data")
            supportsJsonResponse =
                value[Constants.FIELD_SUPPORTS_JSON_RESPONSE]?.jsonPrimitive?.boolean ?: false
            publicKeyCredentialCreationOptions = transform(value)
            logger.d("FIDO2 registration callback initialized successfully")
        } else {
            logger.e("Invalid initialization data - expected JsonObject for 'data', got ${value::class.simpleName}")
            throw IllegalArgumentException("Expected JsonObject for 'data', got ${value::class.simpleName}")
        }
    }

    /**
     * Registers a new FIDO2 credential using the initialized creation options.
     *
     * This method initiates the FIDO2 registration ceremony, prompting the user
     * to create a new credential using available authenticators. Upon successful
     * registration, it formats and submits the response to the Journey workflow.
     *
     * **Sample callbackValue Results:**
     *
     * **Legacy String Format (when supportsJsonResponse = false):**
     * ```
     * {"type":"webauthn.create","challenge":"test-challenge"}::dGVzdC1hdHRlc3RhdGlvbi1vYmplY3Q::dGVzdC1yYXctaWQ::MyDevice
     * ```
     *
     * **JSON Format (when supportsJsonResponse = true):**
     * ```json
     * {
     *   "authenticatorType": "PLATFORM",
     *   "response": "{\"type\":\"webauthn.create\",\"challenge\":\"test-challenge\"}::dGVzdC1hdHRlc3RhdGlvbi1vYmplY3Q::dGVzdC1yYXctaWQ::MyDevice"
     * }
     * ```
     *
     * The data components are separated by "::" and include:
     * 1. Client data JSON (Base64)
     * 2. Attestation object (Base64 converted to integer string)
     * 3. Raw credential ID
     * 4. Device name (if provided)
     *
     * @param deviceName Optional name for the registered device/credential for easier identification
     * @return A [Result] containing the attestation response as a [JsonObject] on success,
     *         or an exception on failure. The response is automatically submitted to the workflow.
     */
    suspend fun register(
        deviceName: String? = null,
        block: Fido2RegistrationCustomizer.() -> Unit = {}
    ): Result<JsonObject> {
        logger.d("Starting FIDO2 registration with device name: $deviceName")
        return Fido2Client {
            logger = this@Fido2RegistrationCallback.logger
        }.register(publicKeyCredentialCreationOptions, block).onSuccess { response ->
            logger.d("FIDO2 registration successful")
            val attestationResponse =
                response[Constants.FIELD_RESPONSE]?.jsonObject ?: JsonObject(emptyMap())
            var data = listOf(
                attestationResponse[FIELD_CLIENT_DATA_JSON]?.jsonPrimitive?.content?.base64ToStr()
                    ?: "",
                attestationResponse[FIELD_ATTESTATION_OBJECT]?.jsonPrimitive?.content?.base64ToIntStr()
                    ?: "",
                response[FIELD_RAW_ID]?.jsonPrimitive?.content ?: ""
            ).joinToString(DATA_SEPARATOR)

            deviceName?.let {
                data += "$DATA_SEPARATOR$it"
            }

            val callbackValue = if (supportsJsonResponse) {
                Json.encodeToString(Fido2JsonResponse(Constants.AUTHENTICATOR_PLATFORM, data))
            } else {
                data
            }
            logger.d("Setting registration callback value")
            valueCallback(callbackValue)
        }.onFailure { exception ->
            logger.e("FIDO2 registration failed", exception)
            handleError(exception)
        }
    }

    /**
     * Transform the input JSON object to prepare it for FIDO2 registration.
     *
     * Converts the Journey-specific format to the standard WebAuthn format required
     * by the Android Credential Manager API. This includes:
     * - Converting challenge to proper Base64 URL encoding
     * - Mapping relying party and user information to correct structure
     * - Transforming credential parameters and exclusion lists
     * - Setting authenticator selection criteria
     *
     * **Sample Input JSON:**
     * ```json
     * {
     *   "_action": "webauthn_registration",
     *   "challenge": "bc579885-7f30-7d4b-6320-738bb3f3f7e0-2c9ff370-276fab-a676-86-7da207222",
     *   "timeout": "120000",
     *   "relyingPartyName": "PingOne",
     *   "_relyingPartyId": "idc.petrov.ca",
     *   "userId": "18b4c0850b0dfa6f705ed05e8a9a9a61a23b1b69d9863dc3b7ee6db2fdf2d42c",
     *   "userName": "user@example.com",
     *   "displayName": "John Doe",
     *   "_pubKeyCredParams": [
     *     {"type": "public-key", "alg": "-7"},
     *     {"type": "public-key", "alg": "-37"},
     *     {"type": "public-key", "alg": "-257"}
     *   ],
     *   "_excludeCredentials": [
     *     {
     *       "type": "public-key",
     *       "id": [-35, -5, 23, 87, 107, 71, -32, -105, 14, 1, -76, -32, 49, -79, 6, -43]
     *     }
     *   ],
     *   "_authenticatorSelection": {
     *     "residentKey": "required",
     *     "requireResidentKey": true,
     *     "userVerification": "required"
     *   },
     *   "attestationPreference": "none"
     * }
     * ```
     *
     * **Sample Output JSON:**
     * ```json
     * {
     *   "challenge": "bc579885-7f30-7d4b-6320-738bb3f3f7e0-2c9ff370-276fab-a676-86-7da207222",
     *   "timeout": 120000,
     *   "attestation": "none",
     *   "rp": {
     *     "name": "PingOne",
     *     "id": "idc.petrov.ca"
     *   },
     *   "user": {
     *     "id": "18b4c0850b0dfa6f705ed05e8a9a9a61a23b1b69d9863dc3b7ee6db2fdf2d42c",
     *     "name": "user@example.com",
     *     "displayName": "John Doe"
     *   },
     *   "pubKeyCredParams": [
     *     {"type": "public-key", "alg": -7},
     *     {"type": "public-key", "alg": -37},
     *     {"type": "public-key", "alg": -257}
     *   ],
     *   "excludeCredentials": [
     *     {
     *       "type": "public-key",
     *       "id": "3fsXV2tH4JcOAbTgMbEG0w=="
     *     }
     *   ],
     *   "authenticatorSelection": {
     *     "requireResidentKey": true,
     *     "residentKey": "required",
     *     "userVerification": "required"
     *   }
     * }
     * ```
     *
     * @param input The input JSON object from the Journey workflow
     * @return The transformed JSON object compatible with WebAuthn standards
     */
    private fun transform(input: JsonObject): JsonObject {
        logger.d("Transforming FIDO2 registration creation options")
        return buildJsonObject {
            put(
                FIELD_CHALLENGE,
                input[FIELD_CHALLENGE]?.jsonPrimitive?.content?.base64DefaultToUrlSafe()
                    ?: ""
            )
            put(
                FIELD_TIMEOUT,
                input[FIELD_TIMEOUT]?.jsonPrimitive?.content?.toIntOrNull()
                    ?: DEFAULT_TIMEOUT
            )
            put(
                FIELD_ATTESTATION,
                input[FIELD_ATTESTATION_PREFERENCE]?.jsonPrimitive?.content
                    ?: DEFAULT_ATTESTATION
            )

            putJsonObject(FIELD_RP) {
                put(
                    FIELD_NAME,
                    input[FIELD_RELYING_PARTY_NAME]?.jsonPrimitive?.content ?: ""
                )
                put(
                    FIELD_ID,
                    input[FIELD_RELYING_PARTY_ID_INTERNAL]?.jsonPrimitive?.content
                        ?: DEFAULT_RELYING_PARTY_ID
                )
            }

            putJsonObject(FIELD_USER) {
                put(
                    FIELD_ID,
                    //For backward compatible from the Legacy SDK, the legacy SDK takes the
                    //base64 encoded String as bytes for userid
                    input[FIELD_USER_ID]?.jsonPrimitive?.content?.toBase64Str() ?: ""
                )
                input[FIELD_USER_NAME]?.jsonPrimitive?.content?.let {
                    put(
                        FIELD_NAME,
                        it
                    )
                }
                input[FIELD_DISPLAY_NAME]?.jsonPrimitive?.content?.let {
                    put(
                        FIELD_DISPLAY_NAME,
                        it
                    )
                }
            }

            putJsonArray(FIELD_PUB_KEY_CRED_PARAMS) {
                input[FIELD_PUB_KEY_CRED_PARAMS_INTERNAL]?.jsonArray?.forEach { element ->
                    element.jsonObject.let { param ->
                        val type = param[Constants.FIELD_TYPE]?.jsonPrimitive?.content
                        val alg = param[Constants.FIELD_ALG]?.jsonPrimitive?.int
                        if (type != null && alg != null) {
                            addJsonObject {
                                put(Constants.FIELD_TYPE, type)
                                put(Constants.FIELD_ALG, alg)
                            }
                        }
                    }
                }
            }

            putJsonArray(FIELD_EXCLUDE_CREDENTIALS) {
                input[FIELD_EXCLUDE_CREDENTIALS_INTERNAL]?.jsonArray?.forEach { element ->
                    element.jsonObject.let { credential ->
                        val type = credential[Constants.FIELD_TYPE]?.jsonPrimitive?.content
                        val idArray = credential[FIELD_ID]?.jsonArray

                        if (type != null && idArray != null) {
                            addJsonObject {
                                put(Constants.FIELD_TYPE, type)
                                val byteArray = ByteArray(idArray.size) { i ->
                                    idArray[i].jsonPrimitive.int.toByte()
                                }
                                put(FIELD_ID, JsonPrimitive(byteArray.toBase64()))
                            }
                        }
                    }
                }
            }

            putJsonObject(FIELD_AUTHENTICATOR_SELECTION) {
                input[FIELD_AUTHENTICATOR_SELECTION_INTERNAL]?.jsonObject?.let { authSelection ->
                    authSelection[FIELD_AUTHENTICATOR_ATTACHMENT]?.jsonPrimitive?.content?.let {
                        put(FIELD_AUTHENTICATOR_ATTACHMENT, it)
                    }
                    authSelection[FIELD_REQUIRE_RESIDENT_KEY]?.jsonPrimitive?.boolean?.let { requireResident ->
                        put(FIELD_REQUIRE_RESIDENT_KEY, requireResident)
                        if (requireResident) put(
                            FIELD_RESIDENT_KEY,
                            DEFAULT_RESIDENT_KEY_REQUIRED
                        ) else
                            put(FIELD_RESIDENT_KEY, RESIDENT_KEY_DISCOURAGED)
                    }
                    authSelection[FIELD_RESIDENT_KEY]?.jsonPrimitive?.content?.let {
                        put(FIELD_RESIDENT_KEY, it)
                    }
                    authSelection[FIELD_USER_VERIFICATION]?.jsonPrimitive?.content?.let {
                        put(FIELD_USER_VERIFICATION, it)
                    }
                }
            }
        }
    }
}
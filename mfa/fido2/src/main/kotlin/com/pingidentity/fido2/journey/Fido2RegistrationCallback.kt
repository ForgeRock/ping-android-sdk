/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.fido2.journey

import com.pingidentity.fido2.Constants
import com.pingidentity.fido2.Fido2
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
        if (name == Constants.FIELD_DATA && value is JsonObject) {
            supportsJsonResponse = value[Constants.FIELD_SUPPORTS_JSON_RESPONSE]?.jsonPrimitive?.boolean ?: false
            publicKeyCredentialCreationOptions = transform(value)
        } else {
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
     * @param deviceName Optional name for the registered device/credential for easier identification
     * @return A [Result] containing the attestation response as a [JsonObject] on success,
     *         or an exception on failure. The response is automatically submitted to the workflow.
     */
    suspend fun register(deviceName: String? = null): Result<JsonObject> {
        return Fido2.register(publicKeyCredentialCreationOptions).onSuccess { response ->
            val attestationResponse = response[Constants.FIELD_RESPONSE]?.jsonObject ?: JsonObject(emptyMap())
            var data = listOf(
                attestationResponse[Constants.FIELD_CLIENT_DATA_JSON]?.jsonPrimitive?.content?.base64ToJson() ?: "",
                attestationResponse[Constants.FIELD_ATTESTATION_OBJECT]?.jsonPrimitive?.content?.base64ToIntStr() ?: "",
                response[Constants.FIELD_RAW_ID]?.jsonPrimitive?.content ?: ""
            ).joinToString(Constants.DATA_SEPARATOR)

            deviceName?.let { data += "${Constants.DATA_SEPARATOR}$it" }

            val callbackValue = if (supportsJsonResponse) {
                Json.encodeToString(Fido2JsonResponse(Constants.AUTHENTICATOR_PLATFORM, data))
            } else {
                data
            }
            valueCallback(callbackValue)
        }.onFailure {
            handleError(it)
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
     * @param input The input JSON object from the Journey workflow
     * @return The transformed JSON object compatible with WebAuthn standards
     */
    private fun transform(input: JsonObject): JsonObject {
        return buildJsonObject {
            put(Constants.FIELD_CHALLENGE, input[Constants.FIELD_CHALLENGE]?.jsonPrimitive?.content?.base64DefaultToUrlSafe() ?: "")
            put(Constants.FIELD_TIMEOUT, input[Constants.FIELD_TIMEOUT]?.jsonPrimitive?.content?.toIntOrNull() ?: Constants.DEFAULT_TIMEOUT)
            put(Constants.FIELD_ATTESTATION, input[Constants.FIELD_ATTESTATION_PREFERENCE]?.jsonPrimitive?.content ?: Constants.DEFAULT_ATTESTATION)

            putJsonObject(Constants.FIELD_RP) {
                put(Constants.FIELD_NAME, input[Constants.FIELD_RELYING_PARTY_NAME]?.jsonPrimitive?.content ?: "")
                put(Constants.FIELD_ID, input[Constants.FIELD_RELYING_PARTY_ID]?.jsonPrimitive?.content ?: Constants.DEFAULT_RELYING_PARTY_ID)
            }

            putJsonObject(Constants.FIELD_USER) {
                put(Constants.FIELD_ID, input[Constants.FIELD_USER_ID]?.jsonPrimitive?.content ?: "")
                input[Constants.FIELD_USER_NAME]?.jsonPrimitive?.content?.let { put(Constants.FIELD_NAME, it) }
                input[Constants.FIELD_DISPLAY_NAME]?.jsonPrimitive?.content?.let { put(Constants.FIELD_DISPLAY_NAME, it) }
            }

            putJsonArray(Constants.FIELD_PUB_KEY_CRED_PARAMS) {
                input[Constants.FIELD_PUB_KEY_CRED_PARAMS_INTERNAL]?.jsonArray?.forEach { element ->
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

            putJsonArray(Constants.FIELD_EXCLUDE_CREDENTIALS) {
                input[Constants.FIELD_EXCLUDE_CREDENTIALS_INTERNAL]?.jsonArray?.forEach { element ->
                    element.jsonObject.let { credential ->
                        val type = credential[Constants.FIELD_TYPE]?.jsonPrimitive?.content
                        val idArray = credential[Constants.FIELD_ID]?.jsonArray

                        if (type != null && idArray != null) {
                            addJsonObject {
                                put(Constants.FIELD_TYPE, type)
                                val byteArray = ByteArray(idArray.size) { i ->
                                    idArray[i].jsonPrimitive.int.toByte()
                                }
                                put(Constants.FIELD_ID, JsonPrimitive(byteArray.toBase64()))
                            }
                        }
                    }
                }
            }

            putJsonObject(Constants.FIELD_AUTHENTICATOR_SELECTION) {
                input[Constants.FIELD_AUTHENTICATOR_SELECTION_INTERNAL]?.jsonObject?.let { authSelection ->
                    authSelection[Constants.FIELD_AUTHENTICATOR_ATTACHMENT]?.jsonPrimitive?.content?.let {
                        put(Constants.FIELD_AUTHENTICATOR_ATTACHMENT, it)
                    }
                    authSelection[Constants.FIELD_REQUIRE_RESIDENT_KEY]?.jsonPrimitive?.boolean?.let { requireResident ->
                        put(Constants.FIELD_REQUIRE_RESIDENT_KEY, requireResident)
                        if (requireResident) put(Constants.FIELD_RESIDENT_KEY, Constants.DEFAULT_RESIDENT_KEY_REQUIRED)
                    }
                    authSelection[Constants.FIELD_USER_VERIFICATION]?.jsonPrimitive?.content?.let {
                        put(Constants.FIELD_USER_VERIFICATION, it)
                    }
                }
            }
        }
    }
}
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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * FIDO2 authentication callback for ForgeRock Journey workflows.
 *
 * This callback handles the FIDO2 authentication process within a Journey workflow.
 * It processes authentication request options from the server, performs the authentication
 * using the device's FIDO2 capabilities, and formats the response for the Journey framework.
 *
 * The callback supports both legacy string-based and modern JSON-based response formats
 * depending on the server capabilities.
 */
class Fido2AuthenticationCallback : Fido2Callback() {

    /**
     * The public key credential request options for FIDO2 authentication.
     * This contains all the parameters needed for the authentication ceremony,
     * including challenge, allowed credentials, and verification requirements.
     */
    lateinit var publicKeyCredentialRequestOptions: JsonObject
        private set

    /**
     * Indicates whether the server supports JSON-formatted responses.
     * When true, responses are sent as JSON objects; otherwise, as legacy strings.
     */
    private var supportsJsonResponse: Boolean = false

    override fun init(name: String, value: JsonElement) {
        if (name == Constants.FIELD_DATA && value is JsonObject) {
            publicKeyCredentialRequestOptions = transform(value)
        } else {
            throw IllegalArgumentException("Expected JsonObject for 'data', got ${value::class.simpleName}")
        }
    }

    /**
     * Performs FIDO2 authentication using the initialized request options.
     *
     * This method initiates the FIDO2 authentication ceremony, prompting the user
     * to verify their identity using available authenticators. Upon successful
     * authentication, it formats and submits the response to the Journey workflow.
     *
     * @return A [Result] containing the authentication response as a [JsonObject] on success,
     *         or an exception on failure. The response is automatically submitted to the workflow.
     */
    suspend fun authenticate(): Result<JsonObject> {
        return Fido2.authenticate(publicKeyCredentialRequestOptions).onSuccess { response ->
            val authResponse = response[Constants.FIELD_RESPONSE]?.jsonObject ?: JsonObject(emptyMap())
            val data = listOf(
                authResponse[Constants.FIELD_CLIENT_DATA_JSON]?.jsonPrimitive?.content?.base64ToJson() ?: "",
                authResponse[Constants.FIELD_AUTHENTICATOR_DATA]?.jsonPrimitive?.content?.base64ToIntStr() ?: "",
                authResponse[Constants.FIELD_SIGNATURE]?.jsonPrimitive?.content?.base64ToIntStr() ?: "",
                response[Constants.FIELD_RAW_ID]?.jsonPrimitive?.content ?: "",
                authResponse[Constants.FIELD_USER_HANDLE]?.jsonPrimitive?.content ?: ""
            ).joinToString(Constants.DATA_SEPARATOR)

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
     * Transforms the input JSON object to prepare it for FIDO2 authentication.
     *
     * Converts the Journey-specific format to the standard WebAuthn format required
     * by the Android Credential Manager API. This includes:
     * - Converting challenge to proper Base64 URL encoding
     * - Transforming allowed credentials with proper ID encoding
     * - Setting appropriate timeout and user verification requirements
     *
     * @param input The input JSON object from the Journey workflow
     * @return The transformed JSON object compatible with WebAuthn standards
     */
    private fun transform(input: JsonObject): JsonObject {
        return buildJsonObject {
            put(Constants.FIELD_CHALLENGE, input[Constants.FIELD_CHALLENGE]?.jsonPrimitive?.content?.base64DefaultToUrlSafe() ?: "")
            put(Constants.FIELD_TIMEOUT, input[Constants.FIELD_TIMEOUT]?.jsonPrimitive?.content?.toIntOrNull() ?: Constants.DEFAULT_TIMEOUT)
            put(Constants.FIELD_USER_VERIFICATION, input[Constants.FIELD_USER_VERIFICATION]?.jsonPrimitive?.content ?: Constants.DEFAULT_USER_VERIFICATION)
            put(Constants.FIELD_RP_ID, input[Constants.FIELD_RELYING_PARTY_ID]?.jsonPrimitive?.content ?: "")

            putJsonArray(Constants.FIELD_ALLOW_CREDENTIALS) {
                input[Constants.FIELD_ALLOW_CREDENTIALS_INTERNAL]?.jsonArray?.forEach { element ->
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
        }
    }
}
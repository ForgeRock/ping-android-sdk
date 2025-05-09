/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.fido2.journey

import com.pingidentity.fido2.Fido2
import kotlinx.serialization.encodeToString
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
 * Callback for FIDO2 registration.
 * This class handles the registration process for FIDO2 credentials.
 */
class Fido2RegistrationCallback : Fido2Callback() {

    /**
     * The public key credential creation options for the registration.
     * This is a JSON object that contains all necessary parameters for creating a new FIDO2 credential.
     */
    lateinit var publicKeyCredentialCreationOptions: JsonObject
        private set
    private var supportsJsonResponse: Boolean = false

    override fun init(name: String, value: JsonElement) {
        when (name) {
            "data" -> {
                if (value is JsonObject) {
                    supportsJsonResponse =
                        value["supportsJsonResponse"]?.jsonPrimitive?.boolean ?: false
                    publicKeyCredentialCreationOptions = transform(value)
                } else {
                    throw IllegalArgumentException("Expected JsonObject for 'data', but got ${value::class.simpleName}")
                }
            }

            else -> throw IllegalArgumentException("Unknown callback name: $name")
        }
    }

    /**
     * Register a new FIDO2 credential.
     *
     * @return A [Result] containing the attestation value as a [JsonObject] or an error.
     * the attestation value will be automatically injected to the registration flow.
     */
    suspend fun register(deviceName: String? = null): Result<JsonObject> {
        return Fido2.register(publicKeyCredentialCreationOptions).onSuccess { jsonObject ->
            val response = jsonObject["response"]?.jsonObject ?: JsonObject(emptyMap())
            var data = listOf(
                (response["clientDataJSON"]?.jsonPrimitive?.content ?: "").base64ToJson(),
                (response["attestationObject"]?.jsonPrimitive?.content ?: "").base64ToIntStr(),
                jsonObject["rawId"]?.jsonPrimitive?.content ?: ""
            ).joinToString("::")

            deviceName?.let { data += "::$it" }
            val callbackValue = if (supportsJsonResponse) {
                Json.encodeToString(Fido2JsonResponse("platform", data))
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
     * @param input The input JSON object to transform.
     * @return The transformed JSON object.
     */
    private fun transform(input: JsonObject): JsonObject {
        return buildJsonObject {
            // challenge
            put(
                "challenge",
                input["challenge"]?.jsonPrimitive?.content?.base64DefaultToUrlSafe() ?: ""
            ) // Use ?: "" for default empty string if missing

            // rp
            putJsonObject("rp") {
                put("name", input["relyingPartyName"]?.jsonPrimitive?.content ?: "")
                // Map "_relyingPartyId" to "id" in "rp". Provide a default if not found.
                put(
                    "id",
                    input["_relyingPartyId"]?.jsonPrimitive?.content
                        ?: "credential-manager-test.example.com"
                )
            }

            // user
            putJsonObject("user") {
                put("id", input["userId"]?.jsonPrimitive?.content ?: "")
                // Use 'let' to only add if the content is not null
                input["userName"]?.jsonPrimitive?.content?.let {
                    put("name", it)
                }
                input["displayName"]?.jsonPrimitive?.content?.let {
                    put("displayName", it)
                }
            }

            // pubKeyCredParams
            putJsonArray("pubKeyCredParams") {
                input["_pubKeyCredParams"]?.jsonArray?.forEach { element ->
                    val type = element.jsonObject["type"]?.jsonPrimitive?.content
                    val alg = element.jsonObject["alg"]?.jsonPrimitive?.int
                    if (type != null && alg != null) { // Only add if both type and alg are present
                        addJsonObject {
                            put("type", type)
                            put("alg", alg)
                        }
                    }
                }
            }

            // timeout
            // Convert to Int; default to a sensible timeout if parse fails or missing
            put("timeout", input["timeout"]?.jsonPrimitive?.content?.toIntOrNull() ?: 60000)

            // attestation
            put("attestation", input["attestationPreference"]?.jsonPrimitive?.content ?: "none")

            // excludeCredentials
            putJsonArray("excludeCredentials") {
                input["_excludeCredentials"]?.jsonArray?.forEach { element ->
                    val type = element.jsonObject["type"]?.jsonPrimitive?.content
                    val idArray = element.jsonObject["id"]?.jsonArray

                    if (type != null && idArray != null) {
                        addJsonObject {
                            put("type", type)
                            // Convert JsonArray<Int> to ByteArray and then Base64Url encode it
                            val byteArray =
                                ByteArray(idArray.size) { i -> idArray[i].jsonPrimitive.int.toByte() }
                            val encodedId =
                                JsonPrimitive(byteArray.toBase64())
                            put("id", encodedId)
                        }
                    }
                }
            }

            // authenticatorSelection
            putJsonObject("authenticatorSelection") {
                val inputAuthenticatorSelection = input["_authenticatorSelection"]?.jsonObject

                inputAuthenticatorSelection?.let {
                    it["authenticatorAttachment"]?.jsonPrimitive?.content?.let { attachment ->
                        put("authenticatorAttachment", attachment)
                    }
                    it["requireResidentKey"]?.jsonPrimitive?.boolean?.let { requireResidentKey ->
                        put("requireResidentKey", requireResidentKey)
                        // If requireResidentKey is true, residentKey should be "required"
                        if (requireResidentKey) {
                            put("residentKey", "required")
                        }
                    }
                    it["userVerification"]?.jsonPrimitive?.content?.let { userVerification ->
                        put("userVerification", userVerification)
                    }
                }
            }
        }
    }
}
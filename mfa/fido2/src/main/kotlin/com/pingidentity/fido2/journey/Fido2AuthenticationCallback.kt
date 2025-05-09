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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

class Fido2AuthenticationCallback : Fido2Callback() {

    lateinit var publicKeyCredentialRequestOptions: JsonObject
        private set

    private var supportsJsonResponse: Boolean = false

    override fun init(name: String, value: JsonElement) {
        when (name) {
            "data" -> {
                if (value is JsonObject) {
                    publicKeyCredentialRequestOptions = transform(value)
                } else {
                    throw IllegalArgumentException("Expected JsonObject for 'data', but got ${value::class.simpleName}")
                }
            }

            else -> throw IllegalArgumentException("Unknown callback name: $name")
        }
    }

    suspend fun authenticate(): Result<JsonObject> {
        return Fido2.authenticate(publicKeyCredentialRequestOptions).onSuccess {
            val response = it["response"]?.jsonObject ?: JsonObject(emptyMap())
            val data = listOf(
                (response["clientDataJSON"]?.jsonPrimitive?.content ?: "").base64ToJson(),
                (response["authenticatorData"]?.jsonPrimitive?.content ?: "").base64ToIntStr(),
                (response["signature"]?.jsonPrimitive?.content ?: "").base64ToIntStr(),
                (it["rawId"]?.jsonPrimitive?.content ?: ""),
                (response["userHandle"]?.jsonPrimitive?.content ?: "")
            ).joinToString("::")
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
     * Transform the input JSON object to prepare it for FIDO2 authentication.
     *
     * @param input The input JSON object to transform.
     * @return The transformed JSON object.
     */
    private fun transform(input: JsonObject): JsonObject {
        return buildJsonObject {
            // challenge: Directly map the challenge string
            put("challenge", input["challenge"]?.jsonPrimitive?.content?.base64DefaultToUrlSafe() ?: "")

            putJsonArray("allowCredentials") {
                input["_allowCredentials"]?.jsonArray?.forEach { element ->
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

            // timeout: Convert from string to integer. Provide a default if parsing fails.
            put(
                "timeout",
                input["timeout"]?.jsonPrimitive?.content?.toIntOrNull() ?: 60000
            ) // Default to 60000 if invalid

            // userVerification: Transform "preferred" or any value to "required"
            put("userVerification", input["userVerification"]?.jsonPrimitive?.content ?: "required")

            // rpId: Hardcode to the value provided in the output example.
            // The input's "_relyingPartyId" is different, so we're using the target output's specific value.
            put("rpId", input["_relyingPartyId"]?.jsonPrimitive?.content ?: "")
        }
    }
}
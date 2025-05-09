/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.fido2.davinci

import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.CredentialManager
import androidx.credentials.exceptions.CreateCredentialException
import com.pingidentity.android.ContextProvider
import com.pingidentity.davinci.plugin.Collector
import com.pingidentity.fido2.Fido2
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * A collector for FIDO2 registration.
 *
 * @property publicKeyCredentialCreationOptions The public key credential creation options.
 * @property attestationValue The attestation value after registration.
 */
class Fido2RegistrationCollector : AbstractFido2Collector() {

    lateinit var publicKeyCredentialCreationOptions: JsonObject
        private set

    lateinit var attestationValue: JsonObject
        private set

    override fun init(input: JsonObject): Collector<JsonObject> {
        return super.init(input)
            .also {
                publicKeyCredentialCreationOptions =
                    input["publicKeyCredentialCreationOptions"]?.jsonObject?.let { transform(it) }
                        ?: throw IllegalArgumentException("Missing publicKeyCredentialCreationOptions")
            }
    }

    override fun payload(): JsonObject? {
        return if (!this::attestationValue.isInitialized) {
            null
        } else {
            buildJsonObject {
                put("attestationValue", attestationValue)
            }
        }
    }

    /**
     * Register a new FIDO2 credential.
     *
     * @return A [Result] containing the attestation value as a [JsonObject] or an error.
     * the attestation value will be automatically injected to the registration flow.
     */
    suspend fun register(): Result<JsonObject> {
        return Fido2.register(publicKeyCredentialCreationOptions).onSuccess {
            attestationValue = it
        }
    }

    /**
     * Transform the input JSON object by encoding the user ID and challenge values.
     *
     * @param inputJson The input JSON object to transform.
     * @return The transformed JSON object with encoded user ID and challenge values.
     */
    @OptIn(ExperimentalEncodingApi::class)
    private fun transform(inputJson: JsonObject): JsonObject {
        val map = inputJson.toMutableMap()

        map["user"]?.let { user ->
            if (user is JsonObject) {
                user.toMutableMap()["id"]?.let { id ->
                    if (id is JsonArray) {
                        val byteArray =
                            id.map { it.jsonPrimitive.int.toByte() }.toByteArray()
                        map["user"] = buildJsonObject {
                            put("id", JsonPrimitive(Base64.UrlSafe.encode(byteArray).trimEnd('=')))
                            user.forEach { key, value ->
                                if (key != "id") {
                                    put(key, value)
                                }
                            }
                        }
                    }
                }
            }
        }

        map["challenge"]?.let { challengeElement ->
            if (challengeElement is JsonArray) {
                val byteArray = challengeElement.map { it.jsonPrimitive.int.toByte() }.toByteArray()
                map["challenge"] =
                    JsonPrimitive(Base64.UrlSafe.encode(byteArray).trimEnd('='))
            }
        }

        return JsonObject(map)
    }
}
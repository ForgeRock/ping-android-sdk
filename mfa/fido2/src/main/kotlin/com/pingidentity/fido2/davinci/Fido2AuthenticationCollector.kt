/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.fido2.davinci

import com.pingidentity.davinci.plugin.Collector
import com.pingidentity.fido2.Fido2
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
 * A collector for FIDO2 authentication.
 *
 * @property publicKeyCredentialRequestOptions The public key credential request options.
 * @property assertionValue The assertion value after authentication.
 */
class Fido2AuthenticationCollector : AbstractFido2Collector() {

    lateinit var publicKeyCredentialRequestOptions: JsonObject
        private set

    lateinit var assertionValue: JsonObject
        private set

    override fun init(input: JsonObject): Collector<JsonObject> {
        return super.init(input)
            .also {
                publicKeyCredentialRequestOptions =
                    input["publicKeyCredentialRequestOptions"]?.jsonObject?.let {
                        transform(it)
                    } ?: throw IllegalArgumentException("Missing publicKeyCredentialRequestOptions")
            }
    }

    override fun payload(): JsonObject? {
        return if (!this::assertionValue.isInitialized) {
            null
        } else {
            buildJsonObject {
                put("assertionValue", assertionValue)
            }
        }
    }

    /**
     * Authenticate using the FIDO2 authentication process.
     *
     * @return A [Result] containing the assertion value as a [JsonObject] or an error.
     * the assertion value will be automatically injected to the authentication flow.
     */
    suspend fun authenticate(): Result<JsonObject> {
        return Fido2.authenticate(publicKeyCredentialRequestOptions).onSuccess {
            assertionValue = it
        }
    }

    /**
     * Transform the input JSON object by converting specific fields to Base64 strings.
     *
     * @param inputJson The input JSON object to transform.
     * @return The transformed JSON object.
     */
    @OptIn(ExperimentalEncodingApi::class)
    private fun transform(inputJson: JsonObject): JsonObject {
        val map = inputJson.toMutableMap()

        // Convert challenge to Base64 string
        map["challenge"]?.let { challenge ->
            if (challenge is JsonArray) {
                val byteArray = challenge.map { it.jsonPrimitive.int.toByte() }.toByteArray()
                map["challenge"] =
                    JsonPrimitive(Base64.UrlSafe.encode(byteArray).trimEnd('='))
            }
        }

        // Convert allowCredentials.[x].id to Base64 string
        map["allowCredentials"]?.let { allowCredentials ->
            if (allowCredentials is JsonArray) {
                val updatedAllowCredentials = allowCredentials.map { credential ->
                    if (credential is JsonObject && credential.containsKey("id")) {
                        val credentialMap = credential.toMutableMap()
                        credentialMap["id"]?.let { id ->
                            if (id is JsonArray) {
                                val byteArray =
                                    id.map { it.jsonPrimitive.int.toByte() }.toByteArray()
                                credentialMap["id"] =
                                    JsonPrimitive(Base64.UrlSafe.encode(byteArray).trimEnd('='))
                            }
                        }
                        JsonObject(credentialMap)
                    } else {
                        credential
                    }
                }
                map["allowCredentials"] = JsonArray(updatedAllowCredentials)
            }
        }

        return JsonObject(map)
    }
}
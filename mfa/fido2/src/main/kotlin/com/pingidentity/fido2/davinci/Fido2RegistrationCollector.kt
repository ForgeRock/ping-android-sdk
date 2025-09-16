/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.fido2.davinci

import androidx.credentials.CreatePublicKeyCredentialRequest
import com.pingidentity.davinci.plugin.Collector
import com.pingidentity.fido2.Constants
import com.pingidentity.fido2.Fido2Client
import com.pingidentity.fido2.toBase64
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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
                logger.d("Initializing FIDO2 registration collector")
                publicKeyCredentialCreationOptions =
                    input[Constants.FIELD_PUBLIC_KEY_CREDENTIAL_CREATION_OPTIONS]?.jsonObject?.let {
                        transform(
                            it
                        )
                    }
                        ?: throw IllegalArgumentException("Missing ${Constants.FIELD_PUBLIC_KEY_CREDENTIAL_CREATION_OPTIONS}")
                logger.d("FIDO2 registration collector initialized with creation options")
            }
    }

    override fun payload(): JsonObject? {
        return if (!this::attestationValue.isInitialized) {
            logger.d("No attestation value available, returning null payload")
            null
        } else {
            logger.d("Returning attestation payload for FIDO2 registration")
            buildJsonObject {
                put(Constants.FIELD_ATTESTATION_VALUE, attestationValue)
            }
        }
    }

    /**
     * Register a new FIDO2 credential.
     *
     * @return A [Result] containing the attestation value as a [JsonObject] or an error.
     * the attestation value will be automatically injected to the registration flow.
     */
    suspend fun register(
        block: (JsonObject) -> CreatePublicKeyCredentialRequest = {
            CreatePublicKeyCredentialRequest(
                it.toString()
            )
        }
    ): Result<JsonObject> {
        logger.d("Starting FIDO2 registration")
        return Fido2Client.register(block(publicKeyCredentialCreationOptions)).onSuccess {
            logger.d("FIDO2 registration successful")
            attestationValue = it
        }.onFailure { exception ->
            logger.e("FIDO2 registration failed", exception)
        }
    }

    /**
     * Transform the input JSON object by encoding the user ID and challenge values.
     *
     * @param inputJson The input JSON object to transform.
     * @return The transformed JSON object with encoded user ID and challenge values.
     */
    private fun transform(inputJson: JsonObject): JsonObject {
        logger.d("Transforming FIDO2 registration creation options")
        val map = inputJson.toMutableMap()

        map[Constants.FIELD_USER]?.let { user ->
            if (user is JsonObject) {
                user.toMutableMap()[Constants.FIELD_ID]?.let { id ->
                    if (id is JsonArray) {
                        val byteArray =
                            id.map { it.jsonPrimitive.int.toByte() }.toByteArray()
                        map[Constants.FIELD_USER] = buildJsonObject {
                            put(
                                Constants.FIELD_ID,
                                JsonPrimitive(byteArray.toBase64())
                            )
                            user.forEach { key, value ->
                                if (key != Constants.FIELD_ID) {
                                    put(key, value)
                                }
                            }
                        }
                    }
                }
            }
        }

        map[Constants.FIELD_CHALLENGE]?.let { challengeElement ->
            if (challengeElement is JsonArray) {
                val byteArray = challengeElement.map { it.jsonPrimitive.int.toByte() }.toByteArray()
                map[Constants.FIELD_CHALLENGE] =
                    JsonPrimitive(byteArray.toBase64())
            }
        }

        logger.d("FIDO2 registration creation options transformed successfully")
        return JsonObject(map)
    }
}

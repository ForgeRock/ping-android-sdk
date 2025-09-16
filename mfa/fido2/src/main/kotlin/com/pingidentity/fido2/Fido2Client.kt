/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.fido2

import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import com.google.android.gms.fido.fido2.api.common.AuthenticatorAssertionResponse
import com.google.android.gms.fido.fido2.api.common.PublicKeyCredentialRequestOptions
import com.pingidentity.android.ContextProvider
import com.pingidentity.fido2.Constants.FIELD_AUTHENTICATOR_ATTACHMENT
import com.pingidentity.fido2.Constants.FIELD_AUTHENTICATOR_DATA
import com.pingidentity.fido2.Constants.FIELD_CLIENT_DATA_JSON
import com.pingidentity.fido2.Constants.FIELD_ID
import com.pingidentity.fido2.Constants.FIELD_RAW_ID
import com.pingidentity.fido2.Constants.FIELD_RESPONSE
import com.pingidentity.fido2.Constants.FIELD_SIGNATURE
import com.pingidentity.fido2.Constants.FIELD_TYPE
import com.pingidentity.fido2.Constants.FIELD_USER_HANDLE
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.coroutines.coroutineContext

/**
 * Core FIDO2 operations handler for Android applications.
 *
 * This singleton object provides the main FIDO2 functionality including credential
 * registration and authentication using multiple Android APIs:
 * - Android Credential Manager API (preferred for Android 14+)
 * - Google Play Services FIDO2 API (fallback for wider compatibility)
 *
 * The module automatically selects the appropriate API based on device capabilities
 * and available dependencies, ensuring maximum compatibility across different Android versions.
 *
 * @see <a href="https://developer.android.com/identity/sign-in/credential-manager">Android Credential Manager</a>
 * @see <a href="https://developers.google.com/identity/fido">Google Play Services FIDO2</a>
 */
object Fido2Client {

    /**
     * Registers a new FIDO2 credential using the provided creation options.
     *
     * This method creates a new FIDO2 credential (passkey) on the device using the
     * Android Credential Manager. The credential can be stored locally on the device
     * or synced across devices depending on the platform capabilities.
     *
     * @param credentialRequest The creation options for the FIDO2 credential
     * @return A [Result] containing the attestation response as a [JsonObject] on success,
     *         or an exception on failure
     */
    suspend fun register(credentialRequest: CreatePublicKeyCredentialRequest): Result<JsonObject> {
        val credentialManager = CredentialManager.create(ContextProvider.context)

        try {
            val result = credentialManager.createCredential(
                context = ContextProvider.context,
                request = credentialRequest
            )
            when (result) {
                is CreatePublicKeyCredentialResponse -> {
                    val attestationValue = Json.parseToJsonElement(
                        result.registrationResponseJson
                    ).jsonObject
                    return Result.success(attestationValue)
                }

                else -> throw IllegalStateException("Unexpected result type: ${result::class.simpleName}")
            }
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            return Result.failure(e)
        }
    }

    /**
     * Authenticates using an existing FIDO2 credential with Credential Manager API.
     *
     * This method performs FIDO2 authentication using the Android Credential Manager,
     * which provides support for discoverable credentials (passkeys) and cross-device
     * synchronization on supported devices.
     *
     * @param credentialOption The authentication options for the FIDO2 credential
     * @return A [Result] containing the assertion response as a [JsonObject] on success,
     *         or an exception on failure
     */
    suspend fun authenticate(credentialOption: GetPublicKeyCredentialOption): Result<JsonObject> {
        val credentialManager = CredentialManager.create(ContextProvider.context)
        val credentialRequest = GetCredentialRequest(listOf(credentialOption))
        try {
            val result = credentialManager.getCredential(
                context = ContextProvider.context,
                request = credentialRequest
            )
            when (val credential = result.credential) {
                is PublicKeyCredential -> {
                    val assertionValue = Json.parseToJsonElement(
                        credential.authenticationResponseJson
                    ).jsonObject
                    return Result.success(assertionValue)
                }

                else -> throw IllegalStateException("Unexpected result type: ${result::class.simpleName}")
            }
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            return Result.failure(e)
        }
    }

    /**
     * Authenticates using an existing FIDO2 credential with Google Play Services API.
     *
     * This method performs FIDO2 authentication using Google Play Services FIDO2 API,
     * which provides broader device compatibility for non-discoverable (device-bound)
     * credentials. This is used as a fallback when Credential Manager is not available.
     *
     * @param credentialOption The authentication options for the FIDO2 credential
     * @return A [Result] containing the assertion response as a [JsonObject] on success,
     *         or an exception on failure
     */
    suspend fun authenticate(credentialOption: PublicKeyCredentialRequestOptions): Result<JsonObject> {
        try {
            val credential = getPublicKeyCredential(ContextProvider.context, credentialOption)

            // Convert GMS PublicKeyCredential to JsonObject format similar to Credential Manager response
            val response = credential.response as AuthenticatorAssertionResponse
            val assertionValue = buildJsonObject {
                put(FIELD_ID, JsonPrimitive(credential.id))
                credential.rawId?.let {
                    put(FIELD_RAW_ID, JsonPrimitive(it.toBase64()))
                }
                put(
                    FIELD_AUTHENTICATOR_ATTACHMENT,
                    JsonPrimitive(
                        credential.authenticatorAttachment ?: Constants.AUTHENTICATOR_PLATFORM
                    )
                )
                put(FIELD_TYPE, JsonPrimitive(credential.type))
                put(FIELD_RESPONSE, buildJsonObject {
                    put(FIELD_AUTHENTICATOR_DATA, JsonPrimitive(response.authenticatorData.toBase64()))
                    put(FIELD_CLIENT_DATA_JSON, JsonPrimitive(response.clientDataJSON.toBase64()))
                    put(FIELD_SIGNATURE, JsonPrimitive(response.signature.toBase64()))
                    response.userHandle?.let {
                        put(FIELD_USER_HANDLE, JsonPrimitive(it.toBase64()))
                    }
                })
            }

            return Result.success(assertionValue)
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            return Result.failure(e)
        }
    }

}
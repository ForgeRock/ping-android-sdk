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
import com.pingidentity.android.ContextProvider
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.coroutines.coroutineContext

/**
 * Core FIDO2 operations handler for Android applications.
 *
 * This singleton object provides the main FIDO2 functionality including credential
 * registration and authentication using the Android Credential Manager API.
 * https://developer.android.com/identity/sign-in/credential-manager
 * It handles the conversion between JSON-based FIDO2 specifications and Android's
 * credential management system.
 */
internal object Fido2 {

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
     * Authenticates using an existing FIDO2 credential with the provided request options.
     *
     * This method performs FIDO2 authentication using existing credentials on the device.
     * It prompts the user to select and verify their identity using biometrics, PIN,
     * or other verification methods supported by the authenticator.
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
}
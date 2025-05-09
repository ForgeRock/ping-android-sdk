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

object Fido2 {

    suspend fun register(publicKeyCredentialCreationOptions: JsonObject): Result<JsonObject> {
        val credentialManager = CredentialManager.create(ContextProvider.context)
        val credentialRequest =
            CreatePublicKeyCredentialRequest(publicKeyCredentialCreationOptions.toString())
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

    suspend fun authenticate(publicKeyCredentialRequestOptions: JsonObject): Result<JsonObject> {
        val credentialManager = CredentialManager.create(ContextProvider.context)
        val credentialOption =
            GetPublicKeyCredentialOption(publicKeyCredentialRequestOptions.toString())
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
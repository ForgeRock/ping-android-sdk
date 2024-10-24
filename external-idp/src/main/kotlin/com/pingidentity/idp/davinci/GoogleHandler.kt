/*
 * Copyright (c) 2024 PingIdentity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.idp.davinci

import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.pingidentity.android.ContextProvider
import com.pingidentity.davinci.plugin.DaVinci
import com.pingidentity.exception.ApiException
import com.pingidentity.idp.UnsupportedIdPException
import com.pingidentity.orchestrate.Request
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * A handler class for managing Google Identity Provider (IdP) authorization.
 *
 * @property davinci The DaVinci instance used for making HTTP requests and handling configurations.
 */
class GoogleHandler(val davinci: DaVinci) : IdpHandler {

    /**
     * Authorizes a user with Google SDK
     *
     * @param url The URL to which the authorization request is made.
     * @return A [Request] object that can be used to continue the DaVinci flow.
     */
    override suspend fun authorize(url: String): Request {

        try {
            Class.forName("com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption")
        } catch (e: ClassNotFoundException) {
            throw UnsupportedIdPException("Google SDK is not available.")
        }

        val response = davinci.config.httpClient.get(url) {
            header("x-requested-with", "forgerock-sdk")
            header("Accept", "application/json")
        }

        if (response.status.isSuccess()) {
            with(response) {
                val json = Json.parseToJsonElement(call.body()).jsonObject
                val clientId =
                    json["idp"]?.jsonObject?.get("clientId")?.jsonPrimitive?.content
                        ?: throw IllegalStateException("Client ID not found")
                val nonce = json["idp"]?.jsonObject?.get("nonce")?.jsonPrimitive?.content
                    ?: throw IllegalStateException("Nonce not found")
                val next =
                    json["_links"]?.jsonObject?.get("next")?.jsonObject?.get("href")?.jsonPrimitive?.content
                        ?: throw IllegalStateException("Next URL not found")

                val signInWithGoogleOption: GetSignInWithGoogleOption =
                    GetSignInWithGoogleOption.Builder(clientId)
                        .setNonce(nonce)
                        .build()

                val request: GetCredentialRequest = GetCredentialRequest.Builder()
                    .addCredentialOption(signInWithGoogleOption)
                    .build()

                val credentialManager = CredentialManager.create(ContextProvider.context)
                val result = credentialManager.getCredential(
                    request = request,
                    context = ContextProvider.currentActivity,
                )

                when (val credential = result.credential) {
                    is CustomCredential -> {
                        if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                            // Use googleIdTokenCredential and extract id to validate and
                            // authenticate on your server.
                            val googleIdTokenCredential = GoogleIdTokenCredential
                                .createFrom(credential.data)

                            return Request().apply {
                                url(next)
                                header("Accept", "application/json")
                                body(buildJsonObject {
                                    put("idToken", googleIdTokenCredential.idToken)
                                })
                            }
                        }
                    }
                }
                throw IllegalStateException("Authorization failed")
            }
        } else {
            throw ApiException(response.status.value, response.body())
        }

    }
}
/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.idp

import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.pingidentity.android.ContextProvider

internal class GoogleHandler : IdpHandler {
    override var tokenType: String = "id_token"
    override suspend fun authorize(idpClient: IdpClient): IdpResult {
        try {
            Class.forName("com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption")
        } catch (e: ClassNotFoundException) {
            throw UnsupportedIdPException("Google SDK is not available.")
        }

        if (idpClient.clientId == null) {
            throw IllegalArgumentException("Client ID is required.")
        }
        if (idpClient.nonce == null) {
            throw IllegalArgumentException("Nonce is required.")
        }

        val signInWithGoogleOption: GetSignInWithGoogleOption =
            GetSignInWithGoogleOption.Builder(idpClient.clientId)
                .setNonce(idpClient.nonce)
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

                    return IdpResult(googleIdTokenCredential.idToken)
                }
            }
        }
        throw IllegalStateException("Authorization failed")
    }
}
/*
 * Copyright (c) 2024 - 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.journey.module

import android.net.Uri
import com.pingidentity.exception.ApiException
import com.pingidentity.journey.Constants.ACCEPT_API_VERSION
import com.pingidentity.journey.Constants.RESOURCE_2_1_PROTOCOL_1_0
import com.pingidentity.oidc.Agent
import com.pingidentity.oidc.AuthCode
import com.pingidentity.oidc.OidcConfig
import com.pingidentity.oidc.Pkce
import com.pingidentity.oidc.exception.AuthorizeException
import com.pingidentity.oidc.module.populateRequest
import com.pingidentity.orchestrate.EmptySession
import com.pingidentity.orchestrate.Session
import java.net.HttpURLConnection

/**
 * Creates an agent for handling session-based authentication.
 *
 * @param cookieName The name of the cookie used for session management.
 * @param onAuthorizeFailed A suspend function that is called when authorization fails.
 * @param block A suspend function that returns a Session object.
 *  @return An instance of Agent<Unit> that handles session-based authentication.
 */
internal fun sessionAgent(
    cookieName: String,
    onAuthorizeFailed: suspend () -> Unit,
    block: suspend () -> Session
): Agent<Unit> {
    return object : Agent<Unit> {

        override fun config(): () -> Unit = {}

        override suspend fun authorize(oidcConfig: OidcConfig<Unit>): AuthCode {
            val session = block()
            if (session is EmptySession) {
                throw AuthorizeException("No Session, please start Journey flow to authenticate.")
            }
            val pkce = Pkce.generate()

            // Create a fresh request, populate it (handles both PAR and standard flow),
            // then attach Journey-specific headers before sending.
            val request = oidcConfig.oidcClientConfig.httpClient.request()
            with(oidcConfig.oidcClientConfig) { populateRequest(request, emptyMap(), pkce) }
            request.header(ACCEPT_API_VERSION, RESOURCE_2_1_PROTOCOL_1_0)
            request.header(cookieName, session.value)

            val response = oidcConfig.oidcClientConfig.httpClient.request(request)

            if (response.status == HttpURLConnection.HTTP_MOVED_TEMP) { // Check if the status is redirect
                val locationHeader = response.header("Location")
                locationHeader?.let {
                    val uri = Uri.parse(it)
                    uri.getQueryParameter("code")?.let { code ->
                        return AuthCode(code, pkce.codeVerifier)
                    }
                }
            }
            onAuthorizeFailed()
            throw AuthorizeException(
                "Authorize failed, session is discarded. Please start Journey flow to authenticate.",
                ApiException(response.status, response.body())
            )
        }

        override suspend fun endSession(
            oidcConfig: OidcConfig<Unit>,
            idToken: String,
        ): Boolean {
            // Since we don't have the Session token, let Journey handle the signoff
            return true
        }
    }
}
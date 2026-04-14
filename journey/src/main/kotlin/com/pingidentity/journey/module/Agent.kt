/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
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
import com.pingidentity.oidc.Constants
import com.pingidentity.oidc.Constants.ACR_VALUES
import com.pingidentity.oidc.Constants.CLIENT_ID
import com.pingidentity.oidc.Constants.CODE
import com.pingidentity.oidc.Constants.CODE_CHALLENGE
import com.pingidentity.oidc.Constants.CODE_CHALLENGE_METHOD
import com.pingidentity.oidc.Constants.DISPLAY
import com.pingidentity.oidc.Constants.LOGIN_HINT
import com.pingidentity.oidc.Constants.NONCE
import com.pingidentity.oidc.Constants.PROMPT
import com.pingidentity.oidc.Constants.REDIRECT_URI
import com.pingidentity.oidc.Constants.RESPONSE_TYPE
import com.pingidentity.oidc.Constants.UI_LOCATES
import com.pingidentity.oidc.OidcConfig
import com.pingidentity.oidc.Pkce
import com.pingidentity.oidc.exception.AuthorizeException
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
            val response = oidcConfig.oidcClientConfig.httpClient.request {
                url = oidcConfig.oidcClientConfig.openId.authorizationEndpoint
                parameter(CLIENT_ID, oidcConfig.oidcClientConfig.clientId)
                parameter(Constants.SCOPE, oidcConfig.oidcClientConfig.scopes.joinToString(" "))
                parameter(RESPONSE_TYPE, CODE)
                parameter(REDIRECT_URI, oidcConfig.oidcClientConfig.redirectUri)
                parameter(CODE_CHALLENGE, pkce.codeChallenge)
                parameter(CODE_CHALLENGE_METHOD, pkce.codeChallengeMethod)
                oidcConfig.oidcClientConfig.acrValues?.let {
                    parameter(ACR_VALUES, it)
                }
                oidcConfig.oidcClientConfig.display?.let {
                    parameter(DISPLAY, it)
                }
                oidcConfig.oidcClientConfig.additionalParameters.forEach { (key, value) ->
                    parameter(key, value)
                }
                oidcConfig.oidcClientConfig.loginHint?.let {
                    parameter(LOGIN_HINT, it)
                }
                oidcConfig.oidcClientConfig.nonce?.let {
                    parameter(NONCE, it)
                }
                oidcConfig.oidcClientConfig.prompt?.let {
                    parameter(PROMPT, it)
                }
                oidcConfig.oidcClientConfig.uiLocales?.let {
                    parameter(UI_LOCATES, it)
                }
                header(ACCEPT_API_VERSION, RESOURCE_2_1_PROTOCOL_1_0)
                header(cookieName, session.value)
            }
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
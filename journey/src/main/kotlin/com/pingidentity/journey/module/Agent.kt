/*
 * Copyright (c) 2024. PingIdentity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.journey.module

import com.pingidentity.exception.ApiException
import com.pingidentity.oidc.Agent
import com.pingidentity.oidc.AuthCode
import com.pingidentity.oidc.OidcConfig
import com.pingidentity.oidc.Pkce
import com.pingidentity.oidc.exception.AuthorizeException
import com.pingidentity.orchestrate.EmptySession
import com.pingidentity.orchestrate.Session
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.client.request.url
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url

internal fun sessionAgent(
    cookie: String,
    block: suspend () -> Session
): Agent<Unit> {
    return object : Agent<Unit> {

        override fun config(): () -> Unit = {}

        override suspend fun authorize(oidcConfig: OidcConfig<Unit>): AuthCode {
            val session = block()
            if (session is EmptySession) {
                throw AuthorizeException("Please start Journey flow to authenticate.")
            }
            val pkce = Pkce.generate()
            val response = oidcConfig.oidcClientConfig.httpClient.get {
                url(oidcConfig.oidcClientConfig.openId.authorizationEndpoint)
                parameter("client_id", oidcConfig.oidcClientConfig.clientId)
                parameter("scope", oidcConfig.oidcClientConfig.scopes.joinToString(" "))
                parameter("response_type", "code")
                parameter("redirect_uri", oidcConfig.oidcClientConfig.redirectUri)
                parameter("code_challenge", pkce.codeChallenge)
                parameter("code_challenge_method", pkce.codeChallengeMethod)
                headers {
                    append("Accept-API-Version", "resource=2.1, protocol=1.0")
                    append(cookie, session.value)
                }
            }
            if (response.status == HttpStatusCode.Found) { // Check if the status is redirect
                val locationHeader = response.headers[HttpHeaders.Location]
                locationHeader?.let {
                    Url(it).parameters["code"]?.let { code ->
                        return AuthCode(code, pkce.codeVerifier)
                    }
                }
            }
            throw ApiException(response.status.value, response.body())
        }

        override suspend fun endSession(
            oidcConfig: OidcConfig<Unit>,
            idToken: String,
        ): Boolean {
            // Since we don't have the Session token, let DaVinci handle the signoff
            return true
        }
    }
}
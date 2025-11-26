/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.oidc.agent

import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import com.pingidentity.browser.BrowserLauncher
import com.pingidentity.oidc.Agent
import com.pingidentity.oidc.AuthCode
import com.pingidentity.oidc.Constants.ACR_VALUES
import com.pingidentity.oidc.Constants.CLIENT_ID
import com.pingidentity.oidc.Constants.CODE
import com.pingidentity.oidc.Constants.CODE_CHALLENGE
import com.pingidentity.oidc.Constants.CODE_CHALLENGE_METHOD
import com.pingidentity.oidc.Constants.DISPLAY
import com.pingidentity.oidc.Constants.ID_TOKEN_HINT
import com.pingidentity.oidc.Constants.LOGIN_HINT
import com.pingidentity.oidc.Constants.NONCE
import com.pingidentity.oidc.Constants.POST_LOGOUT_REDIRECT_URI
import com.pingidentity.oidc.Constants.PROMPT
import com.pingidentity.oidc.Constants.REDIRECT_URI
import com.pingidentity.oidc.Constants.RESPONSE_TYPE
import com.pingidentity.oidc.Constants.SCOPE
import com.pingidentity.oidc.Constants.STATE
import com.pingidentity.oidc.Constants.UI_LOCATES
import com.pingidentity.oidc.OidcConfig
import com.pingidentity.oidc.Pkce
import com.pingidentity.utils.PingDsl
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import java.net.URL

/**
 * This class is used to configure the browser for OpenID Connect operations.
 */
@PingDsl
class BrowserConfig {
    var customTab: (CustomTabsIntent.Builder).() -> Unit = {}
    var intentCustomizer: Intent.() -> Unit = {}
}

/**
 * This object is an agent that handles OpenID Connect operations in a browser.
 */
var browser =
    object : Agent<BrowserConfig> {
        /**
         * Returns a new instance of BrowserConfig.
         */
        override fun config() = ::BrowserConfig

        /**
         * Ends the session.
         *
         * @param oidcConfig The configuration for the OpenID Connect client.
         * @param idToken The ID token for the session.
         * @return A boolean indicating whether the session was ended successfully.
         */
        override suspend fun endSession(
            oidcConfig: OidcConfig<BrowserConfig>,
            idToken: String,
        ): Boolean {
            return if (oidcConfig.oidcClientConfig.signOutRedirectUri != null) {
                val builder =
                    Uri.parse(oidcConfig.oidcClientConfig.openId.endSessionEndpoint).buildUpon()
                        .appendQueryParameter(ID_TOKEN_HINT, idToken)
                        .appendQueryParameter(
                            POST_LOGOUT_REDIRECT_URI,
                            oidcConfig.oidcClientConfig.signOutRedirectUri
                        )
                val result = BrowserLauncher.launch(URL(builder.build().toString()))
                result.isSuccess
            } else {
                var endpoint = oidcConfig.oidcClientConfig.openId.endSessionEndpoint
                oidcConfig.oidcClientConfig.openId.pingEndIdpSessionEndpoint.let {
                    if (it.isNotBlank()) {
                        endpoint = it
                    }
                }
                val response = oidcConfig.oidcClientConfig.httpClient.get(endpoint) {
                    headers {
                        append(HttpHeaders.Accept, "application/json")
                    }
                    url {
                        parameters.append(ID_TOKEN_HINT, idToken)
                        parameters.append(CLIENT_ID, oidcConfig.oidcClientConfig.clientId)
                    }
                }
                return response.status.isSuccess()
            }
        }

        /**
         * Starts the authorization process.
         *
         * @param oidcConfig The configuration for the OpenID Connect client.
         * @return A Result containing the authorization response or an error.
         */
        override suspend fun authorize(oidcConfig: OidcConfig<BrowserConfig>): AuthCode {
            BrowserLauncher.customTabsCustomizer = oidcConfig.config.customTab
            BrowserLauncher.intentCustomizer = oidcConfig.config.intentCustomizer
            BrowserLauncher.logger = oidcConfig.oidcClientConfig.logger

            val builder =
                Uri.parse(oidcConfig.oidcClientConfig.openId.authorizationEndpoint).buildUpon()
                    .appendQueryParameter(CLIENT_ID, oidcConfig.oidcClientConfig.clientId)
                    .appendQueryParameter(RESPONSE_TYPE, CODE)
                    .appendQueryParameter(REDIRECT_URI, oidcConfig.oidcClientConfig.redirectUri)

            oidcConfig.oidcClientConfig.scopes.let {
                builder.appendQueryParameter(SCOPE, it.joinToString(" "))
            }
            oidcConfig.oidcClientConfig.state?.let {
                builder.appendQueryParameter(STATE, it)
            }
            oidcConfig.oidcClientConfig.nonce?.let {
                builder.appendQueryParameter(NONCE, it)
            }
            oidcConfig.oidcClientConfig.display?.let {
                builder.appendQueryParameter(DISPLAY, it)
            }
            oidcConfig.oidcClientConfig.prompt?.let {
                builder.appendQueryParameter(PROMPT, it)
            }
            oidcConfig.oidcClientConfig.uiLocales?.let {
                builder.appendQueryParameter(UI_LOCATES, it)
            }
            oidcConfig.oidcClientConfig.loginHint?.let {
                builder.appendQueryParameter(LOGIN_HINT, it)
            }
            oidcConfig.oidcClientConfig.additionalParameters.let {
                it.forEach { (key, value) ->
                    builder.appendQueryParameter(key, value)
                }
            }
            oidcConfig.oidcClientConfig.acrValues?.let {
                builder.appendQueryParameter(ACR_VALUES, it)
            }

            val pkce = Pkce.generate()

            builder.appendQueryParameter(CODE_CHALLENGE, pkce.codeChallenge)
                .appendQueryParameter(CODE_CHALLENGE_METHOD, pkce.codeChallengeMethod);

            val result = BrowserLauncher.launch(URL(builder.build().toString()))
            val uri = result.getOrThrow()
            val code = uri.getQueryParameter(CODE)
                ?: throw IllegalStateException("No authorization code found in response")
            return AuthCode(code, pkce.codeVerifier)

        }
    }

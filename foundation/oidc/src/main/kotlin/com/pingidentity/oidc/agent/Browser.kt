/*
 * Copyright (c) 2024 - 2025 Ping Identity. All rights reserved.
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
import com.pingidentity.oidc.CLIENT_ID
import com.pingidentity.oidc.ID_TOKEN_HINT
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
                        .appendQueryParameter("id_token_hint", idToken)
                        .appendQueryParameter(
                            "post_logout_redirect_uri",
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
                    .appendQueryParameter("client_id", oidcConfig.oidcClientConfig.clientId)
                    .appendQueryParameter("response_type", "code")
                    .appendQueryParameter("redirect_uri", oidcConfig.oidcClientConfig.redirectUri)

            oidcConfig.oidcClientConfig.scopes.let {
                builder.appendQueryParameter("scope", it.joinToString(" "))
            }
            oidcConfig.oidcClientConfig.state?.let {
                builder.appendQueryParameter("state", it)
            }
            oidcConfig.oidcClientConfig.nonce?.let {
                builder.appendQueryParameter("nonce", it)
            }
            oidcConfig.oidcClientConfig.display?.let {
                builder.appendQueryParameter("display", it)
            }
            oidcConfig.oidcClientConfig.prompt?.let {
                builder.appendQueryParameter("prompt", it)
            }
            oidcConfig.oidcClientConfig.uiLocales?.let {
                builder.appendQueryParameter("ui_locales", it)
            }
            oidcConfig.oidcClientConfig.loginHint?.let {
                builder.appendQueryParameter("login_hint", it)
            }
            oidcConfig.oidcClientConfig.additionalParameters.let {
                it.forEach { (key, value) ->
                    builder.appendQueryParameter(key, value)
                }
            }

            val pkce = Pkce.generate()

            builder.appendQueryParameter("code_challenge", pkce.codeChallenge)
                .appendQueryParameter("code_challenge_method", pkce.codeChallengeMethod);

            val result = BrowserLauncher.launch(URL(builder.build().toString()))
            val uri = result.getOrThrow()
            val code = uri.getQueryParameter("code")
                ?: throw IllegalStateException("No authorization code found in response")
            return AuthCode(code, pkce.codeVerifier)

        }
    }

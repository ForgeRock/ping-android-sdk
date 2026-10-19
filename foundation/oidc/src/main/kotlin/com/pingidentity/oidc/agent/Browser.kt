/*
 * Copyright (c) 2024 - 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.oidc.agent

import android.content.Intent
import androidx.browser.customtabs.CustomTabsIntent
import com.pingidentity.browser.BrowserLauncher
import com.pingidentity.network.isSuccess
import com.pingidentity.oidc.Agent
import com.pingidentity.oidc.AuthCode
import com.pingidentity.oidc.Constants.CLIENT_ID
import com.pingidentity.oidc.Constants.CODE
import com.pingidentity.oidc.Constants.ID_TOKEN_HINT
import com.pingidentity.oidc.Constants.POST_LOGOUT_REDIRECT_URI
import com.pingidentity.oidc.OidcConfig
import com.pingidentity.oidc.module.buildAuthorizeParams
import com.pingidentity.oidc.Pkce
import com.pingidentity.utils.PingDsl
import java.net.URL
import androidx.core.net.toUri

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
                    oidcConfig.oidcClientConfig.openId.endSessionEndpoint.toUri().buildUpon()
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
                val response = oidcConfig.oidcClientConfig.httpClient.request {
                    url = endpoint
                    header("Accept", "application/json")
                    parameter(ID_TOKEN_HINT, idToken)
                    parameter(CLIENT_ID, oidcConfig.oidcClientConfig.clientId)
                }
                response.status.isSuccess()
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

            val pkce = Pkce.generate()
            val params = with(oidcConfig.oidcClientConfig) {
                buildAuthorizeParams(pkce)
            }
            val builder =
                oidcConfig.oidcClientConfig.openId.authorizationEndpoint.toUri().buildUpon()
            params.forEach { (key, value) -> builder.appendQueryParameter(key, value) }

            val result = BrowserLauncher.launch(URL(builder.build().toString()))
            val uri = result.getOrThrow()
            val code = uri.getQueryParameter(CODE)
                ?: throw IllegalStateException("No authorization code found in response")
            return AuthCode(code, pkce.codeVerifier)

        }
    }

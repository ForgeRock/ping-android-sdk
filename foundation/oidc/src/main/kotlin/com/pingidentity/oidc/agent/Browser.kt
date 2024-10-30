/*
 * Copyright (c) 2024. PingIdentity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.oidc.agent

import androidx.browser.customtabs.CustomTabsIntent
import com.pingidentity.oidc.Agent
import com.pingidentity.oidc.CLIENT_ID
import com.pingidentity.oidc.ID_TOKEN_HINT
import com.pingidentity.oidc.OidcConfig
import com.pingidentity.utils.PingDsl
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.isSuccess
import net.openid.appauth.AppAuthConfiguration

/**
 * This class is used to configure the browser for OpenID Connect operations.
 */
@PingDsl
class BrowserConfig {
    var customTab: (CustomTabsIntent.Builder).() -> Unit = {}
    var appAuthConfiguration: (AppAuthConfiguration.Builder).() -> Unit = {}
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
                BrowserLauncherActivity.endSession(oidcConfig, idToken)
            } else {
                var endpoint = oidcConfig.oidcClientConfig.openId.endSessionEndpoint
                oidcConfig.oidcClientConfig.openId.pingEndIdpSessionEndpoint.let {
                    if (it.isNotBlank()) {
                        endpoint = it
                    }
                }
                val response = oidcConfig.oidcClientConfig.httpClient.get(endpoint) {
                    headers {
                        append("Accept", "application/json")
                    }
                    url {
                        parameters.append(ID_TOKEN_HINT, idToken)
                        parameters.append(CLIENT_ID, oidcConfig.oidcClientConfig.clientId)
                    }
                }
                if (response.status.isSuccess()) {
                    return true
                } else {
                    if (response.status == HttpStatusCode.Found) {
                        response.headers["location"]?.let {
                            val error = Url(it).parameters["error"]
                            if (error != null) {
                                oidcConfig.oidcClientConfig.logger.w("Error during end session: $error")
                                return false
                            } else {
                                return true
                            }
                        }
                    }
                    return false
                }
            }
        }

        /**
         * Starts the authorization process.
         *
         * @param oidcConfig The configuration for the OpenID Connect client.
         * @return A Result containing the authorization response or an error.
         */
        override suspend fun authorize(oidcConfig: OidcConfig<BrowserConfig>) =
            BrowserLauncherActivity.authorize(oidcConfig)
    }

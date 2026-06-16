/*
 * Copyright (c) 2025 - 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.oidc

import com.pingidentity.browser.BrowserLauncher.authTabCustomizer
import com.pingidentity.browser.BrowserLauncher.customTabsCustomizer
import com.pingidentity.oidc.module.Oidc
import com.pingidentity.oidc.module.OidcFlow
import com.pingidentity.oidc.module.PARAMETERS
import com.pingidentity.oidc.module.Web
import com.pingidentity.oidc.module.oidcClientConfig
import com.pingidentity.oidc.module.oidcUser
import com.pingidentity.oidc.module.prepareUser
import com.pingidentity.oidc.module.user
import com.pingidentity.orchestrate.FailureNode
import com.pingidentity.orchestrate.SuccessNode
import com.pingidentity.orchestrate.WorkflowConfig
import kotlinx.serialization.json.JsonObject

/**
 * OIDC Web configuration class.
 * Provide configuration for the OIDC web module.
 */
class OidcWebClientConfig : WorkflowConfig() {
    // additional configuration for example browser settings
}

/**
 * OIDC Web class.
 * This class provides the OIDC authorization flow with a browser.
 *
 * @param config The OIDC web configuration.
 */
class OidcWebClient(val config: OidcWebClientConfig) {

    private var oidcFlow = OidcFlow(config)

    /**
     * Starts the OIDC authorization flow.
     */
    suspend fun authorize(parameters: Parameters.() -> Unit = {} ): Result<User> {
        val params = Parameters(mutableMapOf()).apply(parameters)

        oidcFlow.start {
            // Set the parameters for the OIDC flow
            PARAMETERS to params
        }.apply {
            return when (this) {
                is SuccessNode -> Result.success(this.user)
                is FailureNode -> Result.failure(this.cause)
                else -> Result.failure(IllegalStateException("Unexpected node type: ${this::class.simpleName}"))
            }
        }
    }

    /**
     * Retrieves the existing [User]
     * @return The user if found, otherwise null.
     */
    suspend fun user(): User? = oidcFlow.oidcUser {
        oidcClientConfig().storage().get()?.let {
            prepareUser(OidcUser(oidcClientConfig()))
        }
    }
}

/**
 * Creates an instance of [OidcWebClient] with the provided configuration block.
 *
 * @param block The configuration block to apply to the OIDC web configuration.
 * @return An instance of [OidcWebClient].
 */
fun OidcWebClient(block: OidcWebClientConfig.() -> Unit = {}): OidcWebClient {
    val config = OidcWebClientConfig()
    config.apply {
        config.module(Web) // register the web module
    }
    config.apply(block) // apply the configuration block
    return OidcWebClient(config)
}

/**
 * Creates an instance of [OidcWebClient] from a JSON configuration.
 *
 * Required OIDC fields are nested under `oidc`; web UI settings under `web`. Example:
 * ```json
 * {
 *   "timeout": 30000,
 *   "log": "STANDARD",
 *   "oidc": {
 *     "clientId": "my-client-id",
 *     "discoveryEndpoint": "https://auth.example.com/.well-known/openid-configuration",
 *     "scopes": ["openid", "profile"],
 *     "redirectUri": "myapp://oauth2redirect",
 *     "signOutRedirectUri": "myapp://logout",
 *     "refreshThreshold": 60,
 *     "loginHint": "user@example.com",
 *     "state": "custom-state",
 *     "nonce": "custom-nonce",
 *     "display": "page",
 *     "prompt": "login",
 *     "uiLocales": "en-US",
 *     "acrValues": "Level3",
 *     "par": true,
 *     "additionalParameters": { "max_age": "3600" },
 *     "openId": {
 *       "authorizationEndpoint": "https://auth.example.com/authorize",
 *       "tokenEndpoint": "https://auth.example.com/token",
 *       "userinfoEndpoint": "https://auth.example.com/userinfo",
 *       "endSessionEndpoint": "https://auth.example.com/logout",
 *       "revocationEndpoint": "https://auth.example.com/revoke"
 *     }
 *   },
 * }
 * ```
 *
 * @param json The JSON configuration object.
 * @return A [Result] containing the [OidcWebClient] or an exception if the configuration is invalid.
 */
fun OidcWebClient(json: JsonObject): Result<OidcWebClient> {
    return runCatching {
        OidcWebClient {
            val configParser = JsonConfigParser(json)
            logger = configParser.logLevel()
            timeout = configParser.timeoutMillis()

            val oidcConfigParser = JsonConfigParser(configParser.required<JsonObject>(JsonConfigKey.OIDC))
            module(Oidc) {
                discoveryEndpoint = oidcConfigParser.required<String>(JsonConfigKey.DISCOVERY_ENDPOINT)
                clientId = oidcConfigParser.required<String>(JsonConfigKey.CLIENT_ID)
                scopes = oidcConfigParser.scopeSet(JsonConfigKey.SCOPES)
                redirectUri = oidcConfigParser.required<String>(JsonConfigKey.REDIRECT_URI)
                update(oidcConfigParser)
            }
        }
    }
}

class Parameters(val map: MutableMap<String, String>) : MutableMap<String, String> by map {
    infix fun String.to(value: String) {
        map[this] = value
    }
}
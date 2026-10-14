/*
 * Copyright (c) 2025 - 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.oidc

import com.pingidentity.oidc.module.Oidc
import com.pingidentity.oidc.module.OidcFlow
import com.pingidentity.oidc.module.PARAMETERS
import com.pingidentity.oidc.module.Web
import com.pingidentity.oidc.module.oidcClientConfig
import com.pingidentity.oidc.module.oidcUser
import com.pingidentity.oidc.module.prepareUser
import com.pingidentity.oidc.module.toAuthorizationDetailsParam
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
 *     "authorizationDetails": [
 *       {
 *         "type": "payment_initiation",
 *         "actions": ["initiate", "status"],
 *         "locations": ["https://example.com/payments"]
 *       }
 *     ],
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

    /**
     * Adds Rich Authorization Request details (RFC 9396 §2) to this specific authorization
     * request. The list is serialized eagerly into the `authorization_details` request
     * parameter (and into the PAR form body when PAR is enabled).
     *
     * Each entry models one authorization detail object (RFC 9396 §2.2); type-specific members
     * beyond the common data fields are carried through [AuthorizationDetail.additionalFields].
     *
     * Example:
     * ```kotlin
     * web.authorize {
     *     authorizationDetails(
     *         AuthorizationDetail(
     *             type = "payment_initiation",
     *             actions = listOf("initiate", "status"),
     *             locations = listOf("https://example.com/payments"),
     *         )
     *     )
     * }
     * ```
     *
     * A per-call value replaces every configuration-level value for this request: the typed
     * [OidcClientConfig.authorizationDetails] list and a hand-serialized `authorization_details`
     * in [OidcClientConfig.additionalParameters] are both suppressed, so the parameter is emitted
     * exactly once. An empty list is a no-op: the configuration-level value still applies. This
     * function and a raw-string `"authorization_details" to value` entry in the per-call
     * parameters share the same parameter namespace — the last write within the block wins — so
     * the raw string remains the low-level escape hatch for shapes this API does not model.
     *
     * @param details The authorization detail entries for this request.
     */
    fun authorizationDetails(details: List<AuthorizationDetail>) {
        if (details.isNotEmpty()) {
            map[Constants.AUTHORIZATION_DETAILS] = details.toAuthorizationDetailsParam()
        }
    }

    /**
     * Adds Rich Authorization Request details (RFC 9396 §2) to this specific authorization
     * request. Convenience overload of [authorizationDetails] taking the entries directly.
     *
     * @param details The authorization detail entries for this request.
     */
    fun authorizationDetails(vararg details: AuthorizationDetail) {
        authorizationDetails(details.toList())
    }
}
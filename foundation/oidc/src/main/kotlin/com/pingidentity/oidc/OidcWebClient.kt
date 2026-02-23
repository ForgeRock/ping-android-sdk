/*
 * Copyright (c) 2025 - 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.oidc

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

    companion object {
        /**
         * Creates an instance of [OidcWebClient] with the provided configuration block.
         *
         * @param block The configuration block to apply to the OIDC web configuration.
         * @return An instance of [OidcWebClient].
         */
        operator fun invoke(block: OidcWebClientConfig.() -> Unit = {}): OidcWebClient {
            val config = OidcWebClientConfig()
            config.apply {
                config.module(Web) // register the web module
            }
            config.apply(block) // apply the configuration block
            return OidcWebClient(config)
        }
    }
}

class Parameters(val map: MutableMap<String, String>) : MutableMap<String, String> by map {
    infix fun String.to(value: String) {
        map[this] = value
    }
}
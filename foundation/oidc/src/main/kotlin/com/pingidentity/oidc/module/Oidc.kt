/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.oidc.module

import com.pingidentity.oidc.Agent
import com.pingidentity.oidc.AuthCode
import com.pingidentity.oidc.Constants.CLIENT_ID
import com.pingidentity.oidc.Constants.ID_TOKEN_HINT
import com.pingidentity.oidc.DefaultAgent
import com.pingidentity.oidc.OidcClient
import com.pingidentity.oidc.OidcClientConfig
import com.pingidentity.oidc.OidcConfig
import com.pingidentity.oidc.OidcUser
import com.pingidentity.oidc.Pkce
import com.pingidentity.oidc.exception.AuthorizeException
import com.pingidentity.orchestrate.Module
import com.pingidentity.orchestrate.Session
import com.pingidentity.orchestrate.SuccessNode

/**
 * Constant for PKCE.
 */
private const val PKCE = "com.pingidentity.oidc.PKCE"
private const val OIDC_CONFIG = "com.pingidentity.oidc.OidcClientConfig"
internal const val PARAMETERS = "com.pingidentity.oidc.PARAMETERS"

/**
 * Oidc module for Workflow engine
 */
val Oidc =
    Module.of(::OidcClientConfig) {

        /**
         * Initializes the module.
         */
        init {
            // propagate the configuration from workflow to the module
            config.httpClient = httpClient
            config.logger = logger

            // Store the OIDC configuration in the shared context
            sharedContext[OIDC_CONFIG] = config

            //Override the agent setting with DefaultAgent
            config.updateAgent(DefaultAgent)
            config.init()

        }

        start { request ->
            // When user starting the flow again, revoke previous token if exists
            workflow.oidcUser().revoke()

            val pkce = Pkce.generate()
            flowContext[PKCE] = pkce

            val parameters = flowContext.getValue<Map<String, String>>(PARAMETERS) ?: emptyMap()
            config.populateRequest(request, parameters, pkce)
        }

        success { success ->
            val clone =
                config.clone().also {
                    it.updateAgent(agent(success.session, flowContext[PKCE] as Pkce))
                }
            val user = OidcUser(clone).also {
                // Fetch the access token from the session, ignore the result, still consider
                // the user logged in
                it.token()
            }
            SuccessNode(success.input, workflow.prepareUser(user, success.session))
        }

        signOff { request ->
            // Updated
            val endpoint =
                if (workflow.isWeb() && config.openId.pingEndIdpSessionEndpoint.isNotEmpty()) {
                    config.openId.pingEndIdpSessionEndpoint
                } else {
                    config.openId.endSessionEndpoint
                }
            request.url(endpoint)
            OidcClient(config).endSession {
                request.parameter(ID_TOKEN_HINT, it)
                request.parameter(CLIENT_ID, config.clientId)
                true
            }
            request
        }
    }

/**
 * Creates an OIDC agent for the given session and PKCE.
 * This agent is used to handle the authorization flow with the session which contains the auth code.
 *
 * @param session The session containing the auth code.
 * @param pkce The PKCE.
 * @return The agent.
 */
internal fun agent(
    session: Session,
    pkce: Pkce?,
): Agent<Unit> {
    return object : Agent<Unit> {
        private var used = false

        override fun config(): () -> Unit = {}

        override suspend fun authorize(oidcConfig: OidcConfig<Unit>): AuthCode {
            if (session.value.isEmpty()) {
                throw AuthorizeException("Please start the authorization flow again.")
            }
            if (used) {
                throw AuthorizeException("Auth code already used, please start authorization flow again.")
            } else {
                used = true
                return session.authCode(pkce)
            }
        }

        override suspend fun endSession(
            oidcConfig: OidcConfig<Unit>,
            idToken: String,
        ): Boolean {
            // Since we don't have the Session token, let the flow handle the signoff
            return true
        }
    }
}

/**
 * Extension function to convert the session to an [AuthCode]
 */
internal fun Session.authCode(pkce: Pkce?): AuthCode {
    // parse the response and return the auth code
    return AuthCode(code = value, pkce?.codeVerifier)
}

/**
 * Extension function to get the OidcClientConfig.
 */
internal fun OidcFlow.oidcClientConfig(): OidcClientConfig {
    sharedContext.getValue<OidcClientConfig>(OIDC_CONFIG)?.let {
        return it
    }
    throw IllegalStateException("Oidc module is not initialized")
}

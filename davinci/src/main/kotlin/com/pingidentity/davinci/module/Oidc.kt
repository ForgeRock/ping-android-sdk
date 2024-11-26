/*
 * Copyright (c) 2024. PingIdentity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci.module

import com.pingidentity.davinci.plugin.DaVinci
import com.pingidentity.davinci.prepareUser
import com.pingidentity.davinci.user
import com.pingidentity.oidc.Agent
import com.pingidentity.oidc.AuthCode
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
private const val PKCE = "com.pingidentity.davinci.PKCE"
private const val OIDC_CONFIG = "com.pingidentity.davinci.OidcClientConfig"


/**
 * Extension property to get the Oidc module.
 */
val Oidc =
    Module.of(::OidcClientConfig) {

        /**
         * Initializes the module.
         */
        init {

            logger.i(
                "Initializing the OIDC module: the httpClient, logger, " +
                        "and agent will be propagated from DaVinci. " +
                        "Any explicit settings for these in the OIDC module will be ignored."
            )
            // propagate the configuration from workflow to the module
            config.httpClient = httpClient
            config.logger = logger
            sharedContext[OIDC_CONFIG] = config

            //Override the agent setting
            config.updateAgent(DefaultAgent)
            config.init()
        }

        /**
         * Starts the module.
         */
        start { request ->

            // When user starting the flow again, revoke previous token if exists
            workflow.user()?.revoke()

            val pkce = Pkce.generate()
            flowContext[PKCE] = pkce
            config.populateRequest(request, pkce)
        }

        /**
         * Handles success of the module.
         */
        success { success ->
            val clone =
                config.clone().also {
                    it.updateAgent(agent(success.session, flowContext[PKCE] as Pkce))
                }
            SuccessNode(success.input, prepareUser(workflow, OidcUser(clone), success.session))
        }

        /**
         * Handles sign off of the module.
         */
        signOff { request ->
            request.url(config.openId.endSessionEndpoint)
            OidcClient(config).endSession {
                request.parameter("id_token_hint", it)
                request.parameter("client_id", config.clientId)
                true
            }
            request
        }
    }

/**
 * Function to get the agent.
 *
 * @param session The session.
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
            //We don't get the state, The state may not be returned since this is primarily for
            //CSRF in redirect-based interactions, and pi.flow doesn't use redirect.
            if (session.value.isEmpty()) {
                throw AuthorizeException("Please start DaVinci flow to authenticate.")
            }
            if (used) {
                throw AuthorizeException("Auth code already used, please start DaVinci flow again.")
            } else {
                used = true
                return session.authCode(pkce)
            }
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
fun DaVinci.oidcClientConfig(): OidcClientConfig {
    sharedContext.getValue<OidcClientConfig>(OIDC_CONFIG)?.let {
        return it
    }
    throw IllegalStateException("Oidc module is not initialized")
}
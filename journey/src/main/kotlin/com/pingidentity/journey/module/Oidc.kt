/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.journey.module

import com.pingidentity.journey.Constants.OIDC_CLIENT
import com.pingidentity.journey.Journey
import com.pingidentity.journey.SSOToken
import com.pingidentity.journey.journey
import com.pingidentity.journey.options
import com.pingidentity.journey.prepareUser
import com.pingidentity.journey.user
import com.pingidentity.oidc.OidcClient
import com.pingidentity.oidc.OidcClientConfig
import com.pingidentity.oidc.OidcUser
import com.pingidentity.orchestrate.EmptySession
import com.pingidentity.orchestrate.Module
import com.pingidentity.orchestrate.SuccessNode
import kotlin.collections.set


// Defines the OIDC module for handling OpenID Connect (OIDC) flows
val Oidc =
    Module.of(::OidcClientConfig) {

        init {
            // Propagate the HTTP client and logger configuration from the workflow to the module
            config.httpClient = journey.config.httpClient
            config.logger = journey.config.logger
            config.init()

            val clone = config.clone().also {
                // Update the session agent with the current session or an empty session
                it.updateAgent(
                    sessionAgent(
                        journey.options.cookie,
                        { journey.deleteSession() }) {
                        journey.session() ?: EmptySession
                    })
            }

            // Store the OIDC client in the shared context
            sharedContext[OIDC_CLIENT] = OidcClient(clone)
        }

        // Defines the behavior when the module successfully completes
        success { success ->
            SuccessNode(
                success.input,
                prepareUser(
                    journey,
                    OidcUser(journey.oidcClient()),
                    success.session as SSOToken
                )
            )
        }

        // Defines the behavior for signing off the user
        signOff { request ->
            // Use the OIDC client to end the session
            journey.oidcClient().endSession()
            request
        }
    }

/**
 * Retrieves the OIDC client for the current journey.
 */
fun Journey.oidcClient(): OidcClient {
    sharedContext.getValue<OidcClient>(OIDC_CLIENT)?.let {
        return it
    }
    throw IllegalStateException("Oidc module is not initialized")
}
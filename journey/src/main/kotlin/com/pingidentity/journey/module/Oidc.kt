/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.journey.module

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

private const val OIDC_CLIENT_CONFIG = "com.pingidentity.journey.OIDC_CLIENT_CONFIG"

val Oidc =
    Module.of(::OidcClientConfig) {

        init {
            // propagate the configuration from workflow to the module
            config.httpClient = journey.config.httpClient
            config.logger = journey.config.logger

            sharedContext[OIDC_CLIENT_CONFIG] = config
            config.init()
        }

        start { request ->

            // When user starting the flow again, revoke previous token if exists
            journey.user()?.revoke()
            request
        }

        success { success ->
            SuccessNode(success.input,
                prepareUser(
                    journey,
                    OidcUser(journey.oidcClientConfig()),
                    success.session as SSOToken
                )
            )
        }

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

fun Journey.oidcClientConfig(): OidcClientConfig {
    sharedContext.getValue<OidcClientConfig>(OIDC_CLIENT_CONFIG)?.let {
        it.clone().also { clone ->
            clone.updateAgent(sessionAgent(options.cookie) {
                session() ?: EmptySession
            })
            return clone
        }
    }
    throw IllegalStateException("Oidc module is not initialized")
}
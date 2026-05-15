/*
 * Copyright (c) 2024 - 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.journey.module

import android.net.Uri
import com.pingidentity.exception.ApiException
import com.pingidentity.journey.Constants.ACCEPT
import com.pingidentity.journey.Constants.ALLOW
import com.pingidentity.journey.Constants.APPLICATION_JSON
import com.pingidentity.journey.Constants.CSRF
import com.pingidentity.journey.Constants.DECISION
import com.pingidentity.journey.Constants.OIDC_CLIENT
import com.pingidentity.journey.Journey
import com.pingidentity.journey.SSOToken
import com.pingidentity.journey.journey
import com.pingidentity.journey.options
import com.pingidentity.journey.prepareUser
import com.pingidentity.network.isSuccess
import com.pingidentity.oidc.Constants.USER_CODE
import com.pingidentity.oidc.OidcClient
import com.pingidentity.oidc.OidcClientConfig
import com.pingidentity.oidc.OidcUser
import com.pingidentity.oidc.module.VERIFICATION_URI_COMPLETE
import com.pingidentity.oidc.module.deviceUserCode
import com.pingidentity.orchestrate.EmptySession
import com.pingidentity.orchestrate.Module
import com.pingidentity.orchestrate.SuccessNode


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

            val successNode = SuccessNode(
                success.input,
                prepareUser(journey, OidcUser(journey.oidcClient()), success.session as SSOToken)
            )

            // Check if the user code is present before making the request
            val userCode = flowContext.deviceUserCode() ?: return@success successNode
            // The session value may be empty due to NoSession or re-run existing Journey,
            val existingSession  =
                if (success.session.value.isEmpty()) journey.session()
                    ?: success.session
                else success.session

            val response = httpClient.request {
                url = flowContext.getValue<Uri>(VERIFICATION_URI_COMPLETE).toString()
                header(ACCEPT, APPLICATION_JSON)
                header(journey.options.cookie, existingSession.value)
                form {
                    put(USER_CODE, userCode)
                    put(DECISION, ALLOW)
                    // csrf must equal the SSO token value (AM CSRF protection)
                    put(CSRF, existingSession.value)
                }
            }

            if (!response.status.isSuccess()) {
                logger.w("OidcDevice: device-user POST returned status ${response.status}, with body: ${response.body()}")
                throw ApiException(response.status, response.body())
            }

            logger.i("Device authorization succeeded for user_code=$userCode")
            successNode
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
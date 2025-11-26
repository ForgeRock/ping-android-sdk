/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.journey.module

import com.pingidentity.journey.Constants.ACCEPT_API_VERSION
import com.pingidentity.journey.Constants.RESOURCE31
import com.pingidentity.journey.Constants.SESSION_CONFIG
import com.pingidentity.journey.Journey
import com.pingidentity.journey.SSOToken
import com.pingidentity.journey.journey
import com.pingidentity.journey.options
import com.pingidentity.orchestrate.Module

/**
 * The Session module is responsible for managing the session state during the authentication journey.
 * It handles the initialization, storage, and retrieval of the session token.
 **/
val Session = Module.of(::SessionConfig) {

    init {
        config.logger = logger
        sharedContext[SESSION_CONFIG] = config
        config.init()
    }
    start { request ->
        journey.session()?.let { ssoToken ->
            // If the session is not empty, set it to the existing session
            request.header(journey.options.cookie, ssoToken.value)
        }
        request
    }

    next { _, request ->
        // If the session is empty, set it to the empty session
        journey.session()?.let { ssoToken ->
            // If the session is not empty, set it to the existing session
            request.header(journey.options.cookie, ssoToken.value)
        }
        request
    }

    success {
        //The session may be empty due to NoSession or reuse existing session
        if (it.session.value.isNotEmpty()) { // If the session is not empty, save it
            config.tokenStorage.save(it.session as SSOToken)
        }
        it
    }

    signOff { request ->
        val ssoToken = config.tokenStorage.get()
        //Sign off the session

        ssoToken?.let {
            request.url("${journey.options.serverUrl}/json/realms/${journey.options.realm}/sessions")
            request.parameter("_action", "logout")
            request.header(journey.options.cookie, it.value)
            request.header(ACCEPT_API_VERSION, RESOURCE31)
            request.body()
            config.tokenStorage.delete()
        } ?: throw IllegalStateException("Session not found")
        request
    }
}

/**
 * Function to retrieve the session token.
 * @return The session token if found, otherwise null.
 */
internal suspend fun Journey.session(): SSOToken? {
    sharedContext.getValue<SessionConfig>(SESSION_CONFIG)?.let {
        return it.tokenStorage.get()
    }
    return null
}

/**
 * Function to delete the session token.
 */
internal suspend fun Journey.deleteSession() {
    sharedContext.getValue<SessionConfig>(SESSION_CONFIG)?.tokenStorage?.delete()
}



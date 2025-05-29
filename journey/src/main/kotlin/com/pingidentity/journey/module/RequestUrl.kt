/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.journey.module

import com.pingidentity.journey.Constants.AUTH_INDEX_TYPE
import com.pingidentity.journey.Constants.AUTH_INDEX_VALUE
import com.pingidentity.journey.Constants.FORCE_AUTH
import com.pingidentity.journey.Constants.FORCE_AUTH_PARAM
import com.pingidentity.journey.Constants.NO_SESSION
import com.pingidentity.journey.Constants.NO_SESSION_PARAM
import com.pingidentity.journey.Constants.SERVICE
import com.pingidentity.journey.Constants.START_REQUEST
import com.pingidentity.journey.journey
import com.pingidentity.journey.options
import com.pingidentity.orchestrate.Module
import com.pingidentity.orchestrate.Request

/**
 * The RequestUrl module is responsible for initiating the authentication journey.
 * It sets the URL for the authentication request and includes any necessary parameters.
 */
val RequestUrl = Module.of {

    start { request ->
        request.url("${journey.options.serverUrl}/json/realms/${journey.options.realm}/authenticate")
        flowContext.getValue<Request.() -> Unit>(START_REQUEST)?.invoke(request) ?: run {
            // Default parameters
            request.parameter(AUTH_INDEX_TYPE, SERVICE)
            request.parameter(AUTH_INDEX_VALUE, "login")
        }
        flowContext.getValue<Boolean>(FORCE_AUTH)?.let {
            request.parameter(FORCE_AUTH_PARAM, it.toString())
        }
        flowContext.getValue<Boolean>(NO_SESSION)?.let {
            request.parameter(NO_SESSION_PARAM, it.toString())
        }
        request.body()
        request
    }

    next { _, request ->
        flowContext.getValue<Boolean>(NO_SESSION)?.let {
            request.parameter(NO_SESSION_PARAM, it.toString())
        }
        request
    }


    /*
    start { request ->
        request.url("${journey.options.serverUrl}/json/realms/${journey.options.realm}/authenticate")
        flowContext.getValue<Request.() -> Unit>(START_REQUEST)?.invoke(it) ?: {
            // Default parameters
            request.parameter(AUTH_INDEX_TYPE, SERVICE)
            request.parameter(AUTH_INDEX_VALUE, "login")
        }
        flowContext.getValue<Boolean>(FORCE_AUTH)?.let {
        }
        it.body()
        it
    }

     */
}
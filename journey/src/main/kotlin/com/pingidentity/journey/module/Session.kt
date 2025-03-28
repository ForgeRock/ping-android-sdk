/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.journey.module

import com.pingidentity.journey.Journey
import com.pingidentity.journey.SSOToken
import com.pingidentity.orchestrate.EmptySession
import com.pingidentity.orchestrate.Module


private const val SESSION_CONFIG = "com.pingidentity.journey.SESSION_CONFIG"

val Session = Module.of(::SessionConfig) {

    init {
        sharedContext[SESSION_CONFIG] = config
        config.init()
    }

    success {
        //The session may be empty due to NoSession or reuse existing session
        if (it.session != EmptySession) { // If the session is not empty, save it
            config.storage.save(it.session as SSOToken)
        }
        it
    }

    signOff {
        config.storage.delete()
        it
    }
}

suspend fun Journey.session(): SSOToken? {
    try {
        init()
    } catch (e: Exception) {
        config.logger.e("Failed to initialize Journey", e)
    }

    sharedContext[SESSION_CONFIG]?.let { config ->
        config as SessionConfig
        config.storage.get()?.let {
            return it
        }
    }
    return null
}



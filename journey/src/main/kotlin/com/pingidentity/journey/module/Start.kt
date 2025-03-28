/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.journey.module

import com.pingidentity.journey.journey
import com.pingidentity.journey.options
import com.pingidentity.orchestrate.Module

val Start = Module.of {

    start {
        if (journey.options.hasJourneyName() && it.hasUrl.not()) {
            it.url("${journey.options.serverUrl}/json/realms/${journey.options.realm}/authenticate")
            it.parameter("authIndexType", "service")
            it.parameter("authIndexValue", journey.options.journeyName)
            if (journey.options.forceAuth) it.parameter("ForceAuth", "true")
            if (journey.options.noSession) it.parameter("noSession", "true")
            it.body()
        }
        it
    }

}
/*
 * Copyright (c) 2024. PingIdentity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.journey

import com.pingidentity.journey.module.NodeTransform
import com.pingidentity.journey.module.Session
import com.pingidentity.journey.module.Start
import com.pingidentity.orchestrate.Node
import com.pingidentity.orchestrate.OverrideMode
import com.pingidentity.orchestrate.Request
import com.pingidentity.orchestrate.Setup
import com.pingidentity.orchestrate.Workflow
import com.pingidentity.orchestrate.WorkflowConfig
import com.pingidentity.orchestrate.module.Cookie
import com.pingidentity.orchestrate.module.CustomHeader

typealias Journey = Workflow

class JourneyOptions : WorkflowConfig() {
    lateinit var serverUrl: String
    var realm: String = "root"
    var cookie: String = "iPlanetDirectoryPro"
    var forceAuth = false
    var noSession = false
    lateinit var journeyName: String

    fun hasJourneyName(): Boolean {
        return ::journeyName.isInitialized
    }
}

val Journey.options: JourneyOptions
    get() = this.config as JourneyOptions

val <T : Any> Setup<T>.journey: Journey
    get() = this.workflow

suspend fun Journey.start(journeyName: String): Node {
    val request = Request().apply {
        url("${options.serverUrl}/json/realms/${options.realm}/authenticate")
        parameter("authIndexType", "service")
        parameter("authIndexValue", journeyName)
        if (options.forceAuth) parameter("ForceAuth", "true")
        if (options.noSession) parameter("noSession", "true")
    }

    request.body()
    return start(request)
}


fun Journey(block: JourneyOptions.() -> Unit = {}): Journey {
    val config = JourneyOptions()

    // Apply default
    config.apply {
        module(CustomHeader) {
            header("Accept-API-Version", "resource=2.1, protocol=1.0")
        }
        module(Start)
        module(Session) // Persist the Session
        module(NodeTransform)
    }

    // Apply custom
    config.apply(block)

    config.apply {
        module(Cookie, mode = OverrideMode.IGNORE) {//Ignore if already exist
            //config.cookie is only available after config.apply(block)
            persist = mutableListOf(config.cookie)
        }
    }

    return Journey(config)
}

/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.journey

import android.net.Uri
import com.pingidentity.journey.Constants.ACCEPT_API_VERSION
import com.pingidentity.journey.Constants.AUTH_INDEX_TYPE
import com.pingidentity.journey.Constants.AUTH_INDEX_VALUE
import com.pingidentity.journey.Constants.COOKIE
import com.pingidentity.journey.Constants.FORCE_AUTH
import com.pingidentity.journey.Constants.NO_SESSION
import com.pingidentity.journey.Constants.REALM
import com.pingidentity.journey.Constants.RESOURCE_2_1_PROTOCOL_1_0
import com.pingidentity.journey.Constants.SERVICE
import com.pingidentity.journey.Constants.START_REQUEST
import com.pingidentity.journey.Constants.SUSPENDED_ID
import com.pingidentity.journey.module.NodeTransform
import com.pingidentity.journey.module.Session
import com.pingidentity.journey.module.RequestUrl
import com.pingidentity.orchestrate.Node
import com.pingidentity.orchestrate.Request
import com.pingidentity.orchestrate.Setup
import com.pingidentity.orchestrate.SharedContext
import com.pingidentity.orchestrate.Workflow
import com.pingidentity.orchestrate.WorkflowConfig
import com.pingidentity.orchestrate.module.CustomHeader

typealias Journey = Workflow

/**
 * JourneyConfig is a configuration class for the Journey workflow.
 *
 * @property serverUrl The URL of the server.
 * @property realm The realm to use (default is "root").
 * @property cookie The cookie name (default is "iPlanetDirectoryPro").
 */
class JourneyConfig : WorkflowConfig() {
    lateinit var serverUrl: String
    var realm: String = REALM
    var cookie: String = COOKIE
}

/**
 * Extension property to get the server URL from the JourneyConfig.
 */
val Journey.options: JourneyConfig
    get() = this.config as JourneyConfig

/**
 * Extension property to get the workflow from the Setup.
 */
val <T : Any> Setup<T>.journey: Journey
    get() = this.workflow

/**
 * Starts the authentication journey with the specified options.
 *
 * @param journeyName The name of the journey to start.
 * @param option A lambda function to configure additional options for the journey.
 * @return A Node representing the result of the journey start.
 */
suspend fun Journey.start(journeyName: String, option: Option.() -> Unit = {}): Node {
    return start {
        START_REQUEST to fun Request.() {
            parameter(AUTH_INDEX_TYPE, SERVICE)
            parameter(AUTH_INDEX_VALUE, journeyName)
        }
        option(this, option)
    }
}

/**
 * Resumes the authentication journey with the specified URI and options.
 *
 * @param uri The URI to resume the journey from.
 * @param option A lambda function to configure additional options for the journey.
 * @return A Node representing the result of the journey resume.
 */
suspend fun Journey.resume(uri: Uri, option: Option.() -> Unit = {}): Node {
    return start {
        uri.getQueryParameter(SUSPENDED_ID)?.let {
            START_REQUEST to fun Request.() {
                parameter(SUSPENDED_ID, it)
            }
        }
        option(this, option)
    }
}

private fun option(context: SharedContext, block: Option.() -> Unit = {}) {
    val option = Option().apply(block)
    context.apply {
        FORCE_AUTH to option.forceAuth
        NO_SESSION to option.noSession
    }
}

/**
 * Creates a new Journey instance with the provided configuration block.
 * @param block The configuration block for the Journey.
 * @return A new Journey instance.
 */
fun Journey(block: JourneyConfig.() -> Unit = {}): Journey {
    val config = JourneyConfig()

    // Apply default
    config.apply {
        module(CustomHeader) {
            header(ACCEPT_API_VERSION, RESOURCE_2_1_PROTOCOL_1_0)
        }
        module(RequestUrl)
        module(Session) // Persist the Session after success
        module(NodeTransform)
    }

    // Apply custom
    config.apply(block)

    /*
    config.apply {
        module(Cookie, mode = OverrideMode.IGNORE) {//Ignore if already exist
            //config.cookie is only available after config.apply(block)
            persist = mutableListOf(config.cookie)
        }
    }
     */

    return Journey(config)
}

/**
 * Option class to configure additional options for the journey.
 *
 * @property forceAuth Whether to force authentication (default is false).
 * @property noSession Whether to return new session (default is false).
 */
data class Option(var forceAuth: Boolean = false, var noSession: Boolean = false)
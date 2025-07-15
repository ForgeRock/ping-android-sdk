/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.journey

import com.pingidentity.journey.callback.NameCallback
import com.pingidentity.journey.callback.PasswordCallback
import com.pingidentity.journey.module.Oidc
import com.pingidentity.journey.plugin.callbacks
import com.pingidentity.logger.Logger
import com.pingidentity.logger.STANDARD
import com.pingidentity.orchestrate.ContinueNode
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before

open class BaseJourneyTest {
    /*
     Uncomment the following line to set a global timeout for all tests
     This is useful for debugging long-running tests or preventing hangs
     */
//    @get:Rule
//    val globalTimeout: Timeout = Timeout.seconds(360)


    // Common test properties
    protected val logger: Logger = Logger.STANDARD

    // ToDo: Move these to a config file or environment variables
    protected val SERVER_URL: String = "https://openam-sdks.forgeblocks.com/am"
    protected val REALM: String = "alpha"
    protected val COOKIE: String = "5421aeddf91aa20"
    protected var TREE: String = "Login"
    protected val USERNAME: String = "sdkuser"
    protected val PASSWORD: String = "password"
    protected val CLIENT_ID: String = "AndroidTest"
    protected val REDIRECT_URI: String = "org.forgerock.demo:/oauth2redirect"
    protected val DISCOVERY_ENDPOINT: String = "https://openam-sdks.forgeblocks.com/am/oauth2/alpha/.well-known/openid-configuration"

    @Before
    fun setup() = runTest {
        defaultJourney.user()?.logout()
    }

    @After
    fun cleanup() = runTest {
        defaultJourney.user()?.logout()
    }

    // Default Journey configuration for most of the tests
    protected val defaultJourney = Journey {
        logger = Logger.STANDARD
        serverUrl = SERVER_URL
        realm = REALM
        cookie = COOKIE
        module(Oidc) {
            clientId = CLIENT_ID
            redirectUri = REDIRECT_URI
            scopes = mutableSetOf("openid", "email", "address", "profile", "phone")
            discoveryEndpoint = DISCOVERY_ENDPOINT
        }
    }

    // Helper function to handle login callbacks with default username and password
    protected fun ContinueNode.handleLoginCallbacks(username: String = USERNAME, password: String = PASSWORD): ContinueNode = apply {
        callbacks.forEach { callback ->
            when (callback) {
                is NameCallback -> callback.name = username
                is PasswordCallback -> callback.password = password
            }
        }
    }
}

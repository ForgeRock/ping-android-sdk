/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.journey

import com.pingidentity.journey.IntegrationTestConfig.clientId
import com.pingidentity.journey.IntegrationTestConfig.cookieName
import com.pingidentity.journey.IntegrationTestConfig.discoveryEndPoint
import com.pingidentity.journey.IntegrationTestConfig.realm
import com.pingidentity.journey.IntegrationTestConfig.serverUrl
import com.pingidentity.journey.IntegrationTestConfig.username
import com.pingidentity.journey.IntegrationTestConfig.password
import com.pingidentity.journey.IntegrationTestConfig.redirectUri
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
    protected val SERVER_URL: String = serverUrl
    protected val REALM: String = realm
    protected val COOKIE: String = cookieName
    protected var USERNAME: String = username
    protected var PASSWORD: String = password
    protected val CLIENT_ID: String = clientId
    protected val REDIRECT_URI: String = redirectUri
    protected val DISCOVERY_ENDPOINT: String = discoveryEndPoint
    protected var tree: String = "Login"

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
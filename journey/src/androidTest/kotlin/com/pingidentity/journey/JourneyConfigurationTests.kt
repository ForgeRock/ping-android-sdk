/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.journey

import androidx.test.filters.SmallTest
import com.pingidentity.journey.callback.NameCallback
import com.pingidentity.journey.callback.PasswordCallback
import com.pingidentity.journey.module.oidcClient
import com.pingidentity.journey.module.session
import com.pingidentity.journey.plugin.callbacks
import com.pingidentity.oidc.User
import com.pingidentity.orchestrate.ContinueNode
import com.pingidentity.orchestrate.ErrorNode
import com.pingidentity.orchestrate.SuccessNode
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertFailsWith

@SmallTest
class JourneyConfigurationTests : BaseJourneyTest() {

    @Test
    fun createJourneyDefaultConfig() = runTest {
        val minimalJourney = Journey {
        }

        // Attempt to access the late init serverUrl var results in an exception
        assertFailsWith<UninitializedPropertyAccessException> {
            // This will throw an exception because serverUrl is not initialized
            logger.d("Server URL: ${minimalJourney.options.serverUrl}")
        }

        // Ensure that the default values are set correctly
        assertEquals("root", minimalJourney.options.realm)
        assertEquals("iPlanetDirectoryPro", minimalJourney.options.cookie)
        assertTrue(minimalJourney.user() == null)

        // ToDo: Fix readme. It says 30 seconds. . .
        // Default timeout is 15 seconds
        assertEquals(15000, minimalJourney.options.timeout)

        // Ensure that attempting to access oidcClient before it is initialized throws an exception
        assertFailsWith<IllegalStateException> {
            // This will throw an exception because oidcClient is not initialized
            logger.d("OIDC Client: ${minimalJourney.oidcClient()}")
        }

        // Ensure session is null
        assertNull(minimalJourney.session())
    }

    @Test
    fun createJourneyCustomConfig() = runTest {
        val customJourney = Journey {
            timeout = 60
            serverUrl = SERVER_URL
            realm = REALM
            cookie = COOKIE
        }

        assertEquals(SERVER_URL, customJourney.options.serverUrl)
        assertEquals(REALM, customJourney.options.realm)
        assertEquals(COOKIE, customJourney.options.cookie)
        assertEquals(60, customJourney.options.timeout)
        assertTrue(customJourney.user() == null)
    }

    @Test
    fun startJourney() = runTest {
        val result = defaultJourney.start(TREE)
        assertNotNull(result)
        assertTrue(result is ContinueNode)

        val callbacks = (result as ContinueNode).callbacks
        assertEquals(2, callbacks.size)
        assertTrue(callbacks[0] is NameCallback)
        assertTrue(callbacks[1] is PasswordCallback)
    }

    @Test
    fun successfulLogin() = runTest {
        var result = defaultJourney.start(TREE)
        val continueNode = result as ContinueNode
        continueNode.handleLoginCallbacks(USERNAME, PASSWORD)

        result = continueNode.next()
        assertTrue(result is SuccessNode)

        // After handling callbacks, the user should be logged in
        assertNotNull(defaultJourney.session())
        logger.d("Session: ${defaultJourney.session()?.value}")

        // Check the session object
        assertNotNull(defaultJourney.session()?.value)
        assertEquals(defaultJourney.session()?.realm, "/${REALM}")
        assertEquals(defaultJourney.session()?.successUrl, "/enduser/?realm=/alpha")

        val user: User? = defaultJourney.user()
        assertNotNull(user)
        assertNotNull(user?.token()) // This is the access token
        logger.d("User session: ${user?.session()?.value}")

        // User session is accessible through the user object and should match the journey session
        assertEquals(user?.session()?.value, defaultJourney.session()?.value)
    }

    @Test
    fun successfulLoginWithNoSession() = runTest {
        // Start the journey and log in; noSession is set to true
        var result = defaultJourney.start(TREE) {
            noSession = true // This will not create a session
        }
        val continueNode = result as ContinueNode
        continueNode.handleLoginCallbacks(USERNAME, PASSWORD)
        result = continueNode.next()
        assertTrue(result is SuccessNode)

        // After successful login, the session should be null
        assertNull(defaultJourney.session())
    }

    @Test
    fun sessionSignOff() = runTest {
        // Start the journey and log in
        var result = defaultJourney.start(TREE)
        val continueNode = result as ContinueNode
        continueNode.handleLoginCallbacks(USERNAME, PASSWORD)
        result = continueNode.next()
        assertTrue(result is SuccessNode)

        // After successful login, the session should not be null
        assertNotNull(defaultJourney.session())

        // Log out the session
        defaultJourney.signOff()

        // After sign off, the session should be null
        assertNull(defaultJourney.session())
    }

    @Test
    fun handleError() = runTest {
        var result = defaultJourney.start(TREE)
        val continueNode = result as ContinueNode

        continueNode.handleLoginCallbacks("invalidUser", "invalidPassword")
        result = continueNode.next()
        assertTrue(result is ErrorNode)

        val errorNode = result as ErrorNode
        assertEquals("Login failure", errorNode.message)
        logger.e("Error: ${errorNode.message}")
    }
}
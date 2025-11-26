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
import com.pingidentity.journey.module.Oidc
import com.pingidentity.journey.module.Session
import com.pingidentity.journey.module.oidcClient
import com.pingidentity.journey.module.session
import com.pingidentity.journey.plugin.callbacks
import com.pingidentity.logger.Logger
import com.pingidentity.logger.STANDARD
import com.pingidentity.oidc.OidcError
import com.pingidentity.oidc.User
import com.pingidentity.orchestrate.ContinueNode
import com.pingidentity.orchestrate.ErrorNode
import com.pingidentity.orchestrate.FailureNode
import com.pingidentity.orchestrate.SuccessNode
import com.pingidentity.storage.MemoryStorage
import com.pingidentity.utils.Result
import io.ktor.client.plugins.HttpRequestTimeoutException
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Assert.assertNotEquals
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@SmallTest
class JourneyE2ETests : BaseJourneyTest() {

    @Test
    fun createJourneyDefaultConfig() = runTest {
        val minimalJourney = Journey {
        }

        // Attempt to access the late init serverUrl var results in an exception
        assertThrows(UninitializedPropertyAccessException::class.java) {
            // This will throw an exception because serverUrl is not initialized
            logger.d("Server URL: ${minimalJourney.options.serverUrl}")
        }

        // Ensure that the default values are set correctly
        assertEquals("root", minimalJourney.options.realm)
        assertEquals("iPlanetDirectoryPro", minimalJourney.options.cookie)
        assertNull(minimalJourney.user())

        // Default timeout is 15 seconds
        assertEquals(15.seconds.inWholeMilliseconds, minimalJourney.options.timeout)

        // Ensure that attempting to access oidcClient before it is initialized throws an exception
        assertThrows(IllegalStateException::class.java) {
            // This will throw an exception because oidcClient is not initialized
            logger.d("OIDC Client: ${minimalJourney.oidcClient()}")
        }

        // Ensure session is null
        assertNull(minimalJourney.session())
    }

    @Test
    fun createJourneyCustomConfig() = runTest {
        val customJourney = Journey {
            timeout = 60.seconds.inWholeMilliseconds
            serverUrl = SERVER_URL
            realm = REALM
            cookie = COOKIE
        }

        assertEquals(SERVER_URL, customJourney.options.serverUrl)
        assertEquals(REALM, customJourney.options.realm)
        assertEquals(COOKIE, customJourney.options.cookie)
        assertEquals(60.seconds.inWholeMilliseconds, customJourney.options.timeout)
        assertNull(customJourney.user())
    }

    @Test
    fun journeyWithCustomStorage() = runTest {
        val sessionStorage = MemoryStorage<SSOToken>()
        val customStorageJourney = Journey {
            serverUrl = SERVER_URL
            realm = REALM
            cookie = COOKIE
            module(Session) {
                storage = { sessionStorage }
            }
        }

        var result = customStorageJourney.start(tree)
        val continueNode = result as ContinueNode
        continueNode.handleLoginCallbacks(USERNAME, PASSWORD)

        result = continueNode.next()
        assertTrue(result is SuccessNode)

        // After successful login, the session should be stored in custom storage
        assertNotNull(sessionStorage.get())
        assertEquals(customStorageJourney.session()?.value, sessionStorage.get()?.value)
    }

    @Test
    fun startJourney() = runTest {
        val result = defaultJourney.start(tree)
        assertNotNull(result)
        assertTrue(result is ContinueNode)

        val callbacks = (result as ContinueNode).callbacks
        assertEquals(2, callbacks.size)
        assertTrue(callbacks[0] is NameCallback)
        assertTrue(callbacks[1] is PasswordCallback)
    }

    @Test
    fun successfulLogin() = runTest {
        var result = defaultJourney.start(tree)
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
        var result = defaultJourney.start(tree) {
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
        var result = defaultJourney.start(tree)
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
        var result = defaultJourney.start(tree)
        val continueNode = result as ContinueNode

        continueNode.handleLoginCallbacks("invalidUser", "invalidPassword")
        result = continueNode.next()
        assertTrue(result is ErrorNode)

        val errorNode = result as ErrorNode
        assertEquals("Login failure", errorNode.message)
        logger.e("Error: ${errorNode.message}")
    }

    @Test
    fun handleFailure() = runTest {
        val myJourney = Journey {
            logger = Logger.STANDARD
            timeout = 10.milliseconds.inWholeMilliseconds // Set a short timeout for testing failure
            serverUrl = SERVER_URL
            realm = REALM
            cookie = COOKIE
        }

        val result = myJourney.start(tree)
        val failureNode = result as FailureNode
        assertTrue(failureNode.cause is HttpRequestTimeoutException)
        assertTrue(failureNode.cause.message?.contains("Request timeout has expired") ?: false)
    }

    @Test
    fun testUserToken() = runTest {
        var result = defaultJourney.start(tree)
        val continueNode = result as ContinueNode
        continueNode.handleLoginCallbacks(USERNAME, PASSWORD)

        result = continueNode.next()
        assertTrue(result is SuccessNode)

        // After successful login, the user should have an access token
        val tokenResult = defaultJourney.user()?.token()
        assertTrue(tokenResult is Result.Success)

        val at = (tokenResult as Result.Success).value
        assertNotNull(at) // This is the access token
        assertTrue(at.accessToken.isNotEmpty()) // Ensure the access token is not empty
        assertNotNull(at.idToken) // Ensure id token is not null
        assertTrue(at.tokenType == "Bearer") // Ensure the token type is Bearer
        at.scope?.let { assertTrue(it.contains("openid")) }
        at.scope?.let { assertTrue(it.contains("profile")) }
        at.scope?.let { assertTrue(it.contains("email")) }
        assertFalse(at.isExpired) // Ensure the token is not expired
        assertTrue(at.expiresIn > 0) // Ensure the token expires in the future

        // Ensure that the user.token() method returns the same token
        val at1 = (defaultJourney.user()?.token() as Result.Success).value
        assertEquals(at.accessToken, at1.accessToken)
    }

    @Test
    fun testUserTokenAuthorizeFailure() = runTest {
        val invalidOidcClientJourney = Journey {
            serverUrl = SERVER_URL
            realm = REALM
            cookie = COOKIE
            module(Oidc) {
                clientId = "InvalidClientId" // Intentionally set invalid client ID
                redirectUri = REDIRECT_URI
                scopes = mutableSetOf("openid", "email", "address", "profile", "phone")
                discoveryEndpoint = DISCOVERY_ENDPOINT
            }
        }

        var result = invalidOidcClientJourney.start(tree)
        val continueNode = result as ContinueNode
        continueNode.handleLoginCallbacks(USERNAME, PASSWORD)

        result = continueNode.next()
        assertTrue(result is SuccessNode)

        // Try to get the user token
        val tokenResult = invalidOidcClientJourney.user()?.token()
        assertTrue(tokenResult is Result.Failure)
        val error = (tokenResult as Result.Failure).value
        logger.e("Error: $error")

        assertTrue(error is (OidcError.AuthorizeError))
        assertTrue((error as OidcError.AuthorizeError).cause.message?.contains("Authorize failed") ?: false)
    }

    @Test
    fun testUserTokenRefresh() = runTest {
        var result = defaultJourney.start(tree)
        val continueNode = result as ContinueNode
        continueNode.handleLoginCallbacks(USERNAME, PASSWORD)

        result = continueNode.next()
        assertTrue(result is SuccessNode)

        val at1 = defaultJourney.user()?.token()
        assertNotNull(at1) // This is the access token

        val at2 = defaultJourney.user()?.refresh()
        assertNotNull(at2) // This is the refreshed access token

        assertTrue(at1 != at2) // Ensure the tokens are different
    }

    @Test
    fun testUserTokenRevoke() = runTest {
        var result = defaultJourney.start(tree)
        val continueNode = result as ContinueNode
        continueNode.handleLoginCallbacks(USERNAME, PASSWORD)
        result = continueNode.next()
        assertTrue(result is SuccessNode)

        val at1 = (defaultJourney.user()?.token() as Result.Success).value
        assertNotNull(at1)
        logger.d("Access Token before revoke: $at1")

        // Revoke the token
        defaultJourney.user()?.revoke()

        // Ensure that the user.token() method acquires a new token
        val at2 = (defaultJourney.user()?.token() as Result.Success).value
        logger.d("Access Token after revoke: $at2")
        assertNotEquals(at1.accessToken, at2.accessToken)
    }

    @Test
    fun testUserInfo() = runTest {
        var result = defaultJourney.start(tree)
        val continueNode = result as ContinueNode
        continueNode.handleLoginCallbacks(USERNAME, PASSWORD)
        result = continueNode.next()
        assertTrue(result is SuccessNode)

        // After successful login, the user should have user info
        val userInfoResult = defaultJourney.user()?.userinfo(cache = true)
        assertTrue(userInfoResult is Result.Success)
        val userInfo = (userInfoResult as Result.Success).value
        assertNotNull(userInfo) // Ensure user info is not null
        logger.d("User Info: $userInfo")
        assertTrue(userInfo.containsKey("sub")) // Ensure user info contains 'sub' field
        assertTrue(userInfo.containsKey("email")) // Ensure user info contains 'email' field
        assertTrue(userInfo.containsKey("name")) // Ensure user info contains 'name' field
        assertTrue(userInfo.containsKey("given_name")) // Ensure user info contains 'given_name' field
        assertTrue(userInfo.containsKey("family_name")) // Ensure user info contains 'family_name' field
    }

    @Test
    fun testUserLogout() = runTest {
        var result = defaultJourney.start(tree)
        val continueNode = result as ContinueNode
        continueNode.handleLoginCallbacks(USERNAME, PASSWORD)
        result = continueNode.next()
        assertTrue(result is SuccessNode)

        // After successful login, the user should be logged in
        assertNotNull(defaultJourney.session())
        logger.d("Session before logout: ${defaultJourney.session()?.value}")
        // Log out the user
        defaultJourney.user()?.logout()
        // After logout, the session should be null
        assertNull(defaultJourney.session())
        logger.d("Session after logout: ${defaultJourney.session()?.value}")
        // Ensure that the user is logged out
        assertNull(defaultJourney.user()?.token()) // Ensure the user token is null after logout
        assertNull(defaultJourney.user()?.session()) // Ensure the user session is null after logout
        logger.d("User after logout: ${defaultJourney.user()}")
    }
}
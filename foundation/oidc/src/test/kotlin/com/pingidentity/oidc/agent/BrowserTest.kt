/*
 * Copyright (c) 2024 - 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.oidc.agent

import android.net.Uri
import com.pingidentity.browser.BrowserLauncher
import com.pingidentity.network.ktor.KtorHttpClient
import com.pingidentity.oidc.Constants
import com.pingidentity.oidc.OidcClientConfig
import com.pingidentity.oidc.OidcConfig
import com.pingidentity.oidc.OpenIdConfiguration
import com.pingidentity.oidc.exception.AuthorizeException
import com.pingidentity.storage.MemoryStorage
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteReadChannel
import io.mockk.CapturingSlot
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.URL
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue


@RunWith(RobolectricTestRunner::class)
class BrowserTest {

    @BeforeTest
    fun setUp() {
        mockkObject(BrowserLauncher)
    }

    @AfterTest
    fun tearDown() {
        unmockkObject(BrowserLauncher)
    }

    @Test
    fun `browser end session with pingEndIdpSessionEndpoint`() = runTest {
        val mockEngine =
            MockEngine {
                return@MockEngine respond(
                    content =
                    ByteReadChannel(""),
                    status = HttpStatusCode.NoContent
                )
            }

        val mock = KtorHttpClient(HttpClient(mockEngine))

        val oidcClientConfig = OidcClientConfig().apply {
            httpClient = mock
            openId = OpenIdConfiguration(
                authorizationEndpoint = "http://localhost/openid-configuration",
                tokenEndpoint = "http://localhost/token",
                userinfoEndpoint = "http://localhost/userinfo",
                endSessionEndpoint = "http://localhost/end-session",
                pingEndIdpSessionEndpoint = "http://localhost/ping-end-idp-session",
                revocationEndpoint = "http://localhost/revocation"
            )
            storage = { MemoryStorage() }
            clientId = "test-client-id"
            redirectUri = "https://example.com/callback"
        }

        val result = browser.endSession(OidcConfig(browser.config()(), oidcClientConfig), "dummy-id-token")
        val request = mockEngine.requestHistory[0]
        assertTrue(result)
        // Ensure that the pingEndIdpSessionEndpoint is used
        assertEquals("http://localhost/ping-end-idp-session?id_token_hint=dummy-id-token&client_id=test-client-id", request.url.toString())
        assertEquals("GET", request.method.value)
        assertEquals("application/json", request.headers[HttpHeaders.Accept])
    }

    @Test
    fun `browser end session with endSessionEndpoint`() = runTest {
        val mockEngine =
            MockEngine {
                return@MockEngine respond(
                    content =
                    ByteReadChannel(""),
                    status = HttpStatusCode.NoContent
                )
            }

        val mock = KtorHttpClient(HttpClient(mockEngine))

        // The pingone custom end session endpoint is not defined, e.g AIC server
        val oidcClientConfig = OidcClientConfig().apply {
            httpClient = mock
            openId = OpenIdConfiguration(
                authorizationEndpoint = "http://localhost/openid-configuration",
                tokenEndpoint = "http://localhost/token",
                userinfoEndpoint = "http://localhost/userinfo",
                endSessionEndpoint = "http://localhost/end-session",
                pingEndIdpSessionEndpoint = "", //This is empty
                revocationEndpoint = "http://localhost/revocation"
            )
            storage = { MemoryStorage() }
            clientId = "test-client-id"
            redirectUri = "https://example.com/callback"
        }

        val result = browser.endSession(OidcConfig(browser.config()(), oidcClientConfig), "dummy-id-token")
        val request = mockEngine.requestHistory[0]
        assertTrue(result)
        // Ensure that the endSessionEndpoint is used
        assertEquals("http://localhost/end-session?id_token_hint=dummy-id-token&client_id=test-client-id", request.url.toString())
        assertEquals("GET", request.method.value)
        assertEquals("application/json", request.headers[HttpHeaders.Accept])
    }

    @Test
    fun `browser authorize sends a generated state and succeeds when it is echoed`() = runTest {
        val oidcClientConfig = OidcClientConfig().apply {
            openId = OpenIdConfiguration(
                authorizationEndpoint = "http://localhost/authorize",
                tokenEndpoint = "http://localhost/token",
                userinfoEndpoint = "http://localhost/userinfo",
                endSessionEndpoint = "http://localhost/end-session",
                revocationEndpoint = "http://localhost/revocation"
            )
            clientId = "test-client-id"
            redirectUri = "https://example.com/callback"
        }
        val urlSlot = slot<URL>()
        coEvery { BrowserLauncher.launch(capture(urlSlot), any()) } returns Result.success(
            // The authorization server echoes the state that was sent.
            echoUri(urlSlot)
        )

        val authCode = browser.authorize(OidcConfig(browser.config()(), oidcClientConfig))
        assertEquals("test-code", authCode.code)

        // A state must be sent, exactly once, generated (non-empty).
        val query = urlSlot.captured.query ?: ""
        val sentStates = query.split('&').filter { it.startsWith("state=") }
        assertEquals(1, sentStates.size)
        val sentState =
            Uri.parse("?${sentStates.single()}").getQueryParameter(Constants.STATE)
        assertNotNull(sentState)
        assertTrue(sentState.isNotEmpty())
    }

    @Test
    fun `browser authorize sends the configured state`() = runTest {
        val oidcClientConfig = OidcClientConfig().apply {
            openId = OpenIdConfiguration(
                authorizationEndpoint = "http://localhost/authorize",
                tokenEndpoint = "http://localhost/token",
                userinfoEndpoint = "http://localhost/userinfo",
                endSessionEndpoint = "http://localhost/end-session",
                revocationEndpoint = "http://localhost/revocation"
            )
            clientId = "test-client-id"
            redirectUri = "https://example.com/callback"
            state = "configured-state"
        }
        val urlSlot = slot<URL>()
        coEvery { BrowserLauncher.launch(capture(urlSlot), any()) } returns Result.success(
            echoUri(urlSlot)
        )

        val authCode = browser.authorize(OidcConfig(browser.config()(), oidcClientConfig))
        assertEquals("test-code", authCode.code)

        val query = urlSlot.captured.query ?: ""
        val sentStates = query.split('&').filter { it.startsWith("state=") }
        assertEquals(1, sentStates.size)
        assertEquals(
            "configured-state",
            Uri.parse("?${sentStates.single()}").getQueryParameter(Constants.STATE),
        )
    }

    @Test
    fun `browser authorize sends a state from additionalParameters exactly once`() = runTest {
        val oidcClientConfig = OidcClientConfig().apply {
            openId = OpenIdConfiguration(
                authorizationEndpoint = "http://localhost/authorize",
                tokenEndpoint = "http://localhost/token",
                userinfoEndpoint = "http://localhost/userinfo",
                endSessionEndpoint = "http://localhost/end-session",
                revocationEndpoint = "http://localhost/revocation"
            )
            clientId = "test-client-id"
            redirectUri = "https://example.com/callback"
            state = "configured-state"
            additionalParameters = mapOf("state" to "additional-state")
        }
        val urlSlot = slot<URL>()
        coEvery { BrowserLauncher.launch(capture(urlSlot), any()) } returns Result.success(
            echoUri(urlSlot)
        )

        val authCode = browser.authorize(OidcConfig(browser.config()(), oidcClientConfig))
        assertEquals("test-code", authCode.code)

        // The additionalParameters state wins and the URL carries it exactly once.
        val query = urlSlot.captured.query ?: ""
        val sentStates = query.split('&').filter { it.startsWith("state=") }
        assertEquals(1, sentStates.size)
        assertEquals(
            "additional-state",
            Uri.parse("?${sentStates.single()}").getQueryParameter(Constants.STATE),
        )
    }

    @Test
    fun `browser authorize fails when the echoed state does not match`() = runTest {
        val oidcClientConfig = OidcClientConfig().apply {
            openId = OpenIdConfiguration(
                authorizationEndpoint = "http://localhost/authorize",
                tokenEndpoint = "http://localhost/token",
                userinfoEndpoint = "http://localhost/userinfo",
                endSessionEndpoint = "http://localhost/end-session",
                revocationEndpoint = "http://localhost/revocation"
            )
            clientId = "test-client-id"
            redirectUri = "https://example.com/callback"
            state = "expected-state"
        }
        coEvery { BrowserLauncher.launch(any(), any()) } returns Result.success(
            mockk {
                every { getQueryParameter(any()) } answers {
                    if (firstArg<String>() == Constants.STATE) "forged-state" else "test-code"
                }
            }
        )

        val exception = assertFailsWith<AuthorizeException> {
            browser.authorize(OidcConfig(browser.config()(), oidcClientConfig))
        }
        assertContains(exception.message ?: "", "State mismatch")
    }

    @Test
    fun `browser authorize fails when the callback omits the state`() = runTest {
        val oidcClientConfig = OidcClientConfig().apply {
            openId = OpenIdConfiguration(
                authorizationEndpoint = "http://localhost/authorize",
                tokenEndpoint = "http://localhost/token",
                userinfoEndpoint = "http://localhost/userinfo",
                endSessionEndpoint = "http://localhost/end-session",
                revocationEndpoint = "http://localhost/revocation"
            )
            clientId = "test-client-id"
            redirectUri = "https://example.com/callback"
            state = "expected-state"
        }
        coEvery { BrowserLauncher.launch(any(), any()) } returns Result.success(
            mockk {
                every { getQueryParameter(any()) } answers {
                    if (firstArg<String>() == Constants.STATE) null else "test-code"
                }
                every { fragment } returns null
                every { encodedFragment } returns null
            }
        )

        val exception = assertFailsWith<AuthorizeException> {
            browser.authorize(OidcConfig(browser.config()(), oidcClientConfig))
        }
        assertContains(exception.message ?: "", "did not include the state parameter")
    }

    /**
     * Mock URI whose `getQueryParameter` resolves names against the query of the URL captured
     * for [BrowserLauncher.launch] — emulating an authorization server that echoes back the
     * exact parameters that were sent.
     */
    private fun echoUri(urlSlot: CapturingSlot<URL>): Uri =
        mockk {
            every { getQueryParameter(any()) } answers {
                val name = firstArg<String>()
                if (name == Constants.CODE) {
                    "test-code"
                } else {
                    urlSlot.captured.query
                        ?.let { Uri.parse("?$it").getQueryParameter(name) }
                }
            }
        }
}

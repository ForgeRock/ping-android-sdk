/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.oidc

import android.net.Uri
import com.pingidentity.browser.BrowserCanceledException
import com.pingidentity.browser.BrowserLauncher
import com.pingidentity.logger.CONSOLE
import com.pingidentity.logger.Logger
import com.pingidentity.network.ktor.KtorHttpClient
import com.pingidentity.oidc.module.Oidc
import com.pingidentity.storage.MemoryStorage
import com.pingidentity.utils.Result.Success
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteReadChannel
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import kotlinx.coroutines.test.runTest
import java.net.URL
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class OidcWebTest {

    private lateinit var mockEngine: MockEngine

    @BeforeTest
    fun setUp() {

        mockkObject(BrowserLauncher)

        mockEngine =
            MockEngine { request ->
                when (request.url.encodedPath) {
                    "/.well-known/openid-configuration" -> {
                        respond(openIdConfigurationResponse(), HttpStatusCode.OK, headers)
                    }

                    "/token" -> {
                        respond(tokeResponse(), HttpStatusCode.OK, headers)
                    }

                    "/userinfo" -> {
                        respond(userinfoResponse(), HttpStatusCode.OK, headers)
                    }

                    "/revoke" -> {
                        respond("", HttpStatusCode.OK, headers)
                    }

                    "/signoff" -> {
                        respond("", HttpStatusCode.OK, headers)
                    }

                    else -> {
                        return@MockEngine respond(
                            content =
                                ByteReadChannel(""),
                            status = HttpStatusCode.InternalServerError,
                        )
                    }
                }
            }
    }

    @AfterTest
    fun tearDown() {
        mockEngine.close()
    }

    @Test
    fun `authorize returns success when OidcFlow succeeds`() = runTest {

        val mockUri = mockk<Uri>()
        coEvery { BrowserLauncher.launch(any<URL>()) } returns Result.success(mockUri)

        every { mockUri.getQueryParameter(Constants.CODE) } returns "test-code"

        val web = OidcWeb {
            httpClient = KtorHttpClient(HttpClient(mockEngine))
            logger = Logger.CONSOLE
            module(Oidc) {
                clientId = "test-client"
                discoveryEndpoint = "http://localhost/.well-known/openid-configuration"
                scopes = mutableSetOf("openid", "profile")
                redirectUri = "https://example.com/callback"
                storage = { MemoryStorage() }
            }
        }

        val result = web.authorize()
        assertNotNull(web.user())
        assertTrue { result.isSuccess }
        result.getOrElse {
            throw it
        }.let { user ->
            assertNotNull(user)
            assertEquals("Dummy AccessToken", (user.token() as Success).value.accessToken)

            user.logout()

            mockEngine.requestHistory[0] // well-known
            mockEngine.requestHistory[1] // token
            mockEngine.requestHistory[2] // revoke
            mockEngine.requestHistory[3] // signoff
            val signoff = mockEngine.requestHistory[3] // signoff
            assertContains(signoff.url.encodedQuery, "id_token_hint=Dummy+IdToken")
            assertContains(signoff.url.encodedQuery, "client_id=test-client")
        }

        assertNull(web.user())

    }

    @Test
    fun `authorize returns failure when BrowserLauncher fails`() = runTest {
        coEvery { BrowserLauncher.launch(any<URL>()) } returns Result.failure(
            BrowserCanceledException()
        )

        val web = OidcWeb {
            httpClient = KtorHttpClient(HttpClient(mockEngine))
            logger = Logger.CONSOLE
            module(Oidc) {
                clientId = "test-client"
                discoveryEndpoint = "http://localhost/.well-known/openid-configuration"
                scopes = mutableSetOf("openid", "profile")
                redirectUri = "https://example.com/callback"
                storage = { MemoryStorage() }
            }
        }

        val result = web.authorize()
        assertTrue(result.isFailure)
        assertIs<BrowserCanceledException>(result.exceptionOrNull())
    }

    @Test
    fun `authorize returns failure when BrowserLauncher returns Uri without code`() = runTest {
        val mockUri = mockk<Uri>()
        coEvery { BrowserLauncher.launch(any<URL>()) } returns Result.success(mockUri)

        every { mockUri.getQueryParameter(Constants.CODE) } returns null

        val web = OidcWeb {
            httpClient = KtorHttpClient(HttpClient(mockEngine))
            logger = Logger.CONSOLE
            module(Oidc) {
                clientId = "test-client"
                discoveryEndpoint = "http://localhost/.well-known/openid-configuration"
                scopes = mutableSetOf("openid", "profile")
                redirectUri = "https://example.com/callback"
                storage = { MemoryStorage() }
            }
        }

        val result = web.authorize()
        assertTrue(result.isFailure)
        assertIs<IllegalStateException>(result.exceptionOrNull())
    }

    @Test
    fun `authorize correctly passes custom parameters to flow`() = runTest {
        val mockUri = mockk<Uri>()
        val urlSlot = slot<URL>()
        coEvery { BrowserLauncher.launch(capture(urlSlot)) } returns Result.success(mockUri)
        every { mockUri.getQueryParameter(Constants.CODE) } returns "test-code"

        val web = OidcWeb {
            httpClient = KtorHttpClient(HttpClient(mockEngine))
            logger = Logger.CONSOLE
            module(Oidc) {
                clientId = "test-client"
                discoveryEndpoint = "http://localhost/.well-known/openid-configuration"
                scopes = mutableSetOf("openid", "profile")
                redirectUri = "https://example.com/callback"
                storage = { MemoryStorage() }
                state = "test-state"
                nonce = "test-nonce"
                prompt = "login"
                uiLocales = "en"
                loginHint = "test-login"
                acrValues = "urn:mace:incommon:iap:silver"
                display = "page"
                additionalParameters = mapOf(
                    "additional" to "additionalvalue"
                )
            }
        }

        val result = web.authorize {
            "custom" to "value"
        }

        assertTrue(result.isSuccess)
        result.getOrElse { throw it }.let { user ->
            assertNotNull(user)
            assertEquals("Dummy AccessToken", (user.token() as Success).value.accessToken)
        }

        // Assert that the custom parameter is present in the URL
        val url = urlSlot.captured
        assertTrue(url.query.contains("client_id=test-client"))
        assertTrue(url.query.contains("scope=openid+profile"))
        assertTrue(url.query.contains("redirect_uri=https%3A%2F%2Fexample.com%2Fcallback"))
        assertTrue(url.query.contains("response_type=code"))
        assertTrue(url.query.contains("state=test-state"))
        assertTrue(url.query.contains("nonce=test-nonce"))
        assertTrue(url.query.contains("display=page"))
        assertTrue(url.query.contains("prompt=login"))
        assertTrue(url.query.contains("ui_locales=en"))
        assertTrue(url.query.contains("login_hint=test-login"))
        assertTrue(url.query.contains("acr_values=urn%3Amace%3Aincommon%3Aiap%3Asilver"))
        assertTrue(url.query.contains("additional=additionalvalue"))
    }

}
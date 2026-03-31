/*
 * Copyright (c) 2025 - 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.oidc

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.pingidentity.android.ContextProvider
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
import io.ktor.client.request.forms.FormDataContent
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.URL
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class OidcWebClientTest {

    private lateinit var mockEngine: MockEngine
    private val context: Context by lazy { ApplicationProvider.getApplicationContext<Application>() }

    @BeforeTest
    fun setUp() {

        ContextProvider.init(context)
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
        coEvery { BrowserLauncher.launch(any<URL>(), any()) } returns Result.success(mockUri)

        every { mockUri.getQueryParameter(Constants.CODE) } returns "test-code"

        val web = OidcWebClient {
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
        coEvery { BrowserLauncher.launch(any<URL>(), any()) } returns Result.failure(
            BrowserCanceledException()
        )

        val web = OidcWebClient {
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
        coEvery { BrowserLauncher.launch(any<URL>(), any()) } returns Result.success(mockUri)

        every { mockUri.getQueryParameter(Constants.CODE) } returns null

        val web = OidcWebClient {
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
        coEvery { BrowserLauncher.launch(capture(urlSlot), any()) } returns Result.success(mockUri)
        every { mockUri.getQueryParameter(Constants.CODE) } returns "test-code"

        val web = OidcWebClient {
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
        assertTrue(url.query.contains("custom=value"))
    }

    @Test
    fun `authorize with PAR pushes params to PAR endpoint and uses request_uri in authorization URL`() = runTest {
        val parMockEngine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/.well-known/openid-configuration" -> {
                    respond(openIdConfigurationWithParResponse(), HttpStatusCode.OK, headers)
                }

                "/par" -> {
                    respond(parResponse(), HttpStatusCode.OK, headers)
                }

                "/token" -> {
                    respond(tokeResponse(), HttpStatusCode.OK, headers)
                }

                else -> {
                    respond(
                        content = ByteReadChannel(""),
                        status = HttpStatusCode.InternalServerError,
                    )
                }
            }
        }

        val mockUri = mockk<Uri>()
        val urlSlot = slot<URL>()
        coEvery { BrowserLauncher.launch(capture(urlSlot), any()) } returns Result.success(mockUri)
        every { mockUri.getQueryParameter(Constants.CODE) } returns "test-code"

        val web = OidcWebClient {
            httpClient = KtorHttpClient(HttpClient(parMockEngine))
            logger = Logger.CONSOLE
            module(Oidc) {
                clientId = "test-client"
                discoveryEndpoint = "http://localhost/.well-known/openid-configuration"
                scopes = mutableSetOf("openid", "profile")
                redirectUri = "https://example.com/callback"
                storage = { MemoryStorage() }
                par = true
            }
        }

        val result = web.authorize()
        assertTrue(result.isSuccess)

        // Verify PAR request (index 0=well-known, 1=par)
        val parRequest = parMockEngine.requestHistory[1]
        assertEquals("https://auth.test-one-pingone.com/par", parRequest.url.toString())
        assertTrue(parRequest.body is FormDataContent)
        val parBody = parRequest.body as FormDataContent
        assertEquals("test-client", parBody.formData["client_id"])
        assertEquals("code", parBody.formData["response_type"])
        assertEquals("openid profile", parBody.formData["scope"])
        assertEquals("https://example.com/callback", parBody.formData["redirect_uri"])
        assertNotNull(parBody.formData["code_challenge"])
        assertEquals("S256", parBody.formData["code_challenge_method"])

        // Verify the browser was launched with request_uri and client_id only (PAR flow)
        val launchedUrl = urlSlot.captured
        val urlQuery = launchedUrl.query ?: ""
        assertContains(urlQuery, "request_uri=urn%3Aietf%3Aparams%3Aoauth%3Arequest_uri%3Atest-request-uri")
        assertContains(urlQuery, "client_id=test-client")
        // Regular auth params must NOT be in the browser URL when PAR is used
        assertTrue(!urlQuery.contains("scope="))
        assertTrue(!urlQuery.contains("redirect_uri="))
        assertTrue(!urlQuery.contains("code_challenge="))

        parMockEngine.close()
    }

}
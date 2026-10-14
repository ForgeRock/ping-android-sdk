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
import com.pingidentity.oidc.AuthorizationDetail
import com.pingidentity.oidc.Constants
import com.pingidentity.oidc.OidcClientConfig
import com.pingidentity.oidc.OidcConfig
import com.pingidentity.oidc.OpenIdConfiguration
import com.pingidentity.storage.MemoryStorage
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteReadChannel
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.URL
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
        unmockkAll()
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
    fun `browser authorize appends config authorizationDetails to the URL query`() = runTest {
        val mockUri = mockk<Uri>()
        val urlSlot = slot<URL>()
        coEvery { BrowserLauncher.launch(capture(urlSlot), any()) } returns Result.success(mockUri)
        every { mockUri.getQueryParameter(Constants.CODE) } returns "test-code"

        val oidcClientConfig = OidcClientConfig().apply {
            httpClient = mockk()
            openId = OpenIdConfiguration(
                authorizationEndpoint = "http://localhost/authorize",
                tokenEndpoint = "http://localhost/token",
                userinfoEndpoint = "http://localhost/userinfo",
                endSessionEndpoint = "http://localhost/end-session",
                revocationEndpoint = "http://localhost/revocation"
            )
            storage = { MemoryStorage() }
            clientId = "test-client-id"
            redirectUri = "http://localhost/redirect"
            authorizationDetails = listOf(
                AuthorizationDetail(
                    type = "payment_initiation",
                    actions = listOf("initiate", "status"),
                    locations = listOf("https://example.com/payments"),
                    additionalFields = mapOf(
                        "instructedAmount" to buildJsonObject {
                            put("currency", "EUR")
                            put("amount", 559)
                        },
                    ),
                ),
            )
        }

        val result = browser.authorize(OidcConfig(browser.config()(), oidcClientConfig))

        assertEquals("test-code", result.code)
        val url = urlSlot.captured
        val wireValue = assertNotNull(
            Uri.parse(url.toString()).getQueryParameter(Constants.AUTHORIZATION_DETAILS),
        )
        // URL-encoded on the wire, structurally equal once decoded: the vendor-extension member
        // must survive serialization through the additionalFields catch-all.
        val expected = buildJsonArray {
            add(buildJsonObject {
                put("type", "payment_initiation")
                put("actions", buildJsonArray { add("initiate"); add("status") })
                put("locations", buildJsonArray { add("https://example.com/payments") })
                put("instructedAmount", buildJsonObject {
                    put("currency", "EUR")
                    put("amount", 559)
                })
            })
        }
        assertEquals(expected, Json.parseToJsonElement(wireValue))
    }

    @Test
    fun `browser authorize emits no authorization_details parameter without config details`() = runTest {
        val mockUri = mockk<Uri>()
        val urlSlot = slot<URL>()
        coEvery { BrowserLauncher.launch(capture(urlSlot), any()) } returns Result.success(mockUri)
        every { mockUri.getQueryParameter(Constants.CODE) } returns "test-code"

        val oidcClientConfig = OidcClientConfig().apply {
            httpClient = mockk()
            openId = OpenIdConfiguration(
                authorizationEndpoint = "http://localhost/authorize",
                tokenEndpoint = "http://localhost/token",
                userinfoEndpoint = "http://localhost/userinfo",
                endSessionEndpoint = "http://localhost/end-session",
                revocationEndpoint = "http://localhost/revocation"
            )
            storage = { MemoryStorage() }
            clientId = "test-client-id"
            redirectUri = "http://localhost/redirect"
        }

        browser.authorize(OidcConfig(browser.config()(), oidcClientConfig))

        val urlQuery = urlSlot.captured.query ?: ""
        assertFalse(urlQuery.contains("authorization_details"))
    }
}
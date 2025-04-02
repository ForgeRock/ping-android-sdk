/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.oidc.agent

import com.pingidentity.oidc.OidcClientConfig
import com.pingidentity.oidc.OidcConfig
import com.pingidentity.oidc.OpenIdConfiguration
import com.pingidentity.oidc.Token
import com.pingidentity.storage.MemoryStorage
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue


class BrowserTest {

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

        val mock = HttpClient(mockEngine)

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
            storage = MemoryStorage<Token>()
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

        val mock = HttpClient(mockEngine)

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
            storage = MemoryStorage<Token>()
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
}
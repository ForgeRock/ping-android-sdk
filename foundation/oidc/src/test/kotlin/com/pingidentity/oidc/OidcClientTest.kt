/*
 * Copyright (c) 2024. PingIdentity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.oidc

import com.pingidentity.utils.Result.Failure
import com.pingidentity.utils.Result.Success
import com.pingidentity.oidc.agent.BrowserConfig
import com.pingidentity.oidc.agent.browser
import com.pingidentity.storage.MemoryStorage
import com.pingidentity.testrail.TestRailCase
import com.pingidentity.testrail.TestRailWatcher
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.forms.FormDataContent
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Rule
import org.junit.rules.TestWatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class TestAgent(agent: Agent<BrowserConfig>) : Agent<BrowserConfig> by agent {
    override suspend fun authorize(oidcConfig: OidcConfig<BrowserConfig>): AuthCode {
        return AuthCode("TestAgent", "codeVerifier")
    }
}

class OidcClientTest {
    private lateinit var mockEngine: MockEngine
    private lateinit var testAgent: Agent<BrowserConfig>

    @JvmField
    @Rule
    val watcher: TestWatcher = TestRailWatcher

    @BeforeTest
    fun setUp() {
        testAgent = TestAgent(browser)

        mockEngine =
            MockEngine { request ->
                when (request.url.encodedPath) {
                    "/openid-configuration" -> {
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

                    "/idp/signoff" -> {
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

    @TestRailCase(22084)
    @Test
    fun `failed to lookup discovery endpoint`() =
        runTest {
            mockEngine =
                MockEngine { request ->
                    when (request.url.encodedPath) {
                        "/openid-configuration" -> {
                            respond("", HttpStatusCode.InternalServerError, headers)
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
            val oidcClient =
                OidcClient {
                    httpClient = HttpClient(mockEngine)
                    discoveryEndpoint = "http://localhost/openid-configuration"
                    redirectUri = "http://localhost/redirect"
                    clientId = "test-client-id"
                    storage = MemoryStorage()
                    updateAgent(testAgent)
                }

            val result = oidcClient.token()
            assertTrue(result is Failure<OidcError>)
            assertTrue(result.value is OidcError.ApiError)
            assertEquals(
                HttpStatusCode.InternalServerError.value,
                (result.value as OidcError.ApiError).code,
            )
        }

    @TestRailCase(22085)
    @Test
    fun `accessToken should return cached token if not expired`() =
        runTest {
            val oidcClient =
                OidcClient {
                    this.httpClient = HttpClient(mockEngine)
                    discoveryEndpoint = "http://localhost/openid-configuration"
                    redirectUri = "http://localhost/redirect"
                    clientId = "test-client-id"
                    this.storage = MemoryStorage()
                    updateAgent(testAgent)
                }

            val result = oidcClient.token()
            assertTrue(result is Success<Token>)
            val cached = oidcClient.token()
            assertTrue(cached is Success<Token>)

            assertEquals("Dummy AccessToken", result.value.accessToken)
            assertEquals("Dummy Token Type", result.value.tokenType)
            assertEquals("Dummy RefreshToken", result.value.refreshToken)
            assertEquals("Dummy IdToken", result.value.idToken)
            assertEquals("openid email address", result.value.scope)

            assertEquals(2, mockEngine.requestHistory.size)
        }

    @TestRailCase(22086)
    @Test
    fun `accessToken should refresh token if expired`() =
        runTest {
            val oidcClient =
                OidcClient {
                    this.httpClient = HttpClient(mockEngine)
                    discoveryEndpoint = "http://localhost/openid-configuration"
                    redirectUri = "http://localhost/redirect"
                    clientId = "test-client-id"
                    storage = MemoryStorage()
                    updateAgent(testAgent)
                }

            val result = oidcClient.token()
            assertTrue(result is Success<Token>)

            // delay(2.toDuration(DurationUnit.SECONDS)) Delay will be skipped in runTest
            // Advance time by 1 seconds
            withContext(Dispatchers.Default) {
                delay(1.toDuration(DurationUnit.SECONDS))
            }

            oidcClient.token()

            // auto refresh has been triggered
            assertEquals(3, mockEngine.requestHistory.size)
            assertEquals(
                "refresh_token",
                (mockEngine.requestHistory.last().body as FormDataContent).formData["grant_type"],
            )
            assertEquals(
                "Dummy RefreshToken",
                (mockEngine.requestHistory.last().body as FormDataContent).formData["refresh_token"],
            )
            assertEquals(
                "test-client-id",
                (mockEngine.requestHistory.last().body as FormDataContent).formData["client_id"],
            )
        }

    @Test
    fun `revoke should delete token from storage`() =
        runTest {
            val httpClient = HttpClient(mockEngine)
            val oidcClientConfig =
                OidcClientConfig().apply {
                    this.httpClient = httpClient
                    discoveryEndpoint = "http://localhost/openid-configuration"
                    redirectUri = "http://localhost/redirect"
                    clientId = "test-client-id"
                    storage = MemoryStorage()
                    updateAgent(testAgent)
                }
            val oidcClient = OidcClient(oidcClientConfig)

            // First, get an access token
            val result = oidcClient.token()
            assertTrue(result is Success<Token>)

            // Then, revoke the access token
            oidcClient.revoke()

            // Check that the token is no longer in storage
            val tokenInStorage = oidcClientConfig.storage.get()
            assertNull(tokenInStorage)
        }

    @TestRailCase(22087)
    @Test
    fun `userinfo should return user info`() =
        runTest {
            val oidcClient =
                OidcClient {
                    httpClient = HttpClient(mockEngine)
                    discoveryEndpoint = "http://localhost/openid-configuration"
                    redirectUri = "http://localhost/redirect"
                    clientId = "test-client-id"
                    storage = MemoryStorage()
                    updateAgent(testAgent)
                }

            val result = oidcClient.userinfo()
            assertTrue(result is Success<JsonObject>)
            assertEquals("test-sub", result.value["sub"]?.jsonPrimitive?.content)
            assertEquals("test-name", result.value["name"]?.jsonPrimitive?.content)
        }

    @TestRailCase(22088)
    @Test
    fun `endSession should end session and revoke token`() =
        runTest {
            val httpClient = HttpClient(mockEngine)
            val oidcClientConfig =
                OidcClientConfig().apply {
                    this.httpClient = httpClient
                    discoveryEndpoint = "http://localhost/openid-configuration"
                    redirectUri = "http://localhost/redirect"
                    clientId = "test-client-id"
                    storage = MemoryStorage()
                    updateAgent(testAgent)
                }
            val oidcClient = OidcClient(oidcClientConfig)

            // First, get an access token
            val result = oidcClient.token()
            assertTrue(result is Success<Token>)

            // Then, end the session
            val endSessionResult = oidcClient.endSession()
            assertTrue(endSessionResult)

            // Check that the token is no longer in storage
            val tokenInStorage = oidcClientConfig.storage.get()
            assertNull(tokenInStorage)

            val revokeCalled =
                mockEngine.requestHistory.any { request ->
                    request.url.encodedPath == "/revoke"
                }
            assertTrue(revokeCalled, "The /revoke endpoint was not called.")

            val signOffCalled =
                mockEngine.requestHistory.any { request ->
                    request.url.encodedPath == "/idp/signoff"
                }
            assertTrue(signOffCalled, "The /signoff endpoint was not called.")
        }

    @TestRailCase(22089)
    @Test
    fun `endSession with redirect response`() =
        runTest {
            mockEngine =
                MockEngine { request ->
                    when (request.url.encodedPath) {
                        "/openid-configuration" -> {
                            respond(openIdConfigurationResponse(), HttpStatusCode.OK, headers)
                        }

                        "/token" -> {
                            respond(tokeResponse(), HttpStatusCode.OK, headers)
                        }

                        "/revoke" -> {
                            respond("", HttpStatusCode.OK, headers)
                        }

                        "/idp/signoff" -> {
                            respond("", HttpStatusCode.NoContent)
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
            val httpClient = HttpClient(mockEngine)
            val oidcClientConfig =
                OidcClientConfig().apply {
                    this.httpClient = httpClient
                    discoveryEndpoint = "http://localhost/openid-configuration"
                    redirectUri = "http://localhost/redirect"
                    clientId = "test-client-id"
                    storage = MemoryStorage()
                    updateAgent(testAgent)
                }
            val oidcClient = OidcClient(oidcClientConfig)
            oidcClient.token()
            // Then, end the session
            val endSessionResult = oidcClient.endSession()
            assertTrue(endSessionResult)

        }

    @TestRailCase(22090)
    @Test
    fun `endSession redirect response with error`() =
        runTest {
            mockEngine =
                MockEngine { request ->
                    when (request.url.encodedPath) {
                        "/openid-configuration" -> {
                            respond(openIdConfigurationResponse(), HttpStatusCode.OK, headers)
                        }

                        "/token" -> {
                            respond(tokeResponse(), HttpStatusCode.OK, headers)
                        }

                        "/revoke" -> {
                            respond("", HttpStatusCode.OK, headers)
                        }

                        "/idp/signoff" -> {
                            respond("", HttpStatusCode.Found,
                                headersOf("location" to listOf("http://localhost/signoff?error=some_error"))
                            )
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
            val httpClient = HttpClient(mockEngine)
            val oidcClientConfig =
                OidcClientConfig().apply {
                    this.httpClient = httpClient
                    discoveryEndpoint = "http://localhost/openid-configuration"
                    redirectUri = "http://localhost/redirect"
                    clientId = "test-client-id"
                    storage = MemoryStorage()
                    updateAgent(testAgent)
                }
            val oidcClient = OidcClient(oidcClientConfig)
            oidcClient.token()

            // Then, end the session
            val endSessionResult = oidcClient.endSession()
            assertFalse(endSessionResult)

        }

    @TestRailCase(22091)
    @Test
    fun `failed to retrieve access token`() =
        runTest {
            mockEngine =
                MockEngine { request ->
                    when (request.url.encodedPath) {
                        "/openid-configuration" -> {
                            respond(openIdConfigurationResponse(), HttpStatusCode.OK, headers)
                        }

                        "/token" -> {
                            respond(tokeErrorResponse(), HttpStatusCode.BadRequest, headers)
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
            val oidcClient =
                OidcClient {
                    this.httpClient = HttpClient(mockEngine)
                    discoveryEndpoint = "http://localhost/openid-configuration"
                    redirectUri = "http://localhost/redirect"
                    clientId = "test-client-id"
                    this.storage = MemoryStorage<Token>()
                    updateAgent(testAgent)
                }

            val result = oidcClient.token()
            assertTrue(result is Failure<OidcError>)
            assertTrue(result.value is OidcError.ApiError)
            assertEquals(HttpStatusCode.BadRequest.value, (result.value as OidcError.ApiError).code)
        }

    @TestRailCase(22092)
    @Test
    fun `failed to inject access token to userinfo`() =
        runTest {
            mockEngine =
                MockEngine { request ->
                    when (request.url.encodedPath) {
                        "/openid-configuration" -> {
                            respond(openIdConfigurationResponse(), HttpStatusCode.OK, headers)
                        }

                        "/token" -> {
                            respond(tokeErrorResponse(), HttpStatusCode.BadRequest, headers)
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
            val oidcClient =
                OidcClient {
                    this.httpClient = HttpClient(mockEngine)
                    discoveryEndpoint = "http://localhost/openid-configuration"
                    redirectUri = "http://localhost/redirect"
                    clientId = "test-client-id"
                    this.storage = MemoryStorage<Token>()
                    updateAgent(testAgent)
                }

            val result = oidcClient.userinfo()
            assertTrue(result is Failure<OidcError>)
            assertTrue(result.value is OidcError.ApiError)
            assertEquals(HttpStatusCode.BadRequest.value, (result.value as OidcError.ApiError).code)
        }

    @TestRailCase(22093)
    @Test
    fun `failed to retrieve userinfo`() =
        runTest {
            mockEngine =
                MockEngine { request ->
                    when (request.url.encodedPath) {
                        "/openid-configuration" -> {
                            respond(openIdConfigurationResponse(), HttpStatusCode.OK, headers)
                        }

                        "/token" -> {
                            respond(tokeResponse(), HttpStatusCode.OK, headers)
                        }

                        "/userinfo" -> {
                            respond(tokeErrorResponse(), HttpStatusCode.Unauthorized, headers)
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
            val oidcClient =
                OidcClient {
                    this.httpClient = HttpClient(mockEngine)
                    discoveryEndpoint = "http://localhost/openid-configuration"
                    redirectUri = "http://localhost/redirect"
                    clientId = "test-client-id"
                    this.storage = MemoryStorage<Token>()
                    updateAgent(testAgent)
                }

            val result = oidcClient.userinfo()
            assertTrue(result is Failure<OidcError>)
            assertTrue(result.value is OidcError.ApiError)
            assertEquals(
                HttpStatusCode.Unauthorized.value,
                (result.value as OidcError.ApiError).code,
            )
        }

    @TestRailCase(22094)
    @Test
    fun `failed to refresh token after token expired`() =
        runTest(timeout = 100.toDuration(DurationUnit.MINUTES)) {
            mockEngine =
                MockEngine { request ->
                    when (request.url.encodedPath) {
                        "/openid-configuration" -> {
                            respond(openIdConfigurationResponse(), HttpStatusCode.OK, headers)
                        }

                        "/token" -> {
                            (request.body as FormDataContent).formData["grant_type"]?.let {
                                if (it == "refresh_token") {
                                    return@MockEngine respond(
                                        tokeErrorResponse(),
                                        HttpStatusCode.BadRequest,
                                        headers,
                                    )
                                }
                            }
                            respond(tokeResponse(), HttpStatusCode.OK, headers)
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
            val oidcClient =
                OidcClient {
                    this.httpClient = HttpClient(mockEngine)
                    discoveryEndpoint = "http://localhost/openid-configuration"
                    redirectUri = "http://localhost/redirect"
                    clientId = "test-client-id"
                    this.storage = MemoryStorage<Token>()
                    updateAgent(testAgent)
                }

            val result = oidcClient.token()
            assertTrue(result is Success<Token>)
            withContext(Dispatchers.Default) {
                delay(1.toDuration(DurationUnit.SECONDS))
            }
            val refreshResult = oidcClient.token()
            assertTrue(refreshResult is Success<Token>)

            // When failed to refresh, it should called revoke
            val revokeCalled =
                mockEngine.requestHistory.any { request ->
                    request.url.encodedPath == "/revoke"
                }
            assertTrue(revokeCalled, "The /revoke endpoint was not called.")
        }
}

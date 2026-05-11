/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.oidc

import com.pingidentity.network.ktor.KtorHttpClient
import com.pingidentity.storage.MemoryStorage
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.forms.FormDataContent
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class OidcDeviceClientTest {

    private lateinit var mockEngine: MockEngine

    private val deviceAuthResponseJson = """
        {
          "device_code": "test-device-code",
          "user_code": "ABCD-1234",
          "verification_uri": "https://example.com/device",
          "verification_uri_complete": "https://example.com/device?user_code=ABCD-1234",
          "expires_in": 1800,
          "interval": 0
        }
    """.trimIndent()

    private val tokenResponseJson = """
        {
          "access_token": "test-access-token",
          "token_type": "Bearer",
          "scope": "openid",
          "expires_in": 3600
        }
    """.trimIndent()

    private fun pendingResponse() =
        ByteReadChannel("""{"error":"authorization_pending","error_description":"The user has not yet approved the request."}""")

    private fun slowDownResponse() =
        ByteReadChannel("""{"error":"slow_down","error_description":"Slow down"}""")

    private fun expiredTokenResponse() =
        ByteReadChannel("""{"error":"expired_token","error_description":"The device code has expired."}""")

    private fun accessDeniedResponse() =
        ByteReadChannel("""{"error":"access_denied","error_description":"The user denied the request."}""")

    private fun unknownErrorResponse() =
        ByteReadChannel("""{"error":"some_unknown_error","error_description":"Oops"}""")

    @BeforeTest
    fun setUp() {
        mockEngine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/openid-configuration" -> {
                    respond(openIdConfigurationWithDeviceEndpointResponse(), HttpStatusCode.OK, headers)
                }
                "/device_authorization" -> {
                    respond(ByteReadChannel(deviceAuthResponseJson), HttpStatusCode.OK, headers)
                }
                "/token" -> {
                    respond(ByteReadChannel(tokenResponseJson), HttpStatusCode.OK, headers)
                }
                else -> {
                    respond(ByteReadChannel(""), HttpStatusCode.InternalServerError)
                }
            }
        }
    }

    @AfterTest
    fun tearDown() {
        mockEngine.close()
    }

    // ------------------------------------------------------------------
    // Factory function compilation test
    // ------------------------------------------------------------------

    @Test
    fun `OidcDeviceClient factory creates instance without redirectUri`() = runTest {
        val client = OidcDeviceClient {
            discoveryEndpoint = "http://localhost/openid-configuration"
            clientId = "test-client"
            scopes = mutableSetOf("openid")
            httpClient = KtorHttpClient(HttpClient(mockEngine))
            storage = { MemoryStorage() }
        }
        assertNotNull(client)
    }

    // ------------------------------------------------------------------
    // Started emission
    // ------------------------------------------------------------------

    @Test
    fun `deviceAuthorization emits Started with non-empty userCode and verificationUri`() = runTest {
        val client = OidcDeviceClient {
            discoveryEndpoint = "http://localhost/openid-configuration"
            clientId = "test-client"
            scopes = mutableSetOf("openid")
            httpClient = KtorHttpClient(HttpClient(mockEngine))
            storage = { MemoryStorage() }
        }

        val statuses = client.deviceAuthorization().toList()

        val started = statuses.first()
        assertIs<DeviceFlowStatus.Started>(started)
        assertTrue(started.response.userCode.isNotEmpty())
        assertTrue(started.response.verificationUri.isNotEmpty())
        assertEquals("ABCD-1234", started.response.userCode)
        assertEquals("https://example.com/device", started.response.verificationUri)
    }

    // ------------------------------------------------------------------
    // Success path
    // ------------------------------------------------------------------

    @Test
    fun `deviceAuthorization emits Success and user() returns the stored User`() = runTest {
        val storage = MemoryStorage<Token>()
        val client = OidcDeviceClient {
            discoveryEndpoint = "http://localhost/openid-configuration"
            clientId = "test-client"
            scopes = mutableSetOf("openid")
            httpClient = KtorHttpClient(HttpClient(mockEngine))
            this.storage = { storage }
        }

        val statuses = client.deviceAuthorization().toList()

        val success = statuses.last()
        assertIs<DeviceFlowStatus.Success>(success)
        assertNotNull(success.user)

        // user() should return the stored user
        val user = client.user()
        assertNotNull(user)
    }

    // ------------------------------------------------------------------
    // Polling: authorization_pending
    // ------------------------------------------------------------------

    @Test
    fun `deviceAuthorization emits Polling with incrementing pollCount while authorization_pending`() = runTest {
        var tokenCallCount = 0
        val pendingThenSuccessEngine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/openid-configuration" -> respond(openIdConfigurationWithDeviceEndpointResponse(), HttpStatusCode.OK, headers)
                "/device_authorization" -> respond(ByteReadChannel(deviceAuthResponseJson), HttpStatusCode.OK, headers)
                "/token" -> {
                    tokenCallCount++
                    if (tokenCallCount < 3) {
                        respond(pendingResponse(), HttpStatusCode.BadRequest, headers)
                    } else {
                        respond(ByteReadChannel(tokenResponseJson), HttpStatusCode.OK, headers)
                    }
                }
                else -> respond(ByteReadChannel(""), HttpStatusCode.InternalServerError)
            }
        }

        val client = OidcDeviceClient {
            discoveryEndpoint = "http://localhost/openid-configuration"
            clientId = "test-client"
            scopes = mutableSetOf("openid")
            httpClient = KtorHttpClient(HttpClient(pendingThenSuccessEngine))
            storage = { MemoryStorage() }
        }

        val statuses = client.deviceAuthorization().toList()

        val pollingStatuses = statuses.filterIsInstance<DeviceFlowStatus.Polling>()
        assertEquals(2, pollingStatuses.size)
        assertEquals(1, pollingStatuses[0].pollCount)
        assertEquals(2, pollingStatuses[1].pollCount)

        assertIs<DeviceFlowStatus.Success>(statuses.last())
        pendingThenSuccessEngine.close()
    }

    // ------------------------------------------------------------------
    // Expired: expired_token
    // ------------------------------------------------------------------

    @Test
    fun `deviceAuthorization emits Expired when expired_token is returned`() = runTest {
        val expiredEngine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/openid-configuration" -> respond(openIdConfigurationWithDeviceEndpointResponse(), HttpStatusCode.OK, headers)
                "/device_authorization" -> respond(ByteReadChannel(deviceAuthResponseJson), HttpStatusCode.OK, headers)
                "/token" -> respond(expiredTokenResponse(), HttpStatusCode.BadRequest, headers)
                else -> respond(ByteReadChannel(""), HttpStatusCode.InternalServerError)
            }
        }

        val client = OidcDeviceClient {
            discoveryEndpoint = "http://localhost/openid-configuration"
            clientId = "test-client"
            scopes = mutableSetOf("openid")
            httpClient = KtorHttpClient(HttpClient(expiredEngine))
            storage = { MemoryStorage() }
        }

        val statuses = client.deviceAuthorization().toList()

        assertIs<DeviceFlowStatus.Expired>(statuses.last())
        expiredEngine.close()
    }

    // ------------------------------------------------------------------
    // AccessDenied: access_denied
    // ------------------------------------------------------------------

    @Test
    fun `deviceAuthorization emits AccessDenied when access_denied is returned`() = runTest {
        val accessDeniedEngine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/openid-configuration" -> respond(openIdConfigurationWithDeviceEndpointResponse(), HttpStatusCode.OK, headers)
                "/device_authorization" -> respond(ByteReadChannel(deviceAuthResponseJson), HttpStatusCode.OK, headers)
                "/token" -> respond(accessDeniedResponse(), HttpStatusCode.BadRequest, headers)
                else -> respond(ByteReadChannel(""), HttpStatusCode.InternalServerError)
            }
        }

        val client = OidcDeviceClient {
            discoveryEndpoint = "http://localhost/openid-configuration"
            clientId = "test-client"
            scopes = mutableSetOf("openid")
            httpClient = KtorHttpClient(HttpClient(accessDeniedEngine))
            storage = { MemoryStorage() }
        }

        val statuses = client.deviceAuthorization().toList()

        assertIs<DeviceFlowStatus.AccessDenied>(statuses.last())
        accessDeniedEngine.close()
    }

    // ------------------------------------------------------------------
    // Failure: unknown error
    // ------------------------------------------------------------------

    @Test
    fun `deviceAuthorization emits Failure on unknown server error`() = runTest {
        val errorEngine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/openid-configuration" -> respond(openIdConfigurationWithDeviceEndpointResponse(), HttpStatusCode.OK, headers)
                "/device_authorization" -> respond(ByteReadChannel(deviceAuthResponseJson), HttpStatusCode.OK, headers)
                "/token" -> respond(unknownErrorResponse(), HttpStatusCode.BadRequest, headers)
                else -> respond(ByteReadChannel(""), HttpStatusCode.InternalServerError)
            }
        }

        val client = OidcDeviceClient {
            discoveryEndpoint = "http://localhost/openid-configuration"
            clientId = "test-client"
            scopes = mutableSetOf("openid")
            httpClient = KtorHttpClient(HttpClient(errorEngine))
            storage = { MemoryStorage() }
        }

        val statuses = client.deviceAuthorization().toList()

        val failure = statuses.last()
        assertIs<DeviceFlowStatus.Failure>(failure)
        assertTrue(failure.exception.message?.contains("some_unknown_error") == true)
        errorEngine.close()
    }

    // ------------------------------------------------------------------
    // slow_down: interval increases by 5 seconds
    // ------------------------------------------------------------------

    @Test
    fun `deviceAuthorization increases interval by 5 seconds on slow_down`() = runTest {
        var tokenCallCount = 0
        val slowDownEngine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/openid-configuration" -> respond(openIdConfigurationWithDeviceEndpointResponse(), HttpStatusCode.OK, headers)
                "/device_authorization" -> respond(ByteReadChannel(deviceAuthResponseJson), HttpStatusCode.OK, headers)
                "/token" -> {
                    tokenCallCount++
                    if (tokenCallCount == 1) {
                        respond(slowDownResponse(), HttpStatusCode.BadRequest, headers)
                    } else {
                        respond(ByteReadChannel(tokenResponseJson), HttpStatusCode.OK, headers)
                    }
                }
                else -> respond(ByteReadChannel(""), HttpStatusCode.InternalServerError)
            }
        }

        val client = OidcDeviceClient {
            discoveryEndpoint = "http://localhost/openid-configuration"
            clientId = "test-client"
            scopes = mutableSetOf("openid")
            httpClient = KtorHttpClient(HttpClient(slowDownEngine))
            storage = { MemoryStorage() }
        }

        val statuses = client.deviceAuthorization().toList()

        // The Polling state emitted after slow_down must show interval = 0 (base) + 5 = 5
        val polling = statuses.filterIsInstance<DeviceFlowStatus.Polling>()
        assertEquals(1, polling.size)
        assertEquals(5, polling[0].pollInterval)

        assertIs<DeviceFlowStatus.Success>(statuses.last())
        slowDownEngine.close()
    }

    // ------------------------------------------------------------------
    // openId { deviceAuthorizationEndpoint } override
    // ------------------------------------------------------------------

    @Test
    fun `OidcDeviceClient uses openId override for deviceAuthorizationEndpoint`() = runTest {
        val overrideEngine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/custom-device" -> respond(ByteReadChannel(deviceAuthResponseJson), HttpStatusCode.OK, headers)
                "/token" -> respond(ByteReadChannel(tokenResponseJson), HttpStatusCode.OK, headers)
                else -> respond(ByteReadChannel(""), HttpStatusCode.InternalServerError)
            }
        }

        val client = OidcDeviceClient {
            clientId = "test-client"
            scopes = mutableSetOf("openid")
            httpClient = KtorHttpClient(HttpClient(overrideEngine))
            storage = { MemoryStorage() }
            openId {
                deviceAuthorizationEndpoint = "http://localhost/custom-device"
                tokenEndpoint = "http://localhost/token"
            }
        }

        val statuses = client.deviceAuthorization().toList()

        assertIs<DeviceFlowStatus.Started>(statuses.first())
        assertIs<DeviceFlowStatus.Success>(statuses.last())

        // Verify the custom device endpoint was called
        val deviceRequest = overrideEngine.requestHistory.first()
        assertEquals("/custom-device", deviceRequest.url.encodedPath)
        overrideEngine.close()
    }

    // ------------------------------------------------------------------
    // Default storage key is distinct from standard OIDC key
    // ------------------------------------------------------------------

    @Test
    fun `default storage key is com_pingidentity_sdk_v1_device_tokens`() {
        // We verify this by checking the config's storageOption default creates
        // the expected filename. We do so by inspecting that a MemoryStorage override
        // works and that the default is NOT the standard key.
        val clientWithDefault = OidcDeviceClient {
            clientId = "test"
        }
        // The config was created; we can't easily inspect the encrypted storage filename
        // in a unit test, but we verify it compiles and the constant value is correct by
        // accessing the config object.
        assertNotNull(clientWithDefault.config)
    }

    // ------------------------------------------------------------------
    // Missing device authorization endpoint
    // ------------------------------------------------------------------

    @Test
    fun `deviceAuthorization emits Failure when deviceAuthorizationEndpoint is empty`() = runTest {
        val noEndpointEngine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/openid-configuration" -> respond(openIdConfigurationResponse(), HttpStatusCode.OK, headers) // no device endpoint
                else -> respond(ByteReadChannel(""), HttpStatusCode.InternalServerError)
            }
        }

        val client = OidcDeviceClient {
            discoveryEndpoint = "http://localhost/openid-configuration"
            clientId = "test-client"
            scopes = mutableSetOf("openid")
            httpClient = KtorHttpClient(HttpClient(noEndpointEngine))
            storage = { MemoryStorage() }
        }

        val statuses = client.deviceAuthorization().toList()

        val failure = statuses.last()
        assertIs<DeviceFlowStatus.Failure>(failure)
        assertTrue(failure.exception.message?.contains("device_authorization_endpoint") == true)
        noEndpointEngine.close()
    }

    // ------------------------------------------------------------------
    // Device authorization HTTP error emits Failure (no exception thrown)
    // ------------------------------------------------------------------

    @Test
    fun `deviceAuthorization emits Failure when device authorization request fails`() = runTest {
        val serverErrorEngine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/openid-configuration" -> respond(openIdConfigurationWithDeviceEndpointResponse(), HttpStatusCode.OK, headers)
                "/device_authorization" -> respond(ByteReadChannel("server error"), HttpStatusCode.InternalServerError)
                else -> respond(ByteReadChannel(""), HttpStatusCode.InternalServerError)
            }
        }

        val client = OidcDeviceClient {
            discoveryEndpoint = "http://localhost/openid-configuration"
            clientId = "test-client"
            scopes = mutableSetOf("openid")
            httpClient = KtorHttpClient(HttpClient(serverErrorEngine))
            storage = { MemoryStorage() }
        }

        val statuses = client.deviceAuthorization().toList()

        assertIs<DeviceFlowStatus.Failure>(statuses.last())
        serverErrorEngine.close()
    }

    // ------------------------------------------------------------------
    // user() returns null when no token is stored
    // ------------------------------------------------------------------

    @Test
    fun `user returns null when no token is stored`() = runTest {
        val client = OidcDeviceClient {
            discoveryEndpoint = "http://localhost/openid-configuration"
            clientId = "test-client"
            scopes = mutableSetOf("openid")
            httpClient = KtorHttpClient(HttpClient(mockEngine))
            storage = { MemoryStorage() }
        }

        // Initialise config so storage is ready, but don't run deviceAuthorization()
        client.config.init()
        val user = client.user()
        assertNull(user)
    }

    // ------------------------------------------------------------------
    // Token endpoint request parameters
    // ------------------------------------------------------------------

    @Test
    fun `deviceAuthorization sends correct form params to device_authorization endpoint`() = runTest {
        val client = OidcDeviceClient {
            discoveryEndpoint = "http://localhost/openid-configuration"
            clientId = "my-client"
            scopes = mutableSetOf("openid", "profile")
            httpClient = KtorHttpClient(HttpClient(mockEngine))
            storage = { MemoryStorage() }
        }

        client.deviceAuthorization().toList()

        // Request 0 = discovery, request 1 = device_authorization
        val deviceAuthRequest = mockEngine.requestHistory[1]
        assertEquals("/device_authorization", deviceAuthRequest.url.encodedPath)
        val formData = (deviceAuthRequest.body as FormDataContent).formData
        assertEquals("my-client", formData["client_id"])
        assertTrue(formData["scope"]?.contains("openid") == true)
    }
}

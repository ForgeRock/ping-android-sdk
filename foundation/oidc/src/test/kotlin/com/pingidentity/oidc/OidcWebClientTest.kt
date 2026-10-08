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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
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
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    fun `duplicate parameter across sources is emitted once with per-call winning over config`() = runTest {
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
                state = "config-state"
                nonce = "config-nonce"
                additionalParameters = mapOf(
                    // state also set per-call below; login_hint overridden at config level
                    "login_hint" to "config-hint",
                )
            }
        }

        val result = web.authorize {
            "state" to "per-call-state"
            "nonce" to "per-call-nonce"
        }
        assertTrue(result.isSuccess)

        val url = urlSlot.captured
        val urlQuery = url.query ?: ""
        // Each duplicated key reaches the wire exactly once (structural dedup, no key special cases)
        assertEquals(1, Regex("state=").findAll(urlQuery).count())
        assertEquals(1, Regex("nonce=").findAll(urlQuery).count())
        assertEquals(1, Regex("login_hint=").findAll(urlQuery).count())
        // Per-call wins over config for keys set in both
        assertTrue(urlQuery.contains("state=per-call-state"))
        assertTrue(urlQuery.contains("nonce=per-call-nonce"))
        // Config-level (additionalParameters) wins over typed config members; nothing else overrode it
        assertTrue(urlQuery.contains("login_hint=config-hint"))
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

    @Test
    fun `authorize with PAR includes authorization_details in PAR body`() = runTest {
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
                authorizationDetails = listOf(
                    AuthorizationDetail(
                        type = "payment_initiation",
                        actions = listOf("initiate", "status"),
                        locations = listOf("https://example.com/"),
                        additionalFields = mapOf(
                            "instructedAmount" to buildJsonObject {
                                put("currency", "EUR")
                                put("amount", 559)
                            },
                        ),
                    ),
                )
            }
        }

        val result = web.authorize()
        assertTrue(result.isSuccess)

        // Verify PAR request (index 0=well-known, 1=par)
        val parRequest = parMockEngine.requestHistory[1]
        assertTrue(parRequest.body is FormDataContent)
        val parBody = parRequest.body as FormDataContent
        val wireValue = requireNotNull(parBody.formData[Constants.AUTHORIZATION_DETAILS])

        // Structural assertion: array-typed actions/locations and the vendor-extension member
        // (instructedAmount) must survive serialization through the additionalFields catch-all.
        val expected = buildJsonArray {
            add(buildJsonObject {
                put("type", "payment_initiation")
                put("actions", buildJsonArray { add("initiate"); add("status") })
                put("locations", buildJsonArray { add("https://example.com/") })
                put("instructedAmount", buildJsonObject {
                    put("currency", "EUR")
                    put("amount", 559)
                })
            })
        }
        assertEquals(expected, Json.parseToJsonElement(wireValue))

        // Object members are sorted for deterministic output (matches the iOS SDK's .sortedKeys)
        assertTrue(wireValue.startsWith("""[{"actions":"""))

        // Verify the browser was launched with request_uri and client_id only (PAR flow)
        val launchedUrl = urlSlot.captured
        val urlQuery = launchedUrl.query ?: ""
        assertContains(urlQuery, "request_uri=urn%3Aietf%3Aparams%3Aoauth%3Arequest_uri%3Atest-request-uri")
        assertContains(urlQuery, "client_id=test-client")
        // Regular auth params must NOT be in the browser URL when PAR is used
        assertTrue(!urlQuery.contains("scope="))
        assertTrue(!urlQuery.contains("redirect_uri="))
        assertTrue(!urlQuery.contains("code_challenge="))
        // authorization_details must ride in the PAR body, not on the browser URL
        assertFalse(urlQuery.contains("authorization_details="))

        parMockEngine.close()
    }

    @Test
    fun `authorize includes config authorizationDetails in authorization URL query`() = runTest {
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
                authorizationDetails = listOf(
                    AuthorizationDetail(
                        type = "account_information",
                        actions = listOf("list_accounts"),
                        locations = listOf("https://example.com/accounts"),
                    ),
                )
            }
        }

        val result = web.authorize()
        assertTrue(result.isSuccess)

        val url = urlSlot.captured
        val wireValue = requireNotNull(
            Uri.parse(url.toString()).getQueryParameter(Constants.AUTHORIZATION_DETAILS),
        )
        val expected = buildJsonArray {
            add(buildJsonObject {
                put("type", "account_information")
                put("actions", buildJsonArray { add("list_accounts") })
                put("locations", buildJsonArray { add("https://example.com/accounts") })
            })
        }
        assertEquals(expected, Json.parseToJsonElement(wireValue))
    }

    @Test
    fun `authorize without authorizationDetails emits no authorization_details parameter`() = runTest {
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
            }
        }

        val result = web.authorize()
        assertTrue(result.isSuccess)

        val urlQuery = urlSlot.captured.query ?: ""
        assertFalse(urlQuery.contains("authorization_details"))
    }

    @Test
    fun `hand-serialized authorization_details in additionalParameters wins over typed config`() = runTest {
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
                additionalParameters = mapOf(
                    Constants.AUTHORIZATION_DETAILS to """[{"type":"account_information"}]""",
                )
                authorizationDetails = listOf(
                    AuthorizationDetail(
                        type = "payment_initiation",
                        actions = listOf("initiate"),
                    ),
                )
            }
        }

        val result = web.authorize()
        assertTrue(result.isSuccess)

        val url = urlSlot.captured
        val urlQuery = url.query ?: ""
        // Exactly one authorization_details parameter reaches the wire
        assertEquals(1, Regex("authorization_details=").findAll(urlQuery).count())
        // ...and it is the hand-serialized value
        val wireValue = Uri.parse(url.toString())
            .getQueryParameter(Constants.AUTHORIZATION_DETAILS)
        assertEquals("""[{"type":"account_information"}]""", wireValue)
    }

    @Test
    fun `per-call typed authorizationDetails reaches the authorization URL query`() = runTest {
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
            }
        }

        val result = web.authorize {
            authorizationDetails(
                AuthorizationDetail(
                    type = "account_information",
                    actions = listOf("list_accounts"),
                    locations = listOf("https://example.com/accounts"),
                ),
            )
        }
        assertTrue(result.isSuccess)

        val url = urlSlot.captured
        val wireValue = requireNotNull(
            Uri.parse(url.toString()).getQueryParameter(Constants.AUTHORIZATION_DETAILS),
        )
        val expected = buildJsonArray {
            add(buildJsonObject {
                put("type", "account_information")
                put("actions", buildJsonArray { add("list_accounts") })
                put("locations", buildJsonArray { add("https://example.com/accounts") })
            })
        }
        assertEquals(expected, Json.parseToJsonElement(wireValue))
    }

    @Test
    fun `per-call typed authorizationDetails wins over typed config`() = runTest {
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
                authorizationDetails = listOf(
                    AuthorizationDetail(
                        type = "payment_initiation",
                        actions = listOf("initiate"),
                    ),
                )
            }
        }

        val result = web.authorize {
            authorizationDetails(
                AuthorizationDetail(
                    type = "account_information",
                    actions = listOf("list_accounts"),
                ),
            )
        }
        assertTrue(result.isSuccess)

        val url = urlSlot.captured
        val urlQuery = url.query ?: ""
        // Exactly one authorization_details parameter reaches the wire
        assertEquals(1, Regex("authorization_details=").findAll(urlQuery).count())
        // ...and it carries the per-call payload
        val wireValue = requireNotNull(
            Uri.parse(url.toString()).getQueryParameter(Constants.AUTHORIZATION_DETAILS),
        )
        val expected = buildJsonArray {
            add(buildJsonObject {
                put("type", "account_information")
                put("actions", buildJsonArray { add("list_accounts") })
            })
        }
        assertEquals(expected, Json.parseToJsonElement(wireValue))
    }

    @Test
    fun `per-call typed authorizationDetails wins over hand-serialized additionalParameters`() = runTest {
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
                additionalParameters = mapOf(
                    Constants.AUTHORIZATION_DETAILS to """[{"type":"account_information"}]""",
                )
                authorizationDetails = listOf(
                    AuthorizationDetail(
                        type = "payment_initiation",
                        actions = listOf("initiate"),
                    ),
                )
            }
        }

        val result = web.authorize {
            authorizationDetails(
                AuthorizationDetail(
                    type = "payment_initiation",
                    actions = listOf("initiate", "status"),
                    locations = listOf("https://example.com/payments"),
                ),
            )
        }
        assertTrue(result.isSuccess)

        val url = urlSlot.captured
        val urlQuery = url.query ?: ""
        // Exactly one authorization_details parameter reaches the wire
        assertEquals(1, Regex("authorization_details=").findAll(urlQuery).count())
        // ...and it carries the per-call payload
        val wireValue = requireNotNull(
            Uri.parse(url.toString()).getQueryParameter(Constants.AUTHORIZATION_DETAILS),
        )
        val expected = buildJsonArray {
            add(buildJsonObject {
                put("type", "payment_initiation")
                put("actions", buildJsonArray { add("initiate"); add("status") })
                put("locations", buildJsonArray { add("https://example.com/payments") })
            })
        }
        assertEquals(expected, Json.parseToJsonElement(wireValue))
    }

    @Test
    fun `empty per-call authorizationDetails does not suppress typed config details`() = runTest {
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
                authorizationDetails = listOf(
                    AuthorizationDetail(
                        type = "account_information",
                        actions = listOf("list_accounts"),
                        locations = listOf("https://example.com/accounts"),
                    ),
                )
            }
        }

        val result = web.authorize {
            authorizationDetails(emptyList())
        }
        assertTrue(result.isSuccess)

        val url = urlSlot.captured
        val urlQuery = url.query ?: ""
        assertEquals(1, Regex("authorization_details=").findAll(urlQuery).count())
        // The config-level list still applies
        val wireValue = requireNotNull(
            Uri.parse(url.toString()).getQueryParameter(Constants.AUTHORIZATION_DETAILS),
        )
        val expected = buildJsonArray {
            add(buildJsonObject {
                put("type", "account_information")
                put("actions", buildJsonArray { add("list_accounts") })
                put("locations", buildJsonArray { add("https://example.com/accounts") })
            })
        }
        assertEquals(expected, Json.parseToJsonElement(wireValue))
    }

    @Test
    fun `empty per-call authorizationDetails with no config emits no authorization_details parameter`() = runTest {
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
            }
        }

        val result = web.authorize {
            authorizationDetails(emptyList())
        }
        assertTrue(result.isSuccess)

        val urlQuery = urlSlot.captured.query ?: ""
        assertFalse(urlQuery.contains("authorization_details"))
    }

    @Test
    fun `per-call typed authorizationDetails reaches the PAR body`() = runTest {
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

        val result = web.authorize {
            authorizationDetails(
                AuthorizationDetail(
                    type = "payment_initiation",
                    actions = listOf("initiate", "status"),
                    locations = listOf("https://example.com/"),
                    additionalFields = mapOf(
                        "instructedAmount" to buildJsonObject {
                            put("currency", "EUR")
                            put("amount", 559)
                        },
                    ),
                ),
            )
        }
        assertTrue(result.isSuccess)

        // Verify PAR request (index 0=well-known, 1=par)
        val parRequest = parMockEngine.requestHistory[1]
        assertTrue(parRequest.body is FormDataContent)
        val parBody = parRequest.body as FormDataContent
        val wireValue = requireNotNull(parBody.formData[Constants.AUTHORIZATION_DETAILS])
        // Exactly one authorization_details value in the PAR body (Ktor coalesces same-name
        // entries, so count the values, not the keys)
        assertEquals(
            1,
            parBody.formData.getAll(Constants.AUTHORIZATION_DETAILS)?.size,
        )

        // Structural assertion: array-typed actions/locations and the vendor-extension member
        // (instructedAmount) must survive serialization through the additionalFields catch-all.
        val expected = buildJsonArray {
            add(buildJsonObject {
                put("type", "payment_initiation")
                put("actions", buildJsonArray { add("initiate"); add("status") })
                put("locations", buildJsonArray { add("https://example.com/") })
                put("instructedAmount", buildJsonObject {
                    put("currency", "EUR")
                    put("amount", 559)
                })
            })
        }
        assertEquals(expected, Json.parseToJsonElement(wireValue))

        // Verify the browser was launched with request_uri and client_id only (PAR flow)
        val launchedUrl = urlSlot.captured
        val urlQuery = launchedUrl.query ?: ""
        assertContains(urlQuery, "request_uri=urn%3Aietf%3Aparams%3Aoauth%3Arequest_uri%3Atest-request-uri")
        assertContains(urlQuery, "client_id=test-client")
        // authorization_details must ride in the PAR body, not on the browser URL
        assertFalse(urlQuery.contains("authorization_details="))

        parMockEngine.close()
    }

    // -------------------------------------------------------------------------
    // OidcWebClient JSON factory
    // -------------------------------------------------------------------------

    @Test
    fun `createOidcWebClient succeeds with valid JSON config`() {
        val json = buildJsonObject {
            put(JsonConfigKey.OIDC, buildJsonObject {
                put(JsonConfigKey.CLIENT_ID, "my-client")
                put(JsonConfigKey.DISCOVERY_ENDPOINT, "https://auth.example.com/.well-known/openid-configuration")
                put(JsonConfigKey.SCOPES, buildJsonArray { add("openid"); add("profile") })
                put(JsonConfigKey.REDIRECT_URI, "myapp://oauth2redirect")
            })
        }
        assertTrue(OidcWebClient(json).isSuccess)
    }

    @Test
    fun `createOidcWebClient succeeds with scopes as comma-separated string`() {
        val json = buildJsonObject {
            put(JsonConfigKey.OIDC, buildJsonObject {
                put(JsonConfigKey.CLIENT_ID, "my-client")
                put(JsonConfigKey.DISCOVERY_ENDPOINT, "https://auth.example.com/.well-known/openid-configuration")
                put(JsonConfigKey.SCOPES, "openid,profile")
                put(JsonConfigKey.REDIRECT_URI, "myapp://oauth2redirect")
            })
        }
        assertTrue(OidcWebClient(json).isSuccess)
    }

    @Test
    fun `createOidcWebClient fails when oidc block is missing`() {
        assertTrue(OidcWebClient(buildJsonObject {}).isFailure)
    }

    @Test
    fun `createOidcWebClient fails when clientId is missing`() {
        val json = buildJsonObject {
            put(JsonConfigKey.OIDC, buildJsonObject {
                put(JsonConfigKey.DISCOVERY_ENDPOINT, "https://auth.example.com/.well-known/openid-configuration")
                put(JsonConfigKey.SCOPES, buildJsonArray { add("openid") })
                put(JsonConfigKey.REDIRECT_URI, "myapp://oauth2redirect")
            })
        }
        assertTrue(OidcWebClient(json).isFailure)
    }

    @Test
    fun `createOidcWebClient fails when discoveryEndpoint is missing`() {
        val json = buildJsonObject {
            put(JsonConfigKey.OIDC, buildJsonObject {
                put(JsonConfigKey.CLIENT_ID, "my-client")
                put(JsonConfigKey.SCOPES, buildJsonArray { add("openid") })
                put(JsonConfigKey.REDIRECT_URI, "myapp://oauth2redirect")
            })
        }
        assertTrue(OidcWebClient(json).isFailure)
    }

    @Test
    fun `createOidcWebClient fails when scopes is missing`() {
        val json = buildJsonObject {
            put(JsonConfigKey.OIDC, buildJsonObject {
                put(JsonConfigKey.CLIENT_ID, "my-client")
                put(JsonConfigKey.DISCOVERY_ENDPOINT, "https://auth.example.com/.well-known/openid-configuration")
                put(JsonConfigKey.REDIRECT_URI, "myapp://oauth2redirect")
            })
        }
        assertTrue(OidcWebClient(json).isFailure)
    }

    @Test
    fun `createOidcWebClient succeeds without web block`() {
        val json = buildJsonObject {
            put(JsonConfigKey.OIDC, buildJsonObject {
                put(JsonConfigKey.CLIENT_ID, "my-client")
                put(JsonConfigKey.DISCOVERY_ENDPOINT, "https://auth.example.com/.well-known/openid-configuration")
                put(JsonConfigKey.SCOPES, buildJsonArray { add("openid") })
                put(JsonConfigKey.REDIRECT_URI, "myapp://oauth2redirect")
            })
        }
        assertTrue(OidcWebClient(json).isSuccess)
    }

    @Test
    fun `createOidcWebClient succeeds with all optional OIDC fields`() {
        val json = buildJsonObject {
            put(JsonConfigKey.OIDC, buildJsonObject {
                put(JsonConfigKey.CLIENT_ID, "my-client")
                put(JsonConfigKey.DISCOVERY_ENDPOINT, "https://auth.example.com/.well-known/openid-configuration")
                put(JsonConfigKey.SCOPES, buildJsonArray { add("openid") })
                put(JsonConfigKey.REDIRECT_URI, "myapp://oauth2redirect")
                put(JsonConfigKey.SIGN_OUT_REDIRECT_URI, "myapp://logout")
                put(JsonConfigKey.REFRESH_THRESHOLD, 60L)
                put(JsonConfigKey.LOGIN_HINT, "user@example.com")
                put(JsonConfigKey.STATE, "custom-state")
                put(JsonConfigKey.NONCE, "custom-nonce")
                put(JsonConfigKey.DISPLAY, "page")
                put(JsonConfigKey.PROMPT, "login")
                put(JsonConfigKey.UI_LOCALES, "en-US")
                put(JsonConfigKey.ACR_VALUES, "Level3")
                put(JsonConfigKey.PAR, true)
                put(JsonConfigKey.ADDITIONAL_PARAMETERS, buildJsonObject {
                    put("custom_param", "custom_value")
                })
                put(JsonConfigKey.OPEN_ID, buildJsonObject {
                    put(JsonConfigKey.AUTHORIZATION_ENDPOINT, "https://auth.example.com/authorize")
                    put(JsonConfigKey.TOKEN_ENDPOINT, "https://auth.example.com/token")
                    put(JsonConfigKey.USER_INFO_ENDPOINT, "https://auth.example.com/userinfo")
                    put(JsonConfigKey.END_SESSION_ENDPOINT, "https://auth.example.com/logout")
                    put(JsonConfigKey.REVOCATION_ENDPOINT, "https://auth.example.com/revoke")
                })
            })
        }
        assertTrue(OidcWebClient(json).isSuccess)
    }

    @Test
    fun `createOidcWebClient returns failure with typed error when required field is missing`() {
        val json = buildJsonObject {
            put(JsonConfigKey.OIDC, buildJsonObject {
                put(JsonConfigKey.DISCOVERY_ENDPOINT, "https://auth.example.com/.well-known/openid-configuration")
                put(JsonConfigKey.SCOPES, buildJsonArray { add("openid") })
                put(JsonConfigKey.REDIRECT_URI, "myapp://oauth2redirect")
            })
        }
        val result = OidcWebClient(json)
        assertTrue(result.isFailure)
        assertIs<JsonConfigError.MissingRequiredField>(result.exceptionOrNull())
    }

    @Test
    fun `createOidcWebClient parses authorizationDetails into the typed config field`() {
        val json = buildJsonObject {
            put(JsonConfigKey.OIDC, buildJsonObject {
                put(JsonConfigKey.CLIENT_ID, "my-client")
                put(JsonConfigKey.DISCOVERY_ENDPOINT, "https://auth.example.com/.well-known/openid-configuration")
                put(JsonConfigKey.SCOPES, buildJsonArray { add("openid") })
                put(JsonConfigKey.REDIRECT_URI, "myapp://oauth2redirect")
                put(JsonConfigKey.AUTHORIZATION_DETAILS, buildJsonArray {
                    add(buildJsonObject {
                        put("type", "payment_initiation")
                        put("actions", buildJsonArray { add("initiate"); add("status") })
                        put("locations", buildJsonArray { add("https://example.com/payments") })
                        put("instructedAmount", buildJsonObject {
                            put("currency", "EUR")
                            put("amount", 559)
                        })
                    })
                })
            })
        }

        val web = OidcWebClient(json).getOrThrow()
        val oidcConfig = web.config.modules
            .map { it.config }
            .filterIsInstance<OidcClientConfig>()
            .single()

        val details = oidcConfig.authorizationDetails
        assertEquals(1, details.size)
        assertEquals("payment_initiation", details[0].type)
        assertEquals(listOf("initiate", "status"), details[0].actions)
        assertEquals(listOf("https://example.com/payments"), details[0].locations)
        // The vendor-extension member survives via the additionalFields catch-all, whose decode
        // does not depend on the config parser's Json settings.
        assertTrue(details[0].additionalFields.containsKey("instructedAmount"))
    }

    // -------------------------------------------------------------------------
    // JSON-config driven authorize (same parse path as the OidcWebClient(json) factory)
    // -------------------------------------------------------------------------

    @Test
    fun `authorize with authorizationDetails from JSON config reaches the authorization URL query`() = runTest {
        val json = buildJsonObject {
            put(JsonConfigKey.OIDC, buildJsonObject {
                put(JsonConfigKey.CLIENT_ID, "test-client")
                put(JsonConfigKey.DISCOVERY_ENDPOINT, "http://localhost/.well-known/openid-configuration")
                put(JsonConfigKey.SCOPES, buildJsonArray { add("openid"); add("profile") })
                put(JsonConfigKey.REDIRECT_URI, "https://example.com/callback")
                put(JsonConfigKey.AUTHORIZATION_DETAILS, buildJsonArray {
                    add(buildJsonObject {
                        put("type", "account_information")
                        put("actions", buildJsonArray { add("list_accounts") })
                        put("locations", buildJsonArray { add("https://example.com/accounts") })
                    })
                })
            })
        }

        // Mirrors the OidcWebClient(json) factory body; the httpClient line is the test-only
        // injection the JSON factory has no seam for (it must be set before the workflow is
        // constructed, because the Oidc module's init propagates it at the first start()).
        val oidcConfigParser = JsonConfigParser(
            JsonConfigParser(json).required<JsonObject>(JsonConfigKey.OIDC),
        )
        val web = OidcWebClient {
            httpClient = KtorHttpClient(HttpClient(mockEngine))
            logger = Logger.CONSOLE
            module(Oidc) {
                clientId = oidcConfigParser.required<String>(JsonConfigKey.CLIENT_ID)
                discoveryEndpoint = oidcConfigParser.required<String>(JsonConfigKey.DISCOVERY_ENDPOINT)
                scopes = oidcConfigParser.scopeSet(JsonConfigKey.SCOPES)
                redirectUri = oidcConfigParser.required<String>(JsonConfigKey.REDIRECT_URI)
                update(oidcConfigParser)
                storage = { MemoryStorage() }
            }
        }

        val mockUri = mockk<Uri>()
        val urlSlot = slot<URL>()
        coEvery { BrowserLauncher.launch(capture(urlSlot), any()) } returns Result.success(mockUri)
        every { mockUri.getQueryParameter(Constants.CODE) } returns "test-code"

        val result = web.authorize()
        assertTrue(result.isSuccess)

        val url = urlSlot.captured
        val wireValue = requireNotNull(
            Uri.parse(url.toString()).getQueryParameter(Constants.AUTHORIZATION_DETAILS),
        )
        val expected = buildJsonArray {
            add(buildJsonObject {
                put("type", "account_information")
                put("actions", buildJsonArray { add("list_accounts") })
                put("locations", buildJsonArray { add("https://example.com/accounts") })
            })
        }
        assertEquals(expected, Json.parseToJsonElement(wireValue))
    }

}
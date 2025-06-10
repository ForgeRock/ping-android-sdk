/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.journey

import android.content.Context
import android.net.Uri
import com.pingidentity.journey.callback.NameCallback
import com.pingidentity.journey.callback.PasswordCallback
import com.pingidentity.journey.module.NodeTransform
import com.pingidentity.journey.module.Oidc
import com.pingidentity.journey.module.RequestUrl
import com.pingidentity.journey.module.Session
import com.pingidentity.journey.module.session
import com.pingidentity.journey.plugin.callbacks
import com.pingidentity.logger.CONSOLE
import com.pingidentity.logger.Logger
import com.pingidentity.logger.STANDARD
import com.pingidentity.oidc.Token
import com.pingidentity.orchestrate.ContinueNode
import com.pingidentity.orchestrate.ErrorNode
import com.pingidentity.orchestrate.FailureNode
import com.pingidentity.orchestrate.SuccessNode
import com.pingidentity.orchestrate.module.CustomHeader
import com.pingidentity.storage.MemoryStorage
import com.pingidentity.testrail.TestRailWatcher
import com.pingidentity.utils.Result
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.request.forms.FormDataContent
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.utils.io.ByteReadChannel
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Rule
import org.junit.rules.TestWatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue


class JourneyTest {
    @JvmField
    @Rule
    val watcher: TestWatcher = TestRailWatcher
    private val mockContext: Context = mockk()

    private lateinit var mockEngine: MockEngine

    @BeforeTest
    fun setUp() {
        CallbackInitializer().create(mockContext)

        mockEngine =
            MockEngine { request ->
                when (request.url.encodedPath) {
                    "/.well-known/openid-configuration" -> {
                        respond(openIdConfigurationResponse(), HttpStatusCode.OK, headers)
                    }

                    "/access_token" -> {
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

                    "/am/json/realms/root/sessions" -> {
                        respond("", HttpStatusCode.OK, headers)
                    }

                    "/am/json/realms/root/authenticate" -> {
                        if (request.body is TextContent) {
                            val result = request.body as TextContent
                            val json = Json.parseToJsonElement(result.text).jsonObject
                            val callbacks = json["callbacks"]?.jsonArray
                            if ((callbacks?.size ?: 0) == 2) {
                                return@MockEngine respond(
                                    sessionResponse(),
                                    HttpStatusCode.OK,
                                    authenticateHeader
                                )
                            }
                        }
                        return@MockEngine respond(
                            authenticate(),
                            HttpStatusCode.OK,
                            authenticateHeader
                        )
                    }

                    "/authorize" -> {
                        respond("", HttpStatusCode.Found, authorizeResponseHeaders)
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
    fun `Journey default module sequence`() = runTest {

        val journey = Journey {
            serverUrl = "http://localhost/am"
            // Oidc as module
            module(Oidc) {
                clientId = "test"
                discoveryEndpoint =
                    "http://localhost/.well-known/openid-configuration"
                scopes = mutableSetOf("openid", "email", "address")
                redirectUri = "http://localhost:8080"
            }
        }

        assertEquals(5, journey.config.modules.size)
        val list = journey.config.modules
        assertEquals(list[0].module, CustomHeader)
        assertEquals(list[1].module, RequestUrl)
        assertEquals(list[2].module, Session)
        assertEquals(list[3].module, NodeTransform)
        assertEquals(list[4].module, Oidc)

    }

    @Test
    fun `Journey start with default journey`() = runTest {
        val sessionStorage = MemoryStorage<SSOToken>()
        val journey =
            Journey {
                serverUrl = "http://localhost/am"
                logger = Logger.CONSOLE
                httpClient = HttpClient(mockEngine) {
                    followRedirects = false
                }
                module(Session) {
                    storage = { sessionStorage }
                }
            }

        val node = journey.start() // Return first Node
        assertTrue(node is ContinueNode)
        val startRequest = mockEngine.requestHistory[0] // authorize
        assertContains(startRequest.url.encodedQuery, "authIndexValue=login")
        assertContains(startRequest.url.encodedQuery, "authIndexType=service")
    }

    @Test
    fun `Journey Simple happy path test`() = runTest {
        val sessionStorage = MemoryStorage<SSOToken>()
        val journey =
            Journey {
                serverUrl = "http://localhost/am"
                logger = Logger.CONSOLE
                httpClient = HttpClient(mockEngine) {
                    followRedirects = false
                }
                module(Session) {
                    storage = { sessionStorage }
                }
            }

        var node = journey.start("myLogin") // Return first Node
        assertTrue(node is ContinueNode)
        assertTrue { (node as ContinueNode).callbacks.size == 2 }

        (node.callbacks[0] as? NameCallback)?.name = "My First Name"
        (node.callbacks[1] as? PasswordCallback)?.password = "My Password"

        node = node.next()
        assertTrue(node is SuccessNode)
        assertEquals("Dummy Session Token", node.session.value)
        assertEquals("Dummy Session Token", (journey.session()?.value))
        assertEquals("/enduser/?realm=/alpha", (journey.session()?.successUrl))
        assertEquals("/alpha", (journey.session()?.realm))

        val exception = assertFailsWith<IllegalStateException> {
            journey.user()
        }
        assertEquals("Oidc module is not initialized", exception.message)

        assertNotNull(sessionStorage.get())
        journey.signOff()
        assertNull(journey.session())
        assertNull(sessionStorage.get())
    }

    @Test
    fun `Journey Simple happy path test with oidc`() = runTest {
        val tokenStorage = MemoryStorage<Token>()
        val sessionStorage = MemoryStorage<SSOToken>()
        val journey =
            Journey {
                serverUrl = "http://localhost/am"
                logger = Logger.CONSOLE
                httpClient = HttpClient(mockEngine) {
                    followRedirects = false
                }
                // Oidc as module
                module(Oidc) {
                    clientId = "test"
                    discoveryEndpoint =
                        "http://localhost/.well-known/openid-configuration"
                    scopes = mutableSetOf("openid", "email", "address")
                    redirectUri = "http://localhost:8080"
                    storage = { tokenStorage }
                }
                module(Session) {
                    storage = { sessionStorage }
                }
            }

        var node = journey.start("myLogin") // Return first Node
        assertTrue(node is ContinueNode)
        assertTrue { (node as ContinueNode).callbacks.size == 2 }

        (node.callbacks[0] as? NameCallback)?.name = "My First Name"
        (node.callbacks[1] as? PasswordCallback)?.password = "My Password"

        node = node.next()
        assertTrue(node is SuccessNode)

        mockEngine.requestHistory[0] // well-known
        val startRequest = mockEngine.requestHistory[1] // authorize
        assertContains(startRequest.url.encodedQuery, "authIndexValue=myLogin")
        assertContains(startRequest.url.encodedQuery, "authIndexType=service")

        //Assert the request

        val request = mockEngine.requestHistory[2] // authenticate request
        val result = request.body as TextContent
        val json = Json.parseToJsonElement(result.text).jsonObject
        assertEquals("authIdValue", json["authId"]?.jsonPrimitive?.content)
        val callbacks = json["callbacks"]?.jsonArray
        assertEquals(2, callbacks?.size)
        val name =
            callbacks?.get(0)?.jsonObject?.get("input")
                ?.jsonArray?.get(0)?.jsonObject?.get("value")?.jsonPrimitive?.content
        val password =
            callbacks?.get(1)?.jsonObject?.get("input")
                ?.jsonArray?.get(0)?.jsonObject?.get("value")?.jsonPrimitive?.content

        assertEquals("My First Name", name)
        assertEquals("My Password", password)

        // Assert the headers are set
        assertEquals("resource=2.1, protocol=1.0", startRequest.headers["Accept-API-Version"])
        assertEquals("resource=2.1, protocol=1.0", request.headers["Accept-API-Version"])

        assertEquals("Dummy Session Token", node.session.value)


        val user = journey.user()
        assertEquals("Dummy AccessToken", (user?.token() as Result.Success).value.accessToken)
        assertEquals("Dummy Session Token", user.session().value)

        mockEngine.requestHistory[3] // /authorize
        mockEngine.requestHistory[4] // /accessToken

        user.let {
            it.logout()

            //Make sure the request to revoke is made
            val revoke = mockEngine.requestHistory[5]
            assertEquals("https://auth.test-one-pingone.com/revoke", revoke.url.toString())
            val revokeBody = revoke.body as FormDataContent
            assertEquals("test", revokeBody.formData["client_id"])
            assertEquals("Dummy RefreshToken", revokeBody.formData["token"])

            //Make sure the request to session is made
            val signOff = mockEngine.requestHistory[6]
            assertEquals(
                "http://localhost/am/json/realms/root/sessions?_action=logout",
                signOff.url.toString()
            )

            //Ensure storage are removed
            assertNull(tokenStorage.get())
            assertNull(sessionStorage.get())
        }

        //After logout make sure the user is null
        assertNull(journey.user())

    }

    @Test
    fun `Journey addition oidc parameter`() =
        runTest {
            val journey =
                Journey {
                    serverUrl = "http://localhost/am"
                    httpClient = HttpClient(mockEngine) {
                        followRedirects = false
                    }
                    // Oidc as module
                    module(Oidc) {
                        clientId = "test"
                        discoveryEndpoint =
                            "http://localhost/.well-known/openid-configuration"
                        scopes = mutableSetOf("openid", "email", "address")
                        redirectUri = "http://localhost:8080"
                        storage = { MemoryStorage() }
                        logger = Logger.STANDARD
                        acrValues = "acrValues"
                        display = "display"
                        loginHint = "login_hint"
                        nonce = "nonce"
                        prompt = "prompt"
                        uiLocales = "ui_locales"
                        additionalParameters = mutableMapOf("apKey" to "apValue")
                    }
                    module(Session) {
                        storage = { MemoryStorage() }
                    }
                }

            var node = journey.start() // Return first Node
            assertTrue(node is ContinueNode)
            (node.callbacks[0] as? NameCallback)?.name = "My First Name"
            (node.callbacks[1] as? PasswordCallback)?.password = "My Password"

            node = node.next()
            assertTrue(node is SuccessNode)

            mockEngine.requestHistory[0] // well-known
            mockEngine.requestHistory[2] // authenticate
            journey.user()?.token()
            val authorizeReq = mockEngine.requestHistory[3] // authorize
            assertContains(authorizeReq.url.encodedQuery, "client_id=test")
            assertContains(authorizeReq.url.encodedQuery, "nonce=nonce")
            assertContains(authorizeReq.url.encodedQuery, "code_challenge_method=S256")
            assertContains(authorizeReq.url.encodedQuery, "code_challenge=")
            assertContains(
                authorizeReq.url.encodedQuery,
                "redirect_uri=http%3A%2F%2Flocalhost%3A8080"
            )
            assertContains(authorizeReq.url.encodedQuery, "acr_values=acrValues")
            assertContains(authorizeReq.url.encodedQuery, "display=display")
            assertContains(authorizeReq.url.encodedQuery, "login_hint=login_hint")
            assertContains(authorizeReq.url.encodedQuery, "nonce=nonce")
            assertContains(authorizeReq.url.encodedQuery, "prompt=prompt")
            assertContains(authorizeReq.url.encodedQuery, "ui_locales=ui_locales")
            assertContains(authorizeReq.url.encodedQuery, "apKey=apValue")
        }

    @Test
    fun `Journey revoke access token`() =
        runTest {
            val tokenStorage = MemoryStorage<Token>()
            val sessionStorage = MemoryStorage<SSOToken>()
            val journey =
                Journey {
                    serverUrl = "http://localhost/am"
                    httpClient = HttpClient(mockEngine) {
                        followRedirects = false
                    }
                    // Oidc as module
                    module(Oidc) {
                        clientId = "test"
                        discoveryEndpoint =
                            "http://localhost/.well-known/openid-configuration"
                        scopes = mutableSetOf("openid", "email", "address")
                        redirectUri = "http://localhost:8080"
                        storage = { tokenStorage }
                        logger = Logger.STANDARD
                    }
                    module(Session) {
                        storage = { sessionStorage }
                    }
                }

            var node = journey.start() // Return first Node
            assertTrue(node is ContinueNode)

            (node.callbacks[0] as? NameCallback)?.name = "My First Name"
            (node.callbacks[1] as? PasswordCallback)?.password = "My Password"

            node = node.next()
            assertTrue(node is SuccessNode)

            val u = journey.user()
            u?.let {
                it.revoke()
                assertNull(tokenStorage.get())
                assertNotNull(sessionStorage.get())
            } ?: throw Exception("User is null")

        }

    @Test
    fun `Journey with options`() =
        runTest {
            val sessionStorage = MemoryStorage<SSOToken>()
            val journey =
                Journey {
                    serverUrl = "http://localhost/am"
                    httpClient = HttpClient(mockEngine) {
                        followRedirects = false
                    }
                    module(Session) {
                        storage = { sessionStorage }
                    }
                }

            var node = journey.start("myLogin") {
                forceAuth = true
                noSession = true
            }
            assertTrue(node is ContinueNode)
            assertTrue { (node as ContinueNode).callbacks.size == 2 }

            node = node.next()
            assertTrue(node is SuccessNode)

            val startRequest = mockEngine.requestHistory[0] // authenticate
            assertContains(startRequest.url.encodedQuery, "ForceAuth=true")
            assertContains(startRequest.url.encodedQuery, "noSession=true")
            val nextRequest = mockEngine.requestHistory[1] // authenticate
            assertContains(nextRequest.url.encodedQuery, "noSession=true")

        }

    @Test
    fun `Journey with unexpected Error`() = runTest {
        val sessionStorage = MemoryStorage<SSOToken>()
        val mockEngine = MockEngine {
            throw SocketTimeoutException("Simulated timeout")
        }
        val journey =
            Journey {
                serverUrl = "http://localhost/am"
                logger = Logger.CONSOLE
                httpClient = HttpClient(mockEngine) {
                    followRedirects = false
                }
                module(Session) {
                    storage = { sessionStorage }
                }
            }

        val node = journey.start() // Return first Node
        assertTrue(node is FailureNode)
        assertTrue(node.cause is SocketTimeoutException)
    }

    @Test
    fun `Journey resume uri`() = runTest {
        val uri = mockk<Uri>()
        every { uri.getQueryParameter("suspendedId") } returns "test"
        val sessionStorage = MemoryStorage<SSOToken>()
        val journey =
            Journey {
                serverUrl = "http://localhost/am"
                logger = Logger.CONSOLE
                httpClient = HttpClient(mockEngine) {
                    followRedirects = false
                }
                module(Session) {
                    storage = { sessionStorage }
                }
            }

        val node = journey.resume(uri) // Return first Node
        assertTrue(node is ContinueNode)
        val startRequest = mockEngine.requestHistory[0] // authorize
        assertContains(startRequest.url.encodedQuery, "suspendedId=test")
    }

    @Test
    fun `Journey with Error Node Return`() = runTest {
        val sessionStorage = MemoryStorage<SSOToken>()
        val mockEngine = MockEngine {
            respond(
                content =
                    ByteReadChannel("{\"error\": \"error\", \"message\": \"error\"}"),
                status = HttpStatusCode.InternalServerError,
            )
        }
        val journey =
            Journey {
                serverUrl = "http://localhost/am"
                logger = Logger.CONSOLE
                httpClient = HttpClient(mockEngine) {
                    followRedirects = false
                }
                module(Session) {
                    storage = { sessionStorage }
                }
            }

        val node = journey.start() // Return first Node
        assertTrue(node is ErrorNode)
        assertEquals("error", node.message)
    }
}

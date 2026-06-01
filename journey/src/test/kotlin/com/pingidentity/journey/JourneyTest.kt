/*
 * Copyright (c) 2024 - 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.journey

import android.content.Context
import android.net.Uri
import android.os.LocaleList
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
import com.pingidentity.network.ktor.KtorHttpClient
import com.pingidentity.oidc.Token
import com.pingidentity.orchestrate.ContinueNode
import com.pingidentity.orchestrate.ErrorNode
import com.pingidentity.orchestrate.FailureNode
import com.pingidentity.orchestrate.SuccessNode
import com.pingidentity.orchestrate.module.CustomHeader
import com.pingidentity.storage.MemoryStorage
import com.pingidentity.testrail.TestRailWatcher
import com.pingidentity.utils.Result
import com.pingidentity.utils.toAcceptLanguage
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.request.forms.FormDataContent
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue


@RunWith(RobolectricTestRunner::class)
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

                    "/par" -> {
                        respond(parResponse(), HttpStatusCode.Created, headers)
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
                httpClient = KtorHttpClient(HttpClient(mockEngine) {
                    followRedirects = false
                })
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
                httpClient = KtorHttpClient(HttpClient(mockEngine) {
                    followRedirects = false
                })
                module(Session) {
                    storage = { sessionStorage }
                }
            }

        var node = journey.start("myLogin") // Return first Node
        assertTrue(node is ContinueNode)
        assertEquals(2, node.callbacks.size)

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
                httpClient = KtorHttpClient(HttpClient(mockEngine) {
                    followRedirects = false
                })
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
        assertEquals(2, node.callbacks.size)

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
        assertEquals("ping-sdk", startRequest.headers["x-requested-with"])
        assertEquals("android", startRequest.headers["x-requested-platform"])
        assertEquals("resource=2.1, protocol=1.0", request.headers["Accept-API-Version"])
        assertNotNull(request.headers[Constants.ACCEPT_LANGUAGE])
        assertEquals(LocaleList.getDefault().toAcceptLanguage(), request.headers[Constants.ACCEPT_LANGUAGE])

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
                    httpClient = KtorHttpClient(HttpClient(mockEngine) {
                        followRedirects = false
                    })
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
                    httpClient = KtorHttpClient(HttpClient(mockEngine) {
                        followRedirects = false
                    })
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
                    httpClient = KtorHttpClient(HttpClient(mockEngine) {
                        followRedirects = false
                    })
                    module(Session) {
                        storage = { sessionStorage }
                    }
                }

            var node = journey.start("myLogin") {
                forceAuth = true
                noSession = true
            }
            assertTrue(node is ContinueNode)
            assertEquals(2, node.callbacks.size)

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
                httpClient = KtorHttpClient(HttpClient(mockEngine) {
                    followRedirects = false
                })
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
                httpClient = KtorHttpClient(HttpClient(mockEngine) {
                    followRedirects = false
                })
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
                httpClient = KtorHttpClient(HttpClient(mockEngine) {
                    followRedirects = false
                })
                module(Session) {
                    storage = { sessionStorage }
                }
            }

        val node = journey.start() // Return first Node
        assertTrue(node is ErrorNode)
        assertEquals("error", node.message)
    }

    @Test
    fun `Journey handles 3xx redirect status code with FailureNode`() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = ByteReadChannel(""),
                status = HttpStatusCode.Found,
                headers = headersOf("location", "http://example.com/redirect")
            )
        }
        val journey = Journey {
            serverUrl = "http://localhost/am"
            logger = Logger.CONSOLE
            httpClient = KtorHttpClient(HttpClient(mockEngine) {
                followRedirects = false
            })
            module(Session) {
                storage = { MemoryStorage() }
            }
        }

        val node = journey.start()
        assertTrue(node is FailureNode)
        assertTrue(node.cause is com.pingidentity.exception.ApiException)
        val exception = node.cause as com.pingidentity.exception.ApiException
        assertEquals(302, exception.status)
        assertContains(exception.message ?: "", "Unexpected redirect")
    }

    @Test
    fun `Journey handles 4xx error with code 1999 as FailureNode`() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = ByteReadChannel("""{"code": 1999, "message": "Request timed out"}"""),
                status = HttpStatusCode.BadRequest,
            )
        }
        val journey = Journey {
            serverUrl = "http://localhost/am"
            logger = Logger.CONSOLE
            httpClient = KtorHttpClient(HttpClient(mockEngine) {
                followRedirects = false
            })
            module(Session) {
                storage = { MemoryStorage() }
            }
        }

        val node = journey.start()
        assertTrue(node is FailureNode)
        assertTrue(node.cause is com.pingidentity.exception.ApiException)
        val exception = node.cause as com.pingidentity.exception.ApiException
        assertEquals(400, exception.status)
        assertEquals("Request timed out", exception.message)
    }

    @Test
    fun `Journey handles 4xx error with requestTimedOut text as FailureNode`() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = ByteReadChannel("""{"code": "requestTimedOut", "message": "The request timed out"}"""),
                status = HttpStatusCode.BadRequest,
            )
        }
        val journey = Journey {
            serverUrl = "http://localhost/am"
            logger = Logger.CONSOLE
            httpClient = KtorHttpClient(HttpClient(mockEngine) {
                followRedirects = false
            })
            module(Session) {
                storage = { MemoryStorage() }
            }
        }

        val node = journey.start()
        assertTrue(node is FailureNode)
        assertTrue(node.cause is com.pingidentity.exception.ApiException)
        val exception = node.cause as com.pingidentity.exception.ApiException
        assertEquals(400, exception.status)
        assertEquals("The request timed out", exception.message)
    }

    @Test
    fun `Journey handles 4xx API error as ErrorNode`() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = ByteReadChannel("""{"code": 401, "message": "Invalid credentials", "reason": "UNAUTHORIZED"}"""),
                status = HttpStatusCode.Unauthorized,
            )
        }
        val journey = Journey {
            serverUrl = "http://localhost/am"
            logger = Logger.CONSOLE
            httpClient = KtorHttpClient(HttpClient(mockEngine) {
                followRedirects = false
            })
            module(Session) {
                storage = { MemoryStorage() }
            }
        }

        val node = journey.start()
        assertTrue(node is ErrorNode)
        assertEquals("Invalid credentials", node.message)
    }

    @Test
    fun `Journey handles 5xx server error as ErrorNode`() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = ByteReadChannel("""{"error": "server_error", "message": "Internal server error"}"""),
                status = HttpStatusCode.InternalServerError,
            )
        }
        val journey = Journey {
            serverUrl = "http://localhost/am"
            logger = Logger.CONSOLE
            httpClient = KtorHttpClient(HttpClient(mockEngine) {
                followRedirects = false
            })
            module(Session) {
                storage = { MemoryStorage() }
            }
        }

        val node = journey.start()
        assertTrue(node is ErrorNode)
        assertEquals("Internal server error", node.message)
    }

    @Test
    fun `Journey handles 503 service unavailable as ErrorNode`() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = ByteReadChannel("""{"message": "Service temporarily unavailable"}"""),
                status = HttpStatusCode.ServiceUnavailable,
            )
        }
        val journey = Journey {
            serverUrl = "http://localhost/am"
            logger = Logger.CONSOLE
            httpClient = KtorHttpClient(HttpClient(mockEngine) {
                followRedirects = false
            })
            module(Session) {
                storage = { MemoryStorage() }
            }
        }

        val node = journey.start()
        assertTrue(node is ErrorNode)
        assertEquals("Service temporarily unavailable", node.message)
    }

    @Test
    fun `Journey handles 200 with success token response`() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = sessionResponse(),
                status = HttpStatusCode.OK,
                headers = authenticateHeader
            )
        }
        val journey = Journey {
            serverUrl = "http://localhost/am"
            logger = Logger.CONSOLE
            httpClient = KtorHttpClient(HttpClient(mockEngine) {
                followRedirects = false
            })
            module(Session) {
                storage = { MemoryStorage() }
            }
        }

        val node = journey.start()
        assertTrue(node is SuccessNode)
        assertEquals("Dummy Session Token", node.session.value)
        val ssoToken = node.session as SSOToken
        assertEquals("/enduser/?realm=/alpha", ssoToken.successUrl)
        assertEquals("/alpha", ssoToken.realm)
    }

    @Test
    fun `ContinueNode preserves all original JSON fields in request body`() = runTest {
        val journey = Journey {
            serverUrl = "http://localhost/am"
            logger = Logger.CONSOLE
            httpClient = KtorHttpClient(HttpClient(mockEngine) {
                followRedirects = false
            })
            module(Session) {
                storage = { MemoryStorage() }
            }
        }

        val node = journey.start("myLogin")
        assertTrue(node is ContinueNode)
        assertEquals(2, node.callbacks.size)

        // Set callback values
        (node.callbacks[0] as? NameCallback)?.name = "TestUser"
        (node.callbacks[1] as? PasswordCallback)?.password = "TestPassword"

        // Continue to next node
        node.next()

        // Verify the request preserves all original fields
        val authenticateRequest = mockEngine.requestHistory[1]
        val requestBody = (authenticateRequest.body as TextContent).text
        val requestJson = Json.parseToJsonElement(requestBody).jsonObject

        // Verify authId is preserved from original response
        assertEquals("authIdValue", requestJson["authId"]?.jsonPrimitive?.content)

        // Verify callbacks are updated with new values
        val callbacks = requestJson["callbacks"]?.jsonArray
        assertNotNull(callbacks)
        assertEquals(2, callbacks.size)

        // Verify callback values are updated
        val nameValue = callbacks[0].jsonObject["input"]
            ?.jsonArray?.get(0)?.jsonObject?.get("value")?.jsonPrimitive?.content
        val passwordValue = callbacks[1].jsonObject["input"]
            ?.jsonArray?.get(0)?.jsonObject?.get("value")?.jsonPrimitive?.content

        assertEquals("TestUser", nameValue)
        assertEquals("TestPassword", passwordValue)
    }

    @Test
    fun `ContinueNode preserves additional JSON fields beyond authId and callbacks`() = runTest {
        // Create a mock engine that returns a response with extra fields
        val customMockEngine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/am/json/realms/root/authenticate" -> {
                    if (request.body is TextContent) {
                        val bodyText = (request.body as TextContent).text
                        val bodyJson = Json.parseToJsonElement(bodyText).jsonObject
                        // If request has callbacks, return success response
                        if (bodyJson["callbacks"]?.jsonArray?.isNotEmpty() == true) {
                            respond(sessionResponse(), HttpStatusCode.OK, authenticateHeader)
                        } else {
                            // First request without callbacks
                            respond(
                                ByteReadChannel(
                                    """{
                                        "authId": "testAuthId",
                                        "stage": "UsernamePassword",
                                        "header": "Sign In",
                                        "description": "Please enter your credentials",
                                        "customField": "customValue",
                                        "callbacks": [
                                            {
                                                "type": "NameCallback",
                                                "output": [{"name": "prompt", "value": "User Name"}],
                                                "input": [{"name": "IDToken1", "value": ""}],
                                                "_id": 0
                                            }
                                        ]
                                    }"""
                                ),
                                HttpStatusCode.OK,
                                authenticateHeader
                            )
                        }
                    } else {
                        respond(
                            ByteReadChannel(
                                """{
                                    "authId": "testAuthId",
                                    "stage": "UsernamePassword",
                                    "header": "Sign In",
                                    "description": "Please enter your credentials",
                                    "customField": "customValue",
                                    "callbacks": [
                                        {
                                            "type": "NameCallback",
                                            "output": [{"name": "prompt", "value": "User Name"}],
                                            "input": [{"name": "IDToken1", "value": ""}],
                                            "_id": 0
                                        }
                                    ]
                                }"""
                            ),
                            HttpStatusCode.OK,
                            authenticateHeader
                        )
                    }
                }
                else -> respond(ByteReadChannel(""), HttpStatusCode.InternalServerError)
            }
        }

        val journey = Journey {
            serverUrl = "http://localhost/am"
            logger = Logger.CONSOLE
            httpClient = KtorHttpClient(HttpClient(customMockEngine) {
                followRedirects = false
            })
            module(Session) {
                storage = { MemoryStorage() }
            }
        }

        val node = journey.start()
        assertTrue(node is ContinueNode)
        assertEquals(1, node.callbacks.size)
        (node.callbacks[0] as? NameCallback)?.name = "TestUser"

        // Continue to next node
        node.next()

        // Verify the request preserves all original fields including custom ones
        val authenticateRequest = customMockEngine.requestHistory[1]
        val requestBody = (authenticateRequest.body as TextContent).text
        val requestJson = Json.parseToJsonElement(requestBody).jsonObject

        // Verify all original fields are preserved
        assertEquals("testAuthId", requestJson["authId"]?.jsonPrimitive?.content)
        assertEquals("UsernamePassword", requestJson["stage"]?.jsonPrimitive?.content)
        assertEquals("Sign In", requestJson["header"]?.jsonPrimitive?.content)
        assertEquals("Please enter your credentials", requestJson["description"]?.jsonPrimitive?.content)
        assertEquals("customValue", requestJson["customField"]?.jsonPrimitive?.content)

        // Verify callbacks are updated
        val callbacks = requestJson["callbacks"]?.jsonArray
        assertNotNull(callbacks)
        assertEquals(1, callbacks.size)

        customMockEngine.close()
    }

    @Test
    fun `Journey with PAR enabled`() = runTest {
        val tokenStorage = MemoryStorage<Token>()
        val sessionStorage = MemoryStorage<SSOToken>()
        val journey =
            Journey {
                serverUrl = "http://localhost/am"
                logger = Logger.CONSOLE
                httpClient = KtorHttpClient(HttpClient(mockEngine) {
                    followRedirects = false
                })
                // Oidc as module with PAR enabled
                module(Oidc) {
                    clientId = "test"
                    discoveryEndpoint =
                        "http://localhost/.well-known/openid-configuration"
                    scopes = mutableSetOf("openid", "email", "address")
                    redirectUri = "http://localhost:8080"
                    storage = { tokenStorage }
                    par = true // Enable PAR
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
        val startRequest = mockEngine.requestHistory[1] // authenticate
        assertContains(startRequest.url.encodedQuery, "authIndexValue=myLogin")
        assertContains(startRequest.url.encodedQuery, "authIndexType=service")

        val user = journey.user()
        assertEquals("Dummy AccessToken", (user?.token() as Result.Success).value.accessToken)
        assertEquals("Dummy Session Token", user.session().value)

        // Verify PAR request was made
        val parRequest = mockEngine.requestHistory[3] // PAR request
        assertEquals("https://auth.test-one-pingone.com/par", parRequest.url.toString())
        assertTrue(parRequest.body is FormDataContent)

        // Verify client_id and other parameters are in the POST body, not URL
        val parBody = parRequest.body as FormDataContent
        assertEquals("test", parBody.formData["client_id"])
        assertEquals("code", parBody.formData["response_type"])
        assertEquals("openid email address", parBody.formData["scope"])
        assertEquals("http://localhost:8080", parBody.formData["redirect_uri"])
        assertNotNull(parBody.formData["code_challenge"])
        assertEquals("S256", parBody.formData["code_challenge_method"])

        // Verify authorize request uses request_uri parameter
        val authorizeRequest = mockEngine.requestHistory[4] // authorize request
        assertContains(authorizeRequest.url.encodedQuery, "request_uri=urn%3Aietf%3Aparams%3Aoauth%3Arequest_uri%3Atest-request-uri")
        assertContains(authorizeRequest.url.encodedQuery, "client_id=test")
    }

    private fun parResponse(): String =
        """
        {
            "request_uri": "urn:ietf:params:oauth:request_uri:test-request-uri",
            "expires_in": 60
        }
        """.trimIndent()
}

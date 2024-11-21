/*
 * Copyright (c) 2024. PingIdentity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci

import com.pingidentity.testrail.TestRailCase
import com.pingidentity.utils.Result
import com.pingidentity.davinci.collector.PasswordCollector
import com.pingidentity.davinci.collector.SubmitCollector
import com.pingidentity.davinci.collector.TextCollector
import com.pingidentity.davinci.module.NodeTransform
import com.pingidentity.davinci.module.Oidc
import com.pingidentity.davinci.module.category
import com.pingidentity.davinci.module.description
import com.pingidentity.davinci.module.id
import com.pingidentity.davinci.module.name
import com.pingidentity.davinci.plugin.collectors
import com.pingidentity.logger.Logger
import com.pingidentity.logger.STANDARD
import com.pingidentity.oidc.Token
import com.pingidentity.orchestrate.ContinueNode
import com.pingidentity.orchestrate.SuccessNode
import com.pingidentity.orchestrate.module.Cookie
import com.pingidentity.orchestrate.module.Cookies
import com.pingidentity.orchestrate.module.CustomHeader
import com.pingidentity.storage.MemoryStorage
import com.pingidentity.testrail.TestRailWatcher
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.forms.FormDataContent
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Rule
import org.junit.rules.TestWatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DaVinciTest {
    @JvmField
    @Rule
    val watcher: TestWatcher = TestRailWatcher

    private lateinit var mockEngine: MockEngine

    @BeforeTest
    fun setUp() {
        CollectorRegistry().initialize()

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

                    "/customHTMLTemplate" -> {
                        respond(customHTMLTemplate(), HttpStatusCode.OK, customHTMLTemplateHeaders)
                    }

                    "/authorize" -> {
                        respond(authorizeResponse(), HttpStatusCode.OK, authorizeResponseHeaders)
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
    fun `DaVinci default module sequence`() = runTest {

        val daVinci = DaVinci {
            // Oidc as module
            module(Oidc) {
                clientId = "test"
                discoveryEndpoint =
                    "http://localhost/.well-known/openid-configuration"
                scopes = mutableSetOf("openid", "email", "address")
                redirectUri = "http://localhost:8080"
            }
            module(Cookie) {
                storage = MemoryStorage()
                persist = mutableListOf("ST")
            }
        }

        assertEquals(4, daVinci.config.modules.size)
        val list = daVinci.config.modules
        assertEquals(list[0].module, CustomHeader)
        assertEquals(list[1].module, NodeTransform)
        assertEquals(list[2].module, Oidc)
        assertEquals(list[3].module, Cookie)

    }

    @TestRailCase(21282)
    @Test
    fun `DaVinci Simple happy path test`() =
        runTest {
            val tokenStorage = MemoryStorage<Token>()
            val cookieStorage = MemoryStorage<Cookies>()
            val daVinci =
                DaVinci {
                    httpClient = HttpClient(mockEngine)
                    // Oidc as module
                    module(Oidc) {
                        clientId = "test"
                        discoveryEndpoint =
                            "http://localhost/.well-known/openid-configuration"
                        scopes = mutableSetOf("openid", "email", "address")
                        redirectUri = "http://localhost:8080"
                        storage = tokenStorage
                        logger = Logger.STANDARD
                    }
                    module(Cookie) {
                        storage = cookieStorage
                        persist = mutableListOf("ST")
                    }
                }

            var node = daVinci.start() // Return first Node
            assertTrue(node is ContinueNode)
            assertTrue { (node as ContinueNode).collectors.size == 5 }
            assertEquals("cq77vwelou", node.id)
            assertEquals("Username/Password Form",  node.name)
            assertEquals("Test Description",  node.description)
            assertEquals("CUSTOM_HTML",  node.category)

            (node.collectors[0] as? TextCollector)?.value = "My First Name"
            (node.collectors[1] as? PasswordCollector)?.value = "My Password"
            (node.collectors[2] as? SubmitCollector)?.value = "click me"

            node = node.next()
            assertTrue(node is SuccessNode)

            mockEngine.requestHistory[0] // well-known
            val authorizeReq = mockEngine.requestHistory[1] // authorize
            assertContains(authorizeReq.url.encodedQuery, "client_id=test")
            assertContains(authorizeReq.url.encodedQuery, "response_mode=pi.flow")
            assertContains(authorizeReq.url.encodedQuery, "code_challenge_method=S256")
            assertContains(authorizeReq.url.encodedQuery, "code_challenge=")
            assertContains(authorizeReq.url.encodedQuery, "redirect_uri=")

            //Assert the request to the customHTMLTemplate

            val request = mockEngine.requestHistory[2] // customHTMLTemplate
            val result = request.body as TextContent
            val json = Json.parseToJsonElement(result.text).jsonObject
            assertEquals("continue", json["eventName"]?.jsonPrimitive?.content)
            val parameters = json["parameters"]?.jsonObject
            val data = parameters?.get("data")?.jsonObject
            assertEquals("SIGNON", data?.get("actionKey")?.jsonPrimitive?.content)
            val formData = data?.get("formData")?.jsonObject
            assertEquals("My First Name", formData?.get("username")?.jsonPrimitive?.content)
            assertEquals("My Password", formData?.get("password")?.jsonPrimitive?.content)

            // Assert the headers are set
            assertEquals("ping-sdk", request.headers["x-requested-with"])
            assertEquals("android", request.headers["x-requested-platform"])
            assertContains(request.headers["Cookie"].toString(), "interactionId")
            assertContains(request.headers["Cookie"].toString(), "interactionToken")
            assertContains(request.headers["Cookie"].toString(), "skProxyApiEnvironmentId")

            val user = node.user
            assertEquals("Dummy AccessToken", (user.token() as Result.Success).value.accessToken)
            //val customHTMLTemplateRequest = mockEngine.requestHistory[3]

            val u = daVinci.user()
            u?.let {
                it.logout()
                //Make sure the request to revoke is made
                val revoke = mockEngine.requestHistory[4]
                assertEquals("https://auth.test-one-pingone.com/revoke", revoke.url.toString())
                val revokeBody = revoke.body as FormDataContent
                assertEquals("test", revokeBody.formData["client_id"])
                assertEquals("Dummy RefreshToken", revokeBody.formData["token"])

                //Make sure the request to signoff is made
                val signOff = mockEngine.requestHistory[5]
                assertEquals("https://auth.test-one-pingone.com/signoff?id_token_hint=Dummy+IdToken&client_id=test", signOff.url.toString())
                assertContains(signOff.headers["Cookie"].toString(), "ST=session_token")
                //Ensure storage are removed
                assertNull(tokenStorage.get())
                assertNull(cookieStorage.get())
            } ?: throw Exception("User is null")

            //After logout make sure the user is null
            assertNull(daVinci.user())

        }

    @TestRailCase(21283)
    @Test
    fun `DaVinci addition oidc parameter`() =
        runTest {
            val daVinci =
                DaVinci {
                    httpClient = HttpClient(mockEngine)
                    // Oidc as module
                    module(Oidc) {
                        clientId = "test"
                        discoveryEndpoint =
                            "http://localhost/.well-known/openid-configuration"
                        scopes = mutableSetOf("openid", "email", "address")
                        redirectUri = "http://localhost:8080"
                        storage = MemoryStorage()
                        logger = Logger.STANDARD
                        acrValues = "acrValues"
                        display = "display"
                        loginHint = "login_hint"
                        nonce = "nonce"
                        prompt = "prompt"
                        uiLocales = "ui_locales"
                    }
                    module(Cookie) {
                        storage = MemoryStorage()
                    }
                }

            var node = daVinci.start() // Return first Node
            assertTrue(node is ContinueNode)
            (node.collectors[0] as? TextCollector)?.value = "My First Name"
            (node.collectors[1] as? PasswordCollector)?.value = "My Password"
            (node.collectors[2] as? SubmitCollector)?.value = "click me"

            node = node.next()
            assertTrue(node is SuccessNode)

            mockEngine.requestHistory[0] // well-known
            val authorizeReq = mockEngine.requestHistory[1] // authorize
            assertContains(authorizeReq.url.encodedQuery, "client_id=test")
            assertContains(authorizeReq.url.encodedQuery, "response_mode=pi.flow")
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
        }

    @TestRailCase(21284)
    @Test
    fun `DaVinci revoke access token`() =
        runTest {
            val tokenStorage = MemoryStorage<Token>()
            val cookieStorage = MemoryStorage<Cookies>()
            val daVinci =
                DaVinci {
                    httpClient = HttpClient(mockEngine)
                    // Oidc as module
                    module(Oidc) {
                        clientId = "test"
                        discoveryEndpoint =
                            "http://localhost/.well-known/openid-configuration"
                        scopes = mutableSetOf("openid", "email", "address")
                        redirectUri = "http://localhost:8080"
                        storage = tokenStorage
                        logger = Logger.STANDARD
                    }
                    module(Cookie) {
                        storage = cookieStorage
                        persist = mutableListOf("ST")
                    }
                }

            var node = daVinci.start() // Return first Node
            assertTrue(node is ContinueNode)

            (node.collectors[0] as? TextCollector)?.value = "My First Name"
            (node.collectors[1] as? PasswordCollector)?.value = "My Password"
            (node.collectors[2] as? SubmitCollector)?.value = "click me"

            node = node.next()
            assertTrue(node is SuccessNode)

            val u = daVinci.user()
            u?.let {
                it.revoke()
                assertNull(tokenStorage.get())
                assertNotNull(cookieStorage.get())
            } ?: throw Exception("User is null")

        }
}

/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci

import com.pingidentity.testrail.TestRailCase
import com.pingidentity.exception.ApiException
import com.pingidentity.davinci.collector.PasswordCollector
import com.pingidentity.davinci.collector.SubmitCollector
import com.pingidentity.davinci.collector.TextCollector
import com.pingidentity.davinci.module.Oidc
import com.pingidentity.davinci.module.details
import com.pingidentity.davinci.plugin.collectors
import com.pingidentity.logger.CONSOLE
import com.pingidentity.logger.Logger
import com.pingidentity.logger.STANDARD
import com.pingidentity.orchestrate.ContinueNode
import com.pingidentity.orchestrate.module.Cookie
import com.pingidentity.storage.MemoryStorage
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue
import com.pingidentity.orchestrate.ErrorNode
import com.pingidentity.orchestrate.FailureNode
import com.pingidentity.test.readFile
import com.pingidentity.testrail.TestRailWatcher
import io.ktor.http.headers
import org.junit.Rule
import org.junit.rules.TestWatcher
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.BeforeTest
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
class DaVinciErrorTest {
    @JvmField
    @Rule
    val watcher: TestWatcher = TestRailWatcher

    private lateinit var mockEngine: MockEngine

    @BeforeTest
    fun setup() {
        CollectorRegistry().initialize()
    }

    @AfterTest
    fun tearDown() {
        mockEngine.close()
    }

    @TestRailCase(21285)
    @Test
    fun `DaVinci well-known endpoint failed`() =
        runTest {

            mockEngine =
                MockEngine { request ->
                    when (request.url.encodedPath) {
                        "/.well-known/openid-configuration" -> {
                            return@MockEngine respond(
                                content =
                                ByteReadChannel("Not Found"),
                                status = HttpStatusCode.NotFound,
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
                    }
                    module(Cookie) {
                        storage = MemoryStorage()
                        persist = mutableListOf("ST")
                    }
                }

            assertNull(daVinci.user()) //Return null instead of throwing exception

            val node = daVinci.start() // Return first Node
            assertTrue { node is FailureNode }
            assertTrue { (node as FailureNode).cause is ApiException }
            assertTrue { ((node as FailureNode).cause as ApiException).status == 404 }
            assertTrue { ((node as FailureNode).cause as ApiException).content == "Not Found" }
        }

    @TestRailCase(21286)
    @Test
    fun `DaVinci authorize endpoint failed`() =
        runTest {

            mockEngine =
                MockEngine { request ->
                    when (request.url.encodedPath) {
                        "/.well-known/openid-configuration" -> {
                            respond(openIdConfigurationResponse(), HttpStatusCode.OK, headers)
                        }

                        "/authorize" -> {
                            respond(
                                content = ByteReadChannel(
                                    "{\n" +
                                            "    \"id\": \"7bbe285f-c0e0-41ef-8925-c5c5bb370acc\",\n" +
                                            "    \"code\": \"INVALID_REQUEST\",\n" +
                                            "    \"message\": \"Invalid DV Flow Policy ID: Single_Factor\"\n" +
                                            "}"
                                ), HttpStatusCode.BadRequest, headers
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
                    }
                    module(Cookie) {
                        storage = MemoryStorage()
                        persist = mutableListOf("ST")
                    }
                }

            val node = daVinci.start() // Return first Node
            assertTrue { node is ErrorNode }
            assertContains((node as ErrorNode).input.toString(), "INVALID_REQUEST")
        }

    @TestRailCase(21287)
    @Test
    fun `DaVinci authorize endpoint failed with OK response but error during Transform`() =
        runTest {

            mockEngine =
                MockEngine { request ->
                    when (request.url.encodedPath) {
                        "/.well-known/openid-configuration" -> {
                            respond(openIdConfigurationResponse(), HttpStatusCode.OK, headers)
                        }

                        "/authorize" -> {
                            respond(
                                content = ByteReadChannel(
                                    "{\n" +
                                            "    \"environment\": {\n" +
                                            "        \"id\": \"0c6851ed-0f12-4c9a-a174-9b1bf8b438ae\"\n" +
                                            "    },\n" +
                                            "    \"status\": \"FAILED\",\n" +
                                            "    \"error\": {\n" +
                                            "        \"code\": \"login_required\",\n" +
                                            "        \"message\": \"The request could not be completed. There was an issue processing the request\"\n" +
                                            "    }\n" +
                                            "}"
                                ), HttpStatusCode.OK, headers
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
                    }
                    module(Cookie) {
                        storage = MemoryStorage()
                        persist = mutableListOf("ST")
                    }
                }

            val node = daVinci.start() // Return first Node
            assertTrue { node is FailureNode }
            assertContains((node as FailureNode).cause.toString(), "login_required")
        }

    @TestRailCase(21288)
    @Test
    fun `DaVinci transform failed for invalid json`() =
        runTest {

            mockEngine =
                MockEngine { request ->
                    when (request.url.encodedPath) {
                        "/.well-known/openid-configuration" -> {
                            respond(openIdConfigurationResponse(), HttpStatusCode.OK, headers)
                        }

                        // Sending a invalid json from server should throw a failure.
                        "/authorize" -> {
                            respond(
                                content = ByteReadChannel("{ Not a Json }"),
                                HttpStatusCode.OK,
                                authorizeResponseHeaders
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
                    }
                    module(Cookie) {
                        storage = MemoryStorage()
                        persist = mutableListOf("ST")
                    }
                }

            val node = daVinci.start() // Return first Node
            assertTrue { node is FailureNode }
            assertContains((node as FailureNode).cause.toString(), "{ Not a Json }")
        }

    @TestRailCase(21289)
    @Test
    fun `DaVinci invalid password`() =
        runTest {

            mockEngine =
                MockEngine { request ->
                    when (request.url.encodedPath) {
                        "/.well-known/openid-configuration" -> {
                            respond(openIdConfigurationResponse(), HttpStatusCode.OK, headers)
                        }

                        "/customHTMLTemplate" -> {
                            respond(
                                customHTMLTemplateWithInvalidPassword(),
                                HttpStatusCode.BadRequest,
                                customHTMLTemplateHeaders
                            )
                        }

                        "/authorize" -> {
                            respond(
                                authorizeResponse(),
                                HttpStatusCode.OK,
                                authorizeResponseHeaders
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
                    }
                    module(Cookie) {
                        storage = MemoryStorage()
                        persist = mutableListOf("ST")
                    }
                }

            val node = daVinci.start() // Return first Node
            assertTrue(node is ContinueNode)
            (node.collectors[0] as? TextCollector)?.value = "My First Name"
            (node.collectors[1] as? PasswordCollector)?.value = "My Password"
            (node.collectors[2] as? SubmitCollector)?.value = "click me"
            val next = node.next()

            //Make sure the password is cleared by close() interface
            assertEquals("", (node.collectors[1] as? PasswordCollector)?.value)

            assertTrue(next is ErrorNode)
            assertEquals(" Invalid username and/or password", next.message)
            assertContains(
                next.input.toString(),
                "The provided password did not match provisioned password"
            )

        }

    @TestRailCase(23793)
    @Test
    fun `DaVinci Authorization Failure with OK Status and Error Object in Response`() =
        runTest {

            mockEngine =
                MockEngine { request ->
                    when (request.url.encodedPath) {
                        "/.well-known/openid-configuration" -> {
                            respond(openIdConfigurationResponse(), HttpStatusCode.OK, headers)
                        }

                        "/authorize" -> {
                            respond(
                                content = ByteReadChannel(
                                    "{\n" +
                                            "    \"environment\": {\n" +
                                            "        \"id\": \"0c6851ed-0f12-4c9a-a174-9b1bf8b438ae\"\n" +
                                            "    },\n" +
                                            "    \"error\": {\n" +
                                            "        \"code\": \"login_required\",\n" +
                                            "        \"message\": \"The request could not be completed. There was an issue processing the request\"\n" +
                                            "    }\n" +
                                            "}"
                                ), HttpStatusCode.OK, headers
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
                    }
                    module(Cookie) {
                        storage = MemoryStorage()
                        persist = mutableListOf("ST")
                    }
                }

            val node = daVinci.start() // Return first Node
            assertTrue { node is FailureNode }
            assertContains((node as FailureNode).cause.toString(), "login_required")
        }

    @TestRailCase(23794)
    @Test
    fun `DaVinci 4xx Error with Error Timeout in Response`() =
        runTest {
            val randomErrorCode = listOf(
                HttpStatusCode.BadRequest,
                HttpStatusCode.NotFound,
                HttpStatusCode.MethodNotAllowed,
                HttpStatusCode.NotAcceptable
            ).random()
            mockEngine =
                MockEngine { request ->
                    when (request.url.encodedPath) {
                        "/.well-known/openid-configuration" -> {
                            respond(openIdConfigurationResponse(), HttpStatusCode.OK, headers)
                        }

                        "/authorize" -> {

                            respond(
                                content = ByteReadChannel(
                                    "{\n" +
                                            "    \"environment\": {\n" +
                                            "        \"id\": \"0c6851ed-0f12-4c9a-a174-9b1bf8b438ae\"\n" +
                                            "    },\n" +
                                            "    \"code\": \"requestTimedOut\",\n" +
                                            "    \"message\": \"Unauthorized!\"\n" +
                                            "}"
                                ), randomErrorCode, headers
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
                    }
                    module(Cookie) {
                        storage = MemoryStorage()
                        persist = mutableListOf("ST")
                    }
                }

            val node = daVinci.start() // Return first Node
            assertTrue { node is FailureNode }
            assertContains((node as FailureNode).cause.toString(), "Unauthorized!")
            val exception = node.cause as ApiException
            assertTrue { exception.status == randomErrorCode.value }

        }

    @TestRailCase(23795)
    @Test
    fun `DaVinci 4xx Error with Error Code 1999 in Response`() =
        runTest {
            val randomErrorCode = listOf(
                HttpStatusCode.BadRequest,
                HttpStatusCode.NotFound,
                HttpStatusCode.MethodNotAllowed,
                HttpStatusCode.NotAcceptable
            ).random()
            mockEngine =
                MockEngine { request ->
                    when (request.url.encodedPath) {
                        "/.well-known/openid-configuration" -> {
                            respond(openIdConfigurationResponse(), HttpStatusCode.OK, headers)
                        }

                        "/authorize" -> {
                            respond(
                                content = ByteReadChannel(
                                    "{\n" +
                                            "    \"environment\": {\n" +
                                            "        \"id\": \"0c6851ed-0f12-4c9a-a174-9b1bf8b438ae\"\n" +
                                            "    },\n" +
                                            "    \"code\": 1999,\n" +
                                            "    \"message\": \"Unauthorized!\"\n" +
                                            "}"
                                ), randomErrorCode, headers
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
                    }
                    module(Cookie) {
                        storage = MemoryStorage()
                        persist = mutableListOf("ST")
                    }
                }

            val node = daVinci.start() // Return first Node
            assertTrue { node is FailureNode }
            assertContains((node as FailureNode).cause.toString(), "Unauthorized!")
            val exception = node.cause as ApiException
            assertTrue { exception.status == randomErrorCode.value }

        }

    @TestRailCase(23796)
    @Test
    fun `DaVinci 4xx Error with Invalid Connector and Session`() =
        runTest {
            val randomErrorCode = listOf(
                HttpStatusCode.BadRequest,
                HttpStatusCode.NotFound,
                HttpStatusCode.MethodNotAllowed,
                HttpStatusCode.TooManyRequests,
                HttpStatusCode.UpgradeRequired,
                HttpStatusCode.NotAcceptable
            ).random()
            mockEngine =
                MockEngine { request ->
                    when (request.url.encodedPath) {
                        "/.well-known/openid-configuration" -> {
                            respond(openIdConfigurationResponse(), HttpStatusCode.OK, headers)
                        }

                        "/authorize" -> {
                            respond(
                                content = ByteReadChannel(
                                    "{\n" +
                                            "    \"environment\": {\n" +
                                            "        \"id\": \"0c6851ed-0f12-4c9a-a174-9b1bf8b438ae\"\n" +
                                            "    },\n" +
                                            "    \"connectorId\": \"pingOneAuthenticationConnector\",\n" +
                                            "    \"capabilityName\": \"setSession\",\n" +
                                            "    \"message\": \"Invalid Connector.\"\n" +
                                            "}"
                                ), randomErrorCode, headers
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
                    }
                    module(Cookie) {
                        storage = MemoryStorage()
                        persist = mutableListOf("ST")
                    }
                }

            val node = daVinci.start() // Return first Node
            assertTrue { node is FailureNode }
            assertContains((node as FailureNode).cause.toString(), "Invalid Connector.")
            val exception = node.cause as ApiException
            assertTrue { exception.status == randomErrorCode.value }

        }

    @TestRailCase(23797)
    @Test
    fun `DaVinci 4xx Error with Invalid Connector and Redirect`() =
        runTest {
            val randomErrorCode = listOf(400, 401, 403, 404, 405, 429, 417).random()
            mockEngine =
                MockEngine { request ->
                    when (request.url.encodedPath) {
                        "/.well-known/openid-configuration" -> {
                            respond(openIdConfigurationResponse(), HttpStatusCode.OK, headers)
                        }

                        "/authorize" -> {
                            respond(
                                content = ByteReadChannel(
                                    "{\n" +
                                            "    \"environment\": {\n" +
                                            "        \"id\": \"0c6851ed-0f12-4c9a-a174-9b1bf8b438ae\"\n" +
                                            "    },\n" +
                                            "    \"connectorId\": \"pingOneAuthenticationConnector\",\n" +
                                            "    \"capabilityName\": \"returnSuccessResponseRedirect\",\n" +
                                            "    \"message\": \"Invalid response.\"\n" +
                                            "}"
                                ), HttpStatusCode(randomErrorCode, ""), headers
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
                    }
                    module(Cookie) {
                        storage = MemoryStorage()
                        persist = mutableListOf("ST")
                    }
                }

            val node = daVinci.start() // Return first Node
            assertTrue { node is FailureNode }
            assertContains((node as FailureNode).cause.toString(), "Invalid response.")
            val exception = node.cause as ApiException
            assertTrue { exception.status == randomErrorCode }

        }

    @Test
    fun `DaVinci 3xx Error with Location Header in Response`() =
        runTest {
            mockEngine =
                MockEngine { request ->
                    when (request.url.encodedPath) {
                        "/.well-known/openid-configuration" -> {
                            respond(openIdConfigurationResponse(), HttpStatusCode.OK, headers)
                        }
                        "/authorize" -> {
                            respond(content = ByteReadChannel(""),
                                status = HttpStatusCode.Found,
                                headers = headers {
                                    append(
                                        "Location",
                                        " https://apps.pingone.ca/02fb4743-189a-4bc7-9d6c-a919edfe6447/signon/?error=test"
                                    )
                                })
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

            val daVinci =
                DaVinci {
                    httpClient = HttpClient(mockEngine) {
                        followRedirects = false
                    }
                    logger = Logger.CONSOLE
                    // Oidc as module
                    module(Oidc) {
                        clientId = "test"
                        discoveryEndpoint =
                            "http://localhost/.well-known/openid-configuration"
                        scopes = mutableSetOf("openid", "email", "address")
                        redirectUri = "http://localhost:8080"
                        storage = MemoryStorage()
                    }
                    module(Cookie) {
                        storage = MemoryStorage()
                        persist = mutableListOf("ST")
                    }
                }

            val node = daVinci.start() // Return first Node
            assertTrue { node is FailureNode }
            assertContains(
                (node as FailureNode).cause.toString(),
                "Location:  https://apps.pingone.ca/02fb4743-189a-4bc7-9d6c-a919edfe6447/signon/?error=test"
            )
            val exception = node.cause as ApiException
            assertTrue { exception.status == HttpStatusCode.Found.value }

        }

    @Test
    fun `DaVinci password policy failed`() =
        runTest {

            mockEngine =
                MockEngine { request ->
                    when (request.url.encodedPath) {
                        "/.well-known/openid-configuration" -> {
                            respond(openIdConfigurationResponse(), HttpStatusCode.OK, headers)
                        }

                        "/authorize" -> {
                            respond(
                                readFile("PasswordValidationError.json"),
                                HttpStatusCode.BadRequest,
                                authorizeResponseHeaders
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
                    }
                    module(Cookie) {
                        storage = MemoryStorage()
                        persist = mutableListOf("ST")
                    }
                }

            val node = daVinci.start() // Return first Node
            assertTrue(node is ErrorNode)
            assertEquals(1, node.details().size)
            node.details()[0].let {
                assertEquals("ffbab117-06e6-44be-a17a-ae619d3d7334", it.rawResponse.id)
                assertEquals("INVALID_DATA", it.rawResponse.code)
                assertEquals("The request could not be completed. One or more validation errors were in the request.", it.rawResponse.message)

                assertEquals(1, it.rawResponse.details?.size)
                assertEquals("INVALID_VALUE", it.rawResponse.details?.get(0)?.code)
                assertEquals("password", it.rawResponse.details?.get(0)?.target)
                assertEquals("User password did not satisfy password policy requirements", it.rawResponse.details?.get(0)?.message)
                assertEquals(5, it.rawResponse.details?.get(0)?.innerError?.errors?.size)
                assertEquals("The provided password did not contain enough characters from the character set 'ZYXWVUTSRQPONMLKJIHGFEDCBA'.  The minimum number of characters from that set that must be present in user passwords is 1", it.rawResponse.details?.get(0)?.innerError?.errors?.get("minCharacters"))
                assertEquals("The provided password (or a variant of that password) was found in a list of prohibited passwords", it.rawResponse.details?.get(0)?.innerError?.errors?.get("excludesCommonlyUsed"))
                assertEquals("The provided password is shorter than the minimum required length of 8 characters", it.rawResponse.details?.get(0)?.innerError?.errors?.get("length"))
                assertEquals("The provided password is not acceptable because it contains a character repeated more than 2 times in a row", it.rawResponse.details?.get(0)?.innerError?.errors?.get("maxRepeatedCharacters"))
                assertEquals("The provided password does not contain enough unique characters.  The minimum number of unique characters that may appear in a user password is 5", it.rawResponse.details?.get(0)?.innerError?.errors?.get("minUniqueCharacters"))

                assertEquals(400, it.statusCode)
            }

        }
}

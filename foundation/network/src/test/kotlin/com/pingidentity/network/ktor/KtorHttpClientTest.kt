/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.network.ktor

import com.pingidentity.logger.Logger
import com.pingidentity.logger.WARN
import com.pingidentity.network.isSuccess
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.DurationUnit
import kotlin.time.toDuration
import io.ktor.client.HttpClient as KtorClient

class KtorHttpClientTest {

    @Test
    fun `should add multiple request interceptors`() = runTest {
        val mockWebServer = MockWebServer()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("OK"))
        mockWebServer.start()

        var interceptor1Called = false
        var interceptor2Called = false
        var interceptor3Called = false
        var interceptor4Called = false

        val httpClient = HttpClient {
            timeout = 10.toDuration(DurationUnit.SECONDS)
            logger = Logger.WARN

            // Add multiple interceptors
            onRequest {
                interceptor1Called = true
            }
            onRequest {
                interceptor2Called = true
            }
            onResponse {
                interceptor3Called = true
            }
            onResponse {
                interceptor4Called = true
            }
        }

        val response = httpClient.request {
            url = mockWebServer.url("/test").toString()
        }

        // Verify all interceptors were called
        assertTrue(interceptor1Called, "Interceptor1 should have been called")
        assertTrue(interceptor2Called, "Interceptor2 should have been called")
        assertTrue(interceptor3Called, "Interceptor3 should have been called")
        assertTrue(interceptor4Called, "Interceptor4 should have been called")

        // Verify the request was actually made
        assertEquals(200, response.status)
        assertEquals("OK", response.body())

        httpClient.close()
        mockWebServer.shutdown()
    }

    @Test
    fun `should execute request interceptors in order`() = runTest {
        val mockWebServer = MockWebServer()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("OK"))
        mockWebServer.start()

        val executionOrder = mutableListOf<String>()

        val httpClient = HttpClient {
            timeout = 10.toDuration(DurationUnit.SECONDS)
            logger = Logger.WARN

            onRequest {
                executionOrder.add("interceptor1")
            }
            onRequest {
                executionOrder.add("interceptor2")
            }
            onRequest {
                executionOrder.add("interceptor3")
            }
        }

        httpClient.request {
            url = mockWebServer.url("/test").toString()
        }

        // Verify interceptors were called in the correct order
        assertEquals(listOf("interceptor1", "interceptor2", "interceptor3"), executionOrder)

        httpClient.close()
        mockWebServer.shutdown()
    }

    @Test
    fun `should execute response interceptors in order`() = runTest {
        val mockWebServer = MockWebServer()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("Test Response"))
        mockWebServer.start()

        val executionOrder = mutableListOf<String>()

        val httpClient = HttpClient {
            timeout = 10.toDuration(DurationUnit.SECONDS)
            logger = Logger.WARN

            onResponse {
                executionOrder.add("response-interceptor1")
            }
            onResponse {
                executionOrder.add("response-interceptor2")
            }
            onResponse {
                executionOrder.add("response-interceptor3")
            }
        }

        httpClient.request {
            url = mockWebServer.url("/test").toString()
        }

        // Verify response interceptors were called in the correct order
        assertEquals(listOf("response-interceptor1", "response-interceptor2", "response-interceptor3"), executionOrder)

        httpClient.close()
        mockWebServer.shutdown()
    }

    @Test
    fun `should allow request interceptor to access request properties`() = runTest {
        val mockWebServer = MockWebServer()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("OK"))
        mockWebServer.start()

        var capturedUrl: String? = null

        val httpClient = HttpClient {
            timeout = 10.toDuration(DurationUnit.SECONDS)
            logger = Logger.WARN

            onRequest {
                capturedUrl = this.url
            }
        }

        val testUrl = mockWebServer.url("/api/test").toString()
        httpClient.request {
            url = testUrl
            header("X-Custom-Header", "CustomValue")
        }

        // Verify the interceptor captured the correct URL
        assertEquals(testUrl, capturedUrl)

        httpClient.close()
        mockWebServer.shutdown()
    }

    @Test
    fun `should allow response interceptor to access response properties`() = runTest {
        val mockWebServer = MockWebServer()
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setBody("Created")
                .addHeader("X-Response-Id", "12345")
        )
        mockWebServer.start()

        var capturedStatus: Int? = null
        var capturedHeader: String? = null

        val httpClient = HttpClient {
            timeout = 10.toDuration(DurationUnit.SECONDS)
            logger = Logger.WARN

            onResponse {
                capturedStatus = this.status
                capturedHeader = this.header("X-Response-Id")
            }
        }

        val response = httpClient.request {
            url = mockWebServer.url("/api/create").toString()
        }

        // Verify the interceptor captured the correct response properties
        assertEquals(201, capturedStatus)
        assertEquals("12345", capturedHeader)
        // Verify the actual response
        assertEquals(201, response.status)
        assertEquals("Created", response.body())

        httpClient.close()
        mockWebServer.shutdown()
    }

    @Test
    fun `should execute interceptors for multiple requests`() = runTest {
        val mockWebServer = MockWebServer()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("Response 1"))
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("Response 2"))
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("Response 3"))
        mockWebServer.start()

        var requestCount = 0
        var responseCount = 0

        val httpClient = HttpClient {
            timeout = 10.toDuration(DurationUnit.SECONDS)
            logger = Logger.WARN

            onRequest {
                requestCount++
            }
            onResponse {
                responseCount++
            }
        }

        // Make three requests
        httpClient.request { url = mockWebServer.url("/test1").toString() }
        httpClient.request { url = mockWebServer.url("/test2").toString() }
        httpClient.request { url = mockWebServer.url("/test3").toString() }

        // Verify interceptors were called for each request
        assertEquals(3, requestCount)
        assertEquals(3, responseCount)

        httpClient.close()
        mockWebServer.shutdown()
    }

    @Test
    fun `should handle interceptor with different HTTP methods`() = runTest {
        val mockWebServer = MockWebServer()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("GET Response"))
        mockWebServer.enqueue(MockResponse().setResponseCode(201).setBody("POST Response"))
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("PUT Response"))
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("DELETE Response"))
        mockWebServer.start()

        val capturedMethods = mutableListOf<String>()

        val httpClient = HttpClient {
            timeout = 10.toDuration(DurationUnit.SECONDS)
            logger = Logger.WARN

            onRequest {
                // Methods would be captured in actual implementation
                capturedMethods.add("request")
            }
        }

        // Make requests with different methods
        httpClient.request { url = mockWebServer.url("/get").toString() }
        httpClient.request {
            url = mockWebServer.url("/post").toString()
            post(buildJsonObject { put("test", "value") })
        }
        httpClient.request {
            url = mockWebServer.url("/put").toString()
            put(buildJsonObject { put("test", "value") })
        }
        httpClient.request {
            url = mockWebServer.url("/delete").toString()
            delete()
        }

        // Verify interceptor was called for all different methods
        assertEquals(4, capturedMethods.size)

        httpClient.close()
        mockWebServer.shutdown()
    }

    @Test
    fun `should handle interceptor with error responses`() = runTest {
        val mockWebServer = MockWebServer()
        mockWebServer.enqueue(MockResponse().setResponseCode(404).setBody("Not Found"))
        mockWebServer.enqueue(MockResponse().setResponseCode(500).setBody("Server Error"))
        mockWebServer.start()

        val capturedStatuses = mutableListOf<Int>()

        val httpClient = HttpClient {
            timeout = 10.toDuration(DurationUnit.SECONDS)
            logger = Logger.WARN

            onResponse {
                capturedStatuses.add(this.status)
            }
        }

        // Make requests that return error statuses
        httpClient.request { url = mockWebServer.url("/notfound").toString() }
        httpClient.request { url = mockWebServer.url("/error").toString() }

        // Verify interceptor was called even for error responses
        assertEquals(listOf(404, 500), capturedStatuses)

        httpClient.close()
        mockWebServer.shutdown()
    }

    @Test
    fun `should handle request interceptor with headers modification`() = runTest {
        val mockWebServer = MockWebServer()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("OK"))
        mockWebServer.start()

        var headerAdded = false

        val httpClient = HttpClient {
            timeout = 10.toDuration(DurationUnit.SECONDS)
            logger = Logger.WARN

            onRequest {
                // Simulate adding a header in the interceptor
                this.header("X-Interceptor-Added", "true")
                headerAdded = true
            }
        }

        httpClient.request {
            url = mockWebServer.url("/test").toString()
        }

        // Verify the interceptor executed
        assertTrue(headerAdded)

        // Verify the request was made to the mock server
        val recordedRequest = mockWebServer.takeRequest()
        assertNotNull(recordedRequest)

        httpClient.close()
        mockWebServer.shutdown()
    }

    @Test
    fun `should work with no interceptors configured`() = runTest {
        val mockWebServer = MockWebServer()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("OK"))
        mockWebServer.start()

        val httpClient = HttpClient {
            timeout = 10.toDuration(DurationUnit.SECONDS)
            logger = Logger.WARN
            // No interceptors configured
        }

        val response = httpClient.request {
            url = mockWebServer.url("/test").toString()
        }

        // Should work fine without any interceptors
        assertEquals(200, response.status)
        assertEquals("OK", response.body())

        httpClient.close()
        mockWebServer.shutdown()
    }

    @Test
    fun `should create KtorHttpClient instance`() {
        val mockEngine = MockEngine {
            respond(content = "OK", status = HttpStatusCode.OK)
        }
        val ktorClient = KtorClient(mockEngine)
        val httpClient = KtorHttpClient(ktorClient)

        assertNotNull(httpClient)

        ktorClient.close()
    }

    @Test
    fun `should create new request instance`() {
        val mockEngine = MockEngine {
            respond(content = "OK", status = HttpStatusCode.OK)
        }
        val ktorClient = KtorClient(mockEngine)
        val httpClient = KtorHttpClient(ktorClient)

        val request = httpClient.request()

        assertNotNull(request)

        ktorClient.close()
    }

    @Test
    fun `should send request and return response`() = runTest {
        val mockEngine = MockEngine { request ->
            // Assert what was sent
            assertEquals("https://api.example.com/test", request.url.toString())
            assertEquals("GET", request.method.value)
            respond(
                content = "Test Response",
                status = HttpStatusCode.OK
            )
        }
        val ktorClient = KtorClient(mockEngine)
        val httpClient = KtorHttpClient(ktorClient)

        val request = KtorHttpRequest()
        request.url = "https://api.example.com/test"

        val response = httpClient.request(request)

        assertNotNull(response)
        assertEquals(200, response.status)
        assertEquals("Test Response", response.body())

        ktorClient.close()
    }

    @Test
    fun `should send request with builder lambda`() = runTest {
        val mockEngine = MockEngine { request ->
            // Assert what was sent
            assertEquals("https://api.example.com/create", request.url.toString())
            assertEquals("application/json", request.headers["Content-Type"])
            respond(
                content = "Builder Response",
                status = HttpStatusCode.Created
            )
        }
        val ktorClient = KtorClient(mockEngine)
        val httpClient = KtorHttpClient(ktorClient)

        val response = httpClient.request {
            url = "https://api.example.com/create"
            header("Content-Type", "application/json")
        }

        assertNotNull(response)
        assertEquals(201, response.status)
        assertEquals("Builder Response", response.body())

        ktorClient.close()
    }

    @Test
    fun `should handle POST request with JSON body`() = runTest {
        val mockEngine = MockEngine { request ->
            // Assert what was sent
            assertEquals("https://api.example.com/users", request.url.toString())
            assertEquals("POST", request.method.value)
            // Content-Type is set internally by the body() method
            respond(
                content = """{"id": 123}""",
                status = HttpStatusCode.Created
            )
        }
        val ktorClient = KtorClient(mockEngine)
        val httpClient = KtorHttpClient(ktorClient)

        val response = httpClient.request {
            url = "https://api.example.com/users"
            post(buildJsonObject {
                put("name", "John")
            })
        }

        assertEquals(201, response.status)
        assertEquals("""{"id": 123}""", response.body())

        ktorClient.close()
    }

    @Test
    fun `should handle request with headers`() = runTest {
        val mockEngine = MockEngine { request ->
            // Assert what was sent
            assertEquals("https://api.example.com/protected", request.url.toString())
            assertEquals("Bearer token123", request.headers["Authorization"])
            respond(content = "Authorized", status = HttpStatusCode.OK)
        }
        val ktorClient = KtorClient(mockEngine)
        val httpClient = KtorHttpClient(ktorClient)

        val response = httpClient.request {
            url = "https://api.example.com/protected"
            header("Authorization", "Bearer token123")
        }

        assertEquals(200, response.status)
        assertEquals("Authorized", response.body())

        ktorClient.close()
    }

    @Test
    fun `should handle request with query parameters`() = runTest {
        val mockEngine = MockEngine { request ->
            // Assert what was sent
            assertEquals("https://api.example.com/search", request.url.toString().substringBefore('?'))
            assertEquals("kotlin", request.url.parameters["q"])
            assertEquals("10", request.url.parameters["limit"])
            respond(content = "Search Results", status = HttpStatusCode.OK)
        }
        val ktorClient = KtorClient(mockEngine)
        val httpClient = KtorHttpClient(ktorClient)

        val response = httpClient.request {
            url = "https://api.example.com/search"
            parameter("q", "kotlin")
            parameter("limit", "10")
        }

        assertEquals(200, response.status)
        assertEquals("Search Results", response.body())

        ktorClient.close()
    }

    @Test
    fun `should handle 404 response`() = runTest {
        val mockEngine = MockEngine { request ->
            // Assert what was sent
            assertEquals("https://api.example.com/missing", request.url.toString())
            assertEquals("GET", request.method.value)
            respond(
                content = "Not Found",
                status = HttpStatusCode.NotFound
            )
        }
        val ktorClient = KtorClient(mockEngine)
        val httpClient = KtorHttpClient(ktorClient)

        val request = KtorHttpRequest()
        request.url = "https://api.example.com/missing"

        val response = httpClient.request(request)

        assertEquals(404, response.status)
        assertEquals("Not Found", response.body())

        ktorClient.close()
    }

    @Test
    fun `should handle 500 server error`() = runTest {
        val mockEngine = MockEngine { request ->
            // Assert what was sent
            assertEquals("https://api.example.com/error", request.url.toString())
            assertEquals("GET", request.method.value)
            respond(
                content = "Internal Server Error",
                status = HttpStatusCode.InternalServerError
            )
        }
        val ktorClient = KtorClient(mockEngine)
        val httpClient = KtorHttpClient(ktorClient)

        val request = KtorHttpRequest()
        request.url = "https://api.example.com/error"

        val response = httpClient.request(request)

        assertEquals(500, response.status)
        assertEquals("Internal Server Error", response.body())

        ktorClient.close()
    }

    @Test
    fun `should handle response with cookies`() = runTest {
        val mockEngine = MockEngine { request ->
            // Assert what was sent
            assertEquals("https://api.example.com/login", request.url.toString())
            assertEquals("GET", request.method.value)
            respond(
                content = "OK",
                status = HttpStatusCode.OK,
                headers = headersOf(
                    "Set-Cookie" to listOf(
                        "sessionId=abc123; Path=/; HttpOnly"
                    )
                )
            )
        }
        val ktorClient = KtorClient(mockEngine)
        val httpClient = KtorHttpClient(ktorClient)

        val response = httpClient.request {
            url = "https://api.example.com/login"
        }

        assertEquals(200, response.status)
        val cookies = response.cookies()
        assertEquals(1, cookies.size)
        assertTrue(cookies[0].contains("sessionId=abc123"))

        ktorClient.close()
    }

    @Test
    fun `should handle multiple requests sequentially`() = runTest {
        var requestCount = 0
        val expectedUrls = listOf("https://api.example.com/first", "https://api.example.com/second")
        val mockEngine = MockEngine { request ->
            // Assert what was sent
            assertEquals(expectedUrls[requestCount], request.url.toString())
            assertEquals("GET", request.method.value)
            requestCount++
            respond(
                content = "Request $requestCount",
                status = HttpStatusCode.OK
            )
        }
        val ktorClient = KtorClient(mockEngine)
        val httpClient = KtorHttpClient(ktorClient)

        val response1 = httpClient.request {
            url = "https://api.example.com/first"
        }
        val response2 = httpClient.request {
            url = "https://api.example.com/second"
        }

        assertEquals("Request 1", response1.body())
        assertEquals("Request 2", response2.body())
        assertEquals(2, requestCount)

        ktorClient.close()
    }

    @Test
    fun `should close underlying Ktor client`() {
        val mockEngine = MockEngine {
            respond(content = "OK", status = HttpStatusCode.OK)
        }
        val ktorClient = KtorClient(mockEngine)
        val httpClient = KtorHttpClient(ktorClient)

        // Should not throw exception
        httpClient.close()

        // Client closed successfully (no exception thrown)
        assertNotNull(httpClient)
    }

    @Test
    fun `should handle form data submission`() = runTest {
        val mockEngine = MockEngine { request ->
            // Assert what was sent
            assertEquals("https://api.example.com/form", request.url.toString())
            assertEquals("POST", request.method.value)
            respond(
                content = "Form Submitted",
                status = HttpStatusCode.OK
            )
        }
        val ktorClient = KtorClient(mockEngine)
        val httpClient = KtorHttpClient(ktorClient)

        val response = httpClient.request {
            url = "https://api.example.com/form"
            form {
                put("username", "john.doe")
                put("password", "secret")
            }
        }

        assertEquals(200, response.status)
        assertEquals("Form Submitted", response.body())

        ktorClient.close()
    }

    @Test
    fun `should handle request with custom headers`() = runTest {
        val mockEngine = MockEngine { request ->
            // Assert what was sent
            assertEquals("https://api.example.com", request.url.toString())
            assertEquals("application/json", request.headers["Accept"])
            assertEquals("MyApp/1.0", request.headers["User-Agent"])
            respond(content = "OK", status = HttpStatusCode.OK)
        }
        val ktorClient = KtorClient(mockEngine)
        val httpClient = KtorHttpClient(ktorClient)

        val response = httpClient.request {
            url = "https://api.example.com"
            header("Accept", "application/json")
            header("User-Agent", "MyApp/1.0")
        }

        assertEquals(200, response.status)

        ktorClient.close()
    }

    @Test
    fun `should return response headers`() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = "OK",
                status = HttpStatusCode.OK,
                headers = headersOf(
                    "Content-Type" to listOf("application/json"),
                    "X-Custom-Header" to listOf("custom-value")
                )
            )
        }
        val ktorClient = KtorClient(mockEngine)
        val httpClient = KtorHttpClient(ktorClient)

        val response = httpClient.request {
            url = "https://api.example.com"
        }

        assertEquals("application/json", response.header("Content-Type"))
        assertEquals("custom-value", response.header("X-Custom-Header"))

        ktorClient.close()
    }

    @Test
    fun `should preserve request in response`() = runTest {
        val mockEngine = MockEngine {
            respond(content = "OK", status = HttpStatusCode.OK)
        }
        val ktorClient = KtorClient(mockEngine)
        val httpClient = KtorHttpClient(ktorClient)

        val request = KtorHttpRequest()
        request.url = "https://api.example.com/test"

        val response = httpClient.request(request)

        assertEquals(request, response.request)
        assertEquals("https://api.example.com/test", response.request.url)

        ktorClient.close()
    }

    @Test
    fun `should handle empty response body`() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = "",
                status = HttpStatusCode.NoContent
            )
        }
        val ktorClient = KtorClient(mockEngine)
        val httpClient = KtorHttpClient(ktorClient)

        val response = httpClient.request {
            url = "https://api.example.com/delete"
        }

        assertEquals(204, response.status)
        assertEquals("", response.body())

        ktorClient.close()
    }

    @Test
    fun `should use isSuccess extension on status`() = runTest {
        val mockEngine = MockEngine {
            respond(content = "OK", status = HttpStatusCode.OK)
        }
        val ktorClient = KtorClient(mockEngine)
        val httpClient = KtorHttpClient(ktorClient)

        val response = httpClient.request {
            url = "https://api.example.com"
        }

        assertTrue(response.status.isSuccess())

        ktorClient.close()
    }

    @Test
    fun `should handle large response body`() = runTest {
        val largeBody = "x".repeat(100000)
        val mockEngine = MockEngine {
            respond(
                content = largeBody,
                status = HttpStatusCode.OK
            )
        }
        val ktorClient = KtorClient(mockEngine)
        val httpClient = KtorHttpClient(ktorClient)

        val response = httpClient.request {
            url = "https://api.example.com/large"
        }

        assertEquals(200, response.status)
        assertEquals(100000, response.body().length)

        ktorClient.close()
    }

    @Test
    fun `should handle concurrent request creation`() = runTest {
        val mockEngine = MockEngine {
            respond(content = "OK", status = HttpStatusCode.OK)
        }
        val ktorClient = KtorClient(mockEngine)
        val httpClient = KtorHttpClient(ktorClient)

        // Create multiple requests
        val request1 = httpClient.request()
        val request2 = httpClient.request()
        val request3 = httpClient.request()

        assertNotNull(request1)
        assertNotNull(request2)
        assertNotNull(request3)

        // Verify they are different instances
        assertTrue(request1 !== request2)
        assertTrue(request2 !== request3)

        ktorClient.close()
    }

    @Test
    fun `should handle DELETE request without body`() = runTest {
        val mockEngine = MockEngine { request ->
            // Assert what was sent
            assertEquals("https://api.example.com/users/123", request.url.toString())
            assertEquals("DELETE", request.method.value)
            respond(
                content = "Deleted",
                status = HttpStatusCode.NoContent
            )
        }
        val ktorClient = KtorClient(mockEngine)
        val httpClient = KtorHttpClient(ktorClient)

        val response = httpClient.request {
            url = "https://api.example.com/users/123"
            delete()
        }

        assertEquals(204, response.status)
        assertEquals("Deleted", response.body())

        ktorClient.close()
    }

    @Test
    fun `should handle DELETE request with JSON body`() = runTest {
        val mockEngine = MockEngine { request ->
            // Assert what was sent
            assertEquals("https://api.example.com/users/123", request.url.toString())
            assertEquals("DELETE", request.method.value)
            respond(
                content = """{"message": "User deleted"}""",
                status = HttpStatusCode.OK
            )
        }
        val ktorClient = KtorClient(mockEngine)
        val httpClient = KtorHttpClient(ktorClient)

        val response = httpClient.request {
            url = "https://api.example.com/users/123"
            delete(buildJsonObject {
                put("reason", "user requested")
            })
        }

        assertEquals(200, response.status)
        assertEquals("""{"message": "User deleted"}""", response.body())

        ktorClient.close()
    }

    @Test
    fun `should handle PUT request with JSON body`() = runTest {
        val mockEngine = MockEngine { request ->
            // Assert what was sent
            assertEquals("https://api.example.com/users/123", request.url.toString())
            assertEquals("PUT", request.method.value)
            respond(
                content = """{"id": 123, "name": "Updated Name"}""",
                status = HttpStatusCode.OK
            )
        }
        val ktorClient = KtorClient(mockEngine)
        val httpClient = KtorHttpClient(ktorClient)

        val response = httpClient.request {
            url = "https://api.example.com/users/123"
            put(buildJsonObject {
                put("name", "Updated Name")
                put("email", "updated@example.com")
            })
        }

        assertEquals(200, response.status)
        assertEquals("""{"id": 123, "name": "Updated Name"}""", response.body())

        ktorClient.close()
    }

    @Test
    fun `should handle PUT request without body`() = runTest {
        val mockEngine = MockEngine { request ->
            // Assert what was sent
            assertEquals("https://api.example.com/users/123/activate", request.url.toString())
            assertEquals("PUT", request.method.value)
            respond(
                content = """{"status": "activated"}""",
                status = HttpStatusCode.OK
            )
        }
        val ktorClient = KtorClient(mockEngine)
        val httpClient = KtorHttpClient(ktorClient)

        val response = httpClient.request {
            url = "https://api.example.com/users/123/activate"
            put()
        }

        assertEquals(200, response.status)
        assertEquals("""{"status": "activated"}""", response.body())

        ktorClient.close()
    }

    @Test
    fun `should handle DELETE with headers`() = runTest {
        val mockEngine = MockEngine { request ->
            // Assert what was sent
            assertEquals("https://api.example.com/resources/456", request.url.toString())
            assertEquals("DELETE", request.method.value)
            assertEquals("Bearer token123", request.headers["Authorization"])
            respond(
                content = "OK",
                status = HttpStatusCode.OK
            )
        }
        val ktorClient = KtorClient(mockEngine)
        val httpClient = KtorHttpClient(ktorClient)

        val response = httpClient.request {
            url = "https://api.example.com/resources/456"
            header("Authorization", "Bearer token123")
            delete()
        }

        assertEquals(200, response.status)

        ktorClient.close()
    }

    @Test
    fun `should handle PUT with headers`() = runTest {
        val mockEngine = MockEngine { request ->
            // Assert what was sent
            assertEquals("https://api.example.com/settings", request.url.toString())
            assertEquals("PUT", request.method.value)
            assertEquals("Bearer token123", request.headers["Authorization"])
            respond(
                content = """{"updated": true}""",
                status = HttpStatusCode.OK
            )
        }
        val ktorClient = KtorClient(mockEngine)
        val httpClient = KtorHttpClient(ktorClient)

        val response = httpClient.request {
            url = "https://api.example.com/settings"
            header("Authorization", "Bearer token123")
            put(buildJsonObject {
                put("theme", "dark")
            })
        }

        assertEquals(200, response.status)
        assertEquals("""{"updated": true}""", response.body())

        ktorClient.close()
    }

    @Test
    fun `should send x-requested-with and x-requested-platform headers to server`() = runTest {
        val mockWebServer = MockWebServer()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("OK"))
        mockWebServer.start()

        val httpClient = HttpClient {
            timeout = 10.toDuration(DurationUnit.SECONDS)
            logger = Logger.WARN
        }

        val response = httpClient.request {
            url = mockWebServer.url("/test").toString()
        }

        assertEquals(200, response.status)

        // Verify the server received the headers
        val recordedRequest = mockWebServer.takeRequest()
        assertNotNull(recordedRequest)
        assertEquals("ping-sdk", recordedRequest.getHeader("x-requested-with"))
        assertEquals("android", recordedRequest.getHeader("x-requested-platform"))

        httpClient.close()
        mockWebServer.shutdown()
    }
}

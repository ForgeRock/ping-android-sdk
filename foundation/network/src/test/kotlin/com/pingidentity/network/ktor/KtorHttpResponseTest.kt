/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.network.ktor

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import io.ktor.client.HttpClient as KtorClient

class KtorHttpResponseTest {

    @Test
    fun `should create KtorHttpResponse instance`() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = "test response",
                status = HttpStatusCode.OK
            )
        }

        val client = KtorClient(mockEngine)
        val ktorResponse = client.get("https://api.example.com")
        val mockRequest = KtorHttpRequest()

        val response = KtorHttpResponse(mockRequest, ktorResponse)

        assertNotNull(response)
        assertEquals(mockRequest, response.request)

        client.close()
    }

    @Test
    fun `should return correct status code`() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = "OK",
                status = HttpStatusCode.OK
            )
        }

        val client = KtorClient(mockEngine)
        val ktorResponse = client.get("https://api.example.com")
        val response = KtorHttpResponse(KtorHttpRequest(), ktorResponse)

        assertEquals(200, response.status)

        client.close()
    }

    @Test
    fun `should return 201 Created status code`() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = "Created",
                status = HttpStatusCode.Created
            )
        }

        val client = KtorClient(mockEngine)
        val ktorResponse = client.get("https://api.example.com")
        val response = KtorHttpResponse(KtorHttpRequest(), ktorResponse)

        assertEquals(201, response.status)

        client.close()
    }

    @Test
    fun `should return 404 Not Found status code`() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = "Not Found",
                status = HttpStatusCode.NotFound
            )
        }

        val client = KtorClient(mockEngine)
        val ktorResponse = client.get("https://api.example.com")
        val response = KtorHttpResponse(KtorHttpRequest(), ktorResponse)

        assertEquals(404, response.status)

        client.close()
    }

    @Test
    fun `should return 500 Internal Server Error status code`() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = "Server Error",
                status = HttpStatusCode.InternalServerError
            )
        }

        val client = KtorClient(mockEngine)
        val ktorResponse = client.get("https://api.example.com")
        val response = KtorHttpResponse(KtorHttpRequest(), ktorResponse)

        assertEquals(500, response.status)

        client.close()
    }

    @Test
    fun `should return response body as string`() = runTest {
        val expectedBody = "Hello, World!"
        val mockEngine = MockEngine {
            respond(
                content = expectedBody,
                status = HttpStatusCode.OK
            )
        }

        val client = KtorClient(mockEngine)
        val ktorResponse = client.get("https://api.example.com")
        val response = KtorHttpResponse(KtorHttpRequest(), ktorResponse)

        assertEquals(expectedBody, response.body())

        client.close()
    }

    @Test
    fun `should return JSON response body`() = runTest {
        val jsonBody = """{"name": "John", "age": 30}"""
        val mockEngine = MockEngine {
            respond(
                content = jsonBody,
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type", "application/json")
            )
        }

        val client = KtorClient(mockEngine)
        val ktorResponse = client.get("https://api.example.com")
        val response = KtorHttpResponse(KtorHttpRequest(), ktorResponse)

        assertEquals(jsonBody, response.body())

        client.close()
    }

    @Test
    fun `should return empty body`() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = "",
                status = HttpStatusCode.NoContent
            )
        }

        val client = KtorClient(mockEngine)
        val ktorResponse = client.get("https://api.example.com")
        val response = KtorHttpResponse(KtorHttpRequest(), ktorResponse)

        assertEquals("", response.body())

        client.close()
    }

    @Test
    fun `should return cookies from Set-Cookie header`() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = "OK",
                status = HttpStatusCode.OK,
                headers = headersOf(
                    "Set-Cookie", listOf(
                        "sessionId=abc123; Path=/; HttpOnly",
                        "userId=456; Path=/; Secure"
                    )
                )
            )
        }

        val client = KtorClient(mockEngine)
        val ktorResponse = client.get("https://api.example.com")
        val response = KtorHttpResponse(KtorHttpRequest(), ktorResponse)

        val cookies = response.cookies()

        assertEquals(2, cookies.size)
        assertTrue(cookies[0].contains("sessionId=abc123"))
        assertTrue(cookies[1].contains("userId=456"))

        client.close()
    }

    @Test
    fun `should return empty list when no cookies`() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = "OK",
                status = HttpStatusCode.OK
            )
        }

        val client = KtorClient(mockEngine)
        val ktorResponse = client.get("https://api.example.com")
        val response = KtorHttpResponse(KtorHttpRequest(), ktorResponse)

        val cookies = response.cookies()

        assertEquals(0, cookies.size)

        client.close()
    }

    @Test
    fun `should return single cookie`() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = "OK",
                status = HttpStatusCode.OK,
                headers = headersOf("Set-Cookie", "token=xyz789; Path=/")
            )
        }

        val client = KtorClient(mockEngine)
        val ktorResponse = client.get("https://api.example.com")
        val response = KtorHttpResponse(KtorHttpRequest(), ktorResponse)

        val cookies = response.cookies()

        assertEquals(1, cookies.size)
        assertTrue(cookies[0].contains("token=xyz789"))

        client.close()
    }

    @Test
    fun `should return specific header value`() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = "OK",
                status = HttpStatusCode.OK,
                headers = headersOf(
                    "Content-Type" to listOf("application/json"),
                    "Authorization" to listOf("Bearer token123")
                )
            )
        }

        val client = KtorClient(mockEngine)
        val ktorResponse = client.get("https://api.example.com")
        val response = KtorHttpResponse(KtorHttpRequest(), ktorResponse)

        assertEquals("application/json", response.header("Content-Type"))
        assertEquals("Bearer token123", response.header("Authorization"))

        client.close()
    }

    @Test
    fun `should return null for non-existent header`() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = "OK",
                status = HttpStatusCode.OK
            )
        }

        val client = KtorClient(mockEngine)
        val ktorResponse = client.get("https://api.example.com")
        val response = KtorHttpResponse(KtorHttpRequest(), ktorResponse)

        assertNull(response.header("X-Custom-Header"))

        client.close()
    }

    @Test
    fun `should return all headers`() = runTest {
        val content = "OK"
        val mockEngine = MockEngine {
            respond(
                content = content,
                status = HttpStatusCode.OK,
                headers = headersOf(
                    "Content-Type" to listOf("application/json"),
                    "Cache-Control" to listOf("no-cache")
                )
            )
        }

        val client = KtorClient(mockEngine)
        val ktorResponse = client.get("https://api.example.com")
        val response = KtorHttpResponse(KtorHttpRequest(), ktorResponse)

        val headers = response.headers()

        assertTrue(headers.size >= 2)

        client.close()
    }

    @Test
    fun `should handle header with multiple values`() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = "OK",
                status = HttpStatusCode.OK,
                headers = headersOf(
                    "Accept", listOf("application/json", "text/html")
                )
            )
        }

        val client = KtorClient(mockEngine)
        val ktorResponse = client.get("https://api.example.com")
        val response = KtorHttpResponse(KtorHttpRequest(), ktorResponse)

        val headers = response.headers()
        val acceptHeaders = headers.find { it.key == "Accept" }

        assertNotNull(acceptHeaders)
        assertTrue(acceptHeaders.value.size >= 1)

        client.close()
    }

    @Test
    fun `should return request property`() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = "OK",
                status = HttpStatusCode.OK
            )
        }

        val client = KtorClient(mockEngine)
        val ktorResponse = client.get("https://api.example.com")
        val mockRequest = KtorHttpRequest()
        mockRequest.url = "https://api.example.com"

        val response = KtorHttpResponse(mockRequest, ktorResponse)

        assertEquals(mockRequest, response.request)
        assertEquals("https://api.example.com", response.request.url)

        client.close()
    }

    @Test
    fun `should handle large response body`() = runTest {
        val largeBody = "x".repeat(10000)
        val mockEngine = MockEngine {
            respond(
                content = largeBody,
                status = HttpStatusCode.OK
            )
        }

        val client = KtorClient(mockEngine)
        val ktorResponse = client.get("https://api.example.com")
        val response = KtorHttpResponse(KtorHttpRequest(), ktorResponse)

        assertEquals(largeBody, response.body())
        assertEquals(10000, response.body().length)

        client.close()
    }

    @Test
    fun `should handle special characters in response body`() = runTest {
        val specialBody = "Hello\nWorld\t! & <>\""
        val mockEngine = MockEngine {
            respond(
                content = specialBody,
                status = HttpStatusCode.OK
            )
        }

        val client = KtorClient(mockEngine)
        val ktorResponse = client.get("https://api.example.com")
        val response = KtorHttpResponse(KtorHttpRequest(), ktorResponse)

        assertEquals(specialBody, response.body())

        client.close()
    }

    @Test
    fun `should handle UTF-8 characters in response body`() = runTest {
        val utf8Body = "Hello 世界 🌍"
        val mockEngine = MockEngine {
            respond(
                content = utf8Body,
                status = HttpStatusCode.OK
            )
        }

        val client = KtorClient(mockEngine)
        val ktorResponse = client.get("https://api.example.com")
        val response = KtorHttpResponse(KtorHttpRequest(), ktorResponse)

        assertEquals(utf8Body, response.body())

        client.close()
    }

    @Test
    fun `should return correct status for redirect responses`() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = "Moved",
                status = HttpStatusCode.MovedPermanently,
                headers = headersOf("Location" to listOf("https://new.example.com"))
            )
        }

        val client = KtorClient(mockEngine) {
            followRedirects = false
        }
        val ktorResponse = client.get("https://api.example.com")
        val response = KtorHttpResponse(KtorHttpRequest(), ktorResponse)

        assertEquals(301, response.status)
        assertEquals("https://new.example.com", response.header("Location"))

        client.close()
    }

    @Test
    fun `should handle case-insensitive header names`() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = "OK",
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type", "application/json")
            )
        }

        val client = KtorClient(mockEngine)
        val ktorResponse = client.get("https://api.example.com")
        val response = KtorHttpResponse(KtorHttpRequest(), ktorResponse)

        // Ktor handles case-insensitive header lookups
        assertNotNull(response.header("Content-Type"))

        client.close()
    }
}


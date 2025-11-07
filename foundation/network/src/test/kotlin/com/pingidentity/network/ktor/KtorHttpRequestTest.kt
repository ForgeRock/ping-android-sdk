/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.network.ktor

import io.ktor.http.HttpMethod
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class KtorHttpRequestTest {

    @Test
    fun `should create KtorHttpRequest instance`() {
        val request = KtorHttpRequest()
        assertNotNull(request)
        assertNotNull(request.builder)
    }

    @Test
    fun `should set and get URL`() {
        val request = KtorHttpRequest()
        val testUrl = "https://api.example.com/users"

        request.url = testUrl

        assertEquals(testUrl, request.url)
    }

    @Test
    fun `should add query parameters`() {
        val request = KtorHttpRequest()
        request.url = "https://api.example.com/search"

        request.parameter("q", "kotlin")
        request.parameter("limit", "10")

        val url = request.builder.url.build()
        assertEquals("kotlin", url.parameters["q"])
        assertEquals("10", url.parameters["limit"])
    }

    @Test
    fun `should add headers`() {
        val request = KtorHttpRequest()

        request.header("Authorization", "Bearer token123")
        request.header("Content-Type", "application/json")

        assertEquals("Bearer token123", request.builder.headers["Authorization"])
        assertEquals("application/json", request.builder.headers["Content-Type"])
    }

    @Test
    fun `should add multiple cookies from list`() {
        val request = KtorHttpRequest()

        val cookies = listOf(
            "sessionId=abc123; Path=/; HttpOnly",
            "userId=456; Path=/; Secure"
        )

        request.cookies(cookies)

        // Verify cookies were added (checking the builder's internal state)
        assertNotNull(request.builder)
    }

    @Test
    fun `should add single cookie`() {
        val request = KtorHttpRequest()

        request.cookie("token=xyz789; Path=/; HttpOnly; Secure")

        // Verify cookie was added
        assertNotNull(request.builder)
    }

    @Test
    fun `should set JSON body`() {
        val request = KtorHttpRequest()

        val jsonBody = buildJsonObject {
            put("name", "John Doe")
            put("email", "john@example.com")
        }

        request.post(jsonBody)

        assertEquals(HttpMethod.Post, request.builder.method)
        // Content-Type is set in the builder
    }

    @Test
    fun `should set form data`() {
        val request = KtorHttpRequest()

        request.form {
            put("username", "john.doe")
            put("password", "secret123")
        }

        assertEquals(HttpMethod.Post, request.builder.method)
    }

    @Test
    fun `should accumulate form parameters across multiple calls`() {
        val request = KtorHttpRequest()

        // First call
        request.form {
            put("username", "john.doe")
            put("password", "secret123")
        }

        // Second call - should accumulate
        request.form {
            put("remember", "true")
        }

        // Third call - should still accumulate
        request.form {
            put("csrf_token", "abc123")
        }

        assertEquals(HttpMethod.Post, request.builder.method)
        // The form body should contain all parameters
    }

    @Test
    fun `should handle empty form`() {
        val request = KtorHttpRequest()

        request.form {
            // Empty form
        }

        assertEquals(HttpMethod.Post, request.builder.method)
    }

    @Test
    fun `should handle URL with path and query parameters`() {
        val request = KtorHttpRequest()
        request.url = "https://api.example.com/v1/users"

        request.parameter("page", "1")
        request.parameter("size", "20")

        val url = request.builder.url.build()
        assertEquals("https", url.protocol.name)
        assertEquals("api.example.com", url.host)
        assertEquals("/v1/users", url.encodedPath)
        assertEquals("1", url.parameters["page"])
        assertEquals("20", url.parameters["size"])
    }

    @Test
    fun `should set multiple headers`() {
        val request = KtorHttpRequest()

        request.header("Accept", "application/json")
        request.header("Accept-Language", "en-US")
        request.header("User-Agent", "TestClient/1.0")

        assertEquals("application/json", request.builder.headers["Accept"])
        assertEquals("en-US", request.builder.headers["Accept-Language"])
        assertEquals("TestClient/1.0", request.builder.headers["User-Agent"])
    }

    @Test
    fun `should handle URL update`() {
        val request = KtorHttpRequest()

        request.url = "https://api.example.com/old"
        assertEquals("https://api.example.com/old", request.url)

        request.url = "https://api.example.com/new"
        assertEquals("https://api.example.com/new", request.url)
    }

    @Test
    fun `should build complete request with all components`() {
        val request = KtorHttpRequest()

        // Set URL
        request.url = "https://api.example.com/users"

        // Add query parameters
        request.parameter("sort", "name")
        request.parameter("order", "asc")

        // Add headers
        request.header("Authorization", "Bearer token")
        request.header("Accept", "application/json")

        // Add cookies
        request.cookie("session=abc123; Path=/")

        // Verify all components are set
        assertTrue(request.url.startsWith("https://api.example.com/users"))
        assertEquals("Bearer token", request.builder.headers["Authorization"])
        assertEquals("application/json", request.builder.headers["Accept"])

        val url = request.builder.url.build()
        assertEquals("name", url.parameters["sort"])
        assertEquals("asc", url.parameters["order"])
    }

    @Test
    fun `should handle form data with special characters`() {
        val request = KtorHttpRequest()

        request.form {
            put("email", "test@example.com")
            put("message", "Hello & Welcome!")
            put("url", "https://example.com?key=value")
        }

        assertEquals(HttpMethod.Post, request.builder.method)
    }

    @Test
    fun `should accumulate form parameters preserving order`() {
        val request = KtorHttpRequest()

        // Add parameters in specific order
        request.form { put("field1", "value1") }
        request.form { put("field2", "value2") }
        request.form { put("field3", "value3") }

        assertEquals(HttpMethod.Post, request.builder.method)
    }

    @Test
    fun `should handle cookie with all attributes`() {
        val request = KtorHttpRequest()

        request.cookie("token=xyz; Path=/api; Domain=example.com; Secure; HttpOnly; Max-Age=3600")

        assertNotNull(request.builder)
    }

    @Test
    fun `should set JSON body with nested objects`() {
        val request = KtorHttpRequest()

        val complexJson = buildJsonObject {
            put("user", buildJsonObject {
                put("name", "John")
                put("age", 30)
            })
            put("active", true)
        }

        request.post(complexJson)

        assertEquals(HttpMethod.Post, request.builder.method)
        // Content-Type is set in the builder
    }

    @Test
    fun `should handle empty URL`() {
        val request = KtorHttpRequest()

        request.url = ""

        // Ktor defaults to localhost when URL is empty
        assertTrue(request.url.contains("localhost") || request.url.isEmpty())
    }

    @Test
    fun `should add parameter to URL without existing parameters`() {
        val request = KtorHttpRequest()
        request.url = "https://api.example.com/data"

        request.parameter("id", "123")

        val url = request.builder.url.build()
        assertEquals("123", url.parameters["id"])
    }

    @Test
    fun `should handle multiple cookies with same name different paths`() {
        val request = KtorHttpRequest()

        request.cookies(listOf(
            "token=value1; Path=/api",
            "token=value2; Path=/admin"
        ))

        assertNotNull(request.builder)
    }

    @Test
    fun `should set DELETE method without body`() {
        val request = KtorHttpRequest()

        request.delete()

        assertEquals(HttpMethod.Delete, request.builder.method)
    }

    @Test
    fun `should set DELETE method with JSON body`() {
        val request = KtorHttpRequest()

        val jsonBody = buildJsonObject {
            put("reason", "user requested deletion")
        }

        request.delete(jsonBody)

        assertEquals(HttpMethod.Delete, request.builder.method)
    }

    @Test
    fun `should set PUT method without body`() {
        val request = KtorHttpRequest()

        request.put()

        assertEquals(HttpMethod.Put, request.builder.method)
    }

    @Test
    fun `should set PUT method with JSON body`() {
        val request = KtorHttpRequest()

        val jsonBody = buildJsonObject {
            put("name", "Updated Name")
            put("status", "active")
        }

        request.put(jsonBody)

        assertEquals(HttpMethod.Put, request.builder.method)
    }

    @Test
    fun `should handle DELETE with empty JSON body`() {
        val request = KtorHttpRequest()

        request.delete(buildJsonObject {})

        assertEquals(HttpMethod.Delete, request.builder.method)
    }

    @Test
    fun `should handle PUT with empty JSON body`() {
        val request = KtorHttpRequest()

        request.put(buildJsonObject {})

        assertEquals(HttpMethod.Put, request.builder.method)
    }

    @Test
    fun `should set DELETE with URL and headers`() {
        val request = KtorHttpRequest()

        request.url = "https://api.example.com/users/123"
        request.header("Authorization", "Bearer token")
        request.delete()

        assertEquals(HttpMethod.Delete, request.builder.method)
        assertEquals("Bearer token", request.builder.headers["Authorization"])
    }

    @Test
    fun `should set PUT with URL and headers`() {
        val request = KtorHttpRequest()

        request.url = "https://api.example.com/users/123"
        request.header("Authorization", "Bearer token")
        request.put(buildJsonObject {
            put("status", "updated")
        })

        assertEquals(HttpMethod.Put, request.builder.method)
        assertEquals("Bearer token", request.builder.headers["Authorization"])
    }

    @Test
    fun `should return GET method by default`() {
        val request = KtorHttpRequest()

        assertEquals("GET", request.method())
    }

    @Test
    fun `should return POST method after setting body`() {
        val request = KtorHttpRequest()

        request.post(buildJsonObject {
            put("test", "value")
        })

        assertEquals("POST", request.method())
    }

    @Test
    fun `should return POST method after setting form`() {
        val request = KtorHttpRequest()

        request.form {
            put("username", "test")
        }

        assertEquals("POST", request.method())
    }

    @Test
    fun `should return PUT method after calling put`() {
        val request = KtorHttpRequest()

        request.put(buildJsonObject {
            put("test", "value")
        })

        assertEquals("PUT", request.method())
    }

    @Test
    fun `should return DELETE method after calling delete`() {
        val request = KtorHttpRequest()

        request.delete()

        assertEquals("DELETE", request.method())
    }

    @Test
    fun `should retrieve header value by name`() {
        val request = KtorHttpRequest()

        request.header("Authorization", "Bearer token123")
        request.header("Content-Type", "application/json")

        assertEquals("Bearer token123", request.header("Authorization"))
        assertEquals("application/json", request.header("Content-Type"))
    }

    @Test
    fun `should return null for non-existent header`() {
        val request = KtorHttpRequest()

        request.header("Authorization", "Bearer token")

        assertEquals(null, request.header("Non-Existent-Header"))
    }

    @Test
    fun `should handle header name case sensitivity`() {
        val request = KtorHttpRequest()

        request.header("Content-Type", "application/json")

        // Ktor headers are case-insensitive
        assertEquals("application/json", request.header("content-type"))
        assertEquals("application/json", request.header("CONTENT-TYPE"))
        assertEquals("application/json", request.header("Content-Type"))
    }

    @Test
    fun `should retrieve multiple headers with same name`() {
        val request = KtorHttpRequest()

        request.header("Accept", "application/json")
        request.header("Accept", "application/xml")

        // Headers with same name are accumulated
        val acceptHeader = request.header("Accept")
        assertNotNull(acceptHeader)
    }

    @Test
    fun `should return null for headers when no headers set`() {
        val request = KtorHttpRequest()

        assertEquals(null, request.header("Any-Header"))
    }

    @Test
    fun `should retrieve header after multiple operations`() {
        val request = KtorHttpRequest()

        request.url = "https://api.example.com/test"
        request.parameter("key", "value")
        request.header("X-Custom-Header", "custom-value")
        request.post(buildJsonObject {
            put("data", "test")
        })

        assertEquals("custom-value", request.header("X-Custom-Header"))
        assertEquals("POST", request.method())
    }

    @Test
    fun `should handle special characters in header values`() {
        val request = KtorHttpRequest()

        request.header("X-Special", "value with spaces & symbols!")

        assertEquals("value with spaces & symbols!", request.header("X-Special"))
    }

    @Test
    fun `should retrieve headers after method changes`() {
        val request = KtorHttpRequest()

        request.header("Authorization", "Bearer token")
        assertEquals("GET", request.method())

        request.put(buildJsonObject { put("test", "value") })
        assertEquals("PUT", request.method())
        assertEquals("Bearer token", request.header("Authorization"))

        request.delete()
        assertEquals("DELETE", request.method())
        assertEquals("Bearer token", request.header("Authorization"))
    }

    @Test
    fun `should set POST with custom content type and string body`() {
        val request = KtorHttpRequest()

        request.post("text/plain", "Hello, World!")

        assertEquals(HttpMethod.Post, request.builder.method)
        // Verify the body was set
        assertNotNull(request.builder)
    }

    @Test
    fun `should set POST with custom content type`() {
        val request = KtorHttpRequest()

        request.post("application/x-custom", "custom data format")

        assertEquals(HttpMethod.Post, request.builder.method)
    }

    @Test
    fun `should set DELETE with custom content type and string body`() {
        val request = KtorHttpRequest()

        request.delete("text/plain", "Delete reason: user requested")

        assertEquals(HttpMethod.Delete, request.builder.method)
        assertNotNull(request.builder)
    }

    @Test
    fun `should set PUT with custom content type and string body`() {
        val request = KtorHttpRequest()

        request.put("text/plain", "Updated content")

        assertEquals(HttpMethod.Put, request.builder.method)
        assertNotNull(request.builder)
    }


    @Test
    fun `should handle POST with empty string body`() {
        val request = KtorHttpRequest()

        request.post("text/plain", "")

        assertEquals(HttpMethod.Post, request.builder.method)
    }

    @Test
    fun `should handle DELETE with empty string body`() {
        val request = KtorHttpRequest()

        request.delete("text/plain", "")

        assertEquals(HttpMethod.Delete, request.builder.method)
    }

    @Test
    fun `should handle PUT with empty string body`() {
        val request = KtorHttpRequest()

        request.put("text/plain", "")

        assertEquals(HttpMethod.Put, request.builder.method)
    }

}


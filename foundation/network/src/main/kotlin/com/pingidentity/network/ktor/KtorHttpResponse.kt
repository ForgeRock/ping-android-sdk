/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.network.ktor

import com.pingidentity.network.HttpRequest
import com.pingidentity.network.HttpResponse
import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse as KtorHttpResponse

/**
 * Ktor-based HTTP response implementation.
 *
 * This class implements the HttpResponse interface by wrapping Ktor's HttpResponse.
 * It provides access to the response status, headers, cookies, and body content
 * in a consistent way across the SDK.
 *
 * The response maintains a reference to the original request for correlation and
 * logging purposes. All response data is lazily accessed from the underlying Ktor
 * response object.
 *
 * @property request The original HTTP request that generated this response.
 * @property ktorResponse The underlying Ktor HTTP response object.
 *
 * @see HttpResponse
 * @see KtorHttpClient
 * @see KtorHttpRequest
 */
class KtorHttpResponse(
    override val request: HttpRequest,
    private val ktorResponse: KtorHttpResponse
) : HttpResponse {

    /**
     * Gets the HTTP status code.
     *
     * Returns the numeric HTTP status code (e.g., 200, 404, 500) from the
     * underlying Ktor response.
     *
     */
    override val status: Int
        get() = ktorResponse.status.value

    /**
     * Gets the response body as a string.
     *
     * This method reads the entire response body from the Ktor response and
     * returns it as a UTF-8 encoded string. This is a suspend function because
     * reading the body may involve I/O operations.
     *
     * @return The response body as a string.
     * @throws Exception if the body cannot be read or decoded.
     *
     */
    override suspend fun body(): String {
        return ktorResponse.body()
    }


    /**
     * Gets all cookies from the response.
     *
     * Extracts all Set-Cookie headers from the response and returns them as a list
     * of strings. Each string contains a complete cookie definition including name,
     * value, and attributes (Path, Domain, Secure, HttpOnly, etc.).
     *
     * @return List of Set-Cookie header values. Empty list if no cookies are present.
     *
     */
    override fun cookies(): List<String> {
        return ktorResponse.headers.getAll("Set-Cookie") ?: emptyList()
    }

    /**
     * Gets a specific header value by name.
     *
     * Retrieves the value of a header by its name. Header names are case-insensitive
     * per the HTTP specification. If the header has multiple values, this returns
     * the first one.
     *
     * @param name The header name (case-insensitive).
     * @return The header value, or null if the header is not present.
     *
     */
    override fun header(name: String): String? {
        return ktorResponse.headers[name]
    }

    /**
     * Gets all headers from the response.
     *
     * Returns a set of all header entries where each entry contains the header name
     * and a list of values (since HTTP headers can have multiple values with the
     * same name).
     *
     * @return A set of header entries mapping header names to lists of values.
     *
     */
    override fun headers(): Set<Map.Entry<String, List<String>>> {
        return ktorResponse.headers.entries()
    }
}


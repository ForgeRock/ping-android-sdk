/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.network

/**
 * Interface for accessing HTTP response data.
 *
 * This interface provides access to all aspects of an HTTP response including status code,
 * headers, cookies, and body content. It is designed to be implemented by different HTTP
 * client libraries while maintaining a consistent API.
 *
 * Example usage:
 * ```
 * val response: HttpResponse = httpClient.request {
 *     url = "https://api.example.com/users"
 * }
 *
 * // Check status
 * if (response.status.isSuccess()) {
 *     // Read body
 *     val body = response.body()
 *     println("Response: $body")
 *
 *     // Access headers
 *     val contentType = response.header("Content-Type")
 *
 *     // Access cookies
 *     val cookies = response.cookies()
 * } else {
 *     println("Error: ${response.status}")
 * }
 * ```
 */
interface HttpResponse {

    /**
     * The original HTTP request that generated this response.
     *
     * This provides access to the request that was sent, which can be useful for
     * logging, debugging, or correlation purposes.
     *
     */
    val request: HttpRequest

    /**
     * The HTTP status code of the response.
     *
     * Standard HTTP status codes include:
     * - 2xx: Success (200 OK, 201 Created, 204 No Content)
     * - 3xx: Redirection (301 Moved Permanently, 302 Found)
     * - 4xx: Client Error (400 Bad Request, 401 Unauthorized, 404 Not Found)
     * - 5xx: Server Error (500 Internal Server Error, 503 Service Unavailable)
     *
     * Use the `isSuccess()` extension function to check if the status is in the 2xx range.
     *
     */
    val status: Int

    /**
     * Gets the response body as a string.
     *
     * This is a suspend function that reads the entire response body and returns it
     * as a UTF-8 encoded string. For large responses, consider streaming or chunked
     * reading if available in the underlying implementation.
     *
     * @return The response body as a string.
     * @throws Exception if the body cannot be read or decoded.
     *
     */
    suspend fun body(): String

    /**
     * Gets all cookies from the response.
     *
     * Returns a list of Set-Cookie header values. Each cookie string includes
     * the cookie name, value, and attributes (Path, Domain, Secure, HttpOnly, etc.).
     *
     * @return List of cookie strings in Set-Cookie header format. Empty list if no cookies.
     *
     */
    fun cookies(): List<String>

    /**
     * Gets a specific header value by name.
     *
     * Header names are case-insensitive per the HTTP specification. If the header
     * has multiple values, this typically returns the first value or a concatenated
     * string depending on the implementation.
     *
     * @param name The header name (case-insensitive).
     * @return The header value, or null if the header is not present.
     *
     */
    fun header(name: String): String?

    /**
     * Gets all headers from the response.
     *
     * Returns a set of header entries where each entry contains the header name
     * and a list of values (since HTTP headers can have multiple values).
     *
     * @return A set of header entries with name and list of values.
     *
     */
    fun headers(): Set<Map.Entry<String, List<String>>>

}

/**
 * Extension function to check if the HTTP status code represents a successful response.
 *
 * This function returns true for all 2xx status codes (200-299), which indicate
 * successful HTTP responses according to the HTTP specification.
 *
 * @return true if the status code is in the range 200-299, false otherwise.
 *
 * @see HttpResponse.status
 */
fun Int.isSuccess(): Boolean = this in (200 until 300)

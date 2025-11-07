/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.network.ktor

import com.pingidentity.network.HttpRequest
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.cookie
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.headers
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.Parameters
import io.ktor.http.ParametersBuilder
import io.ktor.http.contentType
import io.ktor.http.parseServerSetCookieHeader
import kotlinx.serialization.json.JsonObject
import org.jetbrains.annotations.VisibleForTesting

private const val X_REQUESTED_WITH = "x-requested-with"
private const val PING_SDK = "ping-sdk"
private const val X_REQUESTED_PLATFORM = "x-requested-platform"
private const val ANDROID = "android"

/**
 * Ktor-based HTTP request implementation.
 *
 * This class implements the HttpRequest interface using Ktor's HttpRequestBuilder.
 * It provides a Kotlin-idiomatic way to build HTTP requests with support for all
 * common HTTP methods, headers, parameters, cookies, and body formats.
 *
 * ## Important Notes
 *
 * - **Form Accumulation**: Unlike typical builders, calling [form] multiple times accumulates
 *   all parameters into a single form body. This is intentional for flexible composition.
 * - **Default Method**: The HTTP method is GET by default. It's automatically changed to POST
 *   when calling [post] or [form], PUT when calling [put], and DELETE when calling [delete].
 * - **Cookie Format**: Cookies must be in Set-Cookie header format (e.g., "name=value; Path=/; HttpOnly")
 * - **Thread Safety**: This class is NOT thread-safe. Create separate instances for concurrent requests.
 *
 * @property builder The underlying Ktor HttpRequestBuilder used to construct the request.
 *                   Exposed as internal for testing and advanced use cases.
 * @property formBuilder Internal builder for accumulating form parameters across multiple [form] calls.
 *
 * @see HttpRequest
 * @see KtorHttpClient
 * @see HttpRequestBuilder
 */
class KtorHttpRequest(@VisibleForTesting val builder: HttpRequestBuilder = HttpRequestBuilder()) :
    HttpRequest {

    init {
        header(X_REQUESTED_WITH, PING_SDK)
        header(X_REQUESTED_PLATFORM, ANDROID)
    }

    /**
     * Internal builder for accumulating form parameters across multiple form() calls.
     */
    private val formBuilder = ParametersBuilder()


    /**
     * Gets or sets the URL of the request.
     *
     * The URL can be set as a complete URL string including scheme, host, path, and query parameters.
     * Any query parameters added via [parameter] will be appended to this URL.
     *
     * Note: Setting this property multiple times replaces the previous URL entirely,
     * including any query parameters added through [parameter].
     */
    override var url: String
        get() = builder.url.buildString()
        set(value) {
            builder.url(value)
        }

    /**
     * Adds a query parameter to the request URL.
     *
     * Parameters are automatically URL-encoded and appended to the URL's query string.
     * Multiple calls to this method accumulate parameters rather than replacing them.
     *
     * @param name The parameter name (will be URL-encoded).
     * @param value The parameter value (will be URL-encoded).
     */
    override fun parameter(name: String, value: String) {
        builder.url {
            parameters.append(name, value)
        }
    }

    /**
     * Adds a header to the request.
     *
     * Headers are added to the request and sent to the server. Multiple calls with the same
     * header name will append additional values rather than replacing the previous value.
     *
     * @param name The header name (case-insensitive).
     * @param value The header value (case-sensitive).
     */
    override fun header(name: String, value: String) {
        builder.headers {
            append(name, value)
        }
    }

    /**
     * Adds multiple cookies to the request.
     *
     * Each cookie string must be in Set-Cookie header format. The method parses each string
     * and extracts all cookie attributes including name, value, domain, path, expiration,
     * security flags, and custom extensions.
     *
     * @param cookies List of cookie strings in Set-Cookie header format.
     * @see cookie For adding a single cookie.
     */
    override fun cookies(cookies: List<String>) {
        cookies.forEach { cookieString ->
            val cookie = parseServerSetCookieHeader(cookieString)
            builder.cookie(
                name = cookie.name,
                value = cookie.value,
                maxAge = cookie.maxAge ?: 0,
                expires = cookie.expires,
                domain = cookie.domain,
                path = cookie.path,
                secure = cookie.secure,
                httpOnly = cookie.httpOnly,
                extensions = cookie.extensions
            )
        }
    }

    /**
     * Adds a single cookie to the request.
     *
     * The cookie string must be in Set-Cookie header format. This is a convenience method
     * for adding a single cookie instead of a list.
     *
     * @param cookie Cookie string in Set-Cookie header format.
     * @see cookies For adding multiple cookies at once.
     */
    override fun cookie(cookie: String) {
        val parsedCookie = parseServerSetCookieHeader(cookie)
        builder.cookie(
            name = parsedCookie.name,
            value = parsedCookie.value,
            maxAge = parsedCookie.maxAge ?: 0,
            expires = parsedCookie.expires,
            domain = parsedCookie.domain,
            path = parsedCookie.path,
            secure = parsedCookie.secure,
            httpOnly = parsedCookie.httpOnly,
            extensions = parsedCookie.extensions
        )
    }

    /**
     * Sets the request body as JSON and changes the HTTP method to POST.
     *
     * This method automatically:
     * - Sets the HTTP method to POST
     * - Sets Content-Type header to "application/json"
     * - Serializes the JSON object as the request body
     *
     * Note: Calling this method will change the HTTP method to POST if not already set.
     * Use [put] if you need a PUT request with JSON body.
     *
     * @param body The JSON object to set as the body.
     * @see put For PUT requests with JSON body.
     * @see form For form-encoded POST requests.
     */
    override fun post(body: JsonObject) {
        builder.method = HttpMethod.Post
        builder.contentType(ContentType.Application.Json)
        builder.setBody(body.toString())
    }

    /**
     * Sets the request body with POST method and custom content type.
     *
     * This method allows specifying a custom Content-Type header along with
     * the body content as a string. Useful for sending non-JSON payloads like
     * XML, plain text, or other custom formats.
     *
     * @param contentType The Content-Type header value (e.g., "text/plain", "application/xml").
     * @param body The request body as a string.
     * @see post For JSON POST requests.
     */
    override fun post(contentType: String, body: String) {
        builder.method = HttpMethod.Post
        builder.contentType(ContentType.parse(contentType))
        builder.setBody(body)
    }

    /**
     * Sets the request body as form data and changes the HTTP method to POST.
     *
     * **Important**: When called multiple times, parameters are accumulated instead of replaced.
     * This allows you to compose form data from multiple sources or add parameters conditionally.
     *
     * This method automatically:
     * - Sets the HTTP method to POST
     * - Sets Content-Type header to "application/x-www-form-urlencoded"
     * - URL-encodes all form parameters
     * - Accumulates parameters across multiple calls
     *
     * @param formBuilder A lambda to build the form parameters using map syntax.
     * @see post For JSON POST requests.
     */
    override fun form(formBuilder: MutableMap<String, String>.() -> Unit) {
        builder.method = HttpMethod.Post

        // Build new parameters from lambda
        val formData = mutableMapOf<String, String>()
        formBuilder(formData)

        // Append to the accumulated formBuilder
        formData.forEach { (key, value) ->
            this.formBuilder.append(key, value)
        }

        // Rebuild and set the body with all accumulated parameters
        val parameters = Parameters.build {
            this@KtorHttpRequest.formBuilder.entries().forEach { (key, values) ->
                values.forEach { value ->
                    append(key, value)
                }
            }
        }
        builder.setBody(FormDataContent(parameters))
    }

    /**
     * Sets the HTTP method to DELETE and optionally sets a JSON body.
     *
     * DELETE requests typically don't include a body, but some APIs require one
     * for bulk operations or to specify deletion criteria.
     *
     * @param body Optional JSON body for the DELETE request. Defaults to an empty JSON object.
     */
    override fun delete(body: JsonObject) {
        builder.method = HttpMethod.Delete
        builder.contentType(ContentType.Application.Json)
        builder.setBody(body.toString())
    }

    /**
     * Sets the HTTP method to DELETE with custom content type and body.
     *
     * DELETE requests typically don't have a body, but some APIs support it.
     * This method allows specifying a custom Content-Type header along with
     * the body content as a string.
     *
     * @param contentType The Content-Type header value (e.g., "text/plain", "application/xml").
     * @param body The request body as a string.
     * @see delete For JSON DELETE requests.
     */
    override fun delete(contentType: String, body: String) {
        builder.method = HttpMethod.Delete
        builder.contentType(ContentType.parse(contentType))
        builder.setBody(body)
    }

    /**
     * Sets the HTTP method to PUT and sets a JSON body.
     *
     * PUT requests are typically used to update or replace an entire resource.
     * The body contains the complete representation of the resource.
     *
     * @param body The JSON body for the PUT request.
     * @see post For POST requests with JSON body.
     * @see delete For DELETE requests with optional JSON body.
     */
    override fun put(body: JsonObject) {
        builder.method = HttpMethod.Put
        builder.contentType(ContentType.Application.Json)
        builder.setBody(body.toString())
    }

    /**
     * Sets the HTTP method to PUT with custom content type and body.
     *
     * PUT requests are typically used to update resources. This method allows
     * specifying a custom Content-Type header along with the body content as
     * a string.
     *
     * @param contentType The Content-Type header value (e.g., "text/plain", "application/xml").
     * @param body The request body as a string.
     * @see put For JSON PUT requests.
     */
    override fun put(contentType: String, body: String) {
        builder.method = HttpMethod.Put
        builder.contentType(ContentType.parse(contentType))
        builder.setBody(body)
    }

    /**
     * Returns the HTTP method of the request as a string.
     *
     * The method will be one of: "GET", "POST", "PUT", "DELETE", or other HTTP verbs.
     * Default method is "GET" unless changed by calling [post], [form], [put], or [delete].
     *
     * @return The HTTP method as a string (e.g., "GET", "POST", "PUT", "DELETE").
     */
    override fun method() = builder.method.value

    /**
     * Retrieves the value(s) of a header by name.
     *
     * @param name The header name (case-insensitive).
     * @return The header value(s) as a string, or null if the header doesn't exist.
     */
    override fun header(name: String) = builder.headers[name]
}


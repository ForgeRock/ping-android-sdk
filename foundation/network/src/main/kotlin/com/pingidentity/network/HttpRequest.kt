/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.network

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

private const val CONTENT_TYPE = "application/json"

/**
 * Interface for building HTTP requests.
 *
 * This interface provides methods to configure all aspects of an HTTP request including
 * URL, headers, query parameters, cookies, and request body. It supports various HTTP
 * methods (GET, POST, PUT, DELETE) and body formats (JSON, form data).
 *
 * The interface is designed to be implemented by different HTTP client libraries while
 * maintaining a consistent API across the SDK.
 *
 */
interface HttpRequest {

    /**
     * The URL of the HTTP request.
     *
     * This property can be both read and written. Setting this property configures
     * the target URL for the request.
     *
     */
    var url: String

    /**
     * Adds a query parameter to the request URL.
     *
     * Query parameters are appended to the URL as key-value pairs. Multiple calls
     * to this method will accumulate parameters.
     *
     * @param name The parameter name (will be URL-encoded).
     * @param value The parameter value (will be URL-encoded).
     *
     */
    fun parameter(
        name: String,
        value: String,
    )

    /**
     * Adds a header to the request.
     *
     * Headers provide metadata about the request. Multiple calls with the same
     * header name may either replace or append the value depending on the implementation.
     *
     * @param name The header name (case-insensitive per HTTP spec).
     * @param value The header value.
     *
     */
    fun header(
        name: String,
        value: String,
    )

    /**
     * Adds multiple cookies to the request.
     *
     * Cookies are provided in Set-Cookie header format. The implementation will
     * parse and properly format them for the Cookie header.
     *
     * @param cookies List of cookie strings in Set-Cookie format.
     *
     */
    fun cookies(cookies: List<String>)

    /**
     * Adds a single cookie to the request.
     *
     * The cookie should be in Set-Cookie header format. The implementation will
     * parse and properly format it for the Cookie header.
     *
     * @param cookie Cookie string in Set-Cookie format.
     *
     */
    fun cookie(cookie: String)

    /**
     * Sets the request body as JSON with POST method.
     *
     * This method automatically sets the HTTP method to POST and the Content-Type
     * to application/json. The JSON object is serialized to a string.
     *
     * @param body The JSON object to send. Defaults to an empty object.
     *
     */
    fun post(body: JsonObject = buildJsonObject {})

    /**
     * Sets the request body with POST method and custom content type.
     *
     * This method sets the HTTP method to POST and allows specifying a custom
     * Content-Type header along with the body content as a string.
     *
     * @param contentType The Content-Type header value (e.g., "text/plain", "application/xml").
     * @param body The request body as a string.
     *
     */
    fun post(contentType: String = CONTENT_TYPE, body: String)

    /**
     * Sets the request body as form data with POST method.
     *
     * This method sets the HTTP method to POST and the Content-Type to
     * application/x-www-form-urlencoded. Multiple calls to this method will
     * accumulate parameters rather than replacing them.
     *
     * @param formBuilder A lambda that builds form parameters as a mutable map.
     *
     */
    fun form(formBuilder: MutableMap<String, String>.() -> Unit)

    /**
     * Sets the HTTP method to DELETE and optionally sets a JSON body.
     *
     * DELETE requests typically don't have a body, but some APIs support it.
     * If a body is provided, the Content-Type is set to application/json.
     *
     * @param body Optional JSON body for the DELETE request. Defaults to empty.
     *
     */
    fun delete(body: JsonObject = buildJsonObject {})

    /**
     * Sets the HTTP method to DELETE with custom content type and body.
     *
     * DELETE requests typically don't have a body, but some APIs support it.
     * This method allows specifying a custom Content-Type header along with
     * the body content as a string.
     *
     * @param contentType The Content-Type header value (e.g., "text/plain", "application/xml").
     * @param body The request body as a string.
     *
     */
    fun delete(contentType: String = CONTENT_TYPE, body: String)

    /**
     * Sets the HTTP method to PUT and optionally sets a JSON body.
     *
     * PUT requests are typically used to update resources. If a body is provided,
     * the Content-Type is set to application/json.
     *
     * @param body Optional JSON body for the PUT request. Defaults to empty.
     *
     */
    fun put(body: JsonObject = buildJsonObject {})

    /**
     * Sets the HTTP method to PUT with custom content type and body.
     *
     * PUT requests are typically used to update resources. This method allows
     * specifying a custom Content-Type header along with the body content as
     * a string.
     *
     * @param contentType The Content-Type header value (e.g., "text/plain", "application/xml").
     * @param body The request body as a string.
     *
     */
    fun put(contentType: String = CONTENT_TYPE, body: String)

    /**
     * Get the HTTP method being used for the request.
     */
    fun method(): String

    /**
     * Get the HTTP headers being used for the request.
     * @param name The name of the header to retrieve.
     */
    fun header(name: String): String?

}
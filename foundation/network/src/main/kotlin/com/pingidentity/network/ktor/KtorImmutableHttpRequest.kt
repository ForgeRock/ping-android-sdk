/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.network.ktor

import com.pingidentity.network.HttpRequest
import kotlinx.serialization.json.JsonObject

/**
 * Immutable HTTP request wrapper for Ktor requests.
 *
 * This class provides a read-only view of an already-dispatched Ktor HTTP request.
 * It implements the [HttpRequest] interface but all mutation operations are no-ops
 * since the underlying request has already been sent and cannot be modified.
 *
 * ## Purpose
 *
 * This wrapper is primarily used in response interceptors where you need to inspect
 * the original request that generated a response, but should not be able to modify it.
 * It provides safe access to request metadata like URL, method, and headers without
 * allowing changes to the completed request.
 *
 * ## Immutability
 *
 * All setter and mutation methods ([parameter], [header], [cookies], [post], [form],
 * [put], [delete]) are implemented as no-ops and will silently ignore any attempted
 * modifications. Only getter methods ([url], [method], [header]) return actual values.
 *
 * ## Thread Safety
 *
 * This class is thread-safe for read operations as it wraps an immutable Ktor request
 * that has already been dispatched.
 *
 * @property request The underlying immutable Ktor HttpRequest that has been dispatched.
 *
 * @see HttpRequest
 * @see KtorHttpRequest For mutable request building.
 * @see KtorHttpResponse For the corresponding response wrapper.
 */
class KtorImmutableHttpRequest(val request: io.ktor.client.request.HttpRequest): HttpRequest {

    /**
     * Gets the URL of the dispatched request.
     *
     * Returns the complete URL including scheme, host, path, and query parameters.
     * Setting this property has no effect as the request is immutable.
     */
    override var url: String
        get() = request.url.toString()
        set(_) {
            // No-op: Request is immutable
        }

    /**
     * No-op method for adding query parameters.
     *
     * This method does nothing as the request has already been dispatched and is immutable.
     *
     * @param name The parameter name (ignored).
     * @param value The parameter value (ignored).
     */
    override fun parameter(name: String, value: String) {
        // No-op: Request is immutable
    }

    /**
     * No-op method for adding headers.
     *
     * This method does nothing as the request has already been dispatched and is immutable.
     *
     * @param name The header name (ignored).
     * @param value The header value (ignored).
     */
    override fun header(name: String, value: String) {
        // No-op: Request is immutable
    }

    /**
     * No-op method for adding cookies.
     *
     * This method does nothing as the request has already been dispatched and is immutable.
     *
     * @param cookies The cookie strings (ignored).
     */
    override fun cookies(cookies: List<String>) {
        // No-op: Request is immutable
    }

    /**
     * No-op method for adding a cookie.
     *
     * This method does nothing as the request has already been dispatched and is immutable.
     *
     * @param cookie The cookie string (ignored).
     */
    override fun cookie(cookie: String) {
        // No-op: Request is immutable
    }

    /**
     * No-op method for setting JSON body.
     *
     * This method does nothing as the request has already been dispatched and is immutable.
     *
     * @param body The JSON body (ignored).
     */
    override fun post(body: JsonObject) {
        // No-op: Request is immutable
    }

    /**
     * No-op method for setting body with custom content type.
     *
     * This method does nothing as the request has already been dispatched and is immutable.
     *
     * @param contentType The content type (ignored).
     * @param body The request body (ignored).
     */
    override fun post(contentType: String, body: String) {
        // No-op: Request is immutable
    }

    /**
     * No-op method for setting form data.
     *
     * This method does nothing as the request has already been dispatched and is immutable.
     *
     * @param formBuilder The form builder lambda (ignored).
     */
    override fun form(formBuilder: MutableMap<String, String>.() -> Unit) {
        // No-op: Request is immutable
    }

    /**
     * No-op method for DELETE requests.
     *
     * This method does nothing as the request has already been dispatched and is immutable.
     *
     * @param body The JSON body (ignored).
     */
    override fun delete(body: JsonObject) {
        // No-op: Request is immutable
    }

    /**
     * No-op method for DELETE requests with custom content type.
     *
     * This method does nothing as the request has already been dispatched and is immutable.
     *
     * @param contentType The content type (ignored).
     * @param body The request body (ignored).
     */
    override fun delete(contentType: String, body: String) {
        // No-op: Request is immutable
    }

    /**
     * No-op method for PUT requests.
     *
     * This method does nothing as the request has already been dispatched and is immutable.
     *
     * @param body The JSON body (ignored).
     */
    override fun put(body: JsonObject) {
        // No-op: Request is immutable
    }

    /**
     * No-op method for PUT requests with custom content type.
     *
     * This method does nothing as the request has already been dispatched and is immutable.
     *
     * @param contentType The content type (ignored).
     * @param body The request body (ignored).
     */
    override fun put(contentType: String, body: String) {
        // No-op: Request is immutable
    }

    /**
     * Returns the HTTP method of the dispatched request.
     *
     * @return The HTTP method as a string (e.g., "GET", "POST", "PUT", "DELETE").
     */
    override fun method() = request.method.value

    /**
     * Retrieves header value(s) by name from the dispatched request.
     *
     * Header names are case-insensitive. Returns the header value if present, or null otherwise.
     *
     * @param name The header name (case-insensitive).
     * @return The header value(s) as a string, or null if not present.
     */
    override fun header(name: String) = request.content.headers[name]
}
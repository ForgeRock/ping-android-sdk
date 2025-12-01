/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.network

/**
 * Interface for HTTP client implementations.
 *
 * This interface defines the contract for making HTTP requests and receiving responses.
 * It provides an abstraction that can be implemented using different
 * underlying HTTP client libraries (Ktor, OkHttp, Apache HttpClient, etc.) while
 * maintaining a consistent API across the SDK.
 *
 * ## Implementations
 *
 * The SDK provides a default implementation using Ktor with CIO engine:
 * You can also create custom implementations for specific requirements:
 *
 * ```
 * class CustomHttpClient : HttpClient {
 *     override fun request(): HttpRequest = CustomHttpRequest()
 *     override suspend fun request(request: HttpRequest): HttpResponse { ... }
 *     override suspend fun request(requestBuilder: HttpRequest.() -> Unit): HttpResponse { ... }
 *     override fun close() { ... }
 * }
 * ```
 *
 * @see HttpRequest
 * @see HttpResponse
 * @see com.pingidentity.network.ktor.KtorHttpClient
 */
interface HttpClient {

    /**
     * Creates a new HTTP request instance.
     *
     * This factory method creates a new request object that can be configured
     * with URL, headers, parameters, cookies, and body before being sent.
     *
     * This method is useful when you need to:
     * - Build a request incrementally
     * - Reuse request configuration
     * - Pass requests between functions
     *
     * For most use cases, prefer the `request { }` DSL method for its cleaner syntax.
     *
     * @return A new HTTP request instance.
     *
     */
    fun request(): HttpRequest

    /**
     * Sends an HTTP request and returns the response.
     *
     * This method takes a fully configured request object and executes it,
     * returning the response. The request is sent asynchronously using Kotlin
     * Coroutines, allowing the calling code to suspend until the response is received.
     *
     * @param request The HTTP request to send.
     * @return The HTTP response.
     *
     */
    suspend fun request(request: HttpRequest): HttpResponse

    /**
     * Sends an HTTP request built with a builder lambda and returns the response.
     *
     * This is the most convenient way to make HTTP requests. It creates a new request,
     * applies the builder lambda to configure it using a clean DSL syntax, and then
     * sends it. This method is recommended for most use cases.
     *
     * The lambda receiver is an HttpRequest, allowing you to configure all aspects
     * of the request including URL, headers, parameters, cookies, and body.
     *
     * @param requestBuilder A lambda that configures the request using DSL syntax.
     * @return The HTTP response.
     *
     */
    suspend fun request(requestBuilder: HttpRequest.() -> Unit): HttpResponse

    /**
     * Closes the HTTP client and releases all resources.
     *
     * This method should be called when the client is no longer needed to properly
     * clean up connections, thread pools, connection pools, and other resources.
     * After calling close(), the client should not be used anymore.
     *
     * Failure to call this method may result in:
     * - Resource leaks (threads, connections, memory)
     * - Delayed application shutdown
     * - Connection pool exhaustion
     *
     * It is strongly recommended to use the client in a try-finally block or with
     * resource management patterns to ensure proper cleanup.
     *
     * Note: Once closed, attempting to use the client will result in an exception.
     */
    fun close()
}
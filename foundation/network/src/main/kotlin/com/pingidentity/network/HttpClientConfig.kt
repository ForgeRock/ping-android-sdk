/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.network

import com.pingidentity.logger.Logger
import com.pingidentity.logger.WARN
import com.pingidentity.utils.PingDsl
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

/**
 * Configuration class for creating a default Ktor HTTP client with standard settings.
 *
 * This class provides a DSL for configuring the default HTTP client created by the
 * [HttpClient] factory function. It allows customization of timeout and logging behavior
 * while using sensible defaults.
 *
 * Default values:
 * - Timeout: 15 seconds
 * - Logger: WARN level (logs warnings and errors only)
 *
 * @see HttpClient
 */
@PingDsl
class HttpClientConfig {

    /**
     * The timeout duration for HTTP requests.
     *
     * This timeout applies to the entire request/response cycle, including connection
     * establishment, request sending, and response reading. If the request takes longer
     * than this duration, it will be cancelled with a timeout exception.
     *
     * Default value: 15 seconds
     *
     * Note: Setting this too low may cause requests to fail prematurely on slow
     * networks. Setting it too high may cause the application to hang for extended
     * periods on network issues.
     */
    var timeout: Duration = 15.toDuration(DurationUnit.SECONDS)

    /**
     * The logger instance to use for HTTP client logging.
     *
     * The logger will receive messages about HTTP requests and responses, including
     * URLs, methods, headers, status codes, and timing information. The logging level
     * determines which messages are actually logged.
     *
     * Default value: Logger.WARN (logs only warnings and errors)
     *
     * @see com.pingidentity.logger.Logger
     */
    var logger: Logger = Logger.WARN

    /**
     * Internal list of request interceptors to be installed on the HTTP client.
     *
     * Interceptors are added via [onRequest] in registration order and executed in that same order
     * just before the underlying Ktor request is dispatched.
     *
     */
    internal val requestInterceptors = mutableListOf<(HttpRequest) -> Unit>()

    /**
     * Internal list of response interceptors to be installed on the HTTP client.
     *
     * Interceptors are added via [onResponse] in registration order and executed in that same order
     * once a response has been received (after status + headers parsing, before you typically consume the body externally).
     *
     */
    internal val responseInterceptors = mutableListOf<(HttpResponse) -> Unit>()

    /**
     * Registers a request interceptor.
     *
     * Each interceptor receives the mutable [HttpRequest] instance about to be sent. Mutations are
     * applied in the order interceptors are registered.
     *
     * Example:
     * ```kotlin
     * HttpClient {
     *   onRequest { header("X-Trace-Id", traceIdProvider()) }
     *   onRequest { // second interceptor sees headers added above
     *       if (needsAuth) header("Authorization", "Bearer ${token()}")
     *   }
     * }
     * ```
     *
     * Keep interceptor logic side-effect free except for intentional request mutations.
     *
     * @param interceptor A lambda executed with the request as receiver prior to dispatch.
     */
    fun onRequest(interceptor: HttpRequest.() -> Unit) {
        requestInterceptors.add(interceptor)
    }

    /**
     * Registers a response interceptor.
     *
     * Each interceptor receives the immutable [HttpResponse] wrapper for the completed request.
     * Interceptors run in registration order. They should be lightweight and must not perform
     * blocking operations.
     *
     * Example:
     * ```kotlin
     * HttpClient {
     *   onResponse { logger.d("HTTP ${status} for ${request.url}") }
     *   onResponse { if (status >= 500) logger.w("Server error body(length)=${bodyLengthEstimate()}") }
     * }
     * ```
     *
     * Avoid calling suspend body-reading functions directly here if they are marked suspend—invoke
     * them from a higher-level coroutine if needed. Prefer inspecting headers / status.
     *
     * @param interceptor A lambda executed with the response as receiver after reception.
     */
    fun onResponse(interceptor: HttpResponse.() -> Unit) {
        responseInterceptors.add(interceptor)
    }
}
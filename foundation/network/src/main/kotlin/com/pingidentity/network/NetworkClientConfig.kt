/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.network

import com.pingidentity.logger.Logger
import com.pingidentity.logger.None
import com.pingidentity.utils.PingDsl

/**
 * Configuration class for the network client.
 */
@PingDsl
class NetworkClientConfig {
    /**
     * The HTTP client provider to use.
     * If not provided, a default provider will be used.
     */
    var httpClientProvider: HttpClientProvider? = null

    /**
     * The request interceptors to apply to all requests.
     */
    val requestInterceptors = mutableListOf<RequestInterceptor>()

    /**
     * The response interceptors to apply to all responses.
     */
    val responseInterceptors = mutableListOf<ResponseInterceptor>()

    /**
     * The timeout for HTTP requests in milliseconds.
     * Default is 15 seconds.
     */
    var timeout: Long = 15000

    /**
     * Whether to follow redirects.
     * Default is false.
     */
    var followRedirects: Boolean = false

    /**
     * The logger to use for logging.
     * Default is the SDK's default logger.
     */
    var logger: Logger = Logger.logger

    /**
     * Adds a request interceptor.
     *
     * @param interceptor The interceptor to add.
     */
    fun addRequestInterceptor(interceptor: RequestInterceptor) {
        requestInterceptors.add(interceptor)
        requestInterceptors.sortBy { it.priority }
    }

    /**
     * Adds a response interceptor.
     *
     * @param interceptor The interceptor to add.
     */
    fun addResponseInterceptor(interceptor: ResponseInterceptor) {
        responseInterceptors.add(interceptor)
        responseInterceptors.sortBy { it.priority }
    }
}

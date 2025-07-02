/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.network

import io.ktor.client.request.HttpRequestBuilder

/**
 * Interface for intercepting and modifying requests before they are sent.
 */
interface RequestInterceptor : NetworkInterceptor {
    /**
     * Intercepts a request before it is sent.
     *
     * @param request The request to intercept.
     * @return The modified request.
     */
    fun intercept(request: HttpRequestBuilder): HttpRequestBuilder
}

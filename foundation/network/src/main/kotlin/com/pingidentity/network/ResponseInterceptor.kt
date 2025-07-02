/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.network

import io.ktor.client.statement.HttpResponse

/**
 * Interface for intercepting and modifying responses after they are received.
 */
interface ResponseInterceptor : NetworkInterceptor {
    /**
     * Intercepts a response after it is received.
     *
     * @param response The response to intercept.
     * @return The modified response.
     */
    suspend fun intercept(response: HttpResponse): HttpResponse
}

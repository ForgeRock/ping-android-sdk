/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.network

/**
 * Base interface for all network interceptors.
 */
interface NetworkInterceptor {
    /**
     * Gets the priority of this interceptor.
     * Interceptors with lower priority values will be executed first.
     */
    val priority: Int
}

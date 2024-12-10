/*
 * Copyright (c) 2024 PingIdentity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.idp.davinci

import com.pingidentity.orchestrate.Request

/**
 * Interface representing an Identity Provider (IdP) handler.
 * Implementations of this interface are responsible for handling
 * authorization requests to different IdPs.
 */
interface IdpHandler {

    /**
     * Authorizes a user by making a request to the given authenticate URL.
     *
     * @param url The authenticate URL.
     * @return A [Request] object that can be used to continue the DaVinci flow.
     */
    suspend fun authorize(url: String): Request
}
/*
 * Copyright (c) 2024 - 2025 Ping Identity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.idp

/**
 * Interface for an IDP handler.
 * Implement this interface to handle the IDP authorization.
 */
interface IdpHandler {
    /**
     * The token type used by the IDP handler.
     */
    var tokenType: String

    /**
     * Authorize the IDP client.
     * @return IdpResult The authorization result.
     */
    suspend fun authorize(idpClient: IdpClient): IdpResult

}
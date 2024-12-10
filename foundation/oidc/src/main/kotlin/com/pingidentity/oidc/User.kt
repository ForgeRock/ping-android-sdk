/*
 * Copyright (c) 2024 Ping Identity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.oidc

import com.pingidentity.utils.Result
import kotlinx.serialization.json.JsonObject

/**
 * Interface for a User.
 * Provides methods for token management, user information retrieval, and logout.
 */
interface User {
    /**
     * Retrieves the token for the user.
     * @return A Result object containing either the Token or an OidcError.
     */
    suspend fun token(): Result<Token, OidcError>

    /**
     * Revokes the user's token.
     */
    suspend fun revoke()

    /**
     * Retrieves the user's information.
     * @param cache Whether to cache the user information.
     * @return A Result object containing either the user information as a JsonObject or an OidcError.
     */
    suspend fun userinfo(cache: Boolean = false): Result<JsonObject, OidcError>

    /**
     * Logs out the user.
     */
    suspend fun logout()
}
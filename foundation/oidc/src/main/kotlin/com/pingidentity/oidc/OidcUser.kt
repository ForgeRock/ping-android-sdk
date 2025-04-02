/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.oidc

import androidx.annotation.VisibleForTesting
import com.pingidentity.utils.Result
import kotlinx.serialization.json.JsonObject

/**
 * Class for an OIDC User.
 * @property oidcClient The OIDC client.
 */
class OidcUser(private val oidcClient: OidcClient) : User {

    @VisibleForTesting
    internal var userinfo: JsonObject? = null

    /**
     * Secondary constructor that takes an OidcClientConfig.
     * @param oidcClientConfig The OIDC client configuration.
     */
    constructor(oidcClientConfig: OidcClientConfig) : this(OidcClient(oidcClientConfig))

    /**
     * Gets the token for the user.
     * @return The token for the user.
     */
    override suspend fun token(): Result<Token, OidcError> {
        return oidcClient.token()
    }

    /**
     * Revokes the user's token.
     */
    override suspend fun revoke() {
        return oidcClient.revoke()
    }

    /**
     * Gets the user information.
     * @param cache Whether to cache the user information.
     * @return The user information.
     */
    override suspend fun userinfo(cache: Boolean): Result<JsonObject, OidcError> {
        userinfo?.takeIf { cache }?.let { return Result.Success(it) }
        return oidcClient.userinfo().also { if (it is Result.Success && cache) userinfo = it.value }
    }

    /**
     * Logs out the user.
     */
    override suspend fun logout() {
        oidcClient.endSession()
    }
}
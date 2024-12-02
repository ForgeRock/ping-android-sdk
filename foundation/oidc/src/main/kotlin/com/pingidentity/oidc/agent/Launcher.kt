/*
 * Copyright (c) 2024 Ping Identity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.oidc.agent

import androidx.activity.result.ActivityResultLauncher
import com.pingidentity.oidc.AuthCode
import com.pingidentity.oidc.OidcConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

/**
 * This class is responsible for launching the browser for OpenID Connect operations.
 */
internal class Launcher(
    val authorize: Pair<ActivityResultLauncher<OidcConfig<BrowserConfig>>, MutableStateFlow<Result<AuthCode>?>>,
    val endSession: Pair<ActivityResultLauncher<Pair<String, OidcConfig<BrowserConfig>>>, MutableStateFlow<Result<Boolean>?>>,
) {
    /**
     * Starts the authorization process.
     * @param request The configuration for the OpenID Connect client.
     * @param pending A boolean indicating whether the authorization process is pending.
     */
    suspend fun authorize(
        request: OidcConfig<BrowserConfig>,
        pending: Boolean = false,
    ): AuthCode {
        if (!pending) {
            authorize.first.launch(request)
        }

        // drop the default value
        val result = authorize.second.drop(1).filterNotNull().first()
        result.onFailure { throw it }
        result.onSuccess { return@authorize it }
        throw IllegalStateException("Unexpected state: no result")
    }

    /**
     * Ends the session.
     * @param request A pair containing the ID token for the session and the configuration for the OpenID Connect client.
     * @param pending A boolean indicating whether the session end process is pending.
     */
    suspend fun endSession(
        request: Pair<String, OidcConfig<BrowserConfig>>,
        pending: Boolean = false,
    ): Boolean {
        if (!pending) {
            endSession.first.launch(request)
        }
        // drop the default value
        val result = endSession.second.drop(1).filterNotNull().first()
        result.onFailure { throw it }
        result.onSuccess { return@endSession it }
        throw IllegalStateException("Unexpected state: no result")

    }
}

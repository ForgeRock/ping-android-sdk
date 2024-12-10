/*
 * Copyright (c) 2024 Ping Identity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.oidc.agent

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.pingidentity.oidc.AuthCode
import com.pingidentity.oidc.OidcConfig
import kotlinx.coroutines.flow.MutableStateFlow
import net.openid.appauth.AuthorizationResponse

/**
 * This activity is responsible for launching the browser for OpenID Connect operations.
 */
class BrowserLauncherActivity : AppCompatActivity() {
    /**
     * Called when the activity is starting.
     * @param savedInstanceState If the activity is being re-initialized after previously being shut down then this Bundle contains the data it most recently supplied in onSaveInstanceState(Bundle).
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val state: MutableStateFlow<Result<AuthCode>?> = MutableStateFlow(null)
        val delegate =
            registerForActivityResult(AuthorizeContract()) {
                it.onSuccess { authorizationResponse ->
                    val response =
                        AuthCode(
                            authorizationResponse.authorizationCode!!,
                            authorizationResponse.request.codeVerifier,
                        )
                    state.value = Result.success(response)
                }
                it.onFailure { e ->
                    state.value = Result.failure(e)
                }
                finish()
            }

        val endSessionState: MutableStateFlow<Result<Boolean>?> = MutableStateFlow(null)
        val endSession =
            registerForActivityResult(EndSessionContract()) {
                it.onSuccess { value ->
                    endSessionState.value = Result.success(true)
                }
                it.onFailure { e ->
                    endSessionState.value = Result.failure(e)

                }
                finish()
            }

        BrowserLauncher.init(
            Launcher(
                Pair(delegate, state),
                Pair(endSession, endSessionState),
            ),
        )
    }

    /**
     * Called when the activity is no longer visible to the user.
     */
    override fun onDestroy() {
        super.onDestroy()
        BrowserLauncher.reset()
    }

    companion object {
        /**
         * Starts the authorization process.
         * @param oidcConfig The configuration for the OpenID Connect client.
         * @return The authorization code.
         */
        suspend fun authorize(oidcConfig: OidcConfig<BrowserConfig>): AuthCode {
            return BrowserLauncher.authorize(oidcConfig)
        }

        /**
         * Ends the session.
         * @param oidcConfig The configuration for the OpenID Connect client.
         * @param idToken The ID token for the session.
         * @return A boolean indicating whether the session was ended successfully.
         */
        suspend fun endSession(
            oidcConfig: OidcConfig<BrowserConfig>,
            idToken: String,
        ): Boolean {
            return BrowserLauncher.endSession(oidcConfig, idToken)
        }
    }
}

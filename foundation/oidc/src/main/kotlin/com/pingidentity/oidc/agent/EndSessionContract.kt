/*
 * Copyright (c) 2024 Ping Identity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.oidc.agent

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract
import com.pingidentity.oidc.OidcConfig
import net.openid.appauth.AppAuthConfiguration
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.EndSessionRequest
import net.openid.appauth.EndSessionResponse

/**
 * This class is an ActivityResultContract for the OpenID Connect end session process.
 * It creates an intent for the end session request and parses the result of the end session response.
 */
class EndSessionContract :
    ActivityResultContract<Pair<String, OidcConfig<BrowserConfig>>, Result<EndSessionResponse>>() {

    private lateinit var service: AuthorizationService

    /**
     * Creates an intent for the end session request.
     * @param context The context to use for creating the intent.
     * @param input A pair containing the ID token for the session and the configuration for the OpenID Connect client.
     * @return The intent for the end session request.
     */
    override fun createIntent(
        context: Context,
        input: Pair<String, OidcConfig<BrowserConfig>>,
    ): Intent {
        val configuration =
            AuthorizationServiceConfiguration(
                Uri.parse(input.second.oidcClientConfig.openId.authorizationEndpoint),
                Uri.parse(input.second.oidcClientConfig.openId.tokenEndpoint),
                null,
                Uri.parse(input.second.oidcClientConfig.openId.endSessionEndpoint),
            )

        val endSessionRequest =
            EndSessionRequest.Builder(configuration)
                .setIdTokenHint(input.first)
                .setPostLogoutRedirectUri(Uri.parse(input.second.oidcClientConfig.signOutRedirectUri))
                .build()

        val configBuilder = AppAuthConfiguration.Builder()
        input.second.config.appAuthConfiguration(configBuilder)

        service = AuthorizationService(context, configBuilder.build())
        return service.getEndSessionRequestIntent(endSessionRequest)
    }

    /**
     * Parses the result of the end session response.
     * @param resultCode The result code from the activity result.
     * @param intent The intent containing the end session response.
     * @return A boolean indicating whether the session was ended successfully.
     */
    override fun parseResult(
        resultCode: Int,
        intent: Intent?,
    ): Result<EndSessionResponse> {
        service.dispose()
        intent?.let { i ->
            val resp = EndSessionResponse.fromIntent(i)
            resp?.let { return Result.success(it) }
            val ex = AuthorizationException.fromIntent(i)
            ex?.let { return Result.failure(it) }
        }
        return Result.failure(IllegalStateException("End session response is null"))
    }
}

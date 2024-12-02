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
import androidx.browser.customtabs.CustomTabsIntent
import com.pingidentity.oidc.OidcConfig
import com.pingidentity.oidc.exception.AuthorizeException
import net.openid.appauth.AppAuthConfiguration
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration

private const val CODE = "code"

/**
 * This class is an [ActivityResultContract] for the OpenID Connect authorization process.
 * It creates an intent for the authorization request and parses the result of the authorization response.
 */
class AuthorizeContract :
    ActivityResultContract<OidcConfig<BrowserConfig>, Result<AuthorizationResponse>>() {

    private lateinit var service: AuthorizationService

    /**
     * Creates an intent for the authorization request.
     *
     * @param context The context to use for creating the intent.
     * @param input The configuration for the OpenID Connect client.
     * @return The intent for the authorization request.
     */
    override fun createIntent(
        context: Context,
        input: OidcConfig<BrowserConfig>,
    ): Intent {
        val configuration =
            AuthorizationServiceConfiguration(
                Uri.parse(input.oidcClientConfig.openId.authorizationEndpoint),
                Uri.parse(input.oidcClientConfig.openId.tokenEndpoint),
            )
        val builder =
            AuthorizationRequest.Builder(
                configuration,
                input.oidcClientConfig.clientId,
                CODE,
                Uri.parse(input.oidcClientConfig.redirectUri),
            )
        input.oidcClientConfig.scopes.let { builder.setScopes(it) }
        input.oidcClientConfig.state?.let { builder.setState(it) }
        input.oidcClientConfig.nonce?.let { builder.setNonce(it) }
        input.oidcClientConfig.display?.let { builder.setDisplay(it) }
        input.oidcClientConfig.prompt?.let { builder.setPrompt(it) }
        input.oidcClientConfig.uiLocales?.let { builder.setUiLocales(it) }
        input.oidcClientConfig.loginHint?.let { builder.setLoginHint(it) }
        input.oidcClientConfig.additionalParameters.let { builder.setAdditionalParameters(it) }

        val request = builder.build()
        val configBuilder = AppAuthConfiguration.Builder()
        input.config.appAuthConfiguration(configBuilder)
        service = AuthorizationService(context, configBuilder.build())

        val intentBuilder: CustomTabsIntent.Builder =
            service.createCustomTabsIntentBuilder(request.toUri())
        input.config.customTab(intentBuilder)
        return service.getAuthorizationRequestIntent(request, intentBuilder.build())
    }

    /**
     * Parses the result of the authorization response.
     *
     * @param resultCode The result code of the authorization response.
     * @param intent The intent of the authorization response.
     * @return A Result containing the authorization response or an error.
     */
    override fun parseResult(
        resultCode: Int,
        intent: Intent?,
    ): Result<AuthorizationResponse> {
        service.dispose()

        intent?.let { i ->
            val error = AuthorizationException.fromIntent(i)
            error?.let {
                return Result.failure(
                    AuthorizeException(
                        "Failed to retrieve authorization code. ${it.message}",
                        it,
                    ),
                )
            }
            val result = AuthorizationResponse.fromIntent(i)
            result?.let {
                return Result.success(it)
            } ?: return Result.failure(AuthorizeException("Authorization Code is not returned."))
        }
        return Result.failure(AuthorizeException("Authorization with no intent returned."))
    }
}

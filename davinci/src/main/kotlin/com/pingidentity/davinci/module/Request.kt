/*
 * Copyright (c) 2024 Ping Identity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci.module

import com.pingidentity.oidc.OidcClientConfig
import com.pingidentity.oidc.Pkce
import com.pingidentity.orchestrate.Request

internal val populateRequest: OidcClientConfig.(Request, Pkce) -> Request = { request, pkce ->

    request.url(openId.authorizationEndpoint)
    request.parameter("response_mode", "pi.flow")
    request.parameter("client_id", clientId)
    request.parameter("response_type", "code")
    request.parameter("scope", scopes.joinToString(" "))
    request.parameter("redirect_uri", redirectUri)
    request.parameter("code_challenge", pkce.codeChallenge)
    request.parameter("code_challenge_method", pkce.codeChallengeMethod)
    acrValues?.let {
        request.parameter("acr_values", it)
    }
    display?.let {
        request.parameter("display", it)
    }
    additionalParameters.forEach { (key, value) ->
        request.parameter(key, value)
    }
    loginHint?.let {
        request.parameter("login_hint", it)
    }
    nonce?.let {
        request.parameter("nonce", it)
    }
    prompt?.let {
        request.parameter("prompt", it)
    }
    uiLocales?.let {
        request.parameter("ui_locales", it)
    }
    request
}

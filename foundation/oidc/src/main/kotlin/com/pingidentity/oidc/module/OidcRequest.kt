/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.oidc.module

import com.pingidentity.oidc.Constants.ACR_VALUES
import com.pingidentity.oidc.Constants.CLIENT_ID
import com.pingidentity.oidc.Constants.CODE
import com.pingidentity.oidc.Constants.CODE_CHALLENGE
import com.pingidentity.oidc.Constants.CODE_CHALLENGE_METHOD
import com.pingidentity.oidc.Constants.DISPLAY
import com.pingidentity.oidc.Constants.LOGIN_HINT
import com.pingidentity.oidc.Constants.NONCE
import com.pingidentity.oidc.Constants.PROMPT
import com.pingidentity.oidc.Constants.REDIRECT_URI
import com.pingidentity.oidc.Constants.RESPONSE_TYPE
import com.pingidentity.oidc.Constants.SCOPE
import com.pingidentity.oidc.Constants.STATE
import com.pingidentity.oidc.Constants.UI_LOCATES
import com.pingidentity.oidc.OidcClientConfig
import com.pingidentity.oidc.Pkce
import com.pingidentity.network.HttpRequest as Request

/**
 * Function to populate an OIDC request with the necessary parameters.
 */
internal val populateRequest: OidcClientConfig.(Request, Map<String, String>, Pkce) -> Request = { request, parameters, pkce ->

    request.url = openId.authorizationEndpoint
    request.parameter(CLIENT_ID, clientId)
    request.parameter(RESPONSE_TYPE, CODE)
    request.parameter(SCOPE, scopes.joinToString(" "))
    request.parameter(REDIRECT_URI, redirectUri)
    request.parameter(CODE_CHALLENGE, pkce.codeChallenge)
    request.parameter(CODE_CHALLENGE_METHOD, pkce.codeChallengeMethod)
    acrValues?.let {
        request.parameter(ACR_VALUES, it)
    }
    display?.let {
        request.parameter(DISPLAY, it)
    }
    additionalParameters.forEach { (key, value) ->
        request.parameter(key, value)
    }
    loginHint?.let {
        request.parameter(LOGIN_HINT, it)
    }
    state?.let {
        request.parameter(STATE, it)
    }
    nonce?.let {
        request.parameter(NONCE, it)
    }
    prompt?.let {
        request.parameter(PROMPT, it)
    }
    uiLocales?.let {
        request.parameter(UI_LOCATES, it)
    }
    parameters.forEach { (key, value) ->
        request.parameter(key, value)
    }
    request
}

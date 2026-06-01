/*
 * Copyright (c) 2024 - 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.oidc.module

import androidx.core.net.toUri
import com.pingidentity.network.isSuccess
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
import com.pingidentity.oidc.Constants.REQUEST_URI
import com.pingidentity.oidc.Constants.RESPONSE_MODE
import com.pingidentity.oidc.Constants.RESPONSE_TYPE
import com.pingidentity.oidc.Constants.SCOPE
import com.pingidentity.oidc.Constants.STATE
import com.pingidentity.oidc.Constants.UI_LOCATES
import com.pingidentity.oidc.OidcClientConfig
import com.pingidentity.oidc.Pkce
import com.pingidentity.oidc.exception.AuthorizeException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import com.pingidentity.network.HttpRequest as Request

/**
 * Builds OIDC authorization request parameters using the provided configuration.
 *
 * This function populates all required and optional OAuth2/OIDC parameters for an authorization request:
 * - Required parameters: client_id, response_type, scope, redirect_uri, PKCE parameters
 * - Optional parameters: state, nonce, login_hint, prompt, display, UI locales, ACR values
 * - Additional custom parameters from configuration
 * - Extra parameters passed to this specific request
 *
 * @param pkce PKCE (Proof Key for Code Exchange) parameters for enhanced security
 * @param extraParameters Additional parameters specific to this authorization request
 * @param onParam Callback function to handle each parameter (name, value) pair
 */
internal fun OidcClientConfig.buildAuthorizeParams(
    pkce: Pkce,
    extraParameters: Map<String, String> = emptyMap(),
    onParam: (String, String) -> Unit,
) {
    onParam(CLIENT_ID, clientId)
    onParam(RESPONSE_TYPE, CODE)
    onParam(SCOPE, scopes.joinToString(" "))
    onParam(REDIRECT_URI, redirectUri)
    onParam(CODE_CHALLENGE, pkce.codeChallenge)
    onParam(CODE_CHALLENGE_METHOD, pkce.codeChallengeMethod)
    acrValues?.let {
        onParam(ACR_VALUES, it)
    }
    display?.let {
        onParam(DISPLAY, it)
    }
    additionalParameters.forEach { (key, value) ->
        onParam(key, value)
    }
    loginHint?.let {
        onParam(LOGIN_HINT, it)
    }
    state?.let {
        onParam(STATE, it)
    }
    nonce?.let {
        onParam(NONCE, it)
    }
    prompt?.let {
        onParam(PROMPT, it)
    }
    uiLocales?.let {
        onParam(UI_LOCATES, it)
    }
    extraParameters.forEach { (key, value) ->
        onParam(key, value)
    }
}


/**
 * Internal function to populate an OIDC authorization request with the necessary parameters.
 *
 * This function handles both standard OAuth2 authorization requests and PAR (Pushed Authorization Request):
 *
 * **Standard Flow:**
 * - Builds authorization URL with all parameters in the query string
 * - Suitable for most OAuth2/OIDC implementations
 *
 * **PAR Flow (RFC 9126):**
 * - First pushes authorization parameters to the PAR endpoint via POST
 * - Receives a request_uri that references the pushed parameters
 * - Uses the request_uri in the actual authorization request
 * - Provides better security and reduces URL length
 *
 * @return The populated request ready for execution
 * @throws AuthorizeException If PAR request fails when PAR is enabled
 */
val populateRequest: suspend OidcClientConfig.(Request, Map<String, String>, Pkce) -> Request =
    { request, parameters, pkce ->
        if (par) {
            val response = httpClient.request {
                url = openId.pushAuthorizationRequestEndpoint
                form {
                    request.url.toUri().getQueryParameter(RESPONSE_MODE)
                        ?.let { put(RESPONSE_MODE, it) }
                    buildAuthorizeParams(pkce, parameters) { k, v -> put(k, v) }
                }
            }
            if (response.status.isSuccess()) {
                val res: String = response.body()
                val json = Json.parseToJsonElement(res).jsonObject
                val requestUri = json[REQUEST_URI]?.jsonPrimitive?.content
                    ?: throw AuthorizeException("PAR response missing required 'request_uri' field")
                request.url = openId.authorizationEndpoint
                request.parameter(REQUEST_URI, requestUri)
                request.parameter(CLIENT_ID, clientId)
            } else {
                val errorBody: String = response.body()
                throw AuthorizeException("Failed to create par request: ${response.status} - $errorBody")
            }
        } else {
            request.url = openId.authorizationEndpoint
            buildAuthorizeParams(pkce, parameters) { k, v -> request.parameter(k, v) }
        }
        request
    }

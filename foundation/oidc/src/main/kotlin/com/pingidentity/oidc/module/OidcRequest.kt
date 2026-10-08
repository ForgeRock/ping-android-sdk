/*
 * Copyright (c) 2024 - 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.oidc.module

import androidx.core.net.toUri
import com.pingidentity.network.isSuccess
import com.pingidentity.oidc.AuthorizationDetail
import com.pingidentity.oidc.Constants.ACR_VALUES
import com.pingidentity.oidc.Constants.AUTHORIZATION_DETAILS
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
import com.pingidentity.oidc.Constants.USER_CODE_CAMEL
import com.pingidentity.oidc.OidcClientConfig
import com.pingidentity.oidc.Pkce
import com.pingidentity.oidc.exception.AuthorizeException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import com.pingidentity.network.HttpRequest as Request

/**
 * Builds the OIDC authorization request parameters using the provided configuration.
 *
 * This function populates all required and optional OAuth2/OIDC parameters for an authorization request:
 * - Required parameters: client_id, response_type, scope, redirect_uri, PKCE parameters
 * - Optional parameters: state, nonce, login_hint, prompt, display, UI locales, ACR values
 * - Rich Authorization Request: authorization_details (RFC 9396 §2)
 * - Additional custom parameters from configuration
 * - Extra parameters passed to this specific request
 *
 * Every parameter is emitted **exactly once**. All sources are collected into a single map where
 * a later source overwrites an earlier one, giving the precedence (RFC 6749: the request is the
 * last word): per-call `extraParameters` > [OidcClientConfig.additionalParameters] > typed
 * config members (including the `authorization_details` serialization of
 * [OidcClientConfig.authorizationDetails]). Duplicate keys across sources therefore resolve
 * structurally — no key-name special cases — so the same rule covers `authorization_details`,
 * `state`, and any other parameter a caller sets twice.
 *
 * @param pkce PKCE (Proof Key for Code Exchange) parameters for enhanced security
 * @param extraParameters Additional parameters specific to this authorization request
 * @return The parameters in emission order (required params first, then config/extra values),
 * keyed by parameter name.
 */
internal fun OidcClientConfig.buildAuthorizeParams(
    pkce: Pkce,
    extraParameters: Map<String, String> = emptyMap(),
): LinkedHashMap<String, String> {
    val params = LinkedHashMap<String, String>()
    params[CLIENT_ID] = clientId
    params[RESPONSE_TYPE] = CODE
    params[SCOPE] = scopes.joinToString(" ")
    params[REDIRECT_URI] = redirectUri
    params[CODE_CHALLENGE] = pkce.codeChallenge
    params[CODE_CHALLENGE_METHOD] = pkce.codeChallengeMethod
    acrValues?.let { params[ACR_VALUES] = it }
    if (authorizationDetails.isNotEmpty()) {
        params[AUTHORIZATION_DETAILS] = authorizationDetails.toAuthorizationDetailsParam()
    }
    display?.let { params[DISPLAY] = it }
    additionalParameters.forEach { (key, value) -> params[key] = value }
    loginHint?.let { params[LOGIN_HINT] = it }
    state?.let { params[STATE] = it }
    nonce?.let { params[NONCE] = it }
    prompt?.let { params[PROMPT] = it }
    uiLocales?.let { params[UI_LOCATES] = it }
    extraParameters.forEach { (key, value) -> params[key] = value }
    return params
}

/**
 * `Json` instance used to serialize [AuthorizationDetail] lists onto the wire. Unlike the module
 * `Json` (`com.pingidentity.oidc.json`, which has `encodeDefaults = true`) it leaves
 * `encodeDefaults` off, so absent optional members of an authorization detail object are omitted
 * from the serialized `authorization_details` value instead of emitted as `"locations":null`
 * (RFC 9396 §2.2 makes every member but `type` optional, and omitting absent members is the
 * canonical wire form).
 */
internal val authorizeJson: Json = Json { encodeDefaults = false }

/**
 * Serializes authorization details into the value of the `authorization_details` request
 * parameter (RFC 9396 §2): a JSON array of detail objects.
 *
 * Object members are emitted in deterministic, sorted key order so that the serialized value
 * is stable across invocations. Absent optional members are omitted from the output.
 *
 * @param json The `Json` instance to serialize with.
 * @return The serialized JSON array as a string.
 */
internal fun List<AuthorizationDetail>.toAuthorizationDetailsParam(json: Json = authorizeJson): String =
    json.encodeToJsonElement(this).sortedKeys().toString()

/**
 * Recursively sorts the members of every JSON object by key so that the encoded form matches the
 * deterministic `.sortedKeys` output of the iOS SDK's `AuthorizationDetail.wireValue`.
 */
private fun JsonElement.sortedKeys(): JsonElement =
    when (this) {
        is JsonObject -> JsonObject(
            entries.sortedBy { it.key }.associate { it.key to it.value.sortedKeys() },
        )
        is JsonArray -> JsonArray(map { it.sortedKeys() })
        else -> this
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
                    buildAuthorizeParams(pkce, parameters).forEach { (k, v) -> put(k, v) }
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
            buildAuthorizeParams(pkce, parameters).forEach { (k, v) -> request.parameter(k, v) }
        }
        request
    }

private const val AS_DEVICE_AUTHORIZATION_PATH = "/as/device_authorization"

/**
 * Populates a request to verify a user code in the Device Authorization Grant flow (RFC 8628).
 *
 * **This function applies to DaVinci Environment only.** PingOne DaVinci uses
 * specific URL to handle the device grant flow.
 *
 * Constructs the device flow verification URL from [com.pingidentity.oidc.OpenIdConfiguration.deviceAuthorizationEndpoint]
 * by stripping the `/as/device_authorization` suffix to obtain the base URL, then appending
 * `/applications/{clientId}/deviceFlow` with the `userCode` query parameter.
 *
 * Examples:
 * - `https://auth.pingone.ca/{tenantId}/as/device_authorization`
 *   → `https://auth.pingone.ca/{tenantId}/applications/{clientId}/deviceFlow?userCode={userCode}`
 * - `https://pingone.petrov.ca/as/device_authorization`
 *   → `https://pingone.petrov.ca/applications/{clientId}/deviceFlow?userCode={userCode}`
 *
 * The first argument is the base request, the second is the user code from the device authorization
 * response that needs to be verified.
 * Returns the populated [Request] ready for execution.
 */
val populateDeviceFlowVerificationRequest: suspend OidcClientConfig.(Request, String) -> Request =
    { request, userCode ->
        val deviceAuthEndpoint = openId.deviceAuthorizationEndpoint

        // Strip "/as/device_authorization" to obtain the tenant-scoped base URL.
        val baseUrl = deviceAuthEndpoint.removeSuffix(AS_DEVICE_AUTHORIZATION_PATH)

        request.url = "$baseUrl/applications/$clientId/deviceFlow"

        // PingOne format paths look like "/{tenantId}/as/device_authorization", whereas
        // custom-domain paths look like "/as/device_authorization".
        request.parameter(USER_CODE_CAMEL, userCode)

        request
    }


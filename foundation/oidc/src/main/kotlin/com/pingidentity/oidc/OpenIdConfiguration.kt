/*
 * Copyright (c) 2024 - 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.oidc

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Data class representing the OpenID Connect configuration.
 *
 * @property authorizationEndpoint The URL of the authorization endpoint.
 * @property tokenEndpoint The URL of the token endpoint.
 * @property userinfoEndpoint The URL of the userinfo endpoint.
 * @property endSessionEndpoint The URL of the end session endpoint.
 * @property pingEndIdpSessionEndpoint The URL of the end session endpoint with just using idToken
 * @property revocationEndpoint The URL of the revocation endpoint.
 * @property deviceAuthorizationEndpoint The URL of the device authorization endpoint (RFC 8628).
 */
@Serializable
data class OpenIdConfiguration(
    @SerialName("authorization_endpoint")
    var authorizationEndpoint: String = "",
    @SerialName("pushed_authorization_request_endpoint")
    var pushAuthorizationRequestEndpoint: String = "",
    @SerialName("token_endpoint")
    var tokenEndpoint: String = "",
    @SerialName("userinfo_endpoint")
    var userinfoEndpoint: String = "",
    @SerialName("end_session_endpoint")
    var endSessionEndpoint: String = "",
    @SerialName("ping_end_idp_session_endpoint")
    var pingEndIdpSessionEndpoint: String = "",
    @SerialName("revocation_endpoint")
    var revocationEndpoint: String = "",
    @SerialName("device_authorization_endpoint")
    var deviceAuthorizationEndpoint: String = "",
)
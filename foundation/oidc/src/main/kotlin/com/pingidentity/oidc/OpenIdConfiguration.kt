/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
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
 */
@Serializable
data class OpenIdConfiguration(
    @SerialName("authorization_endpoint")
    val authorizationEndpoint: String = "",
    @SerialName("token_endpoint")
    val tokenEndpoint: String = "",
    @SerialName("userinfo_endpoint")
    val userinfoEndpoint: String = "",
    @SerialName("end_session_endpoint")
    val endSessionEndpoint: String = "",
    @SerialName("ping_end_idp_session_endpoint")
    val pingEndIdpSessionEndpoint: String = "",
    @SerialName("revocation_endpoint")
    val revocationEndpoint: String = "",
)
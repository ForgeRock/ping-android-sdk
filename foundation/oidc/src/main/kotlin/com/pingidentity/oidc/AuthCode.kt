/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.oidc

import kotlinx.serialization.Serializable

/**
 * Data class representing an authorization code.
 *
 * @property code The authorization code as a string. Default is an empty string.
 * @property codeVerifier An optional code verifier associated with the authorization code. Default is null.
 */
@Serializable
data class AuthCode(
    val code: String = "",
    val codeVerifier: String? = null,
)
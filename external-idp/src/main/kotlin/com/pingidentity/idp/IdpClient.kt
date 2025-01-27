/*
 * Copyright (c) 2025 Ping Identity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.idp

/**
 * Data class representing an IDP client.
 *
 * @param clientId The client ID.
 * @param redirectUri The redirect URI.
 * @param scopes The scopes.
 * @param nonce The nonce.
 * @param continueUrl The continue URL.
 */
data class IdpClient(
    val clientId: String? = null,
    val redirectUri: String? = null,
    val scopes: List<String> = emptyList(),
    val nonce: String? = null,
    val continueUrl: String? = null
)

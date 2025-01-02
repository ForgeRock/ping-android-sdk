/*
 * Copyright (c) 2024 PingIdentity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.idp

data class IdpClient(
    val clientId: String? = null,
    val redirectUri: String? = null,
    val scopes: List<String> = emptyList(),
    val nonce: String? = null,
    val continueUrl: String? = null
)

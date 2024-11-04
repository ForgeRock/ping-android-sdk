/*
 * Copyright (c) 2024 PingIdentity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.idp.journey

data class IdpClient(
    val clientId: String,
    val redirectUri: String,
    val scopes: List<String>,
    val nonce: String
)

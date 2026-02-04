/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.pingonemfa.commons

/*
 * Represents a PingOne MFA account.
 */
data class PingOneMfaAccount(
    val region: String,
    val id: String,
    val deviceId: String,
    val environment: String,
    val name: String,
    val family: String
)
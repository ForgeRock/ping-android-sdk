/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.pingonemfapp.data

import com.pingidentity.pingonemfa.commons.PingOneMfaAccount

/*
 * Simple data class to represent an MFA account of PingOneMFA SDK.
 */
data class AccountItem(
    val id: String,
    val deviceId: String,
    val environment: String,
    val region: String,
    val name: String,
    val lastName: String
)
/*
 * Data class for OTP UI display with additional UI-specific fields.
 */
data class OtpUiState(
    val otp: String = "",
    val secondsRemaining: Int = 0,
    val isLoading: Boolean = false,
    val error: String? = null
)

fun List<PingOneMfaAccount>.toUiItems(): List<AccountItem> {
    return map {
        createAccountItem(it)
    }
}

fun createAccountItem(account: PingOneMfaAccount): AccountItem {
    return AccountItem(
        region = account.region,
        name = account.name,
        lastName = account.family,
        id = account.id,
        deviceId = account.deviceId,
        environment = account.environment
    )
}


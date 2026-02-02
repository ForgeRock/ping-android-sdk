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
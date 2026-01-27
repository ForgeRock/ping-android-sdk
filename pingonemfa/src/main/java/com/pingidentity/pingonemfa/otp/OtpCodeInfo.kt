package com.pingidentity.pingonemfa.otp

/*
 * Very simple model for OTP code information.
 */
data class OtpCodeInfo(
    val code: String,
    val secondsRemaining: Int
)
/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.pingonemfa.otp

/*
 * Very simple model for OTP code information.
 */
data class OtpCodeInfo(
    val code: String,
    val secondsRemaining: Int
)
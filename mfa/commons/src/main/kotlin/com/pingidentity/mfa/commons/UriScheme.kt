/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.commons

/**
 * Supported URI Schemes for MFA
 */
enum class UriScheme(val value: String) {
    /* Standard URI scheme for OTP (One-Time Password) authentication */
    OTPAUTH("otpauth://"),

    /* URI scheme for Push Authentication */
    PUSHAUTH("pushauth://"),

    /* URI scheme for Combined Multi-Factor Authentication (Push & OTP) */
    MFAUTH("mfauth://")
}
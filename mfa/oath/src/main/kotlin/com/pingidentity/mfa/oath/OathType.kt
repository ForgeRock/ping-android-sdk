/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.oath

/**
 * Enum representing the different types of OATH credentials.
 */
enum class OathType {
    /**
     * Time-based One-Time Password algorithm.
     */
    TOTP,
    
    /**
     * HMAC-based One-Time Password algorithm.
     */
    HOTP;

    /**
     * Convert from a string representation.
     */
    companion object {
        fun fromString(type: String): OathType {
            return when (type.lowercase()) {
                "totp" -> TOTP
                "hotp" -> HOTP
                else -> throw IllegalArgumentException("Unknown OATH type: $type")
            }
        }
    }
}

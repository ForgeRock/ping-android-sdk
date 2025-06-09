/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.oath

/**
 * Enum representing the different OATH algorithm types.
 */
enum class OathAlgorithm {
    /**
     * SHA-1 algorithm.
     */
    SHA1,
    
    /**
     * SHA-256 algorithm.
     */
    SHA256,
    
    /**
     * SHA-512 algorithm.
     */
    SHA512;

    /**
     * Convert from a string representation.
     */
    companion object {
        @JvmStatic
        fun fromString(algorithm: String): OathAlgorithm {
            return when (algorithm.uppercase()) {
                "SHA1" -> SHA1
                "SHA256" -> SHA256
                "SHA512" -> SHA512
                else -> throw IllegalArgumentException("Unknown OATH algorithm: $algorithm")
            }
        }
    }
}

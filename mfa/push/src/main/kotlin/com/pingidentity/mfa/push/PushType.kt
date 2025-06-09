/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.push

/**
 * The supported Push notification types.
 */
enum class PushType {
    /**
     * Default. Push to accept notification.
     */
    DEFAULT,

    /**
     * Push to Challenge notification.
     */
    CHALLENGE,

    /**
     * Push to Biometric notification.
     */
    BIOMETRIC;

    override fun toString(): String {
        return when (this) {
            DEFAULT -> "default"
            CHALLENGE -> "challenge"
            BIOMETRIC -> "biometric"
        }
    }

    companion object {
        /**
         * Return the push type for a given string.
         * @param type A String representing the PushType.
         * @return The push type. Returns DEFAULT if the type parameter is null or invalid.
         */
        @JvmStatic
        fun fromString(type: String?): PushType {
            if (type == null) {
                return DEFAULT
            }

            return try {
                valueOf(type.uppercase())
            } catch (e: IllegalArgumentException) {
                DEFAULT
            }
        }
    }
}

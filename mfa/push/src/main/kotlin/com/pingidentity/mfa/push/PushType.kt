/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.push


/**
 * Enum representing the supported Push notification types.
 * Different types of push notifications require different handling for authentication.
 */
enum class PushType {
    /**
     * Default push notification type.
     * 
     * The user simply approves or denies the authentication request without additional verification.
     * This is the standard push notification flow.
     *
     * Use `approveNotification` to approve this type of notification.
     */
    DEFAULT,

    /**
     * Challenge-based push notification.
     * 
     * This type requires the user to verify a challenge (usually a numeric code shown on both
     * the login screen and the mobile device) before approving the authentication request.
     * This provides an additional layer of security by ensuring the user is responding to
     * the correct authentication request.
     * 
     * Use `approveChallengeNotification` with the user-provided challenge response to approve
     * this type of notification.
     */
    CHALLENGE,

    /**
     * Biometric push notification.
     * 
     * This type requires the user to verify their identity using biometric authentication
     * (such as fingerprint or face recognition) before approving the authentication request.
     * 
     * Use `approveBiometricNotification` with the authentication method to approve this type
     * of notification.
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
        @JvmStatic
        fun fromString(type: String): PushType {
            return when (type.lowercase()) {
                "default" -> DEFAULT
                "challenge" -> CHALLENGE
                "biometric" -> BIOMETRIC
                else -> throw IllegalArgumentException("Unknown Push type: $type")
            }
        }
    }
}

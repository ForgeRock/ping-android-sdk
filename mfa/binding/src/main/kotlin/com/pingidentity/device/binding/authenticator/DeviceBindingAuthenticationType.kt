/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.binding.authenticator

import kotlinx.serialization.Serializable

/**
 * Defines the types of authentication methods available for device binding.
 * These authentication types determine how users prove their identity when accessing
 * cryptographic keys stored on the device.
 */
@Serializable
enum class DeviceBindingAuthenticationType {

    /**
     * Requires biometric authentication only (fingerprint, face recognition, etc.).
     * No fallback authentication method is allowed. If biometric authentication fails
     * or is not available, the operation will fail.
     */
    BIOMETRIC_ONLY,

    /**
     * Prefers biometric authentication but allows fallback to device credentials
     * (PIN, pattern, password) if biometric authentication fails or is not available.
     * This provides a balance between security and usability.
     */
    BIOMETRIC_ALLOW_FALLBACK,

    /**
     * No authentication is required to access the cryptographic keys.
     * Keys are accessible without user verification. This option provides
     * the least security but maximum convenience.
     */
    NONE,

    /**
     * Requires application-specific PIN authentication.
     * Users must enter a PIN that is managed by the application rather than
     * relying on system-level authentication methods.
     */
    APPLICATION_PIN
}
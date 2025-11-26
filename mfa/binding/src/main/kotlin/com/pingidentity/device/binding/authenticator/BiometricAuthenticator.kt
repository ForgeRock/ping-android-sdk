/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.binding.authenticator

import com.pingidentity.device.binding.CryptoKey
import com.pingidentity.device.binding.CryptoKeyAware

/**
 * Abstract base class for biometric-based device authenticators.
 *
 * This class provides the foundational implementation for authenticators that use biometric
 * authentication methods such as fingerprint, face recognition, or iris scanning. It serves
 * as a common base for concrete biometric authenticator implementations, handling shared
 * cryptographic key management and providing a consistent interface for biometric authentication.
 *
 * The class combines cryptographic key management ([CryptoKeyAware]) with device authentication
 * capabilities ([DeviceAuthenticator]), ensuring that biometric authenticators can securely
 * generate, store, and manage cryptographic keys while providing user-friendly biometric
 * authentication experiences.
 *
 * Common biometric authenticator implementations:
 * - **BiometricOnlyAuthenticator**: Pure biometric authentication without fallback
 * - **BiometricDeviceCredentialAuthenticator**: Biometric with device credential fallback
 *
 * Security features:
 * - Keys are generated and stored in hardware when available
 * - Biometric template data never leaves the secure hardware
 * - Private keys require biometric authentication for access
 * - Automatic key invalidation when biometrics change
 * - Attestation support for high-security environments
 *
 * @see DeviceAuthenticator
 * @see CryptoKeyAware
 * @see BiometricOnlyAuthenticator
 * @see BiometricDeviceCredentialAuthenticator
 *
 * @since 1.0.0
 */
abstract class BiometricAuthenticator : CryptoKeyAware, DeviceAuthenticator {

    /**
     * Cryptographic key configuration used for generating and managing RSA key pairs.
     *
     * This property defines the parameters for cryptographic operations including:
     * - **Key alias**: Unique identifier for the key in Android Keystore
     * - **Key size**: RSA key size in bits (typically 2048 or 4096)
     * - **Algorithm**: Cryptographic algorithm specifications
     * - **Storage location**: Hardware vs software keystore preferences
     *
     * The key configuration is typically set during authenticator initialization and
     * determines how keys are generated, stored, and accessed. Changes to this property
     * after key generation may require re-registration of the device.
     *
     * Key security properties:
     * - Keys are generated in Android Keystore when possible
     * - Hardware backing provides tamper resistance
     * - Biometric authentication required for key access
     * - Automatic invalidation when biometrics change
     *
     * Example configuration:
     * ```kotlin
     * authenticator.cryptoKey = CryptoKey().apply {
     *     keyAlias = "device_auth_key_v1"
     *     keySize = 2048
     *     requireUserAuthentication = true
     *     userAuthenticationValidityDurationSeconds = 0 // Require auth for each use
     * }
     * ```
     *
     * @see CryptoKey
     * @see CryptoKeyAware
     */
    override lateinit var cryptoKey: CryptoKey

    /**
     * Permanently deletes all cryptographic keys and associated data for this authenticator.
     *
     * This method performs secure deletion of the device's authentication keys and any
     * associated cryptographic material. After calling this method, the device will need
     * to be re-registered before it can authenticate again.
     *
     * The deletion process includes:
     * - **Private key removal**: Securely deletes the private key from Android Keystore
     *
     * Security considerations:
     * - Deletion is permanent and cannot be undone
     * - Hardware-backed keys are securely wiped from secure storage
     *
     * @see CryptoKey.delete
     * @see DeviceAuthenticator.deleteKeys
     */
    override suspend fun deleteKeys() {
        cryptoKey.delete()
    }

}


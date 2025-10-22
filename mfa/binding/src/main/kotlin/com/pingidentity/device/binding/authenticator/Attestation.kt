/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.binding.authenticator

/**
 * Sealed class hierarchy representing different types of cryptographic attestation for device authentication.
 *
 * Attestation provides a mechanism to verify the authenticity and integrity of cryptographic keys
 * and the security environment in which they are generated and stored. This sealed class defines
 * the available attestation modes that can be used during device registration and authentication.
 *
 * The attestation system supports two primary modes:
 * - **None**: No attestation verification, suitable for software-based keys
 * - **Default**: Hardware-backed attestation with challenge verification
 *
 * Attestation is particularly important in high-security environments where it's necessary
 * to prove that keys are generated and stored in trusted hardware security modules (HSM)
 * or secure enclaves rather than software-based keystores.
 *
 * Key security benefits:
 * - Verifies hardware-backed key generation
 * - Proves keys cannot be extracted from secure storage
 * - Validates the integrity of the authentication environment
 * - Enables compliance with security standards and regulations
 *
 * Example usage:
 * ```kotlin
 * // No attestation (software keys)
 * val noAttestation = Attestation.None
 *
 * // Hardware attestation with challenge
 * val challenge = generateSecureChallenge()
 * val hwAttestation = Attestation.Default(challenge)
 *
 * // Conditional attestation based on device capability
 * val attestation = Attestation.fromBoolean(
 *     value = deviceSupportsHardwareAttestation(),
 *     challenge = serverChallenge
 * )
 * ```
 *
 * @param challenge Optional cryptographic challenge used for attestation verification.
 *                  When provided, this challenge must be included in the attestation
 *                  statement to prove freshness and prevent replay attacks.
 *
 * @see DeviceAuthenticator.isSupported
 * @see DeviceAuthenticator.register
 *
 * @since 1.0.0
 */
sealed class Attestation(val challenge: ByteArray? = null) {

    /**
     * No attestation mode - suitable for software-based key storage.
     *
     * This attestation type indicates that no cryptographic attestation verification
     * is required or available. It is typically used with software-based authenticators
     * like [AppPinAuthenticator] that generate and store keys in software keystores
     * rather than hardware security modules.
     *
     * Characteristics:
     * - No hardware verification required
     * - Compatible with all device types
     * - Suitable for general-purpose applications
     * - Lower security assurance compared to hardware attestation
     *
     * Example:
     * ```kotlin
     * val authenticator = AppPinAuthenticator()
     * val result = authenticator.register(context, Attestation.None)
     * ```
     */
    data object None : Attestation()

    /**
     * Hardware-backed attestation with cryptographic challenge verification.
     *
     * This attestation type requires hardware-backed key generation and provides
     * cryptographic proof that keys are stored in a trusted execution environment.
     * The attestation includes a challenge to ensure freshness and prevent replay attacks.
     *
     * Characteristics:
     * - Requires hardware security module (HSM) or secure enclave
     * - Provides cryptographic proof of key security properties
     * - Includes anti-replay protection via challenge verification
     * - Highest security assurance available
     *
     * Example:
     * ```kotlin
     * val challenge = getServerChallenge() // From authentication server
     * val attestation = Attestation.Default(challenge)
     * val authenticator = BiometricAuthenticator()
     * val result = authenticator.register(context, attestation)
     * ```
     *
     * @param challenge Cryptographic challenge provided by the relying party.
     *                  This challenge is included in the attestation statement
     *                  to prove the freshness of the attestation and prevent
     *                  replay attacks. Must be cryptographically random.
     *
     * @throws IllegalArgumentException if challenge is empty or invalid
     */
    class Default(challenge: ByteArray) : Attestation(challenge)

    companion object {
        /**
         * Factory method to create an attestation instance based on a boolean condition.
         *
         * This convenience method simplifies attestation selection by allowing callers
         * to specify whether hardware attestation should be used via a boolean parameter.
         * It's particularly useful when attestation requirements are determined dynamically
         * based on device capabilities, security policies, or configuration settings.
         *
         * The method automatically handles challenge management:
         * - When `value` is true, creates [Default] attestation with the provided challenge
         * - When `value` is false, creates [None] attestation (challenge is ignored)
         *
         * Common usage patterns:
         * ```kotlin
         * // Based on device capability detection
         * val attestation = Attestation.fromBoolean(
         *     value = deviceHasHardwareAttestation(),
         *     challenge = serverChallenge
         * )
         *
         * // Based on application security policy
         * val attestation = Attestation.fromBoolean(
         *     value = requireHighSecurity,
         *     challenge = authChallenge
         * )
         *
         * // Based on user preference or admin setting
         * val attestation = Attestation.fromBoolean(
         *     value = userSettings.enableHardwareAttestation,
         *     challenge = challenge
         * )
         * ```
         *
         * @param value Boolean flag indicating whether hardware attestation should be used.
         *              - true: Returns [Default] attestation with challenge verification
         *              - false: Returns [None] attestation (no hardware verification)
         * @param challenge Cryptographic challenge for attestation verification.
         *                  Required when `value` is true, ignored when `value` is false.
         *                  Should be cryptographically random and provided by the relying party.
         *
         * @return [Default] attestation instance if `value` is true, [None] otherwise
         *
         * @see Default
         * @see None
         */
        fun fromBoolean(value: Boolean, challenge: ByteArray): Attestation =
            if (value) Default(challenge) else None
    }
}



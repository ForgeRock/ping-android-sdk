/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.binding.authenticator

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyProperties.AUTH_BIOMETRIC_STRONG
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricPrompt
import com.pingidentity.android.ContextProvider
import com.pingidentity.device.binding.authenticator.exception.DeviceNotRegisteredException
import java.security.PrivateKey
import java.security.interfaces.RSAPublicKey

/**
 * Pure biometric authenticator that requires biometric authentication without fallback options.
 *
 * This authenticator provides the highest level of biometric security by requiring exclusive
 * biometric authentication (fingerprint, face recognition, iris scanning) without allowing
 * fallback to device credentials. It automatically adapts between strong and weak biometric
 * authentication based on device capabilities while maintaining strict biometric-only access.
 *
 * Limitations:
 * - No fallback authentication if biometric fails or is unavailable
 * - Users may be locked out if biometric sensors malfunction
 * - Not suitable for users who cannot use biometric authentication
 * - Requires user education about biometric-only access limitations
 *
 * Example usage:
 * ```kotlin
 * val authenticator = BiometricOnlyAuthenticator {
 *     strongBoxPreferred = true
 *
 *     promptInfo {
 *         setTitle("Biometric Authentication Required")
 *         setSubtitle("Use your fingerprint or face")
 *         setDescription("Access requires biometric verification only")
 *         setNegativeButtonText("Cancel")
 *         setConfirmationRequired(false) // Faster authentication
 *     }
 *
 *     keyGenParameterSpec {
 *         setUserAuthenticationRequired(true)
 *         setUserAuthenticationValidityDurationSeconds(0) // Always require biometric
 *         setInvalidatedByBiometricEnrollment(true)
 *         setIsStrongBoxBacked(true)
 *     }
 * }
 *
 * // Register with biometric-only protection
 * val registrationResult = authenticator.register(context, Attestation.Default(challenge))
 *
 * // Authenticate using biometric only
 * val authResult = authenticator.authenticate(context)
 * if (authResult.isSuccess) {
 *     val (privateKey, cryptoObject) = authResult.getOrNull()!!
 *     // cryptoObject available when using BIOMETRIC_STRONG
 * }
 * ```
 *
 * @param config Configuration object containing biometric prompt settings, key generation
 *               parameters, hardware preferences, and logging configuration
 *
 * @see BiometricAuthenticator
 * @see BiometricAuthenticatorConfig
 * @see BiometricDeviceCredentialAuthenticator
 * @see DeviceBindingAuthenticationType.BIOMETRIC_ONLY
 *
 * @since 1.0.0
 */
open class BiometricOnlyAuthenticator(private val config: BiometricAuthenticatorConfig) :
    BiometricAuthenticator() {

    companion object {
        /**
         * Factory method for creating a [BiometricOnlyAuthenticator] with DSL configuration.
         *
         * This operator function provides a convenient way to create and configure a pure biometric
         * authenticator using a DSL-style configuration block. The factory method applies the
         * configuration and returns a fully configured authenticator instance that enforces
         * biometric-only authentication without fallback options.
         *
         * Example configurations:
         * ```kotlin
         * // Basic biometric-only configuration
         * val authenticator = BiometricOnlyAuthenticator {
         *     promptInfo {
         *         setTitle("Secure Access")
         *         setSubtitle("Biometric required")
         *         setNegativeButtonText("Cancel")
         *     }
         * }
         * ```
         *
         * @param block Configuration lambda applied to [BiometricAuthenticatorConfig] for
         *              customizing biometric-only authenticator behavior, UI appearance,
         *              and security parameters
         * @return Configured [BiometricOnlyAuthenticator] instance ready for biometric-only authentication
         *
         * @see BiometricAuthenticatorConfig
         */
        operator fun invoke(block: BiometricAuthenticatorConfig.() -> Unit = {}): BiometricOnlyAuthenticator {
            val config =
                BiometricAuthenticatorConfig().apply(block) // apply the configuration block
            return BiometricOnlyAuthenticator(config)
        }
    }

    /**
     * The type of device binding authentication provided by this authenticator.
     *
     * This property identifies the authenticator as supporting pure biometric authentication
     * without any fallback mechanisms. The type is used by the authentication framework to
     * determine compatibility, configure appropriate UI flows, and apply biometric-only
     * security policies.
     *
     * @see DeviceBindingAuthenticationType
     */
    override val type = DeviceBindingAuthenticationType.BIOMETRIC_ONLY

    /**
     * Authenticates the user using pure biometric methods with adaptive strength selection.
     *
     * This method provides strict biometric-only authentication that automatically adapts
     * between strong and weak biometric authentication based on device capabilities. It
     * prioritizes BIOMETRIC_STRONG with crypto object support when available, falling back
     * to BIOMETRIC_WEAK with time-based authentication when necessary, but never allows
     * device credential fallback.
     *
     * @param context Android context for displaying biometric prompts and accessing system services
     * @return [Result] containing [Pair] of [PrivateKey] and optional [BiometricPrompt.CryptoObject]
     *         on success, or failure with appropriate exception. CryptoObject is non-null only
     *         when BIOMETRIC_STRONG authentication is used.
     *
     * @see BiometricPrompt
     * @see authenticateWithBiometric
     */
    override suspend fun authenticate(context: Context): Result<Pair<PrivateKey, BiometricPrompt.CryptoObject?>> =
        runCatching {
            config.logger.d("BiometricOnly: Starting authentication")
            cryptoKey.privateKey?.let {
                var privateKey: PrivateKey? = null
                var authenticator = BIOMETRIC_WEAK
                if (config.biometricManager.canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS) {
                    config.logger.d("BiometricOnly: Using BIOMETRIC_STRONG with crypto object")
                    privateKey = it
                    authenticator = BIOMETRIC_STRONG
                }
                val promptInfo = BiometricPrompt.PromptInfo.Builder().apply {
                    setNegativeButtonText("Cancel")
                    setAllowedAuthenticators(authenticator)
                }.apply {
                    config.promptInfo(this)
                }.build()

                config.logger.d("BiometricOnly: Initiating biometric authentication")
                val authenticationResult =
                    authenticateWithBiometric(ContextProvider.context, promptInfo, privateKey)
                config.logger.i("BiometricOnly: Authentication successful")
                Pair(it, authenticationResult.cryptoObject)
            } ?: throw DeviceNotRegisteredException("Device is not registered")
        }

    /**
     * Registers a new device by generating biometric-only protected RSA key pairs.
     *
     * This method creates a new RSA key pair in the Android Keystore with exclusive biometric
     * protection, ensuring that keys can only be accessed through biometric authentication
     * without any fallback mechanisms. The key generation process adapts to device capabilities
     * while maintaining strict biometric-only access requirements.
     *
     * @param context Android context for accessing system services and checking device capabilities
     * @param attestation Attestation configuration specifying hardware verification requirements.
     *                   [Attestation.Default] enables hardware attestation with challenge verification.
     *                   [Attestation.None] generates keys without attestation.
     * @return [Result] containing [KeyPair] with public key, private key reference, and key alias
     *         on success, or failure with appropriate exception
     *
     * @throws Exception if key generation fails due to hardware limitations, biometric unavailability,
     *                   invalid parameters, or system security policy violations
     *
     * @see Attestation
     * @see CryptoKey.create
     * @see KeyGenParameterSpec
     */
    override suspend fun register(context: Context, attestation: Attestation): Result<KeyPair> =
        runCatching {
            config.logger.d("BiometricOnly: Generating keys")
            val builder = cryptoKey.keyBuilder()
            if (attestation.challenge?.isNotEmpty() == true) {
                builder.setAttestationChallenge(attestation.challenge)
            }
            builder.setUserAuthenticationRequired(true)

            if (config.strongBoxPreferred &&
                context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
            ) {
                config.logger.d("StrongBox is available, using StrongBox for key generation")
                builder.setIsStrongBoxBacked(true)
            }

            if (config.biometricManager.canAuthenticate(BIOMETRIC_STRONG) != BiometricManager.BIOMETRIC_SUCCESS) {
                config.logger.d("BiometricOnly: Device doesn't support BIOMETRIC_STRONG, using time-based authentication")
                //If the device doesn't have Strong Biometric (with Crypto Object), we fallback to time-based
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    builder.setUserAuthenticationParameters(
                        cryptoKey.timeout,
                        AUTH_BIOMETRIC_STRONG
                    )
                } else {
                    @Suppress("DEPRECATION")
                    builder.setUserAuthenticationValidityDurationSeconds(cryptoKey.timeout)
                }
            }

            config.keyGenParameterSpec(builder)
            val key = cryptoKey.create(builder.build())
            config.logger.i("BiometricOnly: Successfully generated keys")
            KeyPair(key.public as RSAPublicKey, key.private, cryptoKey.keyAlias)
        }

    /**
     * Checks if biometric-only authentication is supported on the current device.
     *
     * This method determines whether the device can support pure biometric authentication
     * by checking for the availability of either weak or strong biometric authentication
     * capabilities. It provides a permissive check that accepts any level of biometric
     * authentication, allowing the authenticator to adapt between strong and weak biometric
     * modes as needed.
     *
     * @param context Android context for accessing biometric and security services
     * @param attestation Attestation requirements (not used for basic support checking,
     *                   but may be relevant for future hardware attestation validation)
     * @return true if any level of biometric authentication is supported and available,
     *         false otherwise
     *
     * @see BiometricManager.canAuthenticate
     * @see BiometricManager.Authenticators.BIOMETRIC_WEAK
     */
    override fun isSupported(context: Context, attestation: Attestation): Boolean {
        return config.biometricManager.canAuthenticate(BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
    }

}
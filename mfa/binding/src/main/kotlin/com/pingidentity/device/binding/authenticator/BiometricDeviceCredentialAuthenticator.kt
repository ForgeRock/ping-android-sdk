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
import android.security.keystore.KeyProperties.AUTH_DEVICE_CREDENTIAL
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import com.pingidentity.android.ContextProvider
import com.pingidentity.device.binding.authenticator.exception.DeviceNotRegisteredException
import java.security.PrivateKey
import java.security.interfaces.RSAPublicKey

/**
 * Biometric authenticator with device credential fallback support for enhanced user experience.
 *
 * This authenticator provides a flexible authentication approach that allows users to authenticate
 * using either biometric methods (fingerprint, face, iris) or device credentials (PIN, pattern, password)
 * as a fallback option. This combination offers the security benefits of biometric authentication
 * while ensuring users always have an alternative authentication method available.
 *
 * @param config Configuration object containing biometric prompt settings, key generation
 *               parameters, hardware preferences, and logging configuration
 *
 * @see BiometricAuthenticator
 * @see BiometricAuthenticatorConfig
 * @see BiometricOnlyAuthenticator
 * @see DeviceBindingAuthenticationType.BIOMETRIC_ALLOW_FALLBACK
 *
 * @since 1.0.0
 */
open class BiometricDeviceCredentialAuthenticator(private val config: BiometricAuthenticatorConfig) :
    BiometricAuthenticator() {

    companion object {
        /**
         * Factory method for creating a [BiometricDeviceCredentialAuthenticator] with DSL configuration.
         *
         * This operator function provides a convenient way to create and configure a biometric
         * authenticator with device credential fallback using a DSL-style configuration block.
         * The factory method applies the configuration and returns a fully configured authenticator instance.
         *
         * Configuration areas available:
         * - **UI prompts**: Title, subtitle, description for biometric dialogs
         * - **Hardware preferences**: StrongBox support and security level preferences
         * - **Key generation**: Cryptographic parameters and authentication requirements
         * - **Logging**: Debug and monitoring configuration
         *
         * Example configurations:
         * ```kotlin
         * // Basic configuration with default settings
         * val authenticator = BiometricDeviceCredentialAuthenticator {
         *     promptInfo {
         *         setTitle("Authenticate")
         *         setSubtitle("Use biometric or PIN")
         *     }
         * }
         * ```
         *
         * @param block Configuration lambda applied to [BiometricAuthenticatorConfig] for
         *              customizing authenticator behavior, UI appearance, and security parameters
         * @return Configured [BiometricDeviceCredentialAuthenticator] instance ready for use
         *
         * @see BiometricAuthenticatorConfig
         */
        operator fun invoke(block: BiometricAuthenticatorConfig.() -> Unit = {}): BiometricDeviceCredentialAuthenticator {
            val config =
                BiometricAuthenticatorConfig().apply(block) // apply the configuration block
            return BiometricDeviceCredentialAuthenticator(config)
        }
    }

    /**
     * The type of device binding authentication provided by this authenticator.
     *
     * This property identifies the authenticator as supporting biometric authentication
     * with device credential fallback capabilities. The type is used by the authentication
     * framework to determine compatibility, configure appropriate UI flows, and apply
     * relevant security policies.
     *
     * @see DeviceBindingAuthenticationType
     */
    override val type = DeviceBindingAuthenticationType.BIOMETRIC_ALLOW_FALLBACK

    /**
     * Authenticates the user using biometric methods with device credential fallback.
     *
     * This method provides a seamless authentication experience that first attempts biometric
     * authentication and automatically falls back to device credentials when biometric
     * authentication fails, is unavailable, or is not enrolled. The method handles the
     * complexity of multi-modal authentication while providing a simple interface to callers.
     *
     * @param context Android context for displaying biometric prompts and accessing system services
     * @return [Result] containing [Pair] of [PrivateKey] and optional [BiometricPrompt.CryptoObject]
     *         on success, or failure with appropriate exception. CryptoObject is null when
     *         device credential authentication is used.
     *
     * @see BiometricPrompt
     * @see authenticateWithBiometric
     */
    override suspend fun authenticate(context: Context): Result<Pair<PrivateKey, BiometricPrompt.CryptoObject?>> = runCatching {
        config.logger.d("BiometricAndDeviceCredential: Starting authentication...")
        cryptoKey.privateKey?.let {
            val authenticator =
                if (config.biometricManager.canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS) {
                    BIOMETRIC_STRONG or DEVICE_CREDENTIAL
                } else {
                    BIOMETRIC_WEAK or DEVICE_CREDENTIAL
                }

            val promptInfo = BiometricPrompt.PromptInfo.Builder().apply {
                config.promptInfo(this)
                setAllowedAuthenticators(authenticator)
            }.build()

            config.logger.d("BiometricAndDeviceCredential: Initiating biometric authentication")
            val authenticationResult =
                authenticateWithBiometric(
                    ContextProvider.context,
                    promptInfo,
                    null // we don't need to pass the private key here, using pin not work with crypto object
                )
            config.logger.i("BiometricAndDeviceCredential: Authentication successful")
            Pair(it, authenticationResult.cryptoObject)
        } ?: throw DeviceNotRegisteredException("Device is not registered")
    }

    /**
     * Registers a new device by generating biometric and device credential protected key pairs.
     *
     * This method creates a new RSA key pair in the Android Keystore with protection that requires
     * either biometric authentication or device credential for access. The key generation process
     * configures the key with appropriate security parameters, hardware backing preferences,
     * and authentication requirements for both biometric and credential-based access.
     *
     * @param context Android context for accessing system services and checking device capabilities
     * @param attestation Attestation configuration specifying hardware verification requirements.
     *                   [Attestation.Default] enables hardware attestation with challenge verification.
     *                   [Attestation.None] generates keys without attestation.
     * @return [Result] containing [KeyPair] with public key, private key reference, and key alias
     *         on success, or failure with appropriate exception
     *
     * @throws Exception if key generation fails due to hardware limitations, invalid parameters,
     *                   or system security policy violations
     *
     * @see Attestation
     * @see KeyGenParameterSpec
     */
    override suspend fun register(context: Context, attestation: Attestation): Result<KeyPair> = runCatching {
        config.logger.d("BiometricAndDeviceCredential: Generating keys...")
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

        //Use time-based key for device credential
        //When user use device credential, crypto object will not be available
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setUserAuthenticationParameters(
                cryptoKey.timeout,
                AUTH_BIOMETRIC_STRONG or AUTH_DEVICE_CREDENTIAL
            )
        } else {
            @Suppress("DEPRECATION")
            builder.setUserAuthenticationValidityDurationSeconds(cryptoKey.timeout)
        }

        config.keyGenParameterSpec(builder)
        val key = cryptoKey.create(builder.build())
        KeyPair(key.public as RSAPublicKey, key.private, cryptoKey.keyAlias)
    }

    /**
     * Checks if biometric authentication with device credential fallback is supported on the current device.
     *
     * This method determines whether the device can support the combined authentication approach
     * of biometric methods with device credential fallback. It checks the availability of either
     * weak or strong biometric authentication combined with device credential capabilities.
     *
     * @param context Android context for accessing biometric and security services
     * @param attestation Attestation requirements (not used for basic support checking,
     *                   but may be relevant for future hardware attestation validation)
     * @return true if biometric authentication with device credential fallback is supported,
     *         false otherwise
     *
     * @see BiometricManager.canAuthenticate
     * @see BiometricManager.Authenticators.BIOMETRIC_WEAK
     * @see BiometricManager.Authenticators.DEVICE_CREDENTIAL
     */
    override fun isSupported(context: Context, attestation: Attestation): Boolean {
        return config.biometricManager.canAuthenticate(BIOMETRIC_WEAK or DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS
    }

}
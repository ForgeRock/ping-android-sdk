/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.binding.authenticator

import android.security.keystore.KeyGenParameterSpec
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import com.pingidentity.android.ContextProvider
import com.pingidentity.logger.Logger
import com.pingidentity.utils.PingDsl
import com.pingidentity.utils.andThen

/**
 * Configuration class for biometric authenticators that defines UI prompts, key generation, and hardware preferences.
 *
 * This DSL-style configuration class provides comprehensive control over biometric authentication
 * behavior, including user interface presentation, cryptographic key generation parameters,
 * and hardware security preferences. The configuration follows a builder pattern allowing
 * flexible customization while providing sensible defaults for most biometric authentication scenarios.
 *
 * Key configuration areas:
 * - **UI Presentation**: Customizable biometric prompt appearance and messaging
 * - **Key Generation**: Hardware-backed cryptographic key parameters and specifications
 * - **Hardware Preferences**: StrongBox support and security enclave utilization
 * - **Logging**: Configurable logging for debugging and monitoring biometric operations
 * - **Biometric Management**: Integration with Android BiometricManager APIs
 *
 * Default configuration provides:
 * - Standard logging through the SDK logger
 * - Software-backed key generation (StrongBox disabled by default)
 * - Empty prompt configuration (requires customization)
 * - Default Android Keystore key generation parameters
 * - Automatic BiometricManager initialization
 *
 * Example usage:
 * ```kotlin
 * val authenticator = BiometricOnlyAuthenticator {
 *     strongBoxPreferred = true
 *
 *     promptInfo {
 *         setTitle("Authenticate with Biometric")
 *         setSubtitle("Use your fingerprint or face")
 *         setDescription("This verifies your identity for secure access")
 *         setNegativeButtonText("Cancel")
 *     }
 *
 *     keyGenParameterSpec {
 *         setUserAuthenticationRequired(true)
 *         setUserAuthenticationValidityDurationSeconds(0) // Require auth for each use
 *         setInvalidatedByBiometricEnrollment(true)
 *     }
 *
 *     logger = Logger.create("BiometricAuth")
 * }
 * ```
 *
 * Security considerations:
 * - StrongBox provides highest security but limited device support
 * - Key invalidation on biometric changes prevents unauthorized access
 * - User authentication requirements ensure fresh biometric verification
 * - Logging should be disabled or filtered in production builds
 *
 * Hardware compatibility:
 * - Requires Android 6.0+ (API 23) for basic biometric support
 * - Android 9.0+ (API 28) recommended for BiometricPrompt APIs
 * - StrongBox requires Android 9.0+ and compatible hardware
 * - Biometric sensors (fingerprint, face, iris) must be available
 *
 * @see BiometricAuthenticator
 * @see BiometricOnlyAuthenticator
 * @see BiometricDeviceCredentialAuthenticator
 * @see BiometricPrompt.PromptInfo
 * @see KeyGenParameterSpec
 *
 * @since 1.0.0
 */
@PingDsl
class BiometricAuthenticatorConfig {

    /**
     * Logger instance used for debugging and monitoring biometric authentication operations.
     *
     * Provides logging capabilities for biometric authentication flows, including key generation,
     * prompt display, authentication attempts, and error conditions. The logger helps with
     * debugging integration issues and monitoring authentication patterns.
     *
     * Default: [Logger.logger] - Standard SDK logger with default configuration
     *
     * @see Logger
     */
    var logger = Logger.logger

    /**
     * Indicates whether StrongBox hardware security module should be preferred for key storage.
     *
     * StrongBox is a tamper-resistant hardware security module that provides the highest level
     * of security for cryptographic keys. When enabled, the system will attempt to generate
     * and store keys in StrongBox if available, falling back to standard TEE if not supported.
     *
     * StrongBox benefits:
     * - Hardware-isolated key generation and storage
     * - Tamper-resistant and tamper-evident security
     * - Side-channel attack resistance
     * - Compliance with highest security standards
     * - Physical protection against key extraction
     *
     * Compatibility considerations:
     * - Requires Android 9.0+ (API 28)
     * - Limited device support (mainly flagship devices)
     * - May impact performance for key operations
     * - Graceful fallback to TEE when unavailable
     *
     * Default: false (uses standard TEE for broader compatibility)
     *
     * @see KeyGenParameterSpec.Builder.setIsStrongBoxBacked
     */
    var strongBoxPreferred = false

    /**
     * Internal configuration function for biometric prompt UI settings.
     *
     * This property holds the configuration block that will be applied to
     * [BiometricPrompt.PromptInfo.Builder] when creating biometric prompts.
     * It accumulates multiple configuration calls using the [andThen] combinator.
     *
     * @see promptInfo for public configuration method
     */
    internal var promptInfo: BiometricPrompt.PromptInfo.Builder.() -> Unit = {}

    /**
     * Configures the biometric authentication prompt UI appearance and behavior.
     *
     * This method allows customization of the biometric prompt that is displayed to users
     * during authentication. The configuration includes text content, button labels,
     * and authentication behavior settings that control the user experience.
     *
     * Available prompt configurations:
     * - **Title**: Main heading displayed in the prompt
     * - **Subtitle**: Secondary text providing additional context
     * - **Description**: Detailed explanation of the authentication purpose
     * - **Negative Button**: Cancel button text and behavior
     * - **Device Credential**: Whether to allow device PIN/pattern/password fallback
     * - **Confirmation Required**: Whether explicit confirmation is needed after biometric recognition
     *
     * UI design principles:
     * - Use clear, concise language that explains the authentication purpose
     * - Maintain consistency with application branding and terminology
     * - Provide meaningful cancel options for user control
     * - Consider accessibility and localization requirements
     *
     * Example configurations:
     * ```kotlin
     * // Basic fingerprint authentication
     * promptInfo {
     *     setTitle("Unlock with Fingerprint")
     *     setSubtitle("Touch the fingerprint sensor")
     *     setNegativeButtonText("Cancel")
     * }
     *
     * // High-security authentication with confirmation
     * promptInfo {
     *     setTitle("Secure Transaction")
     *     setSubtitle("Authenticate to authorize payment")
     *     setDescription("Your biometric confirms this $${amount} transaction")
     *     setConfirmationRequired(true)
     *     setNegativeButtonText("Cancel Payment")
     * }
     *
     * // Multi-modal biometric authentication
     * promptInfo {
     *     setTitle("Access Secure Data")
     *     setSubtitle("Use fingerprint, face, or iris")
     *     setDescription("Multiple biometric options are available")
     *     setAllowedAuthenticators(BIOMETRIC_WEAK or BIOMETRIC_STRONG)
     * }
     * ```
     *
     * Localization considerations:
     * - All text should support internationalization
     * - Consider text length variations across languages
     * - Maintain consistent terminology with OS biometric settings
     *
     * @param block Configuration lambda applied to [BiometricPrompt.PromptInfo.Builder]
     *
     * @see BiometricPrompt.PromptInfo.Builder
     */
    fun promptInfo(block: BiometricPrompt.PromptInfo.Builder.() -> Unit) {
        promptInfo = promptInfo.andThen(block)
    }

    /**
     * Internal configuration function for cryptographic key generation parameters.
     *
     * This property holds the configuration block that will be applied to
     * [KeyGenParameterSpec.Builder] when generating biometric-protected keys.
     * It accumulates multiple configuration calls using the [andThen] combinator.
     *
     * @see keyGenParameterSpec for public configuration method
     */
    internal var keyGenParameterSpec: KeyGenParameterSpec.Builder.() -> Unit = {}

    /**
     * Configures cryptographic key generation parameters for biometric-protected keys.
     *
     * This method allows customization of how cryptographic keys are generated and stored
     * in the Android Keystore with biometric protection. The configuration controls security
     * properties, authentication requirements, and hardware utilization for maximum security.
     *
     * @param block Configuration lambda applied to [KeyGenParameterSpec.Builder]
     *
     * @see KeyGenParameterSpec.Builder
     * @see android.security.keystore.KeyProperties
     */
    fun keyGenParameterSpec(block: KeyGenParameterSpec.Builder.() -> Unit) {
        keyGenParameterSpec = keyGenParameterSpec.andThen(block)
    }

    /**
     * BiometricManager instance for checking biometric authentication availability and capabilities.
     *
     * This property provides access to Android's BiometricManager, which is used to determine
     * what types of biometric authentication are available on the current device and their
     * current status. The manager is automatically initialized with the application context.
     *
     * @see BiometricManager
     * @see BiometricManager.Authenticators
     */
    val biometricManager: BiometricManager = BiometricManager.from(ContextProvider.context)

}
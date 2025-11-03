/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.binding.journey

import android.os.Build
import com.pingidentity.android.ContextProvider
import com.pingidentity.device.binding.Prompt
import com.pingidentity.device.binding.UserKey
import com.pingidentity.device.binding.UserKeyStorageConfig
import com.pingidentity.device.binding.UserKeysStorage
import com.pingidentity.device.binding.authenticator.AppPinAuthenticator
import com.pingidentity.device.binding.authenticator.AppPinConfig
import com.pingidentity.device.binding.authenticator.BiometricAuthenticatorConfig
import com.pingidentity.device.binding.authenticator.BiometricDeviceCredentialAuthenticator
import com.pingidentity.device.binding.authenticator.BiometricOnlyAuthenticator
import com.pingidentity.device.binding.authenticator.DeviceAuthenticator
import com.pingidentity.device.binding.authenticator.DeviceBindingAuthenticationType
import com.pingidentity.device.binding.authenticator.DeviceBindingAuthenticationType.APPLICATION_PIN
import com.pingidentity.device.binding.authenticator.DeviceBindingAuthenticationType.BIOMETRIC_ALLOW_FALLBACK
import com.pingidentity.device.binding.authenticator.DeviceBindingAuthenticationType.BIOMETRIC_ONLY
import com.pingidentity.device.binding.authenticator.DeviceBindingAuthenticationType.NONE
import com.pingidentity.device.binding.authenticator.NoneAuthenticator
import com.pingidentity.device.binding.ui.UserKeyOption
import com.pingidentity.device.binding.ui.collectUserKey
import com.pingidentity.device.id.DefaultDeviceIdentifier
import com.pingidentity.device.id.DeviceIdentifier
import com.pingidentity.logger.Logger
import com.pingidentity.utils.PingDsl
import com.pingidentity.utils.andThen
import java.time.Instant

private const val USER_KEY_COLLECTOR_KT = "com.pingidentity.device.binding.ui.UserKeyCollectorKt"

/**
 * Configuration class for customizing device binding operations.
 *
 * This DSL-enabled configuration class allows fine-grained control over various aspects
 * of the device binding process, including authentication methods, storage configuration,
 * JWT signing parameters, and user interface behavior.
 *
 * The configuration supports:
 * - Multiple authentication types (biometric, PIN, none)
 * - Customizable storage backends for user key metadata
 * - JWT signing algorithm and timing configuration
 * - Device identification and naming
 * - Custom claims for verification
 * - User key selection strategies
 *
 * Example usage:
 * ```kotlin
 * deviceBindingCallback.bind {
 *     deviceName = "My Custom Device"
 *     signingAlgorithm = "RS256"
 *     userKeyStorage {
 *         fileName = "my_keys.json"
 *     }
 *     biometricAuthenticatorConfig {
 *         promptInfo = {
 *             setTitle("Authenticate for Device Binding")
 *         }
 *     }
 * }
 * ```
 *
 * @see DeviceBindingCallback.bind
 * @see UserKeyStorageConfig
 * @see AppPinConfig
 * @see BiometricAuthenticatorConfig
 */
@PingDsl
class DeviceBindingConfig {

    /**
     * The logger instance used for debugging and monitoring device binding operations.
     * Defaults to the global Logger.logger instance.
     */
    var logger: Logger = Logger.logger

    /**
     * The device identifier used to generate unique device IDs for binding.
     * Defaults to DefaultDeviceIdentifier which uses Android device properties.
     */
    var deviceIdentifier: DeviceIdentifier = DefaultDeviceIdentifier

    /**
     * The human-readable name for the device that will be displayed in user interfaces.
     * Defaults to the device model (Build.MODEL).
     */
    var deviceName: String = Build.MODEL

    /**
     * The cryptographic algorithm used for signing JWTs during device binding.
     * Must be a valid JWS algorithm identifier. Defaults to "RS512".
     * Common values: "RS256", "RS384", "RS512"
     */
    var signingAlgorithm = "RS512"

    /**
     * Internal configuration block for user key storage settings.
     * Use the userKeyStorage() method to configure storage behavior.
     */
    private var userKeyStorage: UserKeyStorageConfig.() -> Unit = {}

    /**
     * Configures the storage backend for user key metadata.
     *
     * This method allows customization of how user key information is stored
     * and retrieved, including file names, encryption settings, and storage location.
     *
     * Example:
     * ```kotlin
     * userKeyStorage {
     *     fileName = "device_keys.json"
     *     encrypted = true
     * }
     * ```
     *
     * @param block Configuration block for UserKeyStorageConfig
     * @see UserKeyStorageConfig
     */
    fun userKeyStorage(block: UserKeyStorageConfig.() -> Unit) {
        userKeyStorage = userKeyStorage.andThen(block)
    }

    /**
     * Creates a UserKeysStorage instance with the configured storage settings.
     *
     * @return Configured UserKeysStorage instance for internal use
     */
    internal fun keyStorage() = UserKeysStorage(UserKeyStorageConfig().apply(userKeyStorage))

    /**
     * Internal configuration block for application PIN authenticator settings.
     * Use the appPinConfig() method to configure PIN authentication behavior.
     */
    internal var appPinConfig: AppPinConfig.() -> Unit = {}

    /**
     * Configures the application PIN authenticator settings.
     *
     * This method allows customization of PIN-based authentication behavior,
     * including PIN collection UI, validation rules, and storage settings.
     *
     * Example:
     * ```kotlin
     * appPinConfig {
     *     pinCollector = { prompt -> myCustomPinCollector(prompt) }
     * }
     * ```
     *
     * @param block Configuration block for AppPinConfig
     * @see AppPinConfig
     */
    fun appPinConfig(block: AppPinConfig.() -> Unit) {
        appPinConfig = appPinConfig.andThen(block)
    }

    /**
     * Internal configuration block for biometric authenticator settings.
     * Use the biometricAuthenticatorConfig() method to configure biometric authentication.
     */
    internal var biometricAuthenticatorConfig: BiometricAuthenticatorConfig.() -> Unit = {}

    /**
     * Configures the biometric authenticator settings.
     *
     * This method allows customization of biometric authentication behavior,
     * including prompt appearance, fallback options, and authentication requirements.
     *
     * Example:
     * ```kotlin
     * biometricAuthenticatorConfig {
     *     promptInfo = {
     *         setTitle("Biometric Authentication")
     *         setNegativeButtonText("Cancel")
     *     }
     * }
     * ```
     *
     * @param block Configuration block for BiometricAuthenticatorConfig
     * @see BiometricAuthenticatorConfig
     */
    fun biometricAuthenticatorConfig(block: BiometricAuthenticatorConfig.() -> Unit) {
        biometricAuthenticatorConfig = biometricAuthenticatorConfig.andThen(block)
    }

    /**
     * Function that provides the current time for JWT "iat" (issued at) claim.
     * Defaults to the current system time when the JWT is created.
     */
    var issueTime: () -> Instant = { Instant.now() }

    /**
     * Function that provides the "nbf" (not before) time for JWT validation.
     * Defaults to the current system time, meaning the JWT is valid immediately.
     */
    var notBeforeTime: () -> Instant = { Instant.now() }

    /**
     * Function that calculates the JWT expiration time based on timeout seconds.
     * Takes the timeout in seconds and returns an Instant representing when the JWT expires.
     * Defaults to current time plus the timeout in seconds.
     *
     * @param timeout The timeout value in seconds
     * @return Instant representing when the JWT should expire
     */
    var expirationTime: (Int) -> Instant = { Instant.now().plusSeconds(it.toLong()) }

    /**
     * Internal configuration block for additional JWT claims.
     * Use the claims() method to add custom verification claims to the JWT.
     */
    internal var claims: MutableMap<String, Any>.() -> Unit = {}

    /**
     * Configures additional custom claims to be included in the signed JWT.
     *
     * These claims can be used for verification purposes and provide additional
     * context about the device binding operation. The claims are included in
     * the JWT payload and can be validated by the server.
     *
     * Example:
     * ```kotlin
     * claims {
     *     put("app_version", "1.2.3")
     *     put("device_type", "mobile")
     *     put("custom_data", mapOf("key" -> "value"))
     * }
     * ```
     *
     * @param block Configuration block that receives a MutableMap for adding claims
     */
    fun claims(block: MutableMap<String, Any>.() -> Unit) {
        claims = claims.andThen(block)
    }

    /**
     * Function that selects which user key to use when multiple keys are available.
     *
     * This function is called when the system finds multiple user keys for the current
     * user and authentication type. The default implementation checks if a UI collector
     * is available and prompts the user to select a key, otherwise it returns the first key.
     *
     * @param keys List of available UserKey objects to choose from
     * @return The selected UserKey to use for the operation
     */
    internal var userKeySelector: suspend (List<UserKey>) -> UserKey = { keys ->
        if (isUserKeyCollectorAvailable()) {
            val option = collectUserKey(
                ContextProvider.currentActivity,
                keys.map { UserKeyOption(it.id, it.userName, it.authType.name) })
            keys.first { it.id == option.id }
        } else {
            keys.first()
        }
    }

    /**
     * Configures a custom user key selection strategy.
     *
     * This method allows you to override the default user key selection behavior
     * when multiple keys are available for the same user and authentication type.
     *
     * Example:
     * ```kotlin
     * userKeySelector { keys ->
     *     // Select the most recently created key
     *     keys.maxByOrNull { it.createdDate } ?: keys.first()
     * }
     * ```
     *
     * @param block Suspend function that takes a list of UserKey objects and returns the selected one
     */
    fun userKeySelector(block: suspend (List<UserKey>) -> UserKey) {
        userKeySelector = block
    }

    /**
     * Factory function for creating device authenticators based on authentication type.
     *
     * This property can be set to provide custom authenticator implementations.
     * If not set, the default authenticators will be used based on the authentication type.
     *
     * @param type The authentication type to create an authenticator for
     * @return A DeviceAuthenticator instance for the specified type
     */
    lateinit var deviceAuthenticator: (DeviceBindingAuthenticationType) -> DeviceAuthenticator

    /**
     * Creates the appropriate device authenticator for the given authentication type and prompt.
     *
     * This method returns either a custom authenticator (if deviceAuthenticator is set) or
     * creates a default authenticator based on the authentication type. The prompt information
     * is used to configure authentication UI elements.
     *
     * @param type The authentication type required
     * @param prompt The prompt information for authentication UI
     * @return A configured DeviceAuthenticator instance
     */
    internal fun authenticator(
        type: DeviceBindingAuthenticationType,
        prompt: Prompt
    ): DeviceAuthenticator {
        return if (::deviceAuthenticator.isInitialized) {
            deviceAuthenticator(type)
        } else {
            when (type) {
                BIOMETRIC_ONLY -> BiometricOnlyAuthenticator(createBiometricConfig(prompt))
                BIOMETRIC_ALLOW_FALLBACK -> BiometricDeviceCredentialAuthenticator(
                    createBiometricConfig(
                        prompt
                    )
                )

                APPLICATION_PIN -> AppPinAuthenticator(createAppPinConfig(prompt))
                NONE -> NoneAuthenticator()
            }
        }
    }

    /**
     * Creates a BiometricAuthenticatorConfig instance with the provided prompt information.
     *
     * This method constructs the configuration for biometric authentication using the
     * prompt details and applies any custom biometric configuration that has been set.
     * It automatically configures the prompt title, subtitle, and description, and
     * sets confirmation to false on Android R+ for better user experience.
     *
     * @param prompt The prompt information containing title, subtitle, and description
     * @return Configured BiometricAuthenticatorConfig instance
     */
    private fun createBiometricConfig(prompt: Prompt) =
        BiometricAuthenticatorConfig().apply {
            logger = this@DeviceBindingConfig.logger
            promptInfo = {
                setTitle(prompt.title)
                setSubtitle(prompt.subtitle)
                setDescription(prompt.description)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setConfirmationRequired(false)
                }
            }
        }.apply(biometricAuthenticatorConfig)

    /**
     * Creates an AppPinConfig instance with the provided prompt information.
     *
     * This method constructs the configuration for application PIN authentication
     * using the prompt details and applies any custom PIN configuration that has been set.
     * The prompt is used to display appropriate context to the user when requesting PIN input.
     *
     * @param prompt The prompt information for PIN authentication context
     * @return Configured AppPinConfig instance
     */
    private fun createAppPinConfig(prompt: Prompt) =
        AppPinConfig().apply {
            logger = this@DeviceBindingConfig.logger
            this.prompt = prompt
        }.apply(appPinConfig)

    /**
     * Checks if the user key collector UI component is available at runtime.
     *
     * This method uses reflection to determine if the UserKeyCollectorKt class
     * is available in the classpath. The user key collector is an optional UI
     * component that allows users to select from multiple available keys.
     * If not available, the system will automatically select the first available key.
     *
     * @return true if the user key collector UI is available, false otherwise
     */
    private fun isUserKeyCollectorAvailable(): Boolean {
        return try {
            Class.forName(USER_KEY_COLLECTOR_KT)
            true
        } catch (e: ClassNotFoundException) {
            false
        }
    }

}
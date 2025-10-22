/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.binding.authenticator

import com.pingidentity.android.ContextProvider
import com.pingidentity.device.binding.Prompt
import com.pingidentity.logger.Logger
import com.pingidentity.storage.CacheStrategy.NO_CACHE
import com.pingidentity.storage.EncryptedDataStoreStorage
import com.pingidentity.storage.EncryptedDataStoreStorageConfig
import com.pingidentity.storage.Storage
import com.pingidentity.utils.PingDsl
import com.pingidentity.utils.andThen

/**
 * Default storage file name and key alias for PIN-based device binding authentication.
 * This constant ensures consistent naming across the authenticator implementation.
 */
private const val DEVICE_BINDING_AUTHENTICATOR_V_1_PIN = "com.pingidentity.device.binding.v1.pin"

/**
 * Class name constant used for runtime checking of PIN collector UI availability.
 * This allows graceful degradation when UI components are not available.
 */
private const val APP_PIN_COLLECTOR_KT = "com.pingidentity.device.binding.ui.AppPinCollectorKt"

/**
 * Configuration class for [AppPinAuthenticator] that defines PIN collection, storage, and security settings.
 *
 * This DSL-style configuration class provides comprehensive control over how PIN-based authentication
 * is handled, including UI presentation, retry behavior, storage encryption, and keystore management.
 * The configuration follows a builder pattern allowing flexible customization while providing
 * sensible defaults for most use cases.
 *
 * Key configuration areas:
 * - **PIN Collection**: Customizable UI prompts and collection strategies
 * - **Security Settings**: Retry limits, keystore types, and encryption parameters
 * - **Storage Configuration**: Encrypted storage backends with flexible naming
 * - **Logging**: Configurable logging for debugging and monitoring
 *
 * Default configuration provides:
 * - 3 PIN retry attempts before lockout
 * - PKCS12 keystore format for maximum compatibility
 * - Encrypted DataStore storage with no caching
 * - Automatic UI PIN collector when available
 *
 * Example usage:
 * ```kotlin
 * val authenticator = AppPinAuthenticator {
 *     pinRetry = 5
 *     keystoreType = "PKCS12"
 *     prompt {
 *         title = "Authenticate Device"
 *         subtitle = "Enter your PIN"
 *         description = "This PIN protects your device keys"
 *     }
 *     pinCollector { prompt ->
 *         showCustomPinDialog(prompt)
 *     }
 *     storage {
 *         fileName = "my_app_keys"
 *         strongBoxPreferred = true
 *     }
 * }
 * ```
 *
 * Security considerations:
 * - PIN attempts are limited to prevent brute force attacks
 * - Storage is encrypted using hardware-backed encryption when available
 * - KeyStore entries are password-protected with user PIN
 * - No caching prevents PIN exposure in memory dumps
 *
 * @see AppPinAuthenticator
 * @see EncryptedDataStoreStorage
 * @see Prompt
 *
 * @since 1.0.0
 */
@PingDsl
class AppPinConfig {

    /**
     * Logger instance used for debugging and monitoring authentication operations.
     *
     * Defaults to [Logger.logger] which provides standard logging capabilities.
     * Can be customized to use application-specific loggers or disable logging
     * for production builds.
     *
     * Example:
     * ```kotlin
     * logger = Logger.create("PinAuth").apply {
     *     level = Logger.Level.DEBUG
     * }
     * ```
     */
    var logger: Logger = Logger.logger

    /**
     * UI prompt configuration for PIN collection dialogs.
     *
     * Defines the text content displayed to users when requesting PIN input.
     * The prompt includes title, subtitle, and description fields that can be
     * customized for different authentication contexts.
     *
     * Default prompt provides generic authentication messages that work for
     * most use cases.
     *
     * Example:
     * ```kotlin
     * prompt = Prompt().apply {
     *     title = "Device Authentication"
     *     subtitle = "Enter PIN to unlock"
     *     description = "Your PIN protects the keys stored on this device"
     * }
     * ```
     *
     * @see Prompt
     */
    var prompt: Prompt = Prompt()

    /**
     * Maximum number of PIN entry attempts before authentication fails.
     *
     * This security measure prevents brute force attacks by limiting the number
     * of consecutive failed PIN attempts. After exceeding this limit, the
     * authentication operation will fail with [InvalidCredentialException].
     *
     * Recommended values:
     * - 3-5 for most applications (balances security and usability)
     * - 1-2 for high-security environments
     * - 10+ for development/testing only
     *
     * Default: 3 attempts
     */
    var pinRetry: Int = 3

    /**
     * PIN collection function that handles user interaction for PIN input.
     *
     * This suspend function is called whenever the authenticator needs to collect
     * a PIN from the user. The default implementation uses the built-in UI collector
     * when available, or returns an empty array as fallback.
     *
     * Custom implementations can:
     * - Show custom UI dialogs
     * - Integrate with biometric fallbacks
     * - Implement enterprise authentication flows
     * - Handle accessibility requirements
     *
     * The function receives a [Prompt] parameter containing display text and
     * must return a [CharArray] containing the user's PIN. The CharArray should
     * be cleared after use for security.
     *
     * @throws Exception if user cancels or PIN collection fails
     *
     * @see pinCollector for setting custom collectors
     */
    internal var pinCollector: suspend (Prompt) -> CharArray = { prompt ->
        if (isAppPinCollectorAvailable()) {
            com.pingidentity.device.binding.ui.collectPin(
                ContextProvider.currentActivity,
                prompt.title,
                prompt.subtitle,
                prompt.description
            )
        } else {
            charArrayOf()
        }
    }

    /**
     * Sets a custom PIN collection strategy.
     *
     * Allows applications to provide their own PIN collection implementation
     * instead of using the default UI collector. This is useful for:
     * - Custom UI designs that match application themes
     * - Integration with existing authentication systems
     * - Accessibility-specific implementations
     * - Testing with predefined PIN values
     *
     * Example:
     * ```kotlin
     * pinCollector { prompt ->
     *     showCustomPinDialog(
     *         title = prompt.title,
     *         message = prompt.description
     *     )
     * }
     * ```
     *
     * @param block Suspend function that collects PIN from user
     */
    fun pinCollector(block: suspend (Prompt) -> CharArray) {
        pinCollector = block
    }

    /**
     * KeyStore type used for storing encrypted private keys.
     *
     * Specifies the format for the software keystore that holds the PIN-protected
     * private keys. Different keystore types offer varying levels of compatibility
     * and security features.
     *
     * Supported values:
     * - "PKCS12": Recommended default, maximum compatibility
     * - "JKS": Java KeyStore format, legacy support
     * - "BKS": BouncyCastle KeyStore, additional algorithm support
     *
     * PKCS12 is recommended because:
     * - Industry standard format
     * - Wide platform support
     * - Strong encryption capabilities
     * - Future-proof design
     *
     * Default: "PKCS12"
     */
    var keystoreType = "PKCS12"

    /**
     * Storage factory function that creates encrypted storage instances.
     *
     * Returns a [Storage] implementation configured for securely persisting
     * encrypted keystore data. The default implementation uses [EncryptedDataStoreStorage]
     * which provides hardware-backed encryption when available.
     *
     * Custom storage implementations can be provided for:
     * - Alternative encryption backends
     * - Cloud storage integration
     * - Enterprise key management systems
     * - Testing with in-memory storage
     *
     * Example:
     * ```kotlin
     * storage = {
     *     CustomEncryptedStorage(
     *         encryptionKey = getApplicationKey(),
     *         backupLocation = getBackupPath()
     *     )
     * }
     * ```
     *
     * @see storage for configuration customization
     */
    var storage: () -> Storage<ByteArray> = {
       EncryptedDataStoreStorage(storageOption)
    }

    /**
     * Storage configuration options for encrypted keystore persistence.
     *
     * Internal configuration function that sets up the default encrypted storage
     * parameters. This configuration is applied when creating storage instances
     * and can be customized using the [storage] function.
     *
     * Default configuration:
     * - File name based on authenticator version constant
     * - Hardware-backed encryption when available
     * - No caching for security
     * - Logging integration for debugging
     *
     * @see storage for customization options
     */
    internal var storageOption: EncryptedDataStoreStorageConfig.() -> Unit = {
        fileName = DEVICE_BINDING_AUTHENTICATOR_V_1_PIN
        removeFileOnDelete = true
        keyAlias = DEVICE_BINDING_AUTHENTICATOR_V_1_PIN
        strongBoxPreferred = false
        cacheStrategy = NO_CACHE
        logger = this@AppPinConfig.logger
    }

    /**
     * Customizes the encrypted storage configuration.
     *
     * Allows fine-tuning of the encrypted storage backend that persists
     * keystore data. The configuration block is applied on top of the
     * default settings, enabling selective customization.
     *
     * Available options:
     * - `fileName`: Custom storage file name
     * - `keyAlias`: Encryption key alias for storage
     * - `strongBoxPreferred`: Enable hardware security module when available
     * - `cacheStrategy`: Memory caching behavior (NO_CACHE recommended)
     * - `logger`: Custom logger for storage operations
     *
     * Example:
     * ```kotlin
     * storage {
     *     fileName = "myapp_device_keys_v2"
     *     strongBoxPreferred = true
     *     cacheStrategy = NO_CACHE
     * }
     * ```
     *
     * Security recommendations:
     * - Use NO_CACHE to prevent memory exposure
     * - Enable strongBoxPreferred for hardware security
     * - Use versioned file names for migration support
     *
     * @param block Configuration block for storage settings
     *
     * @see EncryptedDataStoreStorageConfig
     */
    fun storage(block: EncryptedDataStoreStorageConfig.() -> Unit) {
        storageOption = storageOption.andThen(block)
    }

    /**
     * Checks if the built-in PIN collector UI component is available at runtime.
     *
     * This method performs runtime class loading to determine if the PIN collection
     * UI components are included in the current application build. This allows
     * the authenticator to gracefully degrade when UI components are not available,
     * such as in headless or server-side environments.
     *
     * The check is performed by attempting to load the PIN collector class using
     * reflection. If the class is not found, it indicates that the UI module
     * was not included in the build.
     *
     * Use cases:
     * - Library builds without UI dependencies
     * - Server-side authentication scenarios
     * - Testing environments with custom PIN collection
     * - Modular applications with optional UI components
     *
     * @return true if PIN collector UI is available, false otherwise
     *
     * @see pinCollector for setting custom collection strategies
     */
    internal fun isAppPinCollectorAvailable(): Boolean {
        return try {
            Class.forName(APP_PIN_COLLECTOR_KT)
            true
        } catch (e: ClassNotFoundException) {
            false
        }
    }

}
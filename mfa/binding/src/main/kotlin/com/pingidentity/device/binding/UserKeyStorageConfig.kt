/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.binding

import com.pingidentity.logger.Logger
import com.pingidentity.storage.CacheStrategy
import com.pingidentity.storage.EncryptedDataStoreStorage
import com.pingidentity.storage.EncryptedDataStoreStorageConfig
import com.pingidentity.storage.Storage
import com.pingidentity.utils.PingDsl
import com.pingidentity.utils.andThen

/**
 * Default identifier used for user key storage file name and encryption key alias.
 * This constant ensures consistent naming across the device binding storage system.
 */
private const val DEVICE_BINDING_V_1_USER_KEYS = "com.pingidentity.device.binding.v1.userkeys"

/**
 * Configuration class for customizing user key metadata storage behavior.
 *
 * This DSL-enabled configuration class provides fine-grained control over how user key
 * metadata is stored, retrieved, and managed in the device binding system. It supports
 * various storage backends with configurable encryption, caching, and security options.
 *
 * The configuration uses encrypted storage by default to protect sensitive user key
 * metadata, even though the actual private keys remain in the Android KeyStore.
 * This provides defense-in-depth security for the complete key management system.
 *
 * @see UserKeysStorage
 * @see EncryptedDataStoreStorage
 * @see EncryptedDataStoreStorageConfig
 * @see UserKey
 */
@PingDsl
class UserKeyStorageConfig {

    /**
     * The logger instance used for debugging and monitoring storage operations.
     *
     * This logger captures important events during user key metadata storage operations,
     * including successful saves, retrieval operations, errors, and configuration changes.
     * It helps with troubleshooting storage issues and monitoring system performance.
     *
     * Defaults to the global Logger.logger instance.
     */
    var logger: Logger = Logger.logger

    /**
     * Factory function that creates the underlying storage implementation for user key metadata.
     *
     * This property allows for complete customization of the storage backend while maintaining
     * the same high-level interface. The default implementation uses EncryptedDataStoreStorage
     * with Android DataStore and AES encryption for secure, persistent storage.
     *
     * Custom implementations can be provided to:
     * - Use different storage technologies (Room database, file system, network storage)
     * - Implement custom encryption schemes
     * - Add data compression or validation
     * - Integrate with existing storage systems
     *
     * The storage must handle List<UserKey> serialization and be thread-safe for
     * concurrent access from multiple coroutines.
     *
     * @return A Storage instance configured for List<UserKey> operations
     * @see Storage
     * @see EncryptedDataStoreStorage
     */
    var storage: () -> Storage<List<UserKey>> = {
        EncryptedDataStoreStorage(storageOption)
    }

    /**
     * Internal configuration block for the default EncryptedDataStoreStorage settings.
     *
     * This private property holds the accumulated configuration for the encrypted storage
     * backend. It's built up through calls to the storage() method and provides secure
     * defaults for production use:
     *
     * - **fileName**: Uses versioned identifier for future migration support
     * - **keyAlias**: Same as fileName for consistent key management
     * - **strongBoxPreferred**: Disabled by default for broader device compatibility
     * - **cacheStrategy**: NO_CACHE for maximum security and data freshness
     * - **logger**: Inherits from the parent configuration
     *
     * The configuration can be extended and modified through the storage() method
     * while preserving existing settings.
     */
    private var storageOption: EncryptedDataStoreStorageConfig.() -> Unit = {
        fileName = DEVICE_BINDING_V_1_USER_KEYS
        keyAlias = DEVICE_BINDING_V_1_USER_KEYS
        strongBoxPreferred = false
        cacheStrategy = CacheStrategy.NO_CACHE
        logger = this@UserKeyStorageConfig.logger
    }

    /**
     * Configures the encrypted storage backend with custom settings.
     *
     * This method allows customization of the EncryptedDataStoreStorage behavior
     * while preserving any previously configured settings. It uses the andThen
     * pattern to compose configuration blocks, enabling incremental customization.
     *
     * Common configuration options include:
     * - **fileName**: Custom file name for the storage file
     * - **keyAlias**: Custom alias for the encryption key in Android KeyStore
     * - **strongBoxPreferred**: Enable StrongBox hardware security module (if available)
     * - **cacheStrategy**: Choose between NO_CACHE, IN_MEMORY, or DISK caching
     * - **encrypted**: Enable/disable encryption (encryption strongly recommended)
     *
     * Example:
     * ```kotlin
     * storage {
     *     fileName = "custom_keys"
     *     strongBoxPreferred = true
     *     cacheStrategy = CacheStrategy.IN_MEMORY
     * }
     * ```
     *
     * @param block Configuration block for EncryptedDataStoreStorageConfig customization
     * @see EncryptedDataStoreStorageConfig
     * @see CacheStrategy
     */
    fun storage(block: EncryptedDataStoreStorageConfig.() -> Unit) {
        storageOption = storageOption.andThen(block)
    }
}
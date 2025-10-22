/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.binding

/**
 * Manages persistent storage and retrieval of user key metadata for device binding operations.
 *
 * This class provides a high-level interface for storing, retrieving, and managing UserKey
 * metadata while abstracting the underlying storage implementation. It handles the complete
 * lifecycle of user key information including creation, lookup, updates, and deletion.
 *
 * The storage system maintains only metadata about cryptographic keys, not the actual
 * private keys themselves, which remain securely stored in the Android KeyStore.
 *
 * @param config The storage configuration specifying how and where to store user key metadata
 *
 * @see UserKey
 * @see UserKeyStorageConfig
 * @see CryptoKey
 */
class UserKeysStorage(private val config: UserKeyStorageConfig = UserKeyStorageConfig()) {

    /**
     * Companion object providing factory methods for creating UserKeysStorage instances.
     */
    companion object {
        /**
         * Creates a UserKeysStorage instance with configuration applied through a DSL block.
         *
         * This factory method allows for convenient configuration of storage settings
         * using a lambda expression with receiver syntax. It's the preferred way to
         * create storage instances when custom configuration is needed.
         *
         * @param block Configuration block for customizing UserKeyStorageConfig
         * @return A configured UserKeysStorage instance
         */
        operator fun invoke(block: UserKeyStorageConfig.() -> Unit = {}) =
            UserKeysStorage(UserKeyStorageConfig().apply(block))
    }

    /**
     * The underlying storage implementation configured by the UserKeyStorageConfig.
     * Lazily initialized to defer storage setup until first access.
     */
    internal val storage by lazy { config.storage() }

    /**
     * Finds a user key by user ID.
     *
     * Searches through all stored user keys and returns the first one that matches
     * the specified user ID. Since the save operation replaces existing keys for
     * the same user ID, this will return the most recently saved key for the user.
     *
     * @param userId The unique identifier of the user whose key to find
     * @return The UserKey for the specified user, or null if no key is found
     */
    suspend fun findByUserId(userId: String): UserKey? =
        storage.get()?.firstOrNull { it.userId == userId }

    /**
     * Retrieves all stored user keys.
     *
     * Returns a complete list of all user key metadata stored in the system.
     * This is useful for operations that need to work with multiple users,
     * such as key selection UIs or bulk operations.
     *
     * @return A list of all stored UserKey objects, or an empty list if no keys exist
     */
    suspend fun findAll(): List<UserKey> =
        storage.get() ?: emptyList()

    /**
     * Saves a user key, replacing any existing key for the same user.
     *
     * This method stores the provided user key metadata, automatically removing
     * any previously stored key for the same user ID. This ensures that each
     * user has only one active key in the storage system.
     *
     * The operation is atomic - either the key is successfully saved or the
     * storage remains unchanged if an error occurs.
     *
     * @param userKey The UserKey metadata to save
     */
    suspend fun save(userKey: UserKey) =
        updateStorage { keys -> keys.filter { it.userId != userKey.userId } + userKey }

    /**
     * Saves multiple user keys in a batch operation, adding only new users.
     *
     * This method efficiently adds multiple user keys while avoiding duplicates.
     * Only keys for users that don't already exist in storage are added.
     * Existing user keys are preserved unchanged.
     *
     * This operation is useful for:
     * - Migrating keys from other storage systems
     * - Bulk importing user keys
     * - Adding multiple keys without overwriting existing ones
     *
     * If the input list is empty, no operation is performed.
     *
     * @param userKeys List of UserKey objects to save
     */
    suspend fun saveAll(userKeys: List<UserKey>) {
        if (userKeys.isEmpty()) {
            return // Do nothing if no keys to add
        }

        updateStorage { existingKeys ->
            val existingUserIds = existingKeys.map { it.userId }.toSet()
            val newKeys = userKeys.filter { it.userId !in existingUserIds }
            existingKeys + newKeys
        }
    }

    /**
     * Deletes a specific user key from storage.
     *
     * Removes the user key metadata for the user specified in the provided UserKey object.
     * This is a convenience method that delegates to deleteByUserId using the UserKey's userId.
     *
     * Note: This only removes the metadata - the actual cryptographic keys in the
     * Android KeyStore must be deleted separately using the appropriate CryptoKey methods.
     *
     * @param userKey The UserKey to delete from storage
     */
    suspend fun delete(userKey: UserKey) =
        deleteByUserId(userKey.userId)

    /**
     * Deletes a user key by user ID.
     *
     * Removes all user key metadata for the specified user ID from storage.
     * If no key exists for the user ID, the operation completes successfully
     * without error (idempotent operation).
     *
     * This method only removes the metadata from storage. The actual private
     * key material in the Android KeyStore must be deleted separately.
     *
     * @param userId The unique identifier of the user whose key to delete
     */
    suspend fun deleteByUserId(userId: String) =
        updateStorage { keys -> keys.filter { it.userId != userId } }

    /**
     * Internal helper method for atomically updating the storage with a transformation function.
     *
     * This method provides a safe way to modify the stored key list by:
     * 1. Retrieving the current list of keys
     * 2. Applying the transformation function to create a new list
     * 3. Saving the new list or deleting the storage if empty
     *
     * The transformation is atomic - if any step fails, the storage remains
     * in its previous state. If the transformation results in an empty list,
     * the entire storage is deleted to clean up resources.
     *
     * @param transform Function that takes the current key list and returns the updated list
     */
    private suspend fun updateStorage(transform: (List<UserKey>) -> List<UserKey>) {
        val currentKeys = storage.get() ?: emptyList()
        val updatedKeys = transform(currentKeys)

        if (updatedKeys.isEmpty()) {
            storage.delete()
        } else {
            storage.save(updatedKeys)
        }
    }
}

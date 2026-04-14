/*
 * Copyright (c) 2025-2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.push.storage

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import com.pingidentity.mfa.commons.exception.MfaStorageException
import com.pingidentity.mfa.push.PushCredential
import com.pingidentity.mfa.push.PushDeviceToken
import com.pingidentity.mfa.push.PushNotification
import com.pingidentity.mfa.push.storage.PushStorage
import kotlinx.serialization.json.Json

/**
 * Android's SharedPreferences implementation of [PushStorage]. This implementation is useful to
 * showcase how to implement a custom storage solution rather than using the default encrypted
 * SQLite storage provided.
 *
 * Please, note SharedPreferences stores data as plaintext JSON. This is not secure and should not
 * be used for highly sensitive applications.
 */
class SharedPrefsPushStorage(context: Context, prefName: String = DEFAULT_PREFS_NAME) : PushStorage {

    companion object {
        private const val TAG = "SharedPrefsPushStorage"
        private const val DEFAULT_PREFS_NAME = "push_storage_prefs"

        // Keys for metadata
        private const val KEY_INITIALIZED = "initialized"
        private const val KEY_CREDENTIAL_METADATA = "push_credential_metadata"
        private const val KEY_NOTIFICATION_METADATA = "push_notification_metadata"
        private const val KEY_DEVICE_TOKEN_METADATA = "push_device_token_metadata"
        private const val KEY_CURRENT_DEVICE_TOKEN = "current_device_token"

        // Prefixes for actual data
        private const val KEY_CREDENTIAL_PREFIX = "push_credential_"
        private const val KEY_NOTIFICATION_PREFIX = "push_notification_"
        private const val KEY_DEVICE_TOKEN_PREFIX = "push_device_token_"
    }

    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(
        prefName, Context.MODE_PRIVATE
    )

    private var isInitialized = false

    /**
     * Get all credential IDs from the metadata.
     */
    private fun getCredentialIds(): Set<String> {
        return sharedPreferences.getStringSet(KEY_CREDENTIAL_METADATA, emptySet())?.toSet() ?: emptySet()
    }

    /**
     * Update the credential IDs in the metadata.
     */
    private fun updateCredentialIds(ids: Set<String>) {
        // Create a deep copy to prevent concurrent modification issues
        val idsCopy = HashSet(ids)
        sharedPreferences.edit { putStringSet(KEY_CREDENTIAL_METADATA, idsCopy) }
    }

    /**
     * Get all notification IDs from the metadata.
     */
    private fun getNotificationIds(): Set<String> {
        return sharedPreferences.getStringSet(KEY_NOTIFICATION_METADATA, emptySet())?.toSet() ?: emptySet()
    }

    /**
     * Update the notification IDs in the metadata.
     */
    private fun updateNotificationIds(ids: Set<String>) {
        // Create a deep copy to prevent concurrent modification issues
        val idsCopy = HashSet(ids)
        sharedPreferences.edit { putStringSet(KEY_NOTIFICATION_METADATA, idsCopy) }
    }

    /**
     * Get all device token IDs from the metadata.
     */
    private fun getDeviceTokenIds(): Set<String> {
        return sharedPreferences.getStringSet(KEY_DEVICE_TOKEN_METADATA, emptySet())?.toSet() ?: emptySet()
    }

    /**
     * Update the device token IDs in the metadata.
     */
    private fun updateDeviceTokenIds(ids: Set<String>) {
        // Create a deep copy to prevent concurrent modification issues
        val idsCopy = HashSet(ids)
        sharedPreferences.edit { putStringSet(KEY_DEVICE_TOKEN_METADATA, idsCopy) }
    }

    /**
     * Generate the key for a credential in SharedPreferences.
     */
    private fun getCredentialKey(credentialId: String): String {
        return "$KEY_CREDENTIAL_PREFIX$credentialId"
    }

    /**
     * Generate the key for a notification in SharedPreferences.
     */
    private fun getNotificationKey(notificationId: String): String {
        return "$KEY_NOTIFICATION_PREFIX$notificationId"
    }

    /**
     * Generate the key for a device token in SharedPreferences.
     */
    private fun getDeviceTokenKey(tokenId: String): String {
        return "$KEY_DEVICE_TOKEN_PREFIX$tokenId"
    }

    /**
     * Check if the storage is initialized and throw an exception if not.
     */
    private fun checkInitialized() {
        if (!isInitialized) {
            throw MfaStorageException("Storage not initialized. Call initialize() first.")
        }
    }

    override suspend fun initialize() {
        try {
            // Check if already initialized
            if (sharedPreferences.contains(KEY_INITIALIZED)) {
                isInitialized = true
                return
            }

            // Create metadata sets if they don't exist
            if (!sharedPreferences.contains(KEY_CREDENTIAL_METADATA)) {
                updateCredentialIds(emptySet())
            }

            if (!sharedPreferences.contains(KEY_NOTIFICATION_METADATA)) {
                updateNotificationIds(emptySet())
            }

            if (!sharedPreferences.contains(KEY_DEVICE_TOKEN_METADATA)) {
                updateDeviceTokenIds(emptySet())
            }

            // Mark as initialized
            sharedPreferences.edit { putBoolean(KEY_INITIALIZED, true) }
            isInitialized = true

            Log.i(TAG, "Storage initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize storage", e)
            throw MfaStorageException("Failed to initialize storage: ${e.message}", e)
        }
    }

    override suspend fun clear() {
        try {
            checkInitialized()

            // Clear all data
            sharedPreferences.edit { clear() }

            // Re-initialize
            sharedPreferences.edit { putBoolean(KEY_INITIALIZED, true) }
            updateCredentialIds(emptySet())
            updateNotificationIds(emptySet())
            updateDeviceTokenIds(emptySet())

            Log.i(TAG, "Storage cleared successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear storage", e)
            throw MfaStorageException("Failed to clear storage: ${e.message}", e)
        }
    }

    override suspend fun storePushCredential(credential: PushCredential) {
        try {
            checkInitialized()

            val jsonData = credential.toJson()
            val credentialKey = getCredentialKey(credential.id)

            // Use a synchronized block to prevent race conditions
            synchronized(this) {
                // Store the credential first to ensure it exists
                sharedPreferences.edit(commit = true) {
                    putString(credentialKey, jsonData)
                }

                // Update metadata with proper synchronization
                val ids = getCredentialIds().toMutableSet()
                ids.add(credential.id)
                updateCredentialIds(ids)
            }

            Log.d(TAG, "Stored credential with ID: ${credential.id}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to store credential", e)
            throw MfaStorageException("Failed to store credential: ${e.message}", e)
        }
    }

    override suspend fun retrievePushCredential(credentialId: String): PushCredential? {
        try {
            checkInitialized()

            val credentialKey = getCredentialKey(credentialId)
            val jsonData = sharedPreferences.getString(credentialKey, null) ?: return null

            return PushCredential.fromJson(jsonData)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to retrieve credential", e)
            throw MfaStorageException("Failed to retrieve credential: ${e.message}", e)
        }
    }

    override suspend fun getAllPushCredentials(): List<PushCredential> {
        try {
            checkInitialized()

            val ids = getCredentialIds()
            return ids.mapNotNull { retrievePushCredential(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get all credentials", e)
            throw MfaStorageException("Failed to get all credentials: ${e.message}", e)
        }
    }
    
    override suspend fun getCredentialByIssuerAndAccount(
        issuer: String,
        accountName: String
    ): PushCredential? {
        try {
            checkInitialized()
            
            // For SharedPreferences, we need to get all credentials and filter
            // This is less efficient than SQL-based storage, but works for testing
            val allCredentials = getAllPushCredentials()
            return allCredentials.firstOrNull { 
                it.issuer == issuer && it.accountName == accountName 
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get credential by issuer and account", e)
            throw MfaStorageException("Failed to get credential by issuer and account: ${e.message}", e)
        }
    }

    override suspend fun removePushCredential(credentialId: String): Boolean {
        try {
            checkInitialized()

            val credentialKey = getCredentialKey(credentialId)

            // Use a synchronized block to prevent race conditions
            synchronized(this) {
                // Check if credential exists
                if (!sharedPreferences.contains(credentialKey)) {
                    return false
                }

                // Remove the credential with a committed edit to ensure it's removed before updating metadata
                sharedPreferences.edit(commit = true) { remove(credentialKey) }

                // Update metadata atomically
                val ids = getCredentialIds().toMutableSet()
                ids.remove(credentialId)
                updateCredentialIds(ids)
            }

            Log.d(TAG, "Removed credential with ID: $credentialId")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove credential", e)
            throw MfaStorageException("Failed to remove credential: ${e.message}", e)
        }
    }

    override suspend fun storePushNotification(notification: PushNotification) {
        try {
            checkInitialized()

            val jsonData = notification.toJson()
            val notificationKey = getNotificationKey(notification.id)

            // Use a synchronized block to prevent race conditions
            synchronized(this) {
                // Store the notification first to ensure it exists
                sharedPreferences.edit(commit = true) {
                    putString(notificationKey, jsonData)
                }

                // Update metadata with proper synchronization
                val ids = getNotificationIds().toMutableSet()
                ids.add(notification.id)
                updateNotificationIds(ids)
            }

            Log.d(TAG, "Stored notification with ID: ${notification.id}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to store notification", e)
            throw MfaStorageException("Failed to store notification: ${e.message}", e)
        }
    }

    override suspend fun updatePushNotification(notification: PushNotification) {
        // For SharedPreferences, update is the same as store
        storePushNotification(notification)
    }

    override suspend fun retrievePushNotification(notificationId: String): PushNotification? {
        try {
            checkInitialized()

            val notificationKey = getNotificationKey(notificationId)
            val jsonData = sharedPreferences.getString(notificationKey, null) ?: return null

            return PushNotification.fromJson(jsonData)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to retrieve notification", e)
            throw MfaStorageException("Failed to retrieve notification: ${e.message}", e)
        }
    }

    override suspend fun getAllPushNotifications(): List<PushNotification> {
        try {
            checkInitialized()

            val ids = getNotificationIds()
            return ids.mapNotNull { retrievePushNotification(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get all notifications", e)
            throw MfaStorageException("Failed to get all notifications: ${e.message}", e)
        }
    }

    override suspend fun getPendingPushNotifications(): List<PushNotification> {
        try {
            checkInitialized()

            // Filter all notifications to find pending ones
            return getAllPushNotifications().filter { it.pending }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get pending notifications", e)
            throw MfaStorageException("Failed to get pending notifications: ${e.message}", e)
        }
    }

    override suspend fun removePushNotification(notificationId: String): Boolean {
        try {
            checkInitialized()

            val notificationKey = getNotificationKey(notificationId)

            // Use a synchronized block to prevent race conditions
            synchronized(this) {
                // Check if notification exists
                if (!sharedPreferences.contains(notificationKey)) {
                    return false
                }

                // Remove the notification with a committed edit to ensure it's removed before updating metadata
                sharedPreferences.edit(commit = true) { remove(notificationKey) }

                // Update metadata atomically
                val ids = getNotificationIds().toMutableSet()
                ids.remove(notificationId)
                updateNotificationIds(ids)
            }

            Log.d(TAG, "Removed notification with ID: $notificationId")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove notification", e)
            throw MfaStorageException("Failed to remove notification: ${e.message}", e)
        }
    }

    override suspend fun removePushNotificationsForCredential(credentialId: String): Int {
        try {
            checkInitialized()

            val notifications = getAllPushNotifications().filter { it.credentialId == credentialId }
            val notificationIds = notifications.map { it.id }

            var count = 0

            for (id in notificationIds) {
                if (removePushNotification(id)) {
                    count++
                }
            }

            Log.d(TAG, "Removed $count notifications for credential ID: $credentialId")
            return count
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove notifications for credential", e)
            throw MfaStorageException("Failed to remove notifications for credential: ${e.message}", e)
        }
    }

    override suspend fun clearPushCredentials() {
        try {
            checkInitialized()

            synchronized(this) {
                val ids = getCredentialIds()

                // Use a single committed edit operation to remove all credentials atomically
                sharedPreferences.edit(commit = true) {
                    // Remove all credentials
                    for (id in ids) {
                        remove(getCredentialKey(id))
                    }

                    // Clear metadata but keep initialization flag
                    remove(KEY_CREDENTIAL_METADATA)
                }

                // Reset metadata
                updateCredentialIds(emptySet())
            }

            Log.i(TAG, "Cleared all push credentials")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear credentials", e)
            throw MfaStorageException("Failed to clear credentials: ${e.message}", e)
        }
    }

    override suspend fun clearPushNotifications() {
        try {
            checkInitialized()

            synchronized(this) {
                val ids = getNotificationIds()

                // Use a single committed edit operation to remove all notifications atomically
                sharedPreferences.edit(commit = true) {
                    // Remove all notifications
                    for (id in ids) {
                        remove(getNotificationKey(id))
                    }

                    // Clear metadata but keep initialization flag
                    remove(KEY_NOTIFICATION_METADATA)
                }

                // Reset metadata
                updateNotificationIds(emptySet())
            }

            Log.i(TAG, "Cleared all push notifications")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear notifications", e)
            throw MfaStorageException("Failed to clear notifications: ${e.message}", e)
        }
    }

    override suspend fun storePushDeviceToken(token: PushDeviceToken) {
        try {
            checkInitialized()

            val jsonData = token.toJson()
            val tokenKey = getDeviceTokenKey(token.id)

            // Use a synchronized block to prevent race conditions
            synchronized(this) {
                // Store the token first to ensure it exists
                sharedPreferences.edit(commit = true) {
                    putString(tokenKey, jsonData)
                    // Save as current token
                    putString(KEY_CURRENT_DEVICE_TOKEN, token.id)
                }

                // Update metadata with proper synchronization
                val ids = getDeviceTokenIds().toMutableSet()
                ids.add(token.id)
                updateDeviceTokenIds(ids)
            }

            Log.d(TAG, "Stored device token with ID: ${token.id}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to store device token", e)
            throw MfaStorageException("Failed to store device token: ${e.message}", e)
        }
    }

    override suspend fun getCurrentPushDeviceToken(): PushDeviceToken? {
        try {
            checkInitialized()

            // Get the ID of the current token
            val currentTokenId = sharedPreferences.getString(KEY_CURRENT_DEVICE_TOKEN, null) ?: return null
            
            val tokenKey = getDeviceTokenKey(currentTokenId)
            val jsonData = sharedPreferences.getString(tokenKey, null) ?: return null

            return PushDeviceToken.fromJson(jsonData)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to retrieve current device token", e)
            throw MfaStorageException("Failed to retrieve current device token: ${e.message}", e)
        }
    }

    override suspend fun getAllPushDeviceTokens(): List<PushDeviceToken> {
        try {
            checkInitialized()

            val ids = getDeviceTokenIds()
            return ids.mapNotNull { retrieveDeviceToken(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get all device tokens", e)
            throw MfaStorageException("Failed to get all device tokens: ${e.message}", e)
        }
    }

    override suspend fun clearPushDeviceTokens() {
        try {
            checkInitialized()

            synchronized(this) {
                val ids = getDeviceTokenIds()

                // Use a single committed edit operation to remove all tokens atomically
                sharedPreferences.edit(commit = true) {
                    // Remove all device tokens
                    for (id in ids) {
                        remove(getDeviceTokenKey(id))
                    }

                    // Clear metadata but keep initialization flag
                    remove(KEY_DEVICE_TOKEN_METADATA)
                    remove(KEY_CURRENT_DEVICE_TOKEN)
                }

                // Reset metadata
                updateDeviceTokenIds(emptySet())
            }

            Log.i(TAG, "Cleared all push device tokens")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear device tokens", e)
            throw MfaStorageException("Failed to clear device tokens: ${e.message}", e)
        }
    }
    
    /**
     * Helper method to retrieve a device token by its ID.
     */
    private fun retrieveDeviceToken(tokenId: String): PushDeviceToken? {
        try {
            val tokenKey = getDeviceTokenKey(tokenId)
            val jsonData = sharedPreferences.getString(tokenKey, null) ?: return null

            return PushDeviceToken.fromJson(jsonData)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to retrieve device token", e)
            return null
        }
    }

    override suspend fun close() {
        // No resources to close for SharedPreferences
        isInitialized = false
    }

    override suspend fun getNotificationByMessageId(messageId: String): PushNotification? {
        try {
            checkInitialized()

            // Return null for blank message IDs
            if (messageId.isBlank()) {
                return null
            }

            // For SharedPreferences, we need to get all notifications and filter
            // This is less efficient than SQL-based storage, but works for testing
            val allNotifications = getAllPushNotifications()
            return allNotifications.firstOrNull { it.messageId == messageId }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get notification by message ID", e)
            throw MfaStorageException("Failed to get notification by message ID: ${e.message}", e)
        }
    }

    /**
     * Count the number of push notifications.
     *
     * @param credentialId Optional ID of a specific credential to count notifications for.
     * @return The count of push notifications.
     */
    override suspend fun countPushNotifications(credentialId: String?): Int {
        val allNotifications = getAllPushNotifications()
        return if (credentialId != null) {
            allNotifications.count { it.credentialId == credentialId }
        } else {
            allNotifications.size
        }
    }

    /**
     * Retrieve the oldest push notifications.
     *
     * @param limit The maximum number of notifications to retrieve.
     * @param credentialId Optional ID of a specific credential to retrieve notifications for.
     * @return A list of the oldest push notifications.
     */
    override suspend fun getOldestPushNotifications(limit: Int, credentialId: String?): List<PushNotification> {
        val allNotifications = getAllPushNotifications()
        val filteredNotifications = if (credentialId != null) {
            allNotifications.filter { it.credentialId == credentialId }
        } else {
            allNotifications
        }

        // Sort by creation date (oldest first) and limit
        return filteredNotifications
            .sortedBy { it.createdAt }
            .let { if (limit > 0) it.take(limit) else it }
    }

    /**
     * Purge push notifications by age.
     *
     * @param maxAgeDays The maximum age in days for notifications to keep.
     * @param credentialId Optional ID of a specific credential to purge notifications for.
     * @return The number of notifications removed.
     */
    override suspend fun purgePushNotificationsByAge(maxAgeDays: Int, credentialId: String?): Int {
        val allNotifications = getAllPushNotifications()
        val cutoffTime = System.currentTimeMillis() - (maxAgeDays * 24 * 60 * 60 * 1000L)

        val notificationsToRemove = allNotifications.filter { notification ->
            (credentialId == null || notification.credentialId == credentialId) &&
            notification.createdAt.time < cutoffTime
        }

        notificationsToRemove.forEach { removePushNotification(it.id) }

        return notificationsToRemove.size
    }

    /**
     * Purge push notifications by count (removes oldest notifications when count exceeds the limit).
     *
     * @param maxCount The maximum number of notifications to keep.
     * @param credentialId Optional ID of a specific credential to purge notifications for.
     * @return The number of notifications removed.
     */
    override suspend fun purgePushNotificationsByCount(maxCount: Int, credentialId: String?): Int {
        if (maxCount < 0) {
            return 0
        }

        val allNotifications = getAllPushNotifications()

        // Filter by credential ID if specified
        val filteredNotifications = if (credentialId != null) {
            allNotifications.filter { it.credentialId == credentialId }
        } else {
            allNotifications
        }

        // If we don't exceed the max count, no need to remove any
        if (filteredNotifications.size <= maxCount) {
            return 0
        }

        // Sort by creation date (oldest first)
        val sortedNotifications = filteredNotifications.sortedBy { it.createdAt }

        // Determine how many to remove
        val countToRemove = sortedNotifications.size - maxCount

        // Get the notifications to remove (the oldest ones)
        val notificationsToRemove = sortedNotifications.take(countToRemove)

        // Remove them
        notificationsToRemove.forEach { removePushNotification(it.id) }

        return notificationsToRemove.size
    }
}

/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.push

import com.pingidentity.mfa.commons.MfaStorage
import com.pingidentity.mfa.commons.exception.MfaStorageException

/**
 * Interface for Push-specific storage operations.
 * Extends the base MfaStorage interface with Push-specific functionality.
 */
interface PushStorage : MfaStorage {

    /**
     * Store a push credential.
     *
     * @param credential The Push credential to be stored.
     * @throws MfaStorageException if the credential cannot be stored.
     */
    suspend fun storePushCredential(credential: PushCredential)

    /**
     * Retrieve all stored push credentials.
     *
     * @return A list of all Push credentials.
     * @throws MfaStorageException if the credentials cannot be retrieved.
     */
    suspend fun getAllPushCredentials(): List<PushCredential>

    /**
     * Retrieve a specific push credential by ID.
     *
     * @param credentialId The ID of the credential to retrieve.
     * @return The Push credential, or null if not found.
     * @throws MfaStorageException if the credential cannot be retrieved.
     */
    suspend fun retrievePushCredential(credentialId: String): PushCredential?

    /**
     * Remove a push credential by its ID.
     *
     * @param credentialId The ID of the credential to remove.
     * @return true if the credential was successfully removed, false if it didn't exist.
     * @throws MfaStorageException if the credential cannot be removed.
     */
    suspend fun removePushCredential(credentialId: String): Boolean

    /**
     * Store a push notification.
     *
     * @param notification The Push notification to be stored.
     * @throws MfaStorageException if the notification cannot be stored.
     */
    suspend fun storePushNotification(notification: PushNotification)

    /**
     * Retrieve all stored push notifications.
     *
     * @return A list of all Push notifications.
     * @throws MfaStorageException if the notifications cannot be retrieved.
     */
    suspend fun getAllPushNotifications(): List<PushNotification>

    /**
     * Retrieve all pending push notifications.
     *
     * @return A list of pending Push notifications.
     * @throws MfaStorageException if the notifications cannot be retrieved.
     */
    suspend fun getPendingPushNotifications(): List<PushNotification>

    /**
     * Retrieve a specific push notification by ID.
     *
     * @param notificationId The ID of the notification to retrieve.
     * @return The Push notification, or null if not found.
     * @throws MfaStorageException if the notification cannot be retrieved.
     */
    suspend fun retrievePushNotification(notificationId: String): PushNotification?

    /**
     * Retrieve a push notification by message ID.
     *
     * @param messageId The message ID of the notification to retrieve.
     * @return The Push notification, or null if not found.
     * @throws MfaStorageException if the notification cannot be retrieved.
     */
    suspend fun getNotificationByMessageId(messageId: String): PushNotification?

    /**
     * Update a push notification.
     *
     * @param notification The Push notification to update.
     * @throws MfaStorageException if the notification cannot be updated.
     */
    suspend fun updatePushNotification(notification: PushNotification)

    /**
     * Remove a push notification by its ID.
     *
     * @param notificationId The ID of the notification to remove.
     * @return true if the notification was successfully removed, false if it didn't exist.
     * @throws MfaStorageException if the notification cannot be removed.
     */
    suspend fun removePushNotification(notificationId: String): Boolean

    /**
     * Remove all push notifications associated with a credential.
     *
     * @param credentialId The ID of the credential.
     * @return The number of notifications removed.
     * @throws MfaStorageException if the notifications cannot be removed.
     */
    suspend fun removePushNotificationsForCredential(credentialId: String): Int

    /**
     * Clear all Push credentials from the storage.
     *
     * @throws MfaStorageException if the credentials cannot be cleared.
     */
    suspend fun clearPushCredentials()

    /**
     * Clear all Push notifications from the storage.
     *
     * @throws MfaStorageException if the notifications cannot be cleared.
     */
    suspend fun clearPushNotifications()

    /**
     * Store a push device token.
     *
     * @param token The Push device token to be stored.
     * @throws MfaStorageException if the token cannot be stored.
     */
    suspend fun storePushDeviceToken(token: PushDeviceToken)

    /**
     * Retrieve the current push device token.
     *
     * @return The current Push device token, or null if not found.
     * @throws MfaStorageException if the token cannot be retrieved.
     */
    suspend fun getCurrentPushDeviceToken(): PushDeviceToken?

    /**
     * Retrieve all stored push device tokens.
     *
     * @return A list of all Push device tokens.
     * @throws MfaStorageException if the tokens cannot be retrieved.
     */
    suspend fun getAllPushDeviceTokens(): List<PushDeviceToken>

    /**
     * Clear all Push device tokens from the storage.
     *
     * @throws MfaStorageException if the tokens cannot be cleared.
     */
    suspend fun clearPushDeviceTokens()

    /**
     * Count the number of push notifications.
     *
     * @param credentialId Optional ID of a specific credential to count notifications for.
     * @return The count of push notifications.
     * @throws MfaStorageException if the count cannot be retrieved.
     */
    suspend fun countPushNotifications(credentialId: String? = null): Int

    /**
     * Retrieve the oldest push notifications.
     *
     * @param limit The maximum number of notifications to retrieve.
     * @param credentialId Optional ID of a specific credential to retrieve notifications for.
     * @return A list of the oldest push notifications.
     * @throws MfaStorageException if the notifications cannot be retrieved.
     */
    suspend fun getOldestPushNotifications(limit: Int, credentialId: String? = null): List<PushNotification>

    /**
     * Purge push notifications by age.
     *
     * @param maxAgeDays The maximum age in days for notifications to keep.
     * @param credentialId Optional ID of a specific credential to purge notifications for.
     * @return The number of notifications removed.
     * @throws MfaStorageException if the notifications cannot be purged.
     */
    suspend fun purgePushNotificationsByAge(maxAgeDays: Int, credentialId: String? = null): Int

    /**
     * Purge push notifications by count (removes oldest notifications when count exceeds the limit).
     *
     * @param maxCount The maximum number of notifications to keep.
     * @param credentialId Optional ID of a specific credential to purge notifications for.
     * @return The number of notifications removed.
     * @throws MfaStorageException if the notifications cannot be purged.
     */
    suspend fun purgePushNotificationsByCount(maxCount: Int, credentialId: String? = null): Int
}

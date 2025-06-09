/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.commons.storage

import com.pingidentity.mfa.commons.exception.MfaStorageException
import com.pingidentity.mfa.commons.model.MfaOathCredential
import com.pingidentity.mfa.commons.model.MfaPushCredential
import com.pingidentity.mfa.commons.model.MfaPushNotification
import com.pingidentity.mfa.commons.model.MfaPushDeviceToken

/**
 * Enum for credential types supported in MFA storage.
 */
enum class CredentialType {
    OATH,
    PUSH
}

/**
 * Interface for MFA storage operations.
 * Implementations of this interface handle the storage and retrieval of MFA-related data.
 */
interface MfaStorageClient {
    
    /**
     * Initialize the storage client.
     * This method must be called before any other method on the client.
     *
     * @throws MfaStorageException if initialization fails.
     */
    @Throws(MfaStorageException::class)
    fun initialize()
    
    //
    // OATH credential methods
    //
    
    /**
     * Store an OATH credential in the database.
     *
     * @param credential The OATH credential to be stored.
     * @throws MfaStorageException if the credential cannot be stored.
     */
    @Throws(MfaStorageException::class)
    fun storeOathCredential(credential: MfaOathCredential)
    
    /**
     * Retrieve an OATH credential by its ID.
     *
     * @param credentialId The ID of the credential to retrieve.
     * @return The OATH credential, or null if not found.
     * @throws MfaStorageException if the credential cannot be retrieved.
     */
    @Throws(MfaStorageException::class)
    fun retrieveOathCredential(credentialId: String): MfaOathCredential?
    
    /**
     * Get all OATH credentials.
     *
     * @return A list of all OATH credentials.
     * @throws MfaStorageException if the credentials cannot be retrieved.
     */
    @Throws(MfaStorageException::class)
    fun getAllOathCredentials(): List<MfaOathCredential>
    
    /**
     * Remove an OATH credential by its ID.
     *
     * @param credentialId The ID of the credential to remove.
     * @return true if the credential was successfully removed, false if it didn't exist.
     * @throws MfaStorageException if the credential cannot be removed.
     */
    @Throws(MfaStorageException::class)
    fun removeOathCredential(credentialId: String): Boolean
    
    /**
     * Clear all OATH credentials from the storage.
     *
     * @throws MfaStorageException if the credentials cannot be cleared.
     */
    @Throws(MfaStorageException::class)
    fun clearOathCredentials()
    
    //
    // Push credential methods
    //
    
    /**
     * Save a Push credential in the database.
     *
     * @param credential The Push credential to be saved.
     * @throws MfaStorageException if the credential cannot be saved.
     */
    @Throws(MfaStorageException::class)
    fun savePushCredential(credential: MfaPushCredential)
    
    /**
     * Get a Push credential by its ID.
     *
     * @param id The ID of the credential to retrieve.
     * @return The credential, or null if no credential exists with the given ID.
     * @throws MfaStorageException if the credential cannot be retrieved.
     */
    @Throws(MfaStorageException::class)
    fun getPushCredential(id: String): MfaPushCredential?
    
    /**
     * Update an existing Push credential.
     *
     * @param credential The credential to update.
     * @throws MfaStorageException if the credential cannot be updated.
     */
    @Throws(MfaStorageException::class)
    fun updatePushCredential(credential: MfaPushCredential)
    
    /**
     * Delete a Push credential by its ID.
     *
     * @param id The ID of the credential to delete.
     * @return true if the credential was successfully deleted, false if it didn't exist.
     * @throws MfaStorageException if the credential cannot be deleted.
     */
    @Throws(MfaStorageException::class)
    fun deletePushCredential(id: String): Boolean
    
    /**
     * Get all Push credentials.
     *
     * @return A list of all Push credentials.
     * @throws MfaStorageException if the credentials cannot be retrieved.
     */
    @Throws(MfaStorageException::class)
    fun getAllPushCredentials(): List<MfaPushCredential>
    
    /**
     * Save a Push notification in the database.
     *
     * @param notification The Push notification to be saved.
     * @throws MfaStorageException if the notification cannot be saved.
     */
    @Throws(MfaStorageException::class)
    fun savePushNotification(notification: MfaPushNotification)
    
    /**
     * Get a Push notification by its ID.
     *
     * @param id The ID of the notification to retrieve.
     * @return The notification, or null if no notification exists with the given ID.
     * @throws MfaStorageException if the notification cannot be retrieved.
     */
    @Throws(MfaStorageException::class)
    fun getPushNotification(id: String): MfaPushNotification?
    
    /**
     * Update an existing Push notification.
     *
     * @param notification The notification to update.
     * @throws MfaStorageException if the notification cannot be updated.
     */
    @Throws(MfaStorageException::class)
    fun updatePushNotification(notification: MfaPushNotification)
    
    /**
     * Delete a Push notification by its ID.
     *
     * @param id The ID of the notification to delete.
     * @return true if the notification was successfully deleted, false if it didn't exist.
     * @throws MfaStorageException if the notification cannot be deleted.
     */
    @Throws(MfaStorageException::class)
    fun deletePushNotification(id: String): Boolean
    
    /**
     * Get all Push notifications.
     *
     * @return A list of all Push notifications.
     * @throws MfaStorageException if the notifications cannot be retrieved.
     */
    @Throws(MfaStorageException::class)
    fun getAllPushNotifications(): List<MfaPushNotification>
    
    /**
     * Update a Push notification's status.
     *
     * @param id The ID of the notification to update.
     * @param approved Whether the notification has been approved.
     * @param pending Whether the notification is still pending or has been acted upon.
     * @return The updated notification, or null if no notification exists with the given ID.
     * @throws MfaStorageException if the notification cannot be updated.
     */
    @Throws(MfaStorageException::class)
    fun updatePushNotificationStatus(
        id: String,
        approved: Boolean,
        pending: Boolean
    ): MfaPushNotification?
    
    /**
     * Get all pending Push notifications.
     *
     * @return A list of all pending Push notifications.
     * @throws MfaStorageException if the notifications cannot be retrieved.
     */
    @Throws(MfaStorageException::class)
    fun getPendingPushNotifications(): List<MfaPushNotification>
    
    /**
     * Get all Push notifications for a specific credential.
     *
     * @param credentialId The ID of the credential to get notifications for.
     * @return A list of all Push notifications for the specified credential.
     * @throws MfaStorageException if the notifications cannot be retrieved.
     */
    @Throws(MfaStorageException::class)
    fun getPushNotificationsForCredential(credentialId: String): List<MfaPushNotification>
    
    /**
     * Save a Push device token.
     *
     * @param token The device token to save.
     * @throws MfaStorageException if the token cannot be saved.
     */
    @Throws(MfaStorageException::class)
    fun savePushDeviceToken(token: MfaPushDeviceToken)
    
    /**
     * Get the current Push device token.
     *
     * @return The most recently added device token, or null if no token exists.
     * @throws MfaStorageException if the token cannot be retrieved.
     */
    @Throws(MfaStorageException::class)
    fun getCurrentPushDeviceToken(): MfaPushDeviceToken?
    
    /**
     * Delete a Push device token.
     *
     * @param tokenId The ID of the token to delete.
     * @return true if the token was successfully deleted, false if it didn't exist.
     * @throws MfaStorageException if the token cannot be deleted.
     */
    @Throws(MfaStorageException::class)
    fun deletePushDeviceToken(tokenId: String): Boolean
    
    /**
     * Clear all Push device tokens.
     *
     * @throws MfaStorageException if the tokens cannot be cleared.
     */
    @Throws(MfaStorageException::class)
    fun clearPushDeviceTokens()
    
    /**
     * Get all Push device tokens.
     *
     * @return A list of all Push device tokens.
     * @throws MfaStorageException if the tokens cannot be retrieved.
     */
    @Throws(MfaStorageException::class)
    fun getAllPushDeviceTokens(): List<MfaPushDeviceToken>
    
    /**
     * Clear all Push credentials from the storage.
     *
     * @throws MfaStorageException if the credentials cannot be cleared.
     */
    @Throws(MfaStorageException::class)
    fun clearPushCredentials()
    
    /**
     * Clear all values from the storage for all credential types.
     *
     * @throws MfaStorageException if the database cannot be cleared.
     */
    @Throws(MfaStorageException::class)
    fun clear()
    
    /**
     * Close the storage client and release any resources.
     * This method should be called when the client is no longer needed.
     */
    fun close()
}

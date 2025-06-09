/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.push

import com.pingidentity.mfa.commons.MfaClient
import com.pingidentity.mfa.commons.exception.MfaException

/**
 * Interface for Push client functionality.
 * Extends the base MfaClient interface with Push-specific functionality.
 */
interface MfaPushClient : MfaClient {
    
    /**
     * Add a new PUSH Credential from a standard `pushauth://` or `mfauth://` URI (typically from a QR code).
     *
     * @param uri The URI string in the format pushauth://push/issuer:accountName?r=regEndpoint&a=authEndpoint&s=sharedSecret&d=userId
     * @return The created PushCredential.
     * @throws MfaException if the credential cannot be created.
     */
    @Throws(MfaException::class)
    fun addCredentialFromUri(uri: String): PushCredential
    
    /**
     * Add a new Push credential.
     *
     * @param credential The PushCredential to add.
     * @return The added PushCredential.
     * @throws MfaException if the credential cannot be added.
     */
    @Throws(MfaException::class)
    fun addCredential(credential: PushCredential): PushCredential
    
    /**
     * Get a push credential by ID.
     *
     * @param id The unique identifier of the credential.
     * @return The PushCredential if found, null otherwise.
     * @throws MfaException if the credential cannot be retrieved.
     */
    @Throws(MfaException::class)
    fun getCredential(id: String): PushCredential?
    
    /**
     * Get all push credentials stored.
     *
     * @return A list of all stored PushCredentials.
     * @throws MfaException if the credentials cannot be retrieved.
     */
    @Throws(MfaException::class)
    fun getAllCredentials(): List<PushCredential>
    
    /**
     * Update an existing push credential.
     *
     * @param credential The PushCredential to update.
     * @return The updated PushCredential.
     * @throws MfaException if the credential cannot be updated.
     */
    @Throws(MfaException::class)
    fun updateCredential(credential: PushCredential): PushCredential
    
    /**
     * Delete a push credential by ID.
     *
     * @param id The unique identifier of the credential to delete.
     * @return true if the credential was deleted, false otherwise.
     * @throws MfaException if the credential cannot be deleted.
     */
    @Throws(MfaException::class)
    fun deleteCredential(id: String): Boolean
    
    /**
     * Process a push notification message.
     *
     * @param messageData The data from the push notification message.
     * @return The PushNotification object if successfully processed, null otherwise.
     * @throws MfaException if the message cannot be processed.
     */
    @Throws(MfaException::class)
    fun processPushMessage(messageData: Map<String, String>): PushNotification?
    
    /**
     * Approve a push notification authentication request.
     *
     * @param notification The PushNotification to approve.
     * @return true if the approval was successful, false otherwise.
     * @throws com.pingidentity.mfa.commons.exception.MfaException if the approval fails.
     */
    @Throws(MfaException::class)
    suspend fun approveNotification(notification: PushNotification): Boolean
    
    /**
     * Deny a push notification authentication request.
     *
     * @param notification The PushNotification to deny.
     * @return true if the denial was successful, false otherwise.
     * @throws com.pingidentity.mfa.commons.exception.MfaException if the denial fails.
     */
    @Throws(MfaException::class)
    suspend fun denyNotification(notification: PushNotification): Boolean
    
    /**
     * Get all pending push notifications.
     *
     * @return A list of all pending PushNotifications.
     * @throws MfaException if the notifications cannot be retrieved.
     */
    @Throws(MfaException::class)
    fun getPendingNotifications(): List<PushNotification>
    
    /**
     * Update the FCM device token. If the token has changed, it will be stored locally and updated on the server
     * for all registered push credentials.
     *
     * @param token The new FCM device token
     * @return True if the token was updated, false otherwise
     * @throws com.pingidentity.mfa.commons.exception.MfaException if the token update fails
     */
    @Throws(MfaException::class)
    fun updateDeviceToken(token: String): Boolean
}

/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.push

import com.google.firebase.messaging.RemoteMessage
import com.pingidentity.mfa.commons.MfaClient

/**
 * Interface for Push client functionality.
 * Extends the base MfaClient interface with Push-specific functionality.
 */
interface PushMfaClient : MfaClient {
    
    /**
     * Creates a Push Credential from a standard pushauth:// URI (typically from a QR code).
     *
     * @param uri The URI string in the format pushauth://push/issuer:accountName?params...
     * @return A Result containing the created PushCredential or an Exception in case of failure.
     */
    suspend fun addCredentialFromUri(uri: String): Result<PushCredential>

    /**
     * Save a Push credential.
     *
     * @param credential The PushCredential to save.
     * @return A Result containing the saved PushCredential or an Exception in case of failure.
     */
    suspend fun saveCredential(credential: PushCredential): Result<PushCredential>

    /**
     * Get all Push credentials.
     *
     * @return A Result containing a list of all PushCredentials or an Exception in case of failure.
     */
    suspend fun getCredentials(): Result<List<PushCredential>>

    /**
     * Get a Push credential by ID.
     *
     * @param credentialId The ID of the credential to get.
     * @return A Result containing the PushCredential (or null if not found) or an Exception in case of failure.
     */
    suspend fun getCredential(credentialId: String): Result<PushCredential?>

    /**
     * Delete a Push credential by ID.
     *
     * @param credentialId The ID of the credential to remove.
     * @return A Result containing a Boolean indicating success or an Exception in case of failure.
     */
    suspend fun deleteCredential(credentialId: String): Result<Boolean>

    /**
     * Set the device token for push notifications.
     * This method should be called on initial registration and when the device token changes.
     *
     * @param deviceToken The new device token for push notifications.
     * @param credentialId Optional ID of a specific credential to update the token for.
     *                    When null, updates the token globally for all credentials.
     * @return A Result containing a Boolean indicating success or an Exception in case of failure.
     */
    suspend fun setDeviceToken(deviceToken: String, credentialId: String? = null): Result<Boolean>

    /**
     * Process a push notification message.
     * This method parses the message data and creates a PushNotification object.
     *
     * @param messageData The message data as a Map of String to Any, as typically received from Firebase.
     * @return A Result containing the PushNotification object (or null if message is invalid) or an Exception in case of failure.
     */
    suspend fun processNotification(messageData: Map<String, Any>): Result<PushNotification?>
    
    /**
     * Process a push notification message received as a string.
     * This method parses the string message data (typically a JWT) and creates a PushNotification object.
     *
     * @param message The message data as a String.
     * @return A Result containing the PushNotification object (or null if message is invalid) or an Exception in case of failure.
     */
    suspend fun processNotification(message: String): Result<PushNotification?>
    
    /**
     * Process a push notification message from Firebase Cloud Messaging.
     * This method extracts data from the RemoteMessage and creates a PushNotification object.
     *
     * @param remoteMessage The Firebase RemoteMessage object.
     * @return A Result containing the PushNotification object (or null if message is invalid) or an Exception in case of failure.
     */
    suspend fun processNotification(remoteMessage: RemoteMessage): Result<PushNotification?>

    /**
     * Approve a push notification.
     * This method approves the authentication request for the given notification.
     *
     * @param notificationId The ID of the notification to approve.
     * @return A Result containing a Boolean indicating success or an Exception in case of failure.
     */
    suspend fun approveNotification(notificationId: String): Result<Boolean>
    
    /**
     * Approve a challenge-based push notification.
     * This method approves the authentication request for the given challenge notification
     * with the provided challenge response.
     *
     * @param notificationId The ID of the notification to approve.
     * @param challengeResponse The challenge response provided by the user.
     * @return A Result containing a Boolean indicating success or an Exception in case of failure.
     */
    suspend fun approveChallengeNotification(notificationId: String, challengeResponse: String): Result<Boolean>
    
    /**
     * Approve a biometric push notification.
     * This method approves the authentication request for the given biometric notification
     * with the provided authentication method.
     *
     * @param notificationId The ID of the notification to approve.
     * @param authenticationMethod The authentication method used (e.g., "face", "fingerprint").
     * @return A Result containing a Boolean indicating success or an Exception in case of failure.
     */
    suspend fun approveBiometricNotification(notificationId: String, authenticationMethod: String): Result<Boolean>
    
    /**
     * Deny a push notification.
     * This method denies the authentication request for the given notification.
     *
     * @param notificationId The ID of the notification to deny.
     * @return A Result containing a Boolean indicating success or an Exception in case of failure.
     */
    suspend fun denyNotification(notificationId: String): Result<Boolean>
    
    /**
     * Get all pending push notifications.
     * This method returns all notifications that have not been approved or denied.
     *
     * @return A Result containing a list of all pending PushNotifications or an Exception in case of failure.
     */
    suspend fun getPendingNotifications(): Result<List<PushNotification>>

    /**
     * Get all push notifications.
     * This method returns all stored push notifications, regardless of their status.
     *
     * @return A Result containing a list of all PushNotifications or an Exception in case of failure.
     */
    suspend fun getAllNotifications(): Result<List<PushNotification>>

    /**
     * Get a push notification by ID.
     *
     * @param notificationId The ID of the notification to get.
     * @return A Result containing the PushNotification (or null if not found) or an Exception in case of failure.
     */
    suspend fun getNotification(notificationId: String): Result<PushNotification?>

    /**
     * Manually trigger notification cleanup based on the configured cleanup mode.
     * This method can be used to clean up notifications outside of the automatic cleanup process.
     *
     * @param credentialId Optional ID of a specific credential to clean up notifications for.
     * @return A Result containing the number of notifications removed during cleanup or an Exception in case of failure.
     */
    suspend fun cleanupNotifications(credentialId: String? = null): Result<Int>
}

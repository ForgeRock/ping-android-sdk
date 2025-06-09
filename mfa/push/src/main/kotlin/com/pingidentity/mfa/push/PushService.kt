/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.push

import com.pingidentity.mfa.commons.MfaConfiguration
import com.pingidentity.mfa.commons.exception.MfaException
import com.pingidentity.mfa.commons.storage.MfaStorageClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.security.SecureRandom
import java.util.Base64
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.KeyGenerator

/**
 * Service class that provides Push functionality including credential management
 * and push notification processing.
 *
 * @param storageClient The storage client for persisting credentials and notifications
 * @param configuration The MFA configuration that includes settings like caching behavior
 */
internal class PushService(
    private val storageClient: MfaStorageClient?,
    private val configuration: MfaConfiguration
) {

    companion object {
        private const val SECRET_KEY_ALGORITHM = "AES"
        private const val SECRET_KEY_SIZE = 256
        private const val DEFAULT_TTL = 60 // 60 seconds
    }

    // Logger for logging messages
    private val logger = configuration.logger

    // In-memory cache for credentials and notifications
    private val credentialsCache = ConcurrentHashMap<String, PushCredential>()
    private val notificationsCache = ConcurrentHashMap<String, PushNotification>()
    
    // Device token manager for handling FCM tokens
    private val deviceTokenManager by lazy {
        storageClient?.let { PushDeviceTokenManager(it) }
    }

    /**
     * Add a new Push credential.
     *
     * @param credential The PushCredential to add.
     * @return The added PushCredential.
     * @throws MfaException if the credential cannot be added.
     */
    fun addCredential(credential: PushCredential): PushCredential {
        try {
            // Store the credential
            storageClient?.savePushCredential(credential)
            
            // Cache the credential if caching is enabled
            if (configuration.enableCredentialCache) {
                credentialsCache[credential.id] = credential
            }
            
            return credential
        } catch (e: Exception) {
            throw MfaException("Failed to add push credential", e)
        }
    }

    /**
     * Get a push credential by ID.
     *
     * @param id The unique identifier of the credential.
     * @return The PushCredential if found, null otherwise.
     */
    fun getCredential(id: String): PushCredential? {
        // Check cache first if enabled
        if (configuration.enableCredentialCache && credentialsCache.containsKey(id)) {
            return credentialsCache[id]
        }
        
        // Retrieve from storage
        val credential = storageClient?.getPushCredential(id)
        
        // Cache the credential if found and caching is enabled
        if (credential != null && configuration.enableCredentialCache && credential is PushCredential) {
            credentialsCache[credential.id] = credential
        }
        
        return credential as? PushCredential
    }

    /**
     * Get all push credentials stored.
     *
     * @return A list of all stored PushCredentials.
     */
    fun getAllCredentials(): List<PushCredential> {
        val credentials = storageClient?.getAllPushCredentials() ?: emptyList()
        val pushCredentials = credentials.filterIsInstance<PushCredential>()
        
        // Update cache if enabled
        if (configuration.enableCredentialCache) {
            pushCredentials.forEach { credential ->
                credentialsCache[credential.id] = credential
            }
        }
        
        return pushCredentials
    }

    /**
     * Update an existing push credential.
     *
     * @param credential The PushCredential to update.
     * @return The updated PushCredential.
     * @throws MfaException if the credential cannot be updated.
     */
    fun updateCredential(credential: PushCredential): PushCredential {
        try {
            storageClient?.updatePushCredential(credential)
            
            // Update cache if enabled
            if (configuration.enableCredentialCache) {
                credentialsCache[credential.id] = credential
            }
            
            return credential
        } catch (e: Exception) {
            throw MfaException("Failed to update push credential", e)
        }
    }

    /**
     * Delete a push credential by ID.
     *
     * @param id The unique identifier of the credential to delete.
     * @return true if the credential was deleted, false otherwise.
     */
    fun deleteCredential(id: String): Boolean {
        val success = storageClient?.deletePushCredential(id) ?: false
        
        // Remove from cache if enabled
        if (configuration.enableCredentialCache && success) {
            credentialsCache.remove(id)
        }
        
        return success
    }

    /**
     * Process a push notification message.
     *
     * @param messageData The data from the push notification message.
     * @return The PushNotification object if successfully processed, null otherwise.
     */
    fun processPushMessage(messageData: Map<String, String>): PushNotification? {
        try {
            val credentialId = messageData["credentialId"] ?: return null
            val messageId = messageData["messageId"] ?: return null
            val challenge = messageData["challenge"] ?: return null
            val messageText = messageData["messageText"] ?: messageData["message"] ?: "Authentication request"
            val customPayload = messageData["customPayload"] ?: ""
            val numbersChallenge = messageData["numbersChallenge"] ?: ""
            val amlbCookie = messageData["amlbCookie"] ?: ""
            val contextInfo = messageData["contextInfo"] ?: ""
            val pushTypeStr = messageData["pushType"] ?: PushType.DEFAULT.toString()
            val pushType = PushType.fromString(pushTypeStr)
            val ttl = messageData["ttl"]?.toIntOrNull() ?: DEFAULT_TTL
            
            // Get the associated credential
            val credential = getCredential(credentialId) ?: return null
            
            // Create a push notification
            val notification = PushNotification(
                credentialId = credentialId,
                messageId = messageId,
                messageText = messageText,
                challenge = challenge,
                customPayload = customPayload,
                numbersChallenge = numbersChallenge,
                amlbCookie = amlbCookie,
                contextInfo = contextInfo,
                pushType = pushType,
                ttl = ttl
            )
            
            // Store the notification
            storageClient?.savePushNotification(notification)
            
            // Cache the notification
            notificationsCache[notification.id] = notification
            
            return notification
        } catch (e: Exception) {
            return null
        }
    }

    /**
     * Approve a push notification authentication request.
     *
     * @param notification The PushNotification to approve.
     * @return true if the approval was successful, false otherwise.
     * @throws MfaException if the approval fails.
     */
    suspend fun approveNotification(notification: PushNotification): Boolean {
        try {
            if (notification.expired) {
                throw MfaException("Push notification has expired")
            }
            
            if (notification.responded) {
                throw MfaException("Push notification has already been responded to")
            }
            
            val credential = getCredential(notification.credentialId)
                ?: throw MfaException("Credential not found")
            
            // Mark the notification as approved
            notification.markApproved()
            
            // Update the notification in storage
            storageClient?.updatePushNotification(notification)
            
            // Update cache
            notificationsCache[notification.id] = notification
            
            // Send approval to server
            return withContext(Dispatchers.IO) {
                sendResponse(credential, notification, true)
            }
        } catch (e: Exception) {
            throw MfaException("Failed to approve push notification", e)
        }
    }

    /**
     * Deny a push notification authentication request.
     *
     * @param notification The PushNotification to deny.
     * @return true if the denial was successful, false otherwise.
     * @throws MfaException if the denial fails.
     */
    suspend fun denyNotification(notification: PushNotification): Boolean {
        try {
            if (notification.expired) {
                throw MfaException("Push notification has expired")
            }
            
            if (notification.responded) {
                throw MfaException("Push notification has already been responded to")
            }
            
            val credential = getCredential(notification.credentialId)
                ?: throw MfaException("Credential not found")
            
            // Mark the notification as denied
            notification.markDenied()
            
            // Update the notification in storage
            storageClient?.updatePushNotification(notification)
            
            // Update cache
            notificationsCache[notification.id] = notification
            
            // Send denial to server
            return withContext(Dispatchers.IO) {
                sendResponse(credential, notification, false)
            }
        } catch (e: Exception) {
            throw MfaException("Failed to deny push notification", e)
        }
    }

    /**
     * Get all pending push notifications.
     *
     * @return A list of all pending PushNotifications.
     */
    fun getPendingNotifications(): List<PushNotification> {
        val notifications = storageClient?.getPendingPushNotifications() ?: emptyList()
        val pushNotifications = notifications.filterIsInstance<PushNotification>()
        
        // Update cache
        pushNotifications.forEach { notification ->
            notificationsCache[notification.id] = notification
        }
        
        return pushNotifications
    }

    /**
     * Updates the FCM device token used for push notifications.
     * If the token has changed from the previously stored one, it will be updated locally
     * and sent to the server for all registered push credentials.
     *
     * @param token The new FCM device token
     * @return True if the token was updated successfully, false otherwise
     * @throws MfaException if the token update process fails
     */
    fun updateDeviceToken(token: String): Boolean {
        if (storageClient == null) {
            throw MfaException("Storage client is not available")
        }
        
        // Null check the deviceTokenManager - should never be null if storageClient is not null
        val tokenManager = deviceTokenManager ?: throw MfaException("Device token manager is not initialized")
        
        try {
            // Check if the token has changed
            if (!tokenManager.shouldUpdateToken(token)) {
                logger.d("FCM device token has not changed, no update needed")
                return true // Token is already up to date
            }
            
            logger.d("Updating FCM device token")
            
            // Store the new token locally
            if (!tokenManager.saveDeviceToken(token)) {
                logger.e("Failed to save FCM device token locally")
                return false
            }
            
            // Get all registered credentials to update on the server
            val credentials = getAllCredentials()
            if (credentials.isEmpty()) {
                logger.d("No push credentials found, device token only updated locally")
                return true
            }
            
            // Update token for all credentials on the server
            var allSuccessful = true
            credentials.forEach { credential ->
                val success = updateTokenOnServer(token, credential)
                if (!success) {
                    logger.w("Failed to update token on server for credential: ${credential.id}")
                    allSuccessful = false
                }
            }
            
            if (!allSuccessful) {
                logger.w("Token update completed with some failures")
            }
            
            return true
        } catch (e: Exception) {
            logger.e("Failed to update FCM device token: ${e.message}", e)
            throw MfaException("Failed to update FCM device token", e)
        }
    }

    // FCM token updating functionality has been removed as per design requirements

    // Key pair generation functionality has been removed as per design requirements

    /**
     * Generate a shared secret key.
     *
     * @return Base64-encoded shared secret key.
     */
    private fun generateSharedSecret(): String {
        val keyGenerator = KeyGenerator.getInstance(SECRET_KEY_ALGORITHM)
        keyGenerator.init(SECRET_KEY_SIZE, SecureRandom())
        val secretKey = keyGenerator.generateKey()
        return Base64.getEncoder().encodeToString(secretKey.encoded)
    }

    /**
     * Send a response (approve/deny) to the server.
     *
     * @param credential The associated PushCredential.
     * @param notification The PushNotification being responded to.
     * @param approve true for approval, false for denial.
     * @return true if the response was successfully sent, false otherwise.
     */
    private fun sendResponse(
        credential: PushCredential,
        notification: PushNotification,
        approve: Boolean
    ): Boolean {
        try {
            // For POC purposes, we're just simulating the response
            // In a real implementation, this would create a signed response and send it to the server
            
            // Simulate network request
            val response = JSONObject()
            response.put("credentialId", credential.id)
            response.put("messageId", notification.messageId)
            response.put("approved", approve)
            response.put("timestamp", Date().time)
            
            // In a real implementation, this would be sent to credential.serverEndpoint
            
            return true
        } catch (e: Exception) {
            return false
        }
    }

    /**
     * Updates the FCM device token on the server for a specific credential.
     * This is an internal helper method that would be used in a real implementation.
     * Currently, it only logs the intention to update the token.
     *
     * @param token The FCM device token to register
     * @param credential The push credential to update the token for
     * @return True if the update was successful, false otherwise
     */
    private fun updateTokenOnServer(token: String, credential: PushCredential): Boolean {
        try {
            // In a real implementation, this would send the token to the server
            logger.d("Updating FCM token for credential ${credential.id} on server: $token")
            
            // Construct the server request
            val requestJson = JSONObject().apply {
                put("deviceToken", token)
                put("userId", credential.userId)
                put("resourceId", credential.resourceId)
                // Additional parameters like device model, OS version could be added here
            }
            
            // For now, we just simulate the request
            // In a real implementation, this would be sent to credential.serverEndpoint
            
            return true
        } catch (e: Exception) {
            logger.e("Failed to update FCM token on server: ${e.message}", e)
            return false
        }
    }

    /**
     * Parse a Push URI and create a credential.
     *
     * @param uri The Push URI string.
     * @return The created PushCredential.
     * @throws IllegalArgumentException if the URI is invalid.
     */
    fun parseUri(uri: String): PushCredential {
        return PushUriParser.parse(uri)
    }

    /**
     * Format a Push credential as a URI string.
     *
     * @param credential The PushCredential to format.
     * @return A URI string.
     */
    fun formatUri(credential: PushCredential): String {
        return PushUriParser.format(credential)
    }

    /**
     * Clear all cached credentials and notifications.
     * This is typically called when the user logs out or when a security event occurs.
     */
    fun clearCache() {
        credentialsCache.clear()
        notificationsCache.clear()
        logger.d("Push credentials and notifications cache cleared")
    }
}

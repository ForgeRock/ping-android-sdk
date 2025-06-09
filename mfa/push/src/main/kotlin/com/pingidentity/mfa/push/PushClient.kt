/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.push

import com.pingidentity.mfa.commons.BaseMfaClient
import com.pingidentity.mfa.commons.MfaConfiguration
import com.pingidentity.mfa.commons.exception.MfaException
import com.pingidentity.mfa.commons.exception.MfaInitializationException

/**
 * Implementation of PushClient that provides Push MFA functionality.
 * This client handles push credential management and push notification processing.
 *
 * @param configuration The MFA configuration.
 */
class PushClient(
    configuration: MfaConfiguration
) : BaseMfaClient(configuration), MfaPushClient {

    companion object {
        /**
         * Create a PushClient instance with default configuration.
         *
         * @return A PushClient instance (not initialized).
         */
        @JvmStatic
        fun create(): MfaPushClient {
            // Create configuration
            val configuration = MfaConfiguration.Builder().build()
            
            // Create and initialize client
            val client = PushClient(configuration)
            
            return client
        }
        
        /**
         * Create a PushClient instance with the specified configuration.
         *
         * @param configuration The configuration for the client.
         * @return A PushClient instance (not initialized).
         */
        @JvmStatic
        fun create(configuration: MfaConfiguration): MfaPushClient {
            return PushClient(configuration)
        }
        
        /**
         * Create a PushClient instance with a customizable configuration block.
         * This allows for a more fluent, DSL-style configuration approach.
         * 
         * @param block The configuration block to customize the MFA configuration.
         * @return An initialized PushClient instance.
         * 
         * @sample
         * ```
         * val pushClient = PushClient {
         *     enableCredentialCache = true
         *     timeoutMs = 60000
         *     encryptionEnabled = false
         *     logger = CustomLogger()
         * }
         * ```
         */
        @JvmStatic
        operator fun invoke(block: MfaConfiguration.Builder.() -> Unit = {}): MfaPushClient {
            // Create configuration builder
            val builder = MfaConfiguration.Builder()
            
            // Apply custom configuration
            builder.apply(block)
            
            // Build the final configuration
            val configuration = builder.build()
            
            // Create and initialize client
            val client = PushClient(configuration)
            client.initialize()
            
            return client
        }
    }

    private lateinit var pushService: PushService

    /**
     * Initialize the Push client.
     * This is called by BaseMfaClient after the storage client is initialized.
     *
     * @throws MfaInitializationException if an error occurs during initialization.
     */
    @Throws(MfaInitializationException::class)
    override fun initializeClient() {
        try {
            // Create the Push service with the MFA configuration
            pushService = PushService(storageClient, config)

            // Initialize the HttpClient
            super.initHttp(config.timeoutMs)

            logger.i("Push client initialized successfully")
        } catch (e: Exception) {
            logger.e("Failed to initialize Push client: ${e.message}", e)
            throw MfaInitializationException("Failed to initialize Push client", e)
        }
    }

    /**
     * Add a new Push credential.
     *
     * @param credential The PushCredential to add.
     * @return The added PushCredential.
     * @throws MfaException if the credential cannot be added.
     */
    @Throws(MfaException::class)
    override fun addCredential(credential: PushCredential): PushCredential {
        checkInitialized()
        try {
            // Use pushService to add and store the credential properly
            return pushService.addCredential(credential)
        } catch (e: Exception) {
            logger.e("Failed to add push credential: ${e.message}", e)
            throw MfaException("Failed to add push credential", e)
        }
    }

    /**
     * Get a push credential by ID.
     *
     * @param id The unique identifier of the credential.
     * @return The PushCredential if found, null otherwise.
     * @throws MfaException if the credential cannot be retrieved.
     */
    @Throws(MfaException::class)
    override fun getCredential(id: String): PushCredential? {
        checkInitialized()
        try {
            return pushService.getCredential(id)
        } catch (e: Exception) {
            logger.e("Failed to get credential: ${e.message}", e)
            throw MfaException("Failed to get credential", e)
        }
    }

    /**
     * Get all push credentials stored.
     *
     * @return A list of all stored PushCredentials.
     * @throws MfaException if the credentials cannot be retrieved.
     */
    @Throws(MfaException::class)
    override fun getAllCredentials(): List<PushCredential> {
        checkInitialized()
        try {
            return pushService.getAllCredentials()
        } catch (e: Exception) {
            logger.e("Failed to get all credentials: ${e.message}", e)
            throw MfaException("Failed to get all credentials", e)
        }
    }

    /**
     * Update an existing push credential.
     *
     * @param credential The PushCredential to update.
     * @return The updated PushCredential.
     * @throws MfaException if the credential cannot be updated.
     */
    @Throws(MfaException::class)
    override fun updateCredential(credential: PushCredential): PushCredential {
        checkInitialized()
        try {
            return pushService.updateCredential(credential)
        } catch (e: Exception) {
            logger.e("Failed to update credential: ${e.message}", e)
            throw MfaException("Failed to update credential", e)
        }
    }

    /**
     * Delete a push credential by ID.
     *
     * @param id The unique identifier of the credential to delete.
     * @return true if the credential was deleted, false otherwise.
     * @throws MfaException if the credential cannot be deleted.
     */
    @Throws(MfaException::class)
    override fun deleteCredential(id: String): Boolean {
        checkInitialized()
        try {
            return pushService.deleteCredential(id)
        } catch (e: Exception) {
            logger.e("Failed to delete credential: ${e.message}", e)
            throw MfaException("Failed to delete credential", e)
        }
    }

    /**
     * Process a push notification message.
     *
     * @param messageData The data from the push notification message.
     * @return The PushNotification object if successfully processed, null otherwise.
     * @throws MfaException if the message cannot be processed.
     */
    @Throws(MfaException::class)
    override fun processPushMessage(messageData: Map<String, String>): PushNotification? {
        checkInitialized()
        try {
            return pushService.processPushMessage(messageData)
        } catch (e: Exception) {
            logger.e("Failed to process push message: ${e.message}", e)
            throw MfaException("Failed to process push message", e)
        }
    }

    /**
     * Approve a push notification authentication request.
     *
     * @param notification The PushNotification to approve.
     * @return true if the approval was successful, false otherwise.
     * @throws MfaException if the approval fails.
     */
    @Throws(MfaException::class)
    override suspend fun approveNotification(notification: PushNotification): Boolean {
        checkInitialized()
        try {
            return pushService.approveNotification(notification)
        } catch (e: Exception) {
            logger.e("Failed to approve notification: ${e.message}", e)
            throw MfaException("Failed to approve notification", e)
        }
    }

    /**
     * Deny a push notification authentication request.
     *
     * @param notification The PushNotification to deny.
     * @return true if the denial was successful, false otherwise.
     * @throws MfaException if the denial fails.
     */
    @Throws(MfaException::class)
    override suspend fun denyNotification(notification: PushNotification): Boolean {
        checkInitialized()
        try {
            return pushService.denyNotification(notification)
        } catch (e: Exception) {
            logger.e("Failed to deny notification: ${e.message}", e)
            throw MfaException("Failed to deny notification", e)
        }
    }

    /**
     * Get all pending push notifications.
     *
     * @return A list of all pending PushNotifications.
     * @throws MfaException if the notifications cannot be retrieved.
     */
    @Throws(MfaException::class)
    override fun getPendingNotifications(): List<PushNotification> {
        checkInitialized()
        try {
            return pushService.getPendingNotifications()
        } catch (e: Exception) {
            logger.e("Failed to get pending notifications: ${e.message}", e)
            throw MfaException("Failed to get pending notifications", e)
        }
    }

    /**
     * Update the FCM device token. If the token has changed, it will be stored locally and updated on the server
     * for all registered push credentials.
     *
     * @param token The new FCM device token
     * @return True if the token was updated, false otherwise
     * @throws MfaException if the token update fails
     */
    @Throws(MfaException::class)
    override fun updateDeviceToken(token: String): Boolean {
        checkInitialized()
        try {
            return pushService.updateDeviceToken(token)
        } catch (e: Exception) {
            logger.e("Failed to update device token: ${e.message}", e)
            throw MfaException("Failed to update device token", e)
        }
    }

    /**
     * Add a new Push credential from a URI.
     *
     * @param uri The URI string in the format pushauth://push/issuer:accountName?r=regEndpoint&a=authEndpoint&s=sharedSecret&d=userId
     * @return The created PushCredential.
     * @throws MfaException if the credential cannot be created.
     */
    @Throws(MfaException::class)
    override fun addCredentialFromUri(uri: String): PushCredential {
        checkInitialized()
        try {
            // Parse the URI into a credential
            val credential = pushService.parseUri(uri)
            
            // Add the credential using the addCredential method
            return addCredential(credential)
        } catch (e: Exception) {
            logger.e("Failed to add credential from URI: ${e.message}", e)
            throw MfaException("Failed to add credential from URI", e)
        }
    }

    /**
     * Clean up resources used by the Push client.
     * This method clears caches and then calls the parent close method.
     */
    override fun close() {
        if (isInitialized) {
            try {
                // First clear the service cache
                pushService.clearCache()
                logger.d("Push client cache cleared")
            } catch (e: Exception) {
                // Just log any errors during cache clearing, but continue with closing
                logger.w("Error clearing Push client cache: ${e.message}", e)
            }
        }
        // Call parent close method to handle the rest of cleanup
        super.close()
    }
}

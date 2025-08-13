/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.push

import com.pingidentity.mfa.commons.BaseMfaClient
import com.pingidentity.mfa.commons.exception.MfaInitializationException
import com.pingidentity.mfa.push.storage.SQLPushStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * Implementation of PushClient that provides Push notification functionality.
 * This client handles Push credential management and notification handling.
 *
 * @param configuration The Push configuration.
 * @param storage The PushStorage implementation to use. If null, a default SQLPushStorage will be created.
 */
class PushClient(
    configuration: PushConfiguration,
    storage: PushStorage? = null
) : BaseMfaClient(configuration, storage), PushMfaClient {

    // The storage used for persisting push credentials and notifications
    private lateinit var pushStorage: PushStorage
    
    // The service that handles push operations
    private lateinit var pushService: PushService

    // The manager that handles notification cleanup
    private lateinit var cleanupManager: NotificationCleanupManager

    companion object {
        /**
         * Create a PushClient instance with a customizable configuration block.
         * This allows for a more fluent, DSL-style configuration approach.
         * 
         * @param block The configuration block to customize the client configuration.
         * @return An initialized PushClient instance.
         * 
         * @sample
         * ```
         * val pushClient = PushClient {
         *     encryptionEnabled = true
         *     timeoutMs = 60000
         *     logger = CustomLogger()
         *     storage = SQLPushStorage()
         *     // Add custom push handlers
         *     addPushHandler("CUSTOM_PLATFORM", CustomPushHandler())
         * }
         * ```
         */
        @JvmStatic
        suspend operator fun invoke(block: PushConfiguration.Builder.() -> Unit = {}): PushMfaClient {
            // Create configuration
            val configuration = PushConfiguration(block)

            // Create storage if not provided
            val builder = PushConfiguration.Builder().apply(block)
            val storage = builder.storage ?: createDefaultStorage(configuration)
            
            val client = PushClient(configuration, storage)
            client.initialize()
            
            return client
        }
        
        /**
         * Create a PushClient instance with the default configuration.
         * The client is not initialized automatically and requires a call to initialize() before use.
         *
         * @return An uninitialized PushClient instance.
         */
        @JvmStatic
        fun create(): PushMfaClient {
            // Use default configuration
            val config = PushConfiguration.default()

            // Create default storage with appropriate configuration
            val storage = createDefaultStorage(config)
            
            // Return uninitialized client
            return PushClient(config, storage)
        }

        /**
         * Create a PushClient instance with a specific configuration.
         * The client is not initialized automatically and requires a call to initialize() before use.
         *
         * @param configuration The PushConfiguration to use.
         * @param storage Optional custom storage implementation.
         * @return An uninitialized PushClient instance.
         */
        @JvmStatic
        fun create(configuration: PushConfiguration, storage: PushStorage? = null): PushMfaClient {
            // Create default storage if needed
            val actualStorage = storage ?: createDefaultStorage(configuration)

            // Return uninitialized client
            return PushClient(configuration, actualStorage)
        }

        /**
         * Creates a default PushStorage implementation with the appropriate configuration.
         *
         * @param config The Push configuration to use for the storage.
         * @return A configured PushStorage implementation.
         */
        @JvmStatic
        private fun createDefaultStorage(config: PushConfiguration): PushStorage {
            return SQLPushStorage {
                encryptionEnabled = config.encryptionEnabled
            }
        }
    }
    
    /**
     * Initialize the Push client.
     *
     * @throws MfaInitializationException if the client cannot be initialized.
     */
    override suspend fun initializeClient() = withContext(Dispatchers.IO) {
        try {
            // If storage is not a PushStorage implementation, throw an exception
            if (storage !is PushStorage) {
                throw MfaInitializationException("Storage must implement PushStorage")
            }
            
            // Initialize the PushStorage
            pushStorage = storage as PushStorage
            
            // Initialize the HttpClient first
            super.initHttp(config.timeoutMs)
            
            // Create the Push service and set up with the HttpClient
            pushService = PushService(pushStorage, config as PushConfiguration, httpClient)

            // Initialize the notification cleanup manager
            cleanupManager = NotificationCleanupManager(
                pushStorage,
                (config as PushConfiguration).notificationCleanupConfig,
                logger
            )

            logger.d("Push client initialized successfully")
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            logger.e("Failed to initialize Push client: ${e.message}", e)
            throw MfaInitializationException("Failed to initialize Push client", e)
        }
    }

    /**
     * Creates a Push Credential from a standard pushauth:// URI (typically from a QR code).
     * This method is used by PingAM to register devices for push notifications.
     *
     * @param uri The URI string in the format pushauth://push/issuer:accountName?params...
     * @return A Result containing the created PushCredential or an Exception in case of failure.
     */
    override suspend fun addCredentialFromUri(uri: String): Result<PushCredential> {
        checkInitialized()
        return try {
            Result.success(pushService.addCredentialFromUri(uri))
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            logger.e("Failed to add credential from URI: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Save a Push credential.
     *
     * @param credential The PushCredential to save.
     * @return A Result containing the saved PushCredential or an Exception in case of failure.
     */
    override suspend fun saveCredential(credential: PushCredential): Result<PushCredential> {
        checkInitialized()
        return try {
            Result.success(pushService.addCredential(credential))
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            logger.e("Failed to save credential: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Get all Push credentials.
     *
     * @return A Result containing a list of all PushCredentials or an Exception in case of failure.
     */
    override suspend fun getCredentials(): Result<List<PushCredential>> {
        checkInitialized()
        return try {
            Result.success(pushService.getCredentials())
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            logger.e("Failed to get credentials: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Get a Push credential by ID.
     *
     * @param credentialId The ID of the credential to get.
     * @return A Result containing the PushCredential (or null if not found) or an Exception in case of failure.
     */
    override suspend fun getCredential(credentialId: String): Result<PushCredential?> {
        checkInitialized()
        return try {
            Result.success(pushService.getCredential(credentialId))
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            logger.e("Failed to get credential: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Delete a Push credential by ID.
     * This method removes the credential from local storage but does not delete it from the server.
     *
     * @param credentialId The ID of the credential to remove.
     * @return A Result containing a Boolean indicating success or an Exception in case of failure.
     */
    override suspend fun deleteCredential(credentialId: String): Result<Boolean> {
        checkInitialized()
        return try {
            Result.success(pushService.removeCredential(credentialId))
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            logger.e("Failed to delete credential: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Set the device token for push notifications.
     * This method should be called when the device token changes. It updates the token locally
     * and on the server using the specific PushHandler for the credential's platform.
     * If it fails to update the token locally, the method will not attempt to update it on the server.
     *
     * @param deviceToken The new device token for push notifications.
     * @param credentialId Optional ID of a specific credential to update the token for.
     *                    When null, updates the token globally for all credentials.
     * @return A Result containing a Boolean indicating success or an Exception in case of failure.
     */
    override suspend fun setDeviceToken(deviceToken: String, credentialId: String?): Result<Boolean> {
        checkInitialized()
        return try {
            val success = pushService.updateDeviceToken(deviceToken, credentialId)

            if (success) {
                if (credentialId != null) {
                    logger.d("Device token updated successfully for credential $credentialId")
                } else {
                    logger.d("Device token updated successfully for all credentials")
                }
            }
            Result.success(success)
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            if (credentialId != null) {
                logger.e("Failed to update device token for credential $credentialId: ${e.message}", e)
            } else {
                logger.e("Failed to update device token for all credentials: ${e.message}", e)
            }
            Result.failure(e)
        }
    }
    
    /**
     * Get the current device token.
     *
     * @return A Result containing the current device token (or null if not set) or an Exception in case of failure.
     */
    suspend fun getDeviceToken(): Result<String?> {
        checkInitialized()
        return try {
            Result.success(pushService.getDeviceToken())
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            logger.e("Failed to get device token: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Process a push notification message.
     * This method parses the message data and creates a PushNotification object.
     *
     * @param messageData The message data as a Map of String to Any.
     * @return A Result containing the PushNotification object (or null if message is invalid) or an Exception in case of failure.
     */
    override suspend fun processNotification(messageData: Map<String, Any>): Result<PushNotification?> {
        checkInitialized()
        return try {
            val notification = pushService.processNotification(messageData)

            // Run auto-cleanup after successfully processing a new notification
            if (notification != null) {
                runAutoCleanup(notification.credentialId)
            }

            Result.success(notification)
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            logger.e("Failed to process notification: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Manually trigger notification cleanup based on the configured cleanup mode.
     * This method can be used to clean up notifications outside of the automatic cleanup process.
     *
     * @param credentialId Optional ID of a specific credential to clean up notifications for.
     * @return A Result containing the number of notifications removed during cleanup or an Exception in case of failure.
     */
    override suspend fun cleanupNotifications(credentialId: String?): Result<Int> {
        checkInitialized()
        return try {
            val count = cleanupManager.runCleanup(credentialId)
            Result.success(count)
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            logger.e("Failed to clean up notifications: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Run automatic notification cleanup if enabled in the configuration.
     * This is called internally when a new notification is processed.
     *
     * @param credentialId Optional ID of a specific credential to clean up notifications for.
     */
    private suspend fun runAutoCleanup(credentialId: String? = null) {
        try {
            // Get the cleanup config to check if auto-cleanup is enabled
            val config = (config as PushConfiguration).notificationCleanupConfig

            // Skip if mode is NONE (no cleanup)
            if (config.cleanupMode == NotificationCleanupConfig.CleanupMode.NONE) {
                return
            }

            // Run the cleanup
            val count = cleanupManager.runCleanup(credentialId)

            if (count > 0) {
                logger.d("Auto-cleanup removed $count notifications using mode: ${config.cleanupMode}")
            }
        } catch (e: Exception) {
            // Just log errors during auto-cleanup but don't throw
            coroutineContext.ensureActive()
            logger.w("Auto-cleanup failed: ${e.message}", e)
        }
    }

    /**
     * Approve a push notification.
     * This method approves the authentication request for the given notification.
     *
     * @param notificationId The ID of the notification to approve.
     * @return A Result containing a Boolean indicating success or an Exception in case of failure.
     */
    override suspend fun approveNotification(notificationId: String): Result<Boolean> {
        checkInitialized()
        return try {
            Result.success(pushService.approveNotification(notificationId, emptyMap()))
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            logger.e("Failed to approve notification: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Approve a challenge-based push notification.
     * This method approves the authentication request for the given challenge notification
     * with the provided challenge response.
     *
     * @param notificationId The ID of the notification to approve.
     * @param challengeResponse The challenge response provided by the user.
     * @return A Result containing a Boolean indicating success or an Exception in case of failure.
     */
    override suspend fun approveChallengeNotification(notificationId: String, challengeResponse: String): Result<Boolean> {
        checkInitialized()
        return try {
            val params = mapOf("challengeResponse" to challengeResponse)
            Result.success(pushService.approveNotification(notificationId, params))
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            logger.e("Failed to approve challenge notification: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Approve a biometric push notification.
     * This method approves the authentication request for the given biometric notification
     * with the provided authentication method.
     *
     * Note: The actual biometric authentication is handled by the developer on the app side; this method simply
     * records the approval.
     *
     * @param notificationId The ID of the notification to approve.
     * @param authenticationMethod The authentication method used as String (e.g., "face", "fingerprint").
     * @return A Result containing a Boolean indicating success or an Exception in case of failure.
     */
    override suspend fun approveBiometricNotification(notificationId: String, authenticationMethod: String): Result<Boolean> {
        checkInitialized()
        return try {
            val params = mapOf("authenticationMethod" to authenticationMethod)
            Result.success(pushService.approveNotification(notificationId, params))
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            logger.e("Failed to approve biometric notification: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Deny a push notification.
     * This method denies the authentication request for the given notification.
     *
     * @param notificationId The ID of the notification to deny.
     * @return A Result containing a Boolean indicating success or an Exception in case of failure.
     */
    override suspend fun denyNotification(notificationId: String): Result<Boolean> {
        checkInitialized()
        return try {
            Result.success(pushService.denyNotification(notificationId, emptyMap()))
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            logger.e("Failed to deny notification: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Get all pending push notifications.
     * This method returns all notifications that have not been approved or denied.
     *
     * @return A Result containing a list of all pending PushNotifications or an Exception in case of failure.
     */
    override suspend fun getPendingNotifications(): Result<List<PushNotification>> {
        checkInitialized()
        return try {
            Result.success(pushService.getPendingNotifications())
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            logger.e("Failed to get pending notifications: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Get a push notification by ID.
     *
     * @param notificationId The ID of the notification to get.
     * @return A Result containing the PushNotification (or null if not found) or an Exception in case of failure.
     */
    override suspend fun getNotification(notificationId: String): Result<PushNotification?> {
        checkInitialized()
        return try {
            Result.success(pushService.getNotification(notificationId))
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            logger.e("Failed to get notification: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Clean up resources used by the Push client.
     * This method clears caches and then calls the parent close method.
     */
    override suspend fun close() {
        if (isInitialized) {
            try {
                // First clear the service cache
                pushService.clearCache()
                logger.d("Push client cache cleared")
            } catch (e: Exception) {
                coroutineContext.ensureActive()
                // Just log any errors during cache clearing, but continue with closing
                logger.w("Error clearing Push client cache: ${e.message}", e)
            }
        }
        // Call parent close method to handle the rest of cleanup
        super.close()
    }
}

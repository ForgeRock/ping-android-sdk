/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.push

import com.pingidentity.mfa.commons.exception.CredentialLockedException
import com.pingidentity.mfa.commons.exception.MfaException
import com.pingidentity.mfa.commons.exception.MfaPolicyViolationException
import com.pingidentity.mfa.commons.policy.MfaPolicyEvaluator
import com.pingidentity.mfa.commons.util.DeviceUtils
import com.pingidentity.mfa.push.PushConstants.DEFAULT_DEVICE_NAME
import com.pingidentity.mfa.push.PushConstants.DEFAULT_TTL_SECONDS
import com.pingidentity.mfa.push.PushConstants.KEY_AMLB_COOKIE
import com.pingidentity.mfa.push.PushConstants.KEY_CHALLENGE
import com.pingidentity.mfa.push.PushConstants.KEY_CONTEXT_INFO
import com.pingidentity.mfa.push.PushConstants.KEY_CREDENTIAL_ID
import com.pingidentity.mfa.push.PushConstants.KEY_CUSTOM_PAYLOAD
import com.pingidentity.mfa.push.PushConstants.KEY_DEVICE_ID
import com.pingidentity.mfa.push.PushConstants.KEY_DEVICE_NAME
import com.pingidentity.mfa.push.PushConstants.KEY_MESSAGE_ID
import com.pingidentity.mfa.push.PushConstants.KEY_MESSAGE_TEXT
import com.pingidentity.mfa.push.PushConstants.KEY_NUMBERS_CHALLENGE
import com.pingidentity.mfa.push.PushConstants.KEY_PUSH_TYPE
import com.pingidentity.mfa.push.PushConstants.KEY_TIME_INTERVAL
import com.pingidentity.mfa.push.PushConstants.KEY_TTL
import com.pingidentity.mfa.push.PushConstants.KEY_USER_ID
import com.pingidentity.mfa.push.storage.PushStorage
import io.ktor.client.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext

/**
 * Service class for handling push operations with policy enforcement.
 *
 * This class provides functionality for managing push credentials and notifications,
 * including storing, retrieving, and processing push notifications with policy evaluation.
 *
 * @property storage The storage implementation for persisting push credentials and notifications.
 * @property config The Push configuration.
 * @property httpClient The HTTP client for network operations.
 * @property policyEvaluator The policy evaluator for credential policy validation.
 */
internal class PushService(
    private val storage: PushStorage,
    private val config: PushConfiguration,
    private val httpClient: HttpClient,
    private val policyEvaluator: MfaPolicyEvaluator,
    tokenManager: PushDeviceTokenManager? = null,
    handlers: Map<String, PushHandler>? = null
) {
    // Using logger from PushConfiguration
    private val logger = config.logger

    // Device token manager
    private val deviceTokenManager by lazy {
        tokenManager ?: PushDeviceTokenManager(storage, logger)
    }

    // In-memory cache for credentials, only used if enableCredentialCache is true
    private val credentialsCache = ConcurrentHashMap<String, PushCredential>()

    // Push handlers for different platforms
    private val pushHandlers: Map<String, PushHandler>

    // Initialize properties in the init block
    init {
        pushHandlers = handlers ?: initializePushHandlers()
        DeviceUtils.setLogger(logger)
        logger.d("PushService initialized.")
    }

    /**
     * Initialize the push handlers map with default handlers and any custom handlers.
     *
     * @return A map of platform identifiers to push handlers.
     */
    private fun initializePushHandlers(): Map<String, PushHandler> {
        // Create default handlers
        val defaultHandlers = mapOf(
            PushPlatform.PING_AM.name to PingAMPushHandler(httpClient, logger)
        )

        // If no custom handlers, return default handlers
        if (config.customPushHandlers.isEmpty()) {
            return defaultHandlers
        }

        // Merge default handlers with custom handlers (custom handlers take precedence)
        val mergedHandlers = defaultHandlers.toMutableMap<String, PushHandler>()
        mergedHandlers.putAll(config.customPushHandlers)
        logger.d("Added ${config.customPushHandlers.size} custom push handlers")

        return mergedHandlers
    }

    /**
     * Creates a Push Credential from a standard pushauth:// URI (typically from a QR code).
     * This method is used by PingAM to register devices for push notifications.
     * Evaluates policies during MFA registration.
     *
     * @param uri The URI string in the format pushauth://push/issuer:accountName?params...
     * @return The created PushCredential.
     * @throws IllegalArgumentException if the URI is invalid.
     * @throws MfaPolicyViolationException if policies are violated during registration.
     * @throws MfaException if the credential cannot be created.
     */
    suspend fun addCredentialFromUri(uri: String): PushCredential {
        try {
            // Parse the URI to create a PushCredential
            val credential = PushUriParser.parse(uri)
            
            // Evaluate policies during registration if policies are present
            if (!credential.policies.isNullOrBlank()) {
                logger.d("Evaluating policies for new Push credential")
                val policyResult = policyEvaluator.evaluate(config.context, credential.policies)
                
                if (policyResult.isFailure) {
                    val policyName = policyResult.nonCompliancePolicyName ?: "unknown"
                    logger.w("Push credential registration blocked by policy: $policyName")
                    throw MfaPolicyViolationException(
                        "This credential cannot be registered on this device. It violates the following policy: $policyName",
                        policyResult.nonCompliancePolicy
                    )
                } else {
                    logger.d("All policies passed for new Push credential")
                }
            }

            // Obtain device name
            val deviceName = try {
                DeviceUtils.getDeviceName(config.context)
            } catch (e: Exception) {
                logger.w("Failed to get device name, using default", e)
                DEFAULT_DEVICE_NAME
            }

            // Get the device token
            val deviceToken = deviceTokenManager.getDeviceTokenId() ?: throw MfaException("Device token not set")

            // Get registration parameters from URI and add device info
            val registrationParams = PushUriParser.registrationParameters(uri).toMutableMap()
            registrationParams[KEY_DEVICE_NAME] = deviceName
            registrationParams[KEY_DEVICE_ID] = deviceToken

            // Get the appropriate handler for this platform
            val platform = credential.platform
            val handler = pushHandlers[platform] ?: throw MfaException("No handler for platform: $platform")

            // Register the credential with the appropriate handler
            if (handler.register(credential, registrationParams)) {
                // If registration was successful, add the credential to storage
                logger.d("Successfully registered push credential from URI: $uri")
                return addCredential(credential)
            } else {
                // If registration failed, log and throw an exception
                logger.w("Failed to register push credential from URI: $uri")
                throw MfaException("Failed to register push credential from URI: $uri")
            }
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            logger.e("Failed to add credential from URI: ${e.message}", e)
            throw MfaException("Failed to add credential from URI", e)
        }
    }

    /**
     * Add a push credential with runtime policy evaluation.
     * Following ForgeRock pattern: evaluate policies and lock credential if needed.
     *
     * @param credential The credential to add.
     * @return The added credential (potentially locked due to policy violation).
     * @throws MfaException if the credential cannot be added.
     */
    suspend fun addCredential(credential: PushCredential): PushCredential = withContext(Dispatchers.IO) {
        try {
            // Evaluate policies at runtime if context is available and policies exist
            if (!credential.policies.isNullOrBlank()) {
                logger.d("Evaluating policies for Push credential: ${credential.id}")
                val policyResult = policyEvaluator.evaluate(config.context, credential.policies)
                
                // If credential is not locked but policies are non-compliant, lock it
                if (!credential.isLocked && policyResult.isFailure) {
                    val policyName = policyResult.nonCompliancePolicyName ?: "unknown"
                    logger.w("Locking Push credential due to policy violation: $policyName")
                    credential.lockCredential(policyName)
                }
                // If credential is locked but policies are now compliant, unlock it
                else if (credential.isLocked && policyResult.isSuccess) {
                    logger.d("Unlocking previously locked Push credential: all policies are compliant")
                    credential.unlockCredential()
                }
            }

            // Store in persistent storage
            storage.storePushCredential(credential)

            // Add to cache only if caching is enabled
            if (config.enableCredentialCache) {
                credentialsCache[credential.id] = credential
            }

            logger.d("Added Push credential with ID: ${credential.id} (locked: ${credential.isLocked})")
            return@withContext credential
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            logger.e("Failed to add push credential: ${e.message}", e)
            throw MfaException("Failed to add push credential", e)
        }
    }

    /**
     * Get all push credentials.
     *
     * @return A list of all credentials.
     * @throws MfaException if the credentials cannot be retrieved.
     */
    suspend fun getCredentials(): List<PushCredential> = withContext(Dispatchers.IO) {
        try {
            // If caching is enabled and cache has data, use it
            if (config.enableCredentialCache && credentialsCache.isNotEmpty()) {
                return@withContext credentialsCache.values.toList()
            }

            // Get all credentials from storage
            val credentials = storage.getAllPushCredentials()

            // Loop credentials
            credentials.forEach { credential ->
                // Evaluate policies for each credential at runtime
                if (!credential.policies.isNullOrBlank()) {
                    val policyResult = policyEvaluator.evaluate(config.context, credential.policies)

                    // If credential is not locked but policies are non-compliant, lock it
                    if (!credential.isLocked && policyResult.isFailure) {
                        val policyName = policyResult.nonCompliancePolicyName ?: "unknown"
                        logger.w("Locking Push credential ${credential.id} due to policy violation: $policyName")
                        credential.lockCredential(policyName)
                        // Update storage with locked status
                        storage.storePushCredential(credential)
                    }
                    // If credential is locked but policies are now compliant, unlock it
                    else if (credential.isLocked && policyResult.isSuccess) {
                        logger.d("Unlocking previously locked Push credential ${credential.id}: all policies are compliant")
                        credential.unlockCredential()
                        // Update storage with unlocked status
                        storage.storePushCredential(credential)
                    }
                }

                // Update cache if enabled
                if (config.enableCredentialCache) {
                    credentialsCache[credential.id] = credential
                }
            }

            return@withContext credentials
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            logger.e("Failed to get push credentials: ${e.message}", e)
            throw MfaException("Failed to get push credentials", e)
        }
    }

    /**
     * Get a push credential by ID.
     *
     * @param credentialId The ID of the credential to get.
     * @return The credential, or null if not found.
     * @throws MfaException if the credential cannot be retrieved.
     */
    suspend fun getCredential(credentialId: String): PushCredential? = withContext(Dispatchers.IO) {
        try {
            // Check cache first if caching is enabled
            var credential: PushCredential? = null
            if (config.enableCredentialCache) {
                credential = credentialsCache[credentialId]
            }

            // If not in cache or caching disabled, try to load from storage
            if (credential == null) {
                credential = storage.retrievePushCredential(credentialId)

                // Update cache if credential was found and caching is enabled
                if (credential != null && config.enableCredentialCache) {
                    credentialsCache[credentialId] = credential
                }
            }

            return@withContext credential
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            logger.e("Failed to get push credential with ID $credentialId: ${e.message}", e)
            throw MfaException("Failed to get push credential with ID $credentialId", e)
        }
    }

    /**
     * Remove a push credential.
     *
     * @param credentialId The ID of the credential to remove.
     * @return True if the credential was removed, false if it didn't exist.
     * @throws MfaException if the credential cannot be removed.
     */
    suspend fun removeCredential(credentialId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            // Remove from cache if enabled
            if (config.enableCredentialCache) {
                credentialsCache.remove(credentialId)
            }

            // Remove from storage
            val removed = storage.removePushCredential(credentialId)

            if (removed) {
                logger.d("Removed push credential with ID: $credentialId")
            }

            return@withContext removed
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            logger.e("Failed to remove push credential with ID $credentialId: ${e.message}", e)
            throw MfaException("Failed to remove push credential with ID $credentialId", e)
        }
    }

    /**
     * Set the device token for push notifications.
     * This method should be called on initial registration and when the device token changes.
     *
     * @param deviceToken The new device token for push notifications.
     * @param credentialId Optional ID of a specific credential to update the token for.
     *                    When null, updates the token globally for all credentials.
     * @return True if the token was updated successfully, false otherwise.
     * @throws MfaException if the device token cannot be set.
     */
    suspend fun setDeviceToken(deviceToken: String, credentialId: String? = null): Boolean = withContext(Dispatchers.IO) {
        try {
            val shouldUpdate = deviceTokenManager.shouldUpdateToken(deviceToken)

            // If the token has changed, update it
            if (shouldUpdate) {
                logger.d("Device token has changed, updating")
                logger.d("Previous device token: ${deviceTokenManager.getDeviceTokenId()}")
                logger.d("Setting device token to: $deviceToken")
                return@withContext updateDeviceToken(deviceToken, credentialId)
            } else {
                logger.d("Device token has not changed, no update needed")
                return@withContext true
            }
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            logger.e("Failed to set device token: ${e.message}", e)
            throw MfaException("Failed to set device token", e)
        }
    }

    /**
     * Update the device token for push notifications.
     * 
     * @param deviceToken The new device token
     * @param credentialId Optional credential ID to update the token for on the server
     * @return True if the token was updated successfully, false otherwise
     * @throws MfaException if the device token cannot be updated
     */
    private suspend fun updateDeviceToken(deviceToken: String, credentialId: String? = null): Boolean = withContext(Dispatchers.IO) {
        try {
            val localUpdateSuccess = deviceTokenManager.updateDeviceToken(deviceToken)
            if (!localUpdateSuccess) {
                logger.e("Failed to update device token locally")
                return@withContext false
            }
            
            // Get device name
            val deviceName = try {
                DeviceUtils.getDeviceName(config.context)
            } catch (e: Exception) {
                logger.w("Failed to get device name, using default", e)
                DEFAULT_DEVICE_NAME
            }
            
            // If no credentialId is provided, update all registered credentials
            if (credentialId == null) {
                logger.d("Updating device token for all registered credentials")
                val credentials = storage.getAllPushCredentials()
                if (credentials.isEmpty()) {
                    logger.d("No credentials found to update")
                    return@withContext true
                }

                var allSuccess = true
                for (credential in credentials) {
                    val platform = credential.platform
                    val handler = pushHandlers[platform] ?: continue

                    val params = mapOf(
                        KEY_DEVICE_NAME to deviceName
                    )

                    val success = try {
                        handler.setDeviceToken(credential, deviceToken, params)
                    } catch (e: Exception) {
                        logger.w("Failed to update device token for credential ${credential.id}: ${e.message}")
                        false
                    }

                    if (!success) {
                        allSuccess = false
                        logger.w("Failed to update device token for credential ${credential.id}")
                    }
                }

                return@withContext allSuccess
            } else {
                logger.d("Updating device token for specific credential ID: $credentialId")

                // Get the specific credential for server update
                val credential = storage.retrievePushCredential(credentialId) ?: run {
                    logger.w("Credential not found for ID: $credentialId")
                    return@withContext false
                }

                // Get the appropriate handler for this platform
                val platform = credential.platform
                val handler = pushHandlers[platform] ?: throw MfaException("No handler for platform: $platform")

                // Update token on server using the appropriate handler
                val params = mapOf(
                    KEY_DEVICE_NAME to deviceName
                )

                return@withContext handler.setDeviceToken(credential, deviceToken, params)
            }
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            logger.e("Failed to update device token: ${e.message}", e)
            throw MfaException("Failed to update device token", e)
        }
    }
    
    /**
     * Get the current device token
     * 
     * @return The current device token, or null if not set
     */
    suspend fun getDeviceToken(): String? = withContext(Dispatchers.IO) {
        try {
            return@withContext deviceTokenManager.getDeviceTokenId()
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            logger.e("Failed to get device token: ${e.message}", e)
            return@withContext null
        }
    }

    /**
     * Process a push notification message.
     * It uses the appropriate PushHandler based on the message format. It checks for duplicates
     * using the messageId if available, and stores the notification if it's new.
     *
     * @param messageData The raw message data map from the push service.
     * @return The push notification, or null if the message is invalid.
     * @throws MfaException if the message cannot be processed.
     */
    suspend fun processNotification(messageData: Map<String, Any>): PushNotification? = withContext(Dispatchers.IO) {
        try {
            // Identify the platform based on the message format
            val platform = identifyPlatform(messageData)
            
            if (platform == null) {
                logger.d("Unknown push notification format")
                return@withContext null
            }

            // Get the appropriate handler for this platform
            val handler = pushHandlers[platform] ?: throw MfaException("No handler for platform: $platform")
            
            // Parse the message
            val parsedData = handler.parseMessage(messageData)
            
            return@withContext processAndStoreParsedData(parsedData)
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            logger.e("Failed to process push notification: ${e.message}", e)
            throw MfaException("Failed to process push notification", e)
        }
    }
    
    /**
     * Process a push notification message from a string.
     * It uses the appropriate PushHandler based on the message format. It checks for duplicates
     * using the messageId if available, and stores the notification if it's new.
     *
     * @param message The message as a string (typically a JWT).
     * @return The push notification, or null if the message is invalid.
     * @throws MfaException if the message cannot be processed.
     */
    suspend fun processNotification(message: String): PushNotification? = withContext(Dispatchers.IO) {
        try {
            // Identify the platform based on the message format
            val platform = identifyPlatform(message)
            
            if (platform == null) {
                logger.d("Unknown push notification format in string")
                return@withContext null
            }

            // Get the appropriate handler for this platform
            val handler = pushHandlers[platform] ?: throw MfaException("No handler for platform: $platform")
            
            // Parse the message
            val parsedData = handler.parseMessage(message)
            
            return@withContext processAndStoreParsedData(parsedData)
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            logger.e("Failed to process push notification from string: ${e.message}", e)
            throw MfaException("Failed to process push notification from string", e)
        }
    }
    
    /**
     * Helper method to process and store the parsed notification data.
     * @param parsedData The parsed notification data.
     * @return The created PushNotification, or null if it was a duplicate.
     * @throws MfaException if the notification cannot be stored.
     */
    private suspend fun processAndStoreParsedData(parsedData: Map<String, Any>): PushNotification? {
        // Get the messageId from parsed data
        val messageId = parsedData[KEY_MESSAGE_ID] as? String

        // Check if we already have this notification by messageId (if available)
        if (!messageId.isNullOrBlank()) {
            val existingNotification = storage.getNotificationByMessageId(messageId)
            if (existingNotification != null) {
                logger.d("Notification with messageId=$messageId already exists")
                return existingNotification
            }
        }
        
        // Attempt to update credential with userId if it's missing
        (parsedData[KEY_CREDENTIAL_ID] as? String)?.let { credId ->
            storage.retrievePushCredential(credId)?.let { credential ->
                if (credential.userId.isNullOrBlank()) { // Only update if existing userId is missing
                    (parsedData[KEY_USER_ID] as? String)?.takeIf { it.isNotBlank() }?.let { newUserId ->
                        logger.d("Credential ${credential.id} missing userId. Updating with: $newUserId from push notification.")
                        val updatedCredential = credential.copy(userId = newUserId)
                        storage.storePushCredential(updatedCredential)
                    }
                }
            }
        }

        // Create a notification object
        val notification = createNotification(parsedData)
        
        // Store the notification
        storeNotification(notification)
        
        return notification
    }

    /**
     * Identify the platform based on the message format.
     * It uses each registered PushHandler to check if it can handle the message.
     *
     * @param messageData The message data to identify.
     * @return The platform ID as a String, or null if unknown.
     */
    private fun identifyPlatform(messageData: Map<String, Any>): String? {
        // Try each platform's message handler
        for ((platformId, handler) in pushHandlers) {
            if (handler.canHandle(messageData)) {
                return platformId
            }
        }
        return null
    }
    
    /**
     * Identify the platform based on the message format for string messages.
     * It uses each registered PushHandler to check if it can handle the message.
     *
     * @param message The message as a string to identify.
     * @return The platform ID as a String, or null if unknown.
     */
    private fun identifyPlatform(message: String): String? {
        // Try each platform's message handler
        for ((platformId, handler) in pushHandlers) {
            if (handler.canHandle(message)) {
                return platformId
            }
        }
        return null
    }

    /**
     * Create a push notification from parsed data.
     *
     * @param parsedData The parsed notification data.
     * @return The created push notification.
     */
    private fun createNotification(parsedData: Map<String, Any>): PushNotification {
        val credentialId = parsedData[KEY_CREDENTIAL_ID] as String
        val messageId = parsedData[KEY_MESSAGE_ID] as? String ?: ""
        val messageText = parsedData[KEY_MESSAGE_TEXT] as? String
        val customPayload = parsedData[KEY_CUSTOM_PAYLOAD] as? String
        val challenge = parsedData[KEY_CHALLENGE] as? String
        val numbersChallenge = parsedData[KEY_NUMBERS_CHALLENGE] as? String
        val amlbCookie = parsedData[KEY_AMLB_COOKIE] as? String
        val contextInfo = parsedData[KEY_CONTEXT_INFO] as? String
        val ttl = parsedData[KEY_TTL] as? Int ?: DEFAULT_TTL_SECONDS

        // Fixed syntax for pushType extraction
        val pushTypeStr = parsedData[KEY_PUSH_TYPE] as? String
        val pushType = if (pushTypeStr != null) PushType.fromString(pushTypeStr) else PushType.DEFAULT

        // Use the time interval (date as long) if provided to set sentAt
        val timeInterval = parsedData[KEY_TIME_INTERVAL] as? Long
        val sentAt = if (timeInterval != null) Date(timeInterval) else null
        val createdAt = Date()

        @Suppress("UNCHECKED_CAST")
        val additionalDataMap = parsedData["additionalData"] as? Map<String, Any>

        return PushNotification(
            id = UUID.randomUUID().toString(),
            credentialId = credentialId,
            ttl = ttl,
            messageId = messageId,
            messageText = messageText,
            customPayload = customPayload,
            challenge = challenge,
            numbersChallenge = numbersChallenge,
            loadBalancer = amlbCookie,
            contextInfo = contextInfo,
            pushType = pushType,
            createdAt = createdAt,
            sentAt = sentAt,
            additionalData = additionalDataMap
        )
    }

    /**
     * Store a push notification.
     *
     * @param notification The notification to store.
     * @throws MfaException if the notification cannot be stored.
     */
    private suspend fun storeNotification(notification: PushNotification) {
        try {
            storage.storePushNotification(notification)
            logger.d("Stored push notification: ${notification.id}")
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            logger.e("Failed to store push notification: ${e.message}", e)
            throw MfaException("Failed to store push notification", e)
        }
    }

    /**
     * Approve a push notification.
     *
     * @param notificationId The ID of the notification to approve.
     * @return True if the notification was approved successfully.
     * @throws MfaException if the notification cannot be approved.
     * @see PushClient.approveNotification which wraps this in a Result
     */
    suspend fun approveNotification(notificationId: String, params: Map<String, Any> = emptyMap()): Boolean = withContext(Dispatchers.IO) {
        try {
            // Get the notification
            val notification = storage.retrievePushNotification(notificationId)
                ?: throw MfaException("Notification not found: $notificationId")
            
            // Check if the notification is pending
            if (!notification.pending) {
                logger.d("Cannot approve notification: ${notification.id}, already responded")
                return@withContext false
            }

            // Get the credential
            val credential = storage.retrievePushCredential(notification.credentialId)
                ?: throw MfaException("Credential not found: ${notification.credentialId}")

            // Check if credential is locked due to policy violation
            if (credential.isLocked) {
                val lockingPolicy = credential.lockingPolicy ?: "unknown"
                throw CredentialLockedException(lockingPolicy, "Credential is currently locked")
            }

            // Get the appropriate handler for this platform
            val platform = credential.platform
            val handler = pushHandlers[platform] ?: throw MfaException("No handler for platform: $platform")

            // Send the approval with any additional parameters
            val result = handler.sendApproval(credential, notification, params)
            if (result) {
                // Update the notification status
                notification.markApproved()
                storage.updatePushNotification(notification)
                logger.d("Notification approved: ${notification.id}")
            } else {
                logger.w("Failed to send approval for notification: ${notification.id}")
            }
            
            return@withContext result
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            logger.e("Failed to approve notification: ${e.message}", e)
            throw MfaException("Failed to approve notification", e)
        }
    }

    /**
     * Deny a push notification.
     *
     * @param notificationId The ID of the notification to deny.
     * @return True if the notification was denied successfully.
     * @throws MfaException if the notification cannot be denied.
     */
    suspend fun denyNotification(notificationId: String, params: Map<String, Any> = emptyMap()): Boolean = withContext(Dispatchers.IO) {
        try {
            // Get the notification
            val notification = storage.retrievePushNotification(notificationId)
                ?: throw MfaException("Notification not found: $notificationId")
            
            // Check if the notification is pending
            if (!notification.pending) {
                logger.d("Cannot deny notification: ${notification.id}, already responded")
                return@withContext false
            }
            
            // Get the credential
            val credential = storage.retrievePushCredential(notification.credentialId)
                ?: throw MfaException("Credential not found: ${notification.credentialId}")

            // Check if credential is locked due to policy violation
            if (credential.isLocked) {
                val lockingPolicy = credential.lockingPolicy ?: "unknown"
                throw CredentialLockedException(lockingPolicy, "Credential is currently locked")
            }

            // Get the appropriate handler for this platform
            val platform = credential.platform
            val handler = pushHandlers[platform] ?: throw MfaException("No handler for platform: $platform")

            // Send the denial with any additional parameters
            val result = handler.sendDenial(credential, notification, params)
            if (result) {
                // Update the notification status
                notification.markDenied()
                storage.updatePushNotification(notification)
                logger.d("Notification denied: ${notification.id}")
            } else {
                logger.w("Failed to send denial for notification: ${notification.id}")
            }

            return@withContext result
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            logger.e("Failed to deny notification: ${e.message}", e)
            throw MfaException("Failed to deny notification", e)
        }
    }

    /**
     * Get all pending push notifications.
     *
     * @return A list of all pending notifications.
     * @throws MfaException if the notifications cannot be retrieved.
     */
    suspend fun getPendingNotifications(): List<PushNotification> = withContext(Dispatchers.IO) {
        try {
            return@withContext storage.getPendingPushNotifications()
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            logger.e("Failed to get pending notifications: ${e.message}", e)
            throw MfaException("Failed to get pending notifications", e)
        }
    }

    /**
     * Get all push notifications.
     *
     * @return A list of all stored notifications.
     * @throws MfaException if the notifications cannot be retrieved.
     */
    suspend fun getAllNotifications(): List<PushNotification> = withContext(Dispatchers.IO) {
        try {
            return@withContext storage.getAllPushNotifications()
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            logger.e("Failed to get all notifications: ${e.message}", e)
            throw MfaException("Failed to get all notifications", e)
        }
    }

    /**
     * Get a push notification by ID.
     *
     * @param notificationId The ID of the notification to get.
     * @return The notification, or null if not found.
     * @throws MfaException if the notification cannot be retrieved.
     */
    suspend fun getNotification(notificationId: String): PushNotification? = withContext(Dispatchers.IO) {
        try {
            return@withContext storage.retrievePushNotification(notificationId)
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            logger.e("Failed to get notification: ${e.message}", e)
            throw MfaException("Failed to get notification", e)
        }
    }

    /**
     * Clear the internal memory caches.
     * This should be called when the client is being closed.
     */
    fun clearCache() {
        credentialsCache.clear()
        logger.d("Push credentials cache cleared")
    }
}

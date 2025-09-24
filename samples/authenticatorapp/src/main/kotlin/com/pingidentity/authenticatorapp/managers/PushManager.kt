/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.authenticatorapp.managers

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.pingidentity.authenticatorapp.data.DiagnosticLogger
import com.pingidentity.authenticatorapp.data.PushNotificationItem
import com.pingidentity.authenticatorapp.data.toUiItems
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import com.pingidentity.mfa.push.PushClient
import com.pingidentity.mfa.push.PushCredential
import com.pingidentity.mfa.push.PushNotification

/**
 * Manager class for handling all Push credential and notification operations.
 * Encapsulates Push-specific business logic and state management.
 *
 * @param pushClient The Push MFA client instance
 * @param diagnosticLogger DiagnosticLogger for logging
 */
class PushManager(
    private var pushClient: PushClient? = null,
    private val diagnosticLogger: DiagnosticLogger
) {
    
    private val _pushCredentials = MutableStateFlow<List<PushCredential>>(emptyList())
    val pushCredentials: StateFlow<List<PushCredential>> = _pushCredentials.asStateFlow()
    
    private val _isLoadingPushCredentials = MutableStateFlow(false)
    val isLoadingPushCredentials: StateFlow<Boolean> = _isLoadingPushCredentials.asStateFlow()
    
    private val _pushNotifications = MutableStateFlow<List<PushNotification>>(emptyList())
    val pushNotifications: StateFlow<List<PushNotification>> = _pushNotifications.asStateFlow()
    
    private val _pendingNotifications = MutableStateFlow<List<PushNotification>>(emptyList())
    val pendingNotifications: StateFlow<List<PushNotification>> = _pendingNotifications.asStateFlow()
    
    private val _isLoadingNotifications = MutableStateFlow(false)
    val isLoadingNotifications: StateFlow<Boolean> = _isLoadingNotifications.asStateFlow()
    
    private val _pushNotificationItems = MutableStateFlow<List<PushNotificationItem>>(emptyList())
    val pushNotificationItems: StateFlow<List<PushNotificationItem>> = _pushNotificationItems.asStateFlow()
    
    private val _pendingNotificationItems = MutableStateFlow<List<PushNotificationItem>>(emptyList())
    val pendingNotificationItems: StateFlow<List<PushNotificationItem>> = _pendingNotificationItems.asStateFlow()
    
    private val _lastAddedPushCredential = MutableStateFlow<PushCredential?>(null)
    val lastAddedPushCredential: StateFlow<PushCredential?> = _lastAddedPushCredential.asStateFlow()

    /**
     * Sets the Push client instance.
     */
    fun setClient(client: PushClient) {
        this.pushClient = client
    }

    /**
     * Loads all Push credentials from the SDK.
     */
    suspend fun loadCredentials(): Result<List<PushCredential>> {
        val client = pushClient ?: return Result.failure(Exception("Push client not initialized"))
        _isLoadingPushCredentials.value = true
        return try {
            val result = withContext(Dispatchers.IO) {
                diagnosticLogger.d("Loading Push credentials from PushClient")
                client.getCredentials()
            }
            
            result.onSuccess { credentials ->
                _pushCredentials.value = credentials
                // Update notification items when credentials change
                updateNotificationItems()
            }
            
            _isLoadingPushCredentials.value = false
            result
        } catch (e: Exception) {
            _isLoadingPushCredentials.value = false
            Result.failure(e)
        }
    }

    /**
     * Adds a Push credential from a URI.
     */
    suspend fun addCredentialFromUri(uri: String): Result<PushCredential> {
        val client = pushClient ?: return Result.failure(Exception("Push client not initialized"))
        return try {
            val result = withContext(Dispatchers.IO) {
                diagnosticLogger.d("Adding Push credential from URI: ${maskUri(uri)}")
                client.addCredentialFromUri(uri)
            }
            
            result.onSuccess { credential ->
                _lastAddedPushCredential.value = credential
                // Reload credentials to refresh the list
                loadCredentials()
            }
            
            result
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Removes a Push credential from the SDK.
     */
    suspend fun removeCredential(credentialId: String): Result<Boolean> {
        val client = pushClient ?: return Result.failure(Exception("Push client not initialized"))
        return try {
            val result = withContext(Dispatchers.IO) {
                diagnosticLogger.d("Removing Push credential: $credentialId")
                client.deleteCredential(credentialId)
            }
            
            result.onSuccess { removed ->
                if (removed) {
                    // Reload credentials to refresh the list
                    loadCredentials()
                }
            }
            
            result
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Updates a Push credential in the SDK.
     */
    suspend fun updateCredential(credential: PushCredential): Result<PushCredential> {
        val client = pushClient ?: return Result.failure(Exception("Push client not initialized"))
        return try {
            val result = withContext(Dispatchers.IO) {
                diagnosticLogger.d("Updating Push credential: $credential")
                client.saveCredential(credential)
            }
            
            result.onSuccess {
                // Reload credentials to refresh the list
                loadCredentials()
            }
            
            result
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Loads pending push notifications from the SDK.
     */
    suspend fun loadPushNotifications(): Result<List<PushNotification>> {
        val client = pushClient ?: return Result.failure(Exception("Push client not initialized"))
        _isLoadingNotifications.value = true
        return try {
            val result = withContext(Dispatchers.IO) {
                diagnosticLogger.d("Loading push notifications from PushClient")
                client.getPendingNotifications()
            }
            
            result.onSuccess { notifications ->
                _pendingNotifications.value = notifications
                updateNotificationItems()
            }
            
            _isLoadingNotifications.value = false
            result
        } catch (e: Exception) {
            _isLoadingNotifications.value = false
            Result.failure(e)
        }
    }

    /**
     * Loads all push notifications (not just pending ones).
     */
    suspend fun loadAllPushNotifications(): Result<List<PushNotification>> {
        val client = pushClient ?: return Result.failure(Exception("Push client not initialized"))
        _isLoadingNotifications.value = true
        return try {
            val result = withContext(Dispatchers.IO) {
                client.getAllNotifications()
            }
            
            result.onSuccess { allNotifications ->
                val pendingNotifications = allNotifications.filter { it.pending }
                _pushNotifications.value = allNotifications
                _pendingNotifications.value = pendingNotifications
                updateNotificationItems()
            }
            
            _isLoadingNotifications.value = false
            result
        } catch (e: Exception) {
            _isLoadingNotifications.value = false
            Result.failure(e)
        }
    }

    /**
     * Approves a push notification.
     */
    suspend fun approveNotification(notificationId: String): Result<Boolean> {
        val client = pushClient ?: return Result.failure(Exception("Push client not initialized"))
        return try {
            val result = withContext(Dispatchers.IO) {
                diagnosticLogger.d("Approving push notification: $notificationId")
                client.approveNotification(notificationId)
            }
            
            result.onSuccess { success ->
                if (success) {
                    // Reload notifications after approving
                    loadPushNotifications()
                    loadAllPushNotifications()
                }
            }
            
            result
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Approves a push notification with a challenge response.
     */
    suspend fun approveChallengeNotification(notificationId: String, challengeResponse: String): Result<Boolean> {
        val client = pushClient ?: return Result.failure(Exception("Push client not initialized"))
        return try {
            val result = withContext(Dispatchers.IO) {
                diagnosticLogger.d("Approving challenge push notification: $notificationId")
                client.approveChallengeNotification(notificationId, challengeResponse)
            }
            
            result.onSuccess { success ->
                if (success) {
                    // Reload notifications after approving
                    loadPushNotifications()
                    loadAllPushNotifications()
                }
            }
            
            result
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Denies a push notification.
     */
    suspend fun denyNotification(notificationId: String): Result<Boolean> {
        val client = pushClient ?: return Result.failure(Exception("Push client not initialized"))
        return try {
            val result = withContext(Dispatchers.IO) {
                diagnosticLogger.d("Denying push notification: $notificationId")
                client.denyNotification(notificationId)
            }
            
            result.onSuccess { success ->
                if (success) {
                    // Reload notifications after denying
                    loadPushNotifications()
                    loadAllPushNotifications()
                }
            }
            
            result
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Cleans up old notifications.
     */
    suspend fun cleanupNotifications(): Result<Int> {
        val client = pushClient ?: return Result.failure(Exception("Push client not initialized"))
        return try {
            withContext(Dispatchers.IO) {
                client.cleanupNotifications()
            }.also { result ->
                result.onSuccess {
                    // Reload notifications after cleanup
                    loadPushNotifications()
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Gets the current device token used for push notifications.
     */
    suspend fun getDeviceToken(): Result<String?> {
        val client = pushClient
        return try {
            withContext(Dispatchers.IO) {
                (client as? PushClient)?.getDeviceToken() ?: Result.success("Not available")
            }.also { result ->
                result.onSuccess { token ->
                    diagnosticLogger.d("Retrieved device token from PushClient: $token")
                }.onFailure { e ->
                    diagnosticLogger.e("Error retrieving device token from PushClient: ${e.message}")
                }
            }
        } catch (e: Exception) {
            diagnosticLogger.e("Error retrieving device token from PushClient: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Forces a renewal of the Firebase device token.
     */
    suspend fun forceDeviceTokenRenew(): Result<Unit> {
        return try {
            diagnosticLogger.d("Attempting to force device token renew.")
            
            // Delete current token
            val deleteResult = deleteDeviceToken()
            if (!deleteResult.getOrDefault(false)) {
                return Result.failure(Exception("Failed to delete existing device token, renewal aborted."))
            }
            
            diagnosticLogger.d("Previous device token deleted successfully. Fetching new token.")
            
            // Get new token
            val newToken = suspendCancellableCoroutine<String?> { continuation ->
                FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        continuation.resume(task.result)
                    } else {
                        continuation.resumeWithException(task.exception ?: Exception("Failed to get token"))
                    }
                }
            }
            
            if (newToken == null) {
                return Result.failure(Exception("Failed to fetch new FCM token (token is null)"))
            }
            
            diagnosticLogger.d("New FCM token received. Setting it in PushClient.")
            
            // Set new token in PushClient
            val setResult = withContext(Dispatchers.IO) {
                val client = pushClient
                (client as? PushClient)?.setDeviceToken(newToken)
            }
            
            if (setResult != null) {
                setResult.onSuccess {
                    diagnosticLogger.d("Successfully set new device token in PushClient.")
                }.onFailure { e ->
                    diagnosticLogger.e("Failed to set new device token in PushClient: ${e.message}")
                }
                setResult.map { }
            } else {
                val errorMessage = "PushClient is not available or does not support setDeviceToken."
                diagnosticLogger.e(errorMessage)
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            diagnosticLogger.e("Exception while setting new device token: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Gets a specific push notification item by its ID.
     */
    fun getNotificationItemById(notificationId: String): PushNotificationItem? {
        return _pushNotificationItems.value.find { it.notification.id == notificationId }
    }

    /**
     * Clears the last added Push credential.
     */
    fun clearLastAddedCredential() {
        _lastAddedPushCredential.value = null
    }

    /**
     * Updates the notification items in the state based on current push notifications.
     */
    private fun updateNotificationItems() {
        val pendingItems = _pendingNotifications.value.toUiItems(_pushCredentials.value)
        val allItems = _pushNotifications.value.toUiItems(_pushCredentials.value)

        _pushNotificationItems.value = allItems
        _pendingNotificationItems.value = pendingItems

        // Log the number of pending notifications
        Log.d("PushManager", "Pending notifications: ${pendingItems.size}")
    }

    /**
     * Deletes the Firebase device token.
     */
    private suspend fun deleteDeviceToken(): Result<Boolean> {
        return try {
            suspendCancellableCoroutine { continuation ->
                FirebaseMessaging.getInstance().deleteToken().addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        continuation.resume(Unit)
                    } else {
                        continuation.resumeWithException(task.exception ?: Exception("Failed to delete token"))
                    }
                }
            }
            diagnosticLogger.d("Firebase device token deleted successfully.")
            Result.success(true)
        } catch (e: Exception) {
            diagnosticLogger.e("Firebase device token deletion failed: ${e.message}")
            Result.success(false)
        }
    }

    /**
     * Closes the Push client and releases resources.
     */
    suspend fun close() {
        try {
            pushClient?.close()
        } catch (e: Exception) {
            diagnosticLogger.e("Error closing Push client", e)
        }
    }

    /**
     * Masks sensitive information in a URI for logging.
     */
    private fun maskUri(uri: String): String {
        return uri.replace(Regex("secret=[^&]*"), "secret=*****")
    }
}
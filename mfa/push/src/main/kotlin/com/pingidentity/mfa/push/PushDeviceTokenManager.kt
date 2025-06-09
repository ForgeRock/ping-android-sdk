/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.push

import android.util.Log
import com.pingidentity.mfa.commons.storage.MfaStorageClient
import java.util.Date

/**
 * Helper class to manage FCM device tokens for Push notifications.
 * Responsible for tracking, storing, and updating FCM tokens.
 */
internal class PushDeviceTokenManager(
    private val storageClient: MfaStorageClient,
    private var deviceToken: String? = null
) {
    companion object {
        private const val TAG = "PushDeviceTokenManager"
    }

    /**
     * Get the current Push device token
     * @return The current Push device token object or null if not available
     */
    fun getCurrentDeviceToken(): PushDeviceToken? {
        return storageClient.getCurrentPushDeviceToken() as? PushDeviceToken
    }

    /**
     * Get the current device token ID.
     * @return The current device token ID as a String, or null if not set
     */
    fun getDeviceTokenId(): String? {
        return deviceToken
    }

    /**
     * Check if the device token needs to be updated.
     * @param token The new device token to compare against the current token
     * @return True if the token has changed or is not set, false otherwise
     */
    fun shouldUpdateToken(token: String): Boolean {
        if (deviceToken == null) {
            // No token in memory, check storage
            val currentToken = storageClient.getCurrentPushDeviceToken()
            if (currentToken == null) {
                // No token stored, need to update
                return true
            } else {
                // Token in storage, update memory reference and compare
                deviceToken = currentToken.tokenId
                return deviceToken != token
            }
        } else {
            // Token in memory, compare directly
            return deviceToken != token
        }
    }

    /**
     * Save a new device token in storage and update the in-memory reference.
     * @param token The new device token
     * @return True if successful, false otherwise
     */
    fun saveDeviceToken(token: String): Boolean {
        try {
            Log.d(TAG, "Storing new FCM device token")
            val deviceToken = PushDeviceToken(token)
            storageClient.savePushDeviceToken(deviceToken)
            this.deviceToken = token
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error storing FCM device token: ${e.message}")
            return false
        }
    }
}

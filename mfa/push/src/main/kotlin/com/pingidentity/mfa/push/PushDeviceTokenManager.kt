/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.push

import com.pingidentity.logger.Logger
import com.pingidentity.mfa.push.storage.PushStorage
import java.util.Date

/**
 * Manager class for handling push device tokens.
 * 
 * This class is responsible for managing device tokens used for push notifications,
 * including storing, retrieving, and updating tokens locally.
 *
 * @property storage The storage implementation for persisting device tokens
 * @property logger The logger instance
 */
class PushDeviceTokenManager(
    private val storage: PushStorage,
    private val logger: Logger = Logger.logger
) {
    // The current device token
    private var currentToken: String? = null
    
    /**
     * Get the current push device token
     * 
     * @return The current Push device token, or null if not found
     */
    suspend fun getCurrentDeviceToken(): PushDeviceToken? {
        try {
            return storage.getCurrentPushDeviceToken()
        } catch (e: Exception) {
            logger.e("Failed to get current device token: ${e.message}")
            return null
        }
    }
    
    /**
     * Get the current device token ID
     * 
     * @return The current device token ID as a String, or null if not set
     */
    suspend fun getDeviceTokenId(): String? {
        return currentToken ?: getCurrentDeviceToken()?.tokenId
    }
    
    /**
     * Check if the device token has changed and needs to be updated
     * 
     * @param newToken The new device token to compare
     * @return True if the token has changed and should be updated, false otherwise
     */
    suspend fun shouldUpdateToken(newToken: String): Boolean {
        if (newToken.isEmpty()) {
            return false
        }
        
        // If we don't have a current token in memory, check storage
        if (currentToken == null) {
            val storedToken = getCurrentDeviceToken()
            currentToken = storedToken?.tokenId
        }
        
        // If the token is different from the current one, it needs to be updated
        return currentToken != newToken
    }
    
    /**
     * Update the device token locally
     *
     * @param newToken The new device token
     * @return True if the token was updated successfully, false otherwise
     */
    suspend fun updateDeviceToken(newToken: String): Boolean {
        if (newToken.isEmpty()) {
            logger.w("Cannot update token with empty value")
            return false
        }
        
        // Check if we need to update the token
        if (!shouldUpdateToken(newToken)) {
            logger.d("Device token has not changed, skipping update")
            return true
        }

        try {
            // Store the new token
            val pushDeviceToken = PushDeviceToken(tokenId = newToken, createdAt = Date())
            storage.storePushDeviceToken(pushDeviceToken)
            
            // Update the current token in memory
            currentToken = newToken
            logger.d("Device token updated locally: $newToken")
            return true
        } catch (e: Exception) {
            logger.e("Failed to update device token locally: ${e.message}")
            return false
        }
    }
}

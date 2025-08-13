/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.push

import com.pingidentity.logger.Logger

/**
 * A simple push handler for testing purposes that accepts a simplified message format.
 */
class TestPushHandler(private val logger: Logger = Logger.logger) : PushHandler {
    
    companion object {
        // Keys for the parsed notification data
        const val KEY_CREDENTIAL_ID = "credentialId"
        const val KEY_MESSAGE_ID = "messageId"
        const val KEY_MESSAGE_TEXT = "messageText"
        const val KEY_TTL = "ttl"
        const val KEY_PUSH_TYPE = "pushType"
        const val KEY_CHALLENGE = "challenge" 
        const val KEY_NUMBERS_CHALLENGE = "numbersChallenge"
        
        // Default values
        const val DEFAULT_TTL_SECONDS = 300

        // Test handler name
        const val HANDLER_NAME = "TEST_HANDLER"
    }
    
    override fun canHandle(messageData: Map<String, Any>): Boolean {
        // This test handler handles any message that contains credentialId, messageId and platform=PING_ONE
        return messageData.containsKey("credentialId") && 
               messageData.containsKey("messageId") &&
               messageData["platform"] == HANDLER_NAME
    }
    
    override fun parseMessage(messageData: Map<String, Any>): Map<String, Any> {
        // Map the simple format to the format expected by PushService
        val result = mutableMapOf<String, Any>()
        
        // Copy all provided data into the result
        result.putAll(messageData)
        
        // Ensure key names are correctly mapped
        if (messageData.containsKey("message")) {
            result[KEY_MESSAGE_TEXT] = messageData["message"]!!
        }
        
        // Convert string values to appropriate types where needed
        if (messageData.containsKey("ttl") && messageData["ttl"] is String) {
            val ttlStr = messageData["ttl"] as String
            try {
                result[KEY_TTL] = ttlStr.toInt()
            } catch (e: NumberFormatException) {
                logger.w("Invalid ttl value: $ttlStr. Using default.")
                result[KEY_TTL] = DEFAULT_TTL_SECONDS
            }
        }
        
        // Make sure key mapping is correct
        if (messageData.containsKey("pushType") && messageData["pushType"] is String) {
            result[KEY_PUSH_TYPE] = messageData["pushType"]!!
        }
        
        if (messageData.containsKey("numbersChallenge")) {
            result[KEY_NUMBERS_CHALLENGE] = messageData["numbersChallenge"]!!
        }
        
        // Always include credentialId and messageId which are required
        result[KEY_CREDENTIAL_ID] = messageData["credentialId"]!!
        result[KEY_MESSAGE_ID] = messageData["messageId"]!!
        
        if (messageData.containsKey("challenge")) {
            result[KEY_CHALLENGE] = messageData["challenge"]!!
        }
        
        return result
    }
    
    override suspend fun sendApproval(
        credential: PushCredential,
        notification: PushNotification,
        params: Map<String, Any>
    ): Boolean {
        // For testing purposes, always return success
        return true
    }
    
    override suspend fun sendDenial(
        credential: PushCredential,
        notification: PushNotification,
        params: Map<String, Any>
    ): Boolean {
        // For testing purposes, always return success
        return true
    }
    
    override suspend fun setDeviceToken(
        credential: PushCredential,
        deviceToken: String,
        params: Map<String, Any>
    ): Boolean {
        // For testing purposes, always return success
        return true
    }
    
    override suspend fun register(credential: PushCredential, params: Map<String, Any>): Boolean {
        // For testing purposes, always return success
        return true
    }
}

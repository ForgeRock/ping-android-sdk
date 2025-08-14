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
    
    override fun canHandle(message: String): Boolean {
        // For string messages in tests, we expect a simple format like "TEST_HANDLER:messageId:credentialId"
        return message.startsWith("$HANDLER_NAME:") && message.count { it == ':' } >= 2
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
    
    override fun parseMessage(message: String): Map<String, Any> {
        // Parse string in format "TEST_HANDLER:messageId:credentialId[:optional_params]"
        val parts = message.split(":")
        if (parts.size < 3 || parts[0] != HANDLER_NAME) {
            logger.w("Invalid test message format: $message")
            return emptyMap()
        }
        
        val result = mutableMapOf<String, Any>()
        result[KEY_MESSAGE_ID] = parts[1]
        result[KEY_CREDENTIAL_ID] = parts[2]
        result["platform"] = HANDLER_NAME
        
        // Add any additional parameters if present
        if (parts.size > 3) {
            // Handle optional parameters in pairs (key:value)
            for (i in 3 until parts.size step 2) {
                if (i + 1 < parts.size) {
                    val key = parts[i]
                    val value = parts[i + 1]
                    
                    // Try to convert to appropriate type
                    val typedValue: Any = when {
                        value.equals("true", ignoreCase = true) -> true
                        value.equals("false", ignoreCase = true) -> false
                        value.toIntOrNull() != null -> value.toInt()
                        else -> value
                    }
                    
                    result[key] = typedValue
                }
            }
        }
        
        // Add default message text if not provided
        if (!result.containsKey(KEY_MESSAGE_TEXT)) {
            result[KEY_MESSAGE_TEXT] = "Test notification"
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

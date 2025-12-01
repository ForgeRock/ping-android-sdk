/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.push

import android.util.Base64
import com.pingidentity.logger.Logger
import com.pingidentity.mfa.commons.util.JwtUtils
import com.pingidentity.mfa.push.PushConstants.DEFAULT_DEVICE_NAME
import com.pingidentity.mfa.push.PushConstants.DEFAULT_TTL_SECONDS
import com.pingidentity.mfa.push.PushConstants.KEY_ADDITIONAL_DATA
import com.pingidentity.mfa.push.PushConstants.KEY_AMLB_COOKIE
import com.pingidentity.mfa.push.PushConstants.KEY_CHALLENGE
import com.pingidentity.mfa.push.PushConstants.KEY_CONTEXT_INFO
import com.pingidentity.mfa.push.PushConstants.KEY_CREDENTIAL_ID
import com.pingidentity.mfa.push.PushConstants.KEY_CUSTOM_PAYLOAD
import com.pingidentity.mfa.push.PushConstants.KEY_DEVICE_NAME
import com.pingidentity.mfa.push.PushConstants.KEY_MESSAGE
import com.pingidentity.mfa.push.PushConstants.KEY_MESSAGE_ID
import com.pingidentity.mfa.push.PushConstants.KEY_MESSAGE_TEXT
import com.pingidentity.mfa.push.PushConstants.KEY_NUMBERS_CHALLENGE
import com.pingidentity.mfa.push.PushConstants.KEY_PUSH_TYPE
import com.pingidentity.mfa.push.PushConstants.KEY_RAW_JWT
import com.pingidentity.mfa.push.PushConstants.KEY_TIME_INTERVAL
import com.pingidentity.mfa.push.PushConstants.KEY_TTL
import com.pingidentity.mfa.push.PushConstants.KEY_USER_ID
import com.pingidentity.network.HttpClient

/**
 * This class processes push notifications from PingAM service, handling the parsing of JWT messages,
 * sending approvals/denials, and managing device tokens.
 * It implements the [PushHandler] interface to provide the necessary functionality.
 *
 * @property httpClient The HTTP client used for network operations.
 * @property logger The logger for logging messages.
 * @property pushResponder The responder that handles push authentication responses.
 */
internal class PingAMPushHandler(
    httpClient: HttpClient,
    private val logger: Logger = Logger.logger,
    private val pushResponder: PingAMPushResponder = PingAMPushResponder(httpClient, logger)
) : PushHandler {

    companion object {
        // Keys used in the JWT claims
        private const val JWT_MECHANISM_UID = "u"               // Mechanism UID
        private const val JWT_CHALLENGE = "c"                   // Challenge
        private const val JWT_TTL = "t"                         // Time to live
        private const val JWT_TIME_INTERVAL = "i"               // Time interval (created date/time)
        private const val JWT_NOTIFICATION_MESSAGE = "m"        // Message text
        private const val JWT_PUSH_TYPE = "k"                   // Push type
        private const val JWT_CUSTOM_PAYLOAD = "p"              // Custom payload
        private const val JWT_NUMBERS_CHALLENGE = "n"           // Numbers challenge
        private const val JWT_CONTEXT_INFO = "x"                // Context info
        private const val JWT_AM_LOAD_BALANCER_COOKIE = "l"     // AMLB Cookie
        private const val JWT_USER_ID = "d"                     // User ID
    }
    
    /**
     * Check if this handler can process the given message.
     *
     * @param messageData The message data to check.
     * @return True if this handler can process the message, false otherwise.
     */
    override fun canHandle(messageData: Map<String, Any>): Boolean {
        // Check if the message contains the JWT message
        val message = messageData[KEY_MESSAGE]?.toString()
        val messageId = messageData[KEY_MESSAGE_ID]?.toString()
        if (message == null || messageId == null) {
            logger.w("Message data does not contain required keys: $KEY_MESSAGE or $KEY_MESSAGE_ID")
            return false
        }

        // Check if it's a PingAM JWT message by attempting to parse it
        return isValidJwt(message)
    }
    
    /**
     * Check if this handler can process the given message in string format.
     *
     * @param message The message data as a string, typically a JWT.
     * @return True if this handler can process the message, false otherwise.
     */
    override fun canHandle(message: String): Boolean {
        // For direct strings, assume it's the JWT itself
        return isValidJwt(message)
    }
    
    /**
     * Helper method to validate if a string is a valid PingAM JWT.
     */
    private fun isValidJwt(jwt: String): Boolean {
        try {
            // Validate JWT format and check for required fields
            return JwtUtils.isValidJwt(jwt, listOf(JWT_MECHANISM_UID, JWT_CHALLENGE))
        } catch (e: Exception) {
            logger.w("Not a valid PingAM JWT message. Error: ${e.message}")
            return false
        }
    }
    
    /**
     * Parse a PingAM push notification message.
     *
     * @param messageData The message data to parse.
     * @return A map of parsed data.
     */
    override fun parseMessage(messageData: Map<String, Any>): Map<String, Any> {
        val result = mutableMapOf<String, Any>()
        
        try {
            // Get the message ID and JWT from the message data
            val messageId = messageData[KEY_MESSAGE_ID]?.toString() ?: return result
            val message = messageData[KEY_MESSAGE]?.toString() ?: return result
            
            return parseJwtMessage(message, messageId)
        } catch (e: Exception) {
            logger.e("Error parsing PingAM push notification: ${e.message}", e)
            return result
        }
    }
    
    /**
     * Parse a PingAM push notification message from a string.
     *
     * @param message The message data as a string (JWT token).
     * @return A map of parsed data.
     */
    override fun parseMessage(message: String): Map<String, Any> {
        // For direct string input, assume it's the JWT token itself
        // Generate a message ID based on the token's signature part (last part of JWT)
        try {
            val parts = message.split(".")
            val messageId = if (parts.size >= 3) parts[2].take(8) else System.currentTimeMillis().toString()
            
            return parseJwtMessage(message, messageId)
        } catch (e: Exception) {
            logger.e("Error parsing PingAM JWT string: ${e.message}", e)
            return emptyMap()
        }
    }
    
    /**
     * Helper method to parse JWT message and extract relevant data.
     */
    private fun parseJwtMessage(jwt: String, messageId: String): Map<String, Any> {
        val result = mutableMapOf<String, Any>()
        
        try {
            // Add the message ID to the result
            result[KEY_MESSAGE_ID] = messageId
            
            // Store the raw JWT for reference
            result[KEY_RAW_JWT] = jwt
            
            // Parse the JWT claims using the helper
            val claimsMap = JwtUtils.parseJwtClaims(jwt)
            
            // Extract all the claims we need
            val mechanismUid = claimsMap[JWT_MECHANISM_UID]
            val challenge = claimsMap[JWT_CHALLENGE]
            val ttlString = claimsMap[JWT_TTL]
            val timeIntervalString = claimsMap[JWT_TIME_INTERVAL]
            val customMessage = claimsMap[JWT_NOTIFICATION_MESSAGE]
            val pushType = claimsMap[JWT_PUSH_TYPE]
            val customPayload = claimsMap[JWT_CUSTOM_PAYLOAD]
            val numbersChallenge = claimsMap[JWT_NUMBERS_CHALLENGE]
            val contextInfo = claimsMap[JWT_CONTEXT_INFO]
            val userId = claimsMap[JWT_USER_ID]
            
            // Handle AMLB Cookie - it's base64 encoded in the JWT
            val base64loadBalancer = claimsMap[JWT_AM_LOAD_BALANCER_COOKIE]
            val loadBalancer = base64loadBalancer?.let {
                Base64.decode(it.toString(), Base64.NO_WRAP).toString(Charsets.UTF_8)
            }
            
            // Parse the TTL
            var ttl = DEFAULT_TTL_SECONDS // Default TTL seconds
            ttlString?.let { currentTtlString ->
                try {
                    ttl = when (currentTtlString) {
                        is Int -> currentTtlString
                        is String -> currentTtlString.toInt()
                        else -> currentTtlString.toString().toInt()
                    }
                } catch (e: NumberFormatException) {
                    logger.w("TTL was not a valid number: $currentTtlString. Error: ${e.message}")
                }
            }
            
            // Add all extracted data to the result map
            result[KEY_MESSAGE_ID] = messageId
            result[KEY_TTL] = ttl
            mechanismUid?.let { result[KEY_CREDENTIAL_ID] = it }
            challenge?.let { result[KEY_CHALLENGE] = it }
            loadBalancer?.let { result[KEY_AMLB_COOKIE] = it }
            customMessage?.let { result[KEY_MESSAGE_TEXT] = it }
            customPayload?.let { result[KEY_CUSTOM_PAYLOAD] = it }
            numbersChallenge?.let { result[KEY_NUMBERS_CHALLENGE] = it }
            contextInfo?.let { result[KEY_CONTEXT_INFO] = it }
            pushType?.let { result[KEY_PUSH_TYPE] = it }
            userId?.let { result[KEY_USER_ID] = it }
            
            timeIntervalString?.let { currentIntervalString ->
                try {
                    val timeInterval = when (currentIntervalString) {
                        is Long -> currentIntervalString
                        is Int -> currentIntervalString.toLong()
                        is String -> currentIntervalString.toLong()
                        else -> currentIntervalString.toString().toLong()
                    }
                    result[KEY_TIME_INTERVAL] = timeInterval
                } catch (e: NumberFormatException) {
                    logger.w("Time interval was not a valid number: $currentIntervalString. Error: ${e.message}")
                }
            }
            
            // Store the original data for reference
            result[KEY_RAW_JWT] = jwt

            // Additional data includes everything from claimsMap not already extracted
            val additionalData = claimsMap.filterKeys {
                it !in listOf(
                    JWT_MECHANISM_UID, JWT_CHALLENGE, JWT_TTL, JWT_TIME_INTERVAL,
                    JWT_NOTIFICATION_MESSAGE, JWT_PUSH_TYPE, JWT_CUSTOM_PAYLOAD,
                    JWT_NUMBERS_CHALLENGE, JWT_CONTEXT_INFO, JWT_AM_LOAD_BALANCER_COOKIE,
                    JWT_USER_ID
                )
            }
            result[KEY_ADDITIONAL_DATA] = additionalData
            
            logger.d("Successfully parsed PingAM push notification with messageId: $messageId")
        } catch (e: Exception) {
            logger.e("Failed to parse PingAM push notification: ${e.message}", e)
        }
        
        return result
    }
    
    /**
     * Send an approval response for a notification.
     *
     * @param credential The credential to use for the response.
     * @param notification The notification to approve.
     * @return True if the approval was sent successfully, false otherwise.
     */
    override suspend fun sendApproval(
        credential: PushCredential,
        notification: PushNotification,
        params: Map<String, Any>
    ): Boolean {
        logger.d("Sending PingAM approval for notification: ${notification.id}")

        var challengeResponse: String? = null

        when (notification.pushType) {
            PushType.CHALLENGE -> {
                // For challenge-based authentication, we need to get the user-selected challenge response
                val userProvidedResponse = params["challengeResponse"]?.toString()

                if (userProvidedResponse == null) {
                    logger.w("Challenge response is required for challenge-based authentication")
                    return false
                }

                // Use the user-provided challenge response directly
                challengeResponse = userProvidedResponse
                logger.d("Using user-provided challenge response: $challengeResponse")
            }

            PushType.BIOMETRIC -> {
                // For biometric-based authentication, we pass the method used (e.g., "face", "fingerprint")
                val authenticationMethod = params["authenticationMethod"]?.toString() ?: "face" // Default to face biometrics
                logger.d("Using biometric authentication method: $authenticationMethod")
            }

            PushType.DEFAULT -> {
                // Default push notification just needs the standard challenge response
                logger.d("Processing default push notification")
            }
        }

        // Send the authentication response
        return pushResponder.authenticate(
            credential = credential,
            notification = notification,
            approve = true,
            challengeResponse = challengeResponse
        )
    }
    
    /**
     * Send a denial response for a notification.
     *
     * @param credential The credential to use for the response.
     * @param notification The notification to deny.
     * @param params Additional parameters.
     * @return True if the denial was sent successfully, false otherwise.
     */
    override suspend fun sendDenial(
        credential: PushCredential,
        notification: PushNotification,
        params: Map<String, Any>
    ): Boolean {
        logger.d("Sending PingAM denial for notification: ${notification.id}")

        // Send the authentication response with deny=true
        return pushResponder.authenticate(
            credential = credential,
            notification = notification,
            approve = false,
            challengeResponse = null // No challenge response needed for denial
        )
    }

    /**
     * Set device token on the server.
     *
     * @param credential The credential to register or update.
     * @param deviceToken The device token.
     * @param params Additional parameters for registration.
     * @return True if registration was successful, false otherwise.
     */
    override suspend fun setDeviceToken(
        credential: PushCredential,
        deviceToken: String,
        params: Map<String, Any>
    ): Boolean {
        // Get the device name from params or use a default
        val deviceName = params[KEY_DEVICE_NAME]?.toString() ?: DEFAULT_DEVICE_NAME
            
        // Call the push responder to update the device token
        return pushResponder.updateDeviceToken(
            credential = credential,
            deviceToken = deviceToken,
            deviceName = deviceName
        )
    }

    /**
     * Register a push credential on the server.
     *
     * @param credential The credential to register.
     * @param params Additional parameters for registration including messageId.
     * @return True if the registration was successful, false otherwise.
     */
    override suspend fun register(
        credential: PushCredential,
        params: Map<String, Any>
    ): Boolean {
        // PingAM push registration requires a specific endpoint and parameters
        logger.d("Registering PingAM push credential: ${credential.issuer}:${credential.accountName}")
        if (params.isEmpty()) {
            logger.w("No parameters provided for PingAM push registration")
            return false
        }

        // Call the push responder to perform the registration
        return pushResponder.register(
            credential = credential,
            params = params
        )
    }

}

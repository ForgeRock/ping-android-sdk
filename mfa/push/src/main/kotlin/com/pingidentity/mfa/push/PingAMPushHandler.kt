/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.push

import android.util.Base64
import com.pingidentity.mfa.push.PushConstants.KEY_ADDITIONAL_DATA
import com.pingidentity.mfa.push.PushConstants.KEY_AMLB_COOKIE
import com.pingidentity.mfa.push.PushConstants.KEY_CHALLENGE
import com.pingidentity.mfa.push.PushConstants.KEY_CONTEXT_INFO
import com.pingidentity.mfa.push.PushConstants.KEY_CREDENTIAL_ID
import com.pingidentity.mfa.push.PushConstants.KEY_CUSTOM_PAYLOAD
import com.pingidentity.mfa.push.PushConstants.KEY_MESSAGE_TEXT
import com.pingidentity.mfa.push.PushConstants.KEY_NUMBERS_CHALLENGE
import com.pingidentity.mfa.push.PushConstants.KEY_PUSH_TYPE
import com.pingidentity.mfa.push.PushConstants.KEY_RAW_JWT
import com.pingidentity.mfa.push.PushConstants.KEY_TTL
import com.pingidentity.mfa.push.PushConstants.KEY_USER_ID
import com.pingidentity.logger.Logger
import com.pingidentity.mfa.push.PushConstants.DEFAULT_DEVICE_NAME
import com.pingidentity.mfa.push.PushConstants.DEFAULT_TTL_SECONDS
import com.pingidentity.mfa.push.PushConstants.KEY_DEVICE_NAME
import com.pingidentity.mfa.push.PushConstants.KEY_MESSAGE
import com.pingidentity.mfa.push.PushConstants.KEY_MESSAGE_ID
import com.pingidentity.mfa.push.PushConstants.KEY_TIME_INTERVAL
import io.ktor.client.HttpClient
import kotlinx.serialization.json.Json

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
        try {
            // Parse the JWT manually - split into parts
            val parts = message.split(".")
            if (parts.size != 3) {
                logger.w("Not a valid JWT format (should have 3 parts)")
                return false
            }

            // Decode the claims part (second part of the JWT)
            val claimsJson = String(Base64.decode(parts[1], Base64.URL_SAFE), Charsets.UTF_8)
            val jsonParser = Json { ignoreUnknownKeys = true }
            val claimsMap = jsonParser.decodeFromString<Map<String, String>>(claimsJson)
            
            // Check for required fields that identify this as a PingAM push message
            val mechanismUid = claimsMap[JWT_MECHANISM_UID]
            val challenge = claimsMap[JWT_CHALLENGE]
            
            return mechanismUid != null && challenge != null
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
            
            // Parse the JWT manually - split into parts
            val parts = message.split(".")
            if (parts.size != 3) {
                logger.w("Not a valid JWT format (should have 3 parts)")
                return result
            }
            
            // Decode the claims part (second part of the JWT)
            val claimsJson = String(Base64.decode(parts[1], Base64.URL_SAFE), Charsets.UTF_8)
            
            // Parse the JSON into a map
            val jsonParser = Json { ignoreUnknownKeys = true }
            val claimsMap = try {
                jsonParser.decodeFromString<Map<String, String>>(claimsJson)
            } catch (_: Exception) {
                // If we can't decode directly to String values, try with JsonElement and convert
                logger.d("Parsing JWT with JsonElement due to mixed value types")
                @Suppress("UNCHECKED_CAST")
                jsonParser.decodeFromString<Map<String, kotlinx.serialization.json.JsonElement>>(claimsJson)
                    .mapValues { entry -> 
                        val value = entry.value
                        when {
                            value is kotlinx.serialization.json.JsonPrimitive && value.isString -> 
                                value.content
                            else -> value.toString().removeSurrounding("\"")
                        }
                    }
            }
            
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
            val loadBalancer = if (base64loadBalancer != null) {
                Base64.decode(base64loadBalancer, Base64.NO_WRAP).toString(Charsets.UTF_8)
            } else null
            
            // Parse the TTL
            var ttl = DEFAULT_TTL_SECONDS // Default TTL seconds
            if (ttlString != null) {
                try {
                    ttl = ttlString.toInt()
                } catch (e: NumberFormatException) {
                    logger.w("TTL was not a valid number: $ttlString. Error: ${e.message}")
                }
            }
            
            // Add all extracted data to the result map
            result[KEY_MESSAGE_ID] = messageId
            if (mechanismUid != null) {
                result[KEY_CREDENTIAL_ID] = mechanismUid
            }
            if (challenge != null) {
                result[KEY_CHALLENGE] = challenge
            }
            result[KEY_TTL] = ttl
            if (loadBalancer != null) {
                result[KEY_AMLB_COOKIE] = loadBalancer
            }
            if (customMessage != null) {
                result[KEY_MESSAGE_TEXT] = customMessage
            }
            if (customPayload != null) {
                result[KEY_CUSTOM_PAYLOAD] = customPayload
            }
            if (numbersChallenge != null) {
                result[KEY_NUMBERS_CHALLENGE] = numbersChallenge
            }
            if (contextInfo != null) {
                result[KEY_CONTEXT_INFO] = contextInfo
            }
            if (pushType != null) {
                result[KEY_PUSH_TYPE] = pushType
            }
            if (userId != null) {
                result[KEY_USER_ID] = userId
            }
            if (timeIntervalString != null) {
                try {
                    val timeInterval = timeIntervalString.toLong()
                    result[KEY_TIME_INTERVAL] = timeInterval
                } catch (e: NumberFormatException) {
                    logger.w("Time interval was not a valid number: $timeIntervalString. Error: ${e.message}")
                }
            }
            
            // Store the original data for reference
            result[KEY_RAW_JWT] = message

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
        try {
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
        } catch (e: Exception) {
            logger.e("Failed to send PingAM approval: ${e.message}", e)
            return false
        }
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
        try {
            logger.d("Sending PingAM denial for notification: ${notification.id}")

            // Send the authentication response with deny=true
            return pushResponder.authenticate(
                credential = credential,
                notification = notification,
                approve = false,
                challengeResponse = null // No challenge response needed for denial
            )
        } catch (e: Exception) {
            logger.e("Failed to send PingAM denial: ${e.message}", e)
            return false
        }
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

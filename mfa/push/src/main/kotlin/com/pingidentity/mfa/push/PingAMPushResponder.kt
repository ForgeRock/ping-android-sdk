/*
 * Copyright (c) 2025-2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.push

import android.util.Base64
import com.pingidentity.exception.ApiException
import com.pingidentity.logger.Logger
import com.pingidentity.mfa.commons.util.JwtUtils
import com.pingidentity.mfa.push.PushConstants.RESPONSE_ALGORITHM
import com.pingidentity.network.HttpClient
import com.pingidentity.network.HttpResponse
import com.pingidentity.network.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.URL
import java.security.InvalidKeyException
import java.security.NoSuchAlgorithmException
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * PingAM implementation for handling network operations related to Push authentication.
 * This class is responsible for making HTTP requests to the PingAM servers.
 *
 * @property httpClient The HTTP client to use for making requests.
 * @property logger The logger to use for logging.
 */
class PingAMPushResponder(
    private val httpClient: HttpClient,
    private val logger: Logger = Logger.Companion.logger
) {

    /**
     * Register a push credential on the server.
     *
     * @param credential The credential to register.
     * @param params Additional parameters for registration including messageId.
     * @return True if registration was successful, false otherwise.
     */
    suspend fun register(
        credential: PushCredential,
        params: Map<String, Any>
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // Extract registration parameters from credential and params
            val endpoint = credential.registrationEndpoint
            val base64Secret = credential.sharedSecret
            val amlbCookie = params[PushConstants.KEY_ALMB_COOKIE]?.toString()
            val challenge = params[PushConstants.KEY_CHALLENGE]?.toString()

            // Get deviceId if not provided fail
            val deviceId = params[PushConstants.KEY_DEVICE_ID]?.toString()
                ?: throw IllegalArgumentException("Device ID is required for registration")

            // Get messageId if not provided fail
            val messageId = params[PushConstants.KEY_MESSAGE_ID]?.toString()
                ?: throw IllegalArgumentException("Message ID is required for registration")

            // Generate challenge response if challenge is provided or fail if not
            val challengeResponse = if (challenge != null) {
                generateChallengeResponse(base64Secret, challenge)
            } else {
                throw IllegalArgumentException("Challenge is required for registration")
            }

            logger.d("Registering credential with endpoint: $endpoint, messageId: $messageId")

            // Set up payload for the registration
            val payload = mapOf<String, Any>(
                PushConstants.KEY_DEVICE_ID to deviceId,
                PushConstants.KEY_DEVICE_NAME to params[PushConstants.KEY_DEVICE_NAME].toString(),
                PushConstants.KEY_DEVICE_TYPE to PushConstants.ANDROID,
                PushConstants.KEY_COMMUNICATION_TYPE to PushConstants.GCM,
                PushConstants.KEY_MECHANISM_UID to credential.id,
                PushConstants.KEY_RESPONSE to challengeResponse
            )

            // Generate JWT
            val jwt = generateJwt(base64Secret, payload)

            // Make the HTTP request
            val response: HttpResponse = httpClient.request {
                url = endpoint
                header(PushConstants.HEADER_CONTENT_TYPE, PushConstants.APPLICATION_JSON)
                header(
                    PushConstants.HEADER_ACCEPT_API_VERSION,
                    PushConstants.ACCEPT_API_VERSION
                )
                if (amlbCookie != null) {
                    header(PushConstants.HEADER_COOKIE, amlbCookie)
                }
                post(buildJsonObject {
                    put(PushConstants.KEY_MESSAGE_ID, JsonPrimitive(messageId))
                    put(PushConstants.KEY_JWT, JsonPrimitive(jwt))
                })
            }

            // Check if the response was successful
            if (response.status.isSuccess()) {
                return@withContext true
            } else {
                throw ApiException(response.status, response.body())
            }
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            logger.e("Registration failed", e)
            return@withContext false
        }
    }

    /**
     * Update a device token on the server.
     *
     * @param credential The credential to use for the update.
     * @param deviceToken The new device token.
     * @param deviceName The device name.
     * @return True if the update was successful, false otherwise.
     */
    suspend fun updateDeviceToken(
        credential: PushCredential,
        deviceToken: String,
        deviceName: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // Get the endpoint URL
            val url = URL(credential.updateEndpoint)

            // Set up payload for the update
            val payload = mapOf(
                PushConstants.KEY_DEVICE_ID to deviceToken,
                PushConstants.KEY_DEVICE_NAME to deviceName,
                PushConstants.KEY_DEVICE_TYPE to PushConstants.ANDROID,
                PushConstants.KEY_COMMUNICATION_TYPE to PushConstants.GCM
            )

            // Generate JWT with the payload
            val jwt = generateJwt(credential.sharedSecret, payload)

            // Make the HTTP request
            val response: HttpResponse = httpClient.request {
                this.url = url.toString()
                header(PushConstants.HEADER_CONTENT_TYPE, PushConstants.APPLICATION_JSON)
                header(
                    PushConstants.HEADER_ACCEPT_API_VERSION,
                    PushConstants.ACCEPT_API_VERSION
                )
                val requestBody = buildJsonObject {
                    put(PushConstants.KEY_MECHANISM_UID, credential.id)
                    put(PushConstants.KEY_JWT, jwt)
                    credential.userId?.let {
                        put(PushConstants.KEY_USERNAME, it)
                    }
                }

                // Add userId to the request body if available
                post(requestBody)
            }

            // Check if the response was successful
            if (response.status.isSuccess()) {
                return@withContext true
            } else {
                throw ApiException(response.status, response.body())
            }
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            logger.e("Device token update failed", e)
            return@withContext false
        }
    }

    /**
     * Send an authentication response (approve or deny) for a notification.
     *
     * @param credential The credential to use for the authentication.
     * @param notification The notification to respond to.
     * @param approve True to approve, false to deny.
     * @param challengeResponse The challenge response for challenge-based authentication (optional).
     * @return True if the response was sent successfully, false otherwise.
     */
    suspend fun authenticate(
        credential: PushCredential,
        notification: PushNotification,
        approve: Boolean,
        challengeResponse: String?
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // Get the endpoint URL
            val url = URL(credential.authenticationEndpoint)

            // Set up payload based on approval status and challenge response
            val payload = mutableMapOf<String, Any>()

            // Generate challenge response to the notification
            if (notification.challenge != null) {
                val response =
                    generateChallengeResponse(credential.sharedSecret, notification.challenge)
                payload[PushConstants.KEY_RESPONSE] = response
            } else {
                throw IllegalArgumentException("Challenge is required for authentication")
            }

            // For number challenge types
            if (challengeResponse != null) {
                payload[PushConstants.KEY_CHALLENGE_RESPONSE] = challengeResponse
            }

            // For denial
            if (!approve) {
                payload[PushConstants.KEY_DENY] = true
            }

            // Generate JWT with the payload
            val jwt = generateJwt(credential.sharedSecret, payload)

            // Make the HTTP request
            val response: HttpResponse = httpClient.request {
                this.url = url.toString()
                header(PushConstants.HEADER_CONTENT_TYPE, PushConstants.APPLICATION_JSON)
                header(
                    PushConstants.HEADER_ACCEPT_API_VERSION,
                    PushConstants.ACCEPT_API_VERSION
                )
                // Add AMLB cookie if present in the notification
                notification.loadBalancer?.let {
                    header(PushConstants.HEADER_COOKIE, it)
                }
                post(buildJsonObject {
                    put(PushConstants.KEY_MESSAGE_ID, notification.messageId)
                    put(PushConstants.KEY_JWT, jwt)
                })

            }

            // Check if the response was successful
            if (response.status.isSuccess()) {
                return@withContext true
            } else {
                throw ApiException(response.status, response.body())
            }
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            logger.e("Authentication response failed", e)
            return@withContext false
        }
    }

    /**
     * Generate a JWT for authentication.
     *
     * @param base64Secret The base64-encoded secret key.
     * @param claims The claims to include in the JWT.
     * @return The JWT string.
     */
    internal fun generateJwt(base64Secret: String, claims: Map<String, Any>): String {
        try {
            return JwtUtils.generateJwt(base64Secret, claims)
        } catch (e: Exception) {
            logger.e("Error generating JWT", e)
            throw e
        }
    }

    /**
     * Generate a challenge response using the shared secret.
     *
     * @param base64Secret The base64-encoded secret key.
     * @param base64Challenge The base64-encoded challenge.
     * @return The challenge response.
     */
    internal fun generateChallengeResponse(base64Secret: String, base64Challenge: String): String {
        try {
            if (base64Secret.isEmpty()) {
                throw IllegalArgumentException("Secret cannot be empty")
            }

            val secret = Base64.decode(base64Secret, Base64.NO_WRAP)
            val challenge = Base64.decode(base64Challenge, Base64.NO_WRAP)

            // Create HMAC-SHA256 signature
            val mac = Mac.getInstance(RESPONSE_ALGORITHM)
            val secretKey = SecretKeySpec(secret, mac.algorithm)
            mac.init(secretKey)
            val result = mac.doFinal(challenge)

            // Encode the result in Base64
            return Base64.encodeToString(result, Base64.NO_WRAP)
        } catch (e: NoSuchAlgorithmException) {
            throw e
        } catch (e: InvalidKeyException) {
            throw IllegalArgumentException("Invalid secret key", e)
        } catch (e: IllegalArgumentException) {
            throw e
        }
    }


    /**
     * Helper function to convert any Map<String, Any> to a JsonElement
     */
    private fun mapToJsonElement(map: Map<String, Any>): JsonElement {
        return buildJsonObject {
            map.forEach { (key, value) ->
                when (value) {
                    is String -> put(key, JsonPrimitive(value))
                    is Number -> put(key, JsonPrimitive(value))
                    is Boolean -> put(key, JsonPrimitive(value))
                    is Map<*, *> -> @Suppress("UNCHECKED_CAST") put(
                        key,
                        mapToJsonElement(value as Map<String, Any>)
                    )

                    else -> put(key, JsonPrimitive(value.toString()))
                }
            }
        }
    }

}
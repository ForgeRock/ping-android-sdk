/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.push

import android.util.Base64
import com.pingidentity.logger.Logger
import io.ktor.client.HttpClient
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.net.URL
import java.security.InvalidKeyException
import java.security.NoSuchAlgorithmException
import java.util.Date
import java.util.UUID
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
            val messageId =
                params[PushConstants.KEY_MESSAGE_ID]?.toString() ?: return@withContext false
            val amlbCookie = params[PushConstants.KEY_ALMB_COOKIE]?.toString()
            val challenge = params[PushConstants.KEY_CHALLENGE]?.toString()

            val challengeResponse =
                challenge?.let { generateChallengeResponse(credential.sharedSecret, it) }

            logger.d("Registering credential with endpoint: $endpoint, messageId: $messageId")

            // Set up claims for JWT
            val claims = mapOf(
                PushConstants.KEY_MESSAGE_ID to messageId,
                PushConstants.KEY_ACTION to PushConstants.ACTION_REGISTER
            )

            // Set up payload for the registration
            val payload = mapOf(
                PushConstants.KEY_DEVICE_ID to params[PushConstants.KEY_DEVICE_ID]?.toString(),
                PushConstants.KEY_DEVICE_NAME to params[PushConstants.KEY_DEVICE_NAME]?.toString(),
                PushConstants.KEY_DEVICE_TYPE to PushConstants.ANDROID,
                PushConstants.KEY_COMMUNICATION_TYPE to PushConstants.GCM,
                PushConstants.KEY_MECHANISM_UID to credential.resourceId,
                PushConstants.KEY_RESPONSE to challengeResponse
            )

            // Generate JWT
            val jwt = generateJwt(base64Secret, claims)

            // Create request body
            val requestBody = mapOf(
                PushConstants.KEY_JWT to jwt,
                PushConstants.KEY_PAYLOAD to payload
            )

            // Make the HTTP request
            val response: HttpResponse = httpClient.post {
                url(endpoint)
                contentType(ContentType.Application.Json)
                headers {
                    append(PushConstants.HEADER_CONTENT_TYPE, PushConstants.APPLICATION_JSON)
                    append(
                        PushConstants.HEADER_ACCEPT_API_VERSION,
                        PushConstants.ACCEPT_API_VERSION
                    )
                    if (amlbCookie != null) {
                        append(PushConstants.HEADER_COOKIE, amlbCookie)
                    }
                }
                setBody(Json.Default.encodeToString(mapToJsonElement(requestBody)))
            }

            // Check if the response was successful
            return@withContext response.status == HttpStatusCode.Companion.OK
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

            // Set up keys for JWT
            val keys = mapOf(
                PushConstants.KEY_MECHANISM_UID to credential.id,
                PushConstants.KEY_USERNAME to credential.userId
            )

            // Set up payload for the update
            val payload = mapOf(
                PushConstants.KEY_DEVICE_ID to deviceToken,
                PushConstants.KEY_DEVICE_NAME to deviceName,
                PushConstants.KEY_DEVICE_TYPE to PushConstants.ANDROID,
                PushConstants.KEY_COMMUNICATION_TYPE to PushConstants.GCM
            )

            // Generate JWT with the action and keys
            val claims = keys.toMutableMap().apply {
                put(PushConstants.KEY_ACTION, PushConstants.ACTION_UPDATE)
            }
            val jwt = generateJwt(credential.sharedSecret, claims as Map<String, Any>)

            // Make the HTTP request
            val response: HttpResponse = httpClient.post {
                url(url.toString())
                contentType(ContentType.Application.Json)
                headers {
                    append(PushConstants.HEADER_CONTENT_TYPE, PushConstants.APPLICATION_JSON)
                    append(
                        PushConstants.HEADER_ACCEPT_API_VERSION,
                        PushConstants.ACCEPT_API_VERSION
                    )
                }
                val requestBody = mapOf(
                    PushConstants.KEY_JWT to jwt,
                    PushConstants.KEY_PAYLOAD to payload
                )
                setBody(Json.Default.encodeToString(mapToJsonElement(requestBody)))
            }

            // Check if the response was successful
            return@withContext response.status == HttpStatusCode.Companion.OK
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

            // Set up keys for JWT
            val keys = mapOf(
                PushConstants.KEY_MESSAGE_ID to notification.messageId,
                PushConstants.KEY_MECHANISM_UID to credential.id
            )

            // Set up payload based on approval status and challenge response
            val payload = mutableMapOf<String, Any>()

            // Generate challenge response to the notification
            if (notification.challenge != null) {
                val response =
                    generateChallengeResponse(credential.sharedSecret, notification.challenge)
                payload[PushConstants.KEY_RESPONSE] = response
            }

            // For number challenge types
            if (challengeResponse != null) {
                payload[PushConstants.KEY_CHALLENGE_RESPONSE] = challengeResponse
            }

            // For denial
            if (!approve) {
                payload[PushConstants.KEY_DENY] = true
            }

            // Generate JWT with the action and keys
            val claims = keys.toMutableMap().apply {
                put(PushConstants.KEY_ACTION, PushConstants.ACTION_AUTH)
            }
            val jwt = generateJwt(credential.sharedSecret, claims)

            // Make the HTTP request
            val response: HttpResponse = httpClient.post {
                url(url.toString())
                contentType(ContentType.Application.Json)
                headers {
                    append(PushConstants.HEADER_CONTENT_TYPE, PushConstants.APPLICATION_JSON)
                    append(
                        PushConstants.HEADER_ACCEPT_API_VERSION,
                        PushConstants.ACCEPT_API_VERSION
                    )
                    // Add AMLB cookie if present in the notification
                    notification.loadBalancer?.let {
                        append(PushConstants.HEADER_COOKIE, it)
                    }
                }
                val requestBody = mapOf(
                    PushConstants.KEY_JWT to jwt,
                    PushConstants.KEY_PAYLOAD to payload
                )
                setBody(Json.Default.encodeToString(mapToJsonElement(requestBody)))
            }

            // Check if the response was successful
            return@withContext response.status == HttpStatusCode.Companion.OK
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
            // Create JWT header
            val header = mapOf(
                PushConstants.JWT_ALG to PushConstants.JWT_ALG_HS256,
                PushConstants.JWT_TYP to PushConstants.JWT_TYP_VALUE
            )

            // Convert header to JSON and then to base64
            val headerJson = Json.Default.encodeToString(mapToJsonElement(header))
            val base64Header = Base64.encodeToString(headerJson.toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP)

            // Add timestamp and UUID to claims
            val claimsWithIat = claims.toMutableMap().apply {
                put(PushConstants.JWT_IAT, Date().time / 1000)
                put(PushConstants.JWT_JTI, UUID.randomUUID().toString())
            }

            // Convert claims to JSON and then to base64
            val claimsJson = Json.Default.encodeToString(mapToJsonElement(claimsWithIat))
            val base64Claims = Base64.encodeToString(claimsJson.toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP)

            // Combine header and claims
            val dataToSign = "$base64Header.$base64Claims"

            // Create signature
            val secret = Base64.decode(base64Secret, Base64.DEFAULT)
            val mac = Mac.getInstance(PushConstants.JWT_ALGORITHM)
            val secretKey = SecretKeySpec(secret, mac.algorithm)
            mac.init(secretKey)
            val signature = mac.doFinal(dataToSign.toByteArray())
            val base64Signature = Base64.encodeToString(signature, Base64.URL_SAFE or Base64.NO_WRAP)

            // Return complete JWT
            return "$base64Header.$base64Claims.$base64Signature"
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
            val secret = Base64.decode(base64Secret, Base64.DEFAULT)
            val challenge = Base64.decode(base64Challenge, Base64.DEFAULT)

            val mac = Mac.getInstance(PushConstants.JWT_ALGORITHM)
            val secretKey = SecretKeySpec(secret, PushConstants.JWT_ALGORITHM)
            mac.init(secretKey)

            val result = mac.doFinal(challenge)
            return Base64.encodeToString(result, Base64.NO_WRAP)
        } catch (e: NoSuchAlgorithmException) {
            logger.e("Algorithm not supported", e)
            throw e
        } catch (e: InvalidKeyException) {
            logger.e("Invalid key", e)
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

                    null -> put(key, JsonNull)
                    else -> put(key, JsonPrimitive(value.toString()))
                }
            }
        }
    }

}
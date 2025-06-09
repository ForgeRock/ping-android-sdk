/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.push

import com.pingidentity.mfa.commons.model.MfaPushNotification
import org.json.JSONObject
import java.util.Date
import java.util.UUID

/**
 * Represents a push notification message received for authentication.
 *
 * @property id Unique identifier for this push notification.
 * @property credentialId ID of the associated PushCredential.
 * @property messageId Server message ID for this push notification.
 * @property messageText The human-readable message to display to the user.
 * @property challenge The authentication challenge received in the notification.
 * @property customPayload Additional custom payload data.
 * @property numbersChallenge Challenge with numeric format.
 * @property amlbCookie Cookie for load balancing.
 * @property contextInfo Additional context information.
 * @property pushType The type of push notification (DEFAULT, CHALLENGE, BIOMETRIC).
 * @property ttl Time to live in seconds for this notification.
 * @property createdAt Time when this notification was created.
 * @property approved Whether this notification has been approved by the user.
 * @property pending Whether this notification is pending (not yet approved or denied).
 * @property responded Whether the user has responded to this notification.
 * @property expired Whether this notification has expired.
 */
data class PushNotification(
    override val id: String,
    override val credentialId: String,
    override val messageId: String,
    override val messageText: String,
    override val challenge: String,
    override val customPayload: String,
    override val numbersChallenge: String,
    override val amlbCookie: String,
    override val contextInfo: String,
    val pushType: PushType,
    override val ttl: Int,
    override val createdAt: Date,
    override var approved: Boolean = false,
    override var pending: Boolean = true
) : MfaPushNotification {
    
    /**
     * Creates a new PushNotification instance with all attributes.
     *
     * @param credentialId ID of the associated PushCredential.
     * @param messageId Server message ID for this push notification.
     * @param messageText The human-readable message to display to the user.
     * @param challenge The authentication challenge received in the notification.
     * @param customPayload Additional custom payload data.
     * @param numbersChallenge Challenge with numeric format.
     * @param amlbCookie Cookie for load balancing.
     * @param contextInfo Additional context information.
     * @param pushType The type of push notification.
     * @param ttl Time to live in seconds for this notification.
     */
    constructor(
        credentialId: String,
        messageId: String,
        messageText: String,
        challenge: String,
        customPayload: String,
        numbersChallenge: String,
        amlbCookie: String,
        contextInfo: String,
        pushType: PushType,
        ttl: Int
    ) : this(
        id = UUID.randomUUID().toString(),
        credentialId = credentialId,
        messageId = messageId,
        messageText = messageText,
        challenge = challenge,
        customPayload = customPayload,
        numbersChallenge = numbersChallenge,
        amlbCookie = amlbCookie,
        contextInfo = contextInfo,
        pushType = pushType,
        ttl = ttl,
        createdAt = Date(),
        approved = false,
        pending = true
    )
    
    /**
     * String representation of the push notification type.
     */
    override val type: String
        get() = this.pushType.toString()

    /**
     * Checks if the user has responded to this notification.
     *
     * @return true if the notification has been approved or is not pending, false otherwise.
     */
    val responded: Boolean
        get() = approved || !pending

    /**
     * Checks if this notification has expired.
     *
     * @return true if the notification has expired, false otherwise.
     */
    val expired: Boolean
        get() {
            val expirationTime = createdAt.time + (ttl * 1000L)
            return System.currentTimeMillis() > expirationTime
        }
    
    /**
     * Mark this notification as approved.
     */
    fun markApproved() {
        approved = true
        pending = false
    }

    /**
     * Mark this notification as denied.
     */
    fun markDenied() {
        approved = false
        pending = false
    }

    /**
     * Convert the PushNotification to a JSONObject representation.
     *
     * @return A JSONObject representing this PushNotification.
     */
    fun toJsonObject(): JSONObject {
        val json = JSONObject()
        json.put("id", id)
        json.put("credentialId", credentialId)
        json.put("messageId", messageId)
        json.put("messageText", messageText)
        json.put("challenge", challenge)
        json.put("customPayload", customPayload)
        json.put("numbersChallenge", numbersChallenge)
        json.put("amlbCookie", amlbCookie)
        json.put("contextInfo", contextInfo)
        json.put("type", type)
        json.put("ttl", ttl)
        json.put("createdAt", createdAt.time)
        json.put("approved", approved)
        json.put("pending", pending)
        return json
    }
    
    /**
     * Convert the PushNotification to a JSON string representation.
     * This method satisfies the MfaPushNotification interface.
     *
     * @return A JSON string representing this PushNotification.
     */
    override fun toJson(): String {
        return toJsonObject().toString()
    }

}

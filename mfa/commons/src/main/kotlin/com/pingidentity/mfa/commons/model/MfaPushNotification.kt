/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.commons.model

import java.util.Date

/**
 * Interface representing a Push notification message.
 */
interface MfaPushNotification {
    /**
     * Unique identifier for this push notification.
     */
    val id: String
    
    /**
     * ID of the associated PushCredential.
     */
    val credentialId: String
    
    /**
     * Server message ID for this push notification.
     */
    val messageId: String
    
    /**
     * The human-readable message to display to the user.
     */
    val messageText: String
    
    /**
     * The authentication challenge received in the notification.
     */
    val challenge: String
    
    /**
     * Additional custom payload data.
     */
    val customPayload: String
    
    /**
     * Challenge with numeric format.
     */
    val numbersChallenge: String
    
    /**
     * Cookie for load balancing.
     */
    val amlbCookie: String
    
    /**
     * Additional context information.
     */
    val contextInfo: String
    
    /**
     * The type of push notification.
     */
    val type: String
    
    /**
     * Time to live in seconds for this notification.
     */
    val ttl: Int
    
    /**
     * Time when this notification was created.
     */
    val createdAt: Date
    
    /**
     * Whether this notification has been approved by the user.
     */
    val approved: Boolean
    
    /**
     * Whether this notification is pending (not yet approved or denied).
     */
    val pending: Boolean
    
    /**
     * Convert the notification to a JSON string representation.
     */
    fun toJson(): String
}

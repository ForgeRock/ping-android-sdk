/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.commons.model

import java.util.Date

/**
 * Interface representing a Push device token for Firebase Cloud Messaging (FCM).
 */
interface MfaPushDeviceToken {
    /**
     * The FCM token ID.
     */
    val tokenId: String
    
    /**
     * The timestamp when this token was received.
     */
    val createdAt: Date
    
    /**
     * Convert the token to a JSON string representation.
     */
    fun toJson(): String
}

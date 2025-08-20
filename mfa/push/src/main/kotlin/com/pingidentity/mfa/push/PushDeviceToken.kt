/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.push

import com.pingidentity.mfa.commons.json
import kotlinx.serialization.Serializable
import java.util.Date
import java.util.UUID

/**
 * Represents a device token for push notifications.
 * This model holds information about the FCM or other messaging service device token.
 *
 * @property id Unique identifier for this device token record
 * @property tokenId The actual device token string issued by the messaging service
 * @property createdAt The timestamp when this token was created/received
 */
@Serializable
data class PushDeviceToken(
    val id: String = UUID.randomUUID().toString(),
    val tokenId: String,
    @Serializable(with = DateSerializer::class)
    val createdAt: Date = Date()
) {
    companion object {
        /**
         * Creates a PushDeviceToken from JSON string.
         * 
         * @param jsonString The JSON string representation of a PushDeviceToken
         * @return The deserialized PushDeviceToken, or null if deserialization fails
         */
        fun fromJson(jsonString: String): PushDeviceToken {
            return json.decodeFromString(jsonString)
        }
    }

    /**
     * Converts this PushDeviceToken to a JSON string.
     * 
     * @return The JSON string representation of this PushDeviceToken
     */
    fun toJson(): String {
        return json.encodeToString(serializer(), this)
    }
}

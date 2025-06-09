/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.push

import com.pingidentity.mfa.commons.model.MfaPushDeviceToken
import org.json.JSONException
import org.json.JSONObject
import java.util.Date

/**
 * Represents a Firebase Cloud Messaging (FCM) device token with its associated ID and timestamp.
 *
 * @property tokenId The FCM token ID.
 * @property createdAt The timestamp when this token was received.
 */
class PushDeviceToken private constructor(
    override val tokenId: String,
    override val createdAt: Date
) : MfaPushDeviceToken {

    /**
     * Constructs a new PushDeviceToken.
     *
     * @param tokenId The FCM token ID (cannot be null or empty).
     * @throws IllegalArgumentException If tokenId is null or empty.
     */
    constructor(tokenId: String) : this(
        tokenId = tokenId,
        createdAt = Date()
    ) {
        require(tokenId.isNotEmpty()) { "Token ID cannot be empty" }
    }

    /**
     * Returns a string representation of the object.
     */
    override fun toString(): String {
        return "PushDeviceToken(tokenId='$tokenId', createdAt=$createdAt)"
    }

    /**
     * Convert the PushDeviceToken to a JSONObject representation.
     *
     * @return A JSONObject representing this PushDeviceToken.
     */
    fun toJsonObject(): JSONObject {
        val json = JSONObject()
        json.put("tokenId", tokenId)
        json.put("createdAt", createdAt.time)
        return json
    }

    /**
     * Convert the PushDeviceToken to a JSON string representation.
     *
     * @return A string representation of this PushDeviceToken.
     */
    override fun toJson(): String {
        return toJsonObject().toString()
    }

    companion object {
        /**
         * Create a PushDeviceToken from a JSONObject.
         *
         * @param json The JSONObject containing token data.
         * @return The created PushDeviceToken.
         * @throws JSONException if the JSON is invalid or missing required fields.
         */
        fun fromJson(json: JSONObject): PushDeviceToken {
            val tokenId = json.getString("tokenId")
            val createdAt = Date(json.getLong("createdAt"))
            return PushDeviceToken(tokenId, createdAt)
        }
    }
}

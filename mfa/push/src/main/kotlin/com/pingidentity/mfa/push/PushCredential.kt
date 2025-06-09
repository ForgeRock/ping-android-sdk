/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.push

import com.pingidentity.mfa.commons.model.MfaPushCredential
import org.json.JSONObject
import java.util.Date
import java.util.UUID

/**
 * PushCredential represents a push notification credential for authentication.
 *
 * @property id Unique identifier for the credential (local ID).
 * @property userId User identifier on the server.
 * @property resourceId Server-side device identifier.
 * @property issuer The name of the issuer for this credential.
 * @property displayIssuer The name of the issuer for this credential, editable by the user.
 * @property accountName The account name (username) associated with this credential.
 * @property displayAccountName The account name (username) associated with this credential, editable by the user.
 * @property serverEndpoint The endpoint URL for the push server.
 * @property sharedSecret The shared secret for enhanced security.
 * @property createdAt The timestamp when this credential was created.
 * @property imageURL Optional URL for the issuer's logo or image.
 * @property backgroundColor Optional background color for the credential.
 * @property policies Optional Authenticator Policies in a JSON String format for the credential.
 * @property lockingPolicy Optional lName of the Policy locking the credential.
 * @property isLocked Indicates whether the credential is locked.
 */
data class PushCredential(
    override val id: String = UUID.randomUUID().toString(),
    override val userId: String = "",
    override val resourceId: String = "",
    override val issuer: String,
    override val displayIssuer: String,
    override val accountName: String,
    override val displayAccountName: String,
    override val serverEndpoint: String,
    override val sharedSecret: String,
    override val createdAt: Date = Date(),
    override val imageURL: String? = null,
    override val backgroundColor: String? = null,
    override val policies: String? = null,
    override val lockingPolicy: String? = null,
    override val isLocked: Boolean = false
) : MfaPushCredential {

    companion object {
        /**
         * Create an PushCredential from a URI.
         * Format: pushauth://push/issuer:accountName?r=regEndpoint&a=authEndpoint&s=sharedSecret&d=userId
         * Format: mfauth://push/issuer:accountName?r=regEndpoint&a=authEndpoint&s=sharedSecret&d=userId
         *
         * @param uri The URI string.
         * @return An PushCredential.
         * @throws IllegalArgumentException if the URI is invalid.
         */
        @JvmStatic
        fun fromUri(uri: String): PushCredential {
            return PushUriParser.parse(uri)
        }
    }

    /**
     * Convert the PushCredential to a JSONObject representation.
     *
     * @return A JSONObject representing this PushCredential.
     */
    private fun toJsonObject(): JSONObject {
        val json = JSONObject()
        json.put("id", id)
        json.put("userId", userId)
        json.put("resourceId", resourceId)
        json.put("issuer", issuer)
        json.put("displayIssuer", displayIssuer)
        json.put("accountName", accountName)
        json.put("displayAccountName", displayAccountName)
        json.put("serverEndpoint", serverEndpoint)
        json.put("sharedSecret", sharedSecret)
        json.put("createdAt", createdAt.time)
        json.put("imageURL", imageURL)
        json.put("backgroundColor", backgroundColor)
        json.put("policies", policies)
        json.put("lockingPolicy", lockingPolicy)
        json.put("isLocked", isLocked)
        return json
    }
    
    /**
     * Convert the PushCredential to a JSON string representation.
     *
     * @return A string representation of this PushCredential.
     */
    override fun toJson(): String {
        return toJsonObject().toString()
    }

}

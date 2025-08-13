/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.push

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import java.util.Date
import java.util.UUID

/**
 * Represents a push credential for receiving push notifications.
 * This model holds all necessary information to identify and interact with push notifications.
 *
 * @property id Unique identifier for the credential (local ID).
 * @property userId User identifier on the server.
 * @property resourceId Server-side device identifier. Default to id if resourceId is not provided.
 *                      In PingAM, resourceId is the same as the local id.
 * @property issuer The name of the issuer for this credential. Should be a unique identifier for the service or
 *                  application issuing the credential.
 * @property displayIssuer The name of the issuer for this credential, editable by the user.
 * @property accountName The account name (username) associated with this credential. Should be unique per user.
 * @property displayAccountName The account name (username) associated with this credential, editable by the user.
 * @property serverEndpoint The endpoint where authentication responses should be sent.
 * @property sharedSecret The secret key used for cryptographic operations.
 * @property createdAt The timestamp when this credential was created.
 * @property imageURL Optional URL for the issuer's logo or image.
 * @property backgroundColor Optional background color for the credential.
 * @property policies Optional Authenticator Policies in a JSON String format for the credential.
 * @property lockingPolicy Optional name of the Policy locking the credential.
 * @property isLocked Indicates whether the credential is locked.
 * @property platform The platform for which this credential is intended (e.g., PING_AM).
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class PushCredential(
    val id: String = UUID.randomUUID().toString(),
    val userId: String? = null,
    val resourceId: String = id,
    val issuer: String,
    val displayIssuer: String = issuer,
    val accountName: String,
    val displayAccountName: String = accountName,
    val serverEndpoint: String,
    val sharedSecret: String,
    @Serializable(with = DateSerializer::class)
    val createdAt: Date = Date(),
    val imageURL: String? = null,
    val backgroundColor: String? = null,
    val policies: String? = null,
    val lockingPolicy: String? = null,
    @EncodeDefault
    val isLocked: Boolean = false,
    val platform: String = PushPlatform.PING_AM.name
) {

    /**
     * Returns the registration endpoint for this credential.
     * This is used to register the device with the PingAM servers.
     */
    val registrationEndpoint: String
        get() = "${serverEndpoint}?_action=register"

    /**
     * Returns the authentication endpoint for this credential.
     * This is used to authenticate the user via push notification with the PingAM servers.
     */
    val authenticationEndpoint: String
        get() = "${serverEndpoint}?_action=authenticate"

    /**
     * Returns the update endpoint for this credential.
     * This is used to refresh or update the device token with the PingAM servers.
     */
    val updateEndpoint: String
        get() = "${serverEndpoint}?_action=refresh"


    companion object {
        /**
         * Create a PushCredential from a JSON string.
         *
         * @param jsonString The JSON string.
         * @return A PushCredential.
         * @throws kotlinx.serialization.SerializationException if the JSON is invalid.
         */
        @JvmStatic
        fun fromJson(jsonString: String): PushCredential {
            val json = Json { 
                ignoreUnknownKeys = true 
                isLenient = true
            }
            return json.decodeFromString(jsonString)
        }
        
        /**
         * Create a PushCredential from a URI.
         * Format: pushauth://push/Issuer:AccountName?a=endpoint&b=SharedSecret
         *
         * @param uri The URI string.
         * @return A PushCredential.
         * @throws IllegalArgumentException if the URI is invalid.
         */
        @JvmStatic
        fun fromUri(uri: String): PushCredential {
            return PushUriParser.parse(uri)
        }
    }
    
    /**
     * Converts this credential to a JSON string representation.
     *
     * @return A JSON string representing this credential.
     */
    fun toJson(): String {
        // Create a custom instance of Json with pretty printing for better readability
        val json = Json { 
            prettyPrint = false
            encodeDefaults = true
            ignoreUnknownKeys = true
        }
        
        // Use the built-in serialization directly
        return json.encodeToString(serializer(), this)
    }
    
    /**
     * Returns a string representation of this credential.
     *
     * @return String representation of this credential.
     */
    override fun toString(): String {
        return "PushCredential(id='$id', issuer='$issuer', displayIssuer='$displayIssuer', " +
               "accountName='$accountName', displayAccountName='$displayAccountName', " +
               "platform=$platform, userId='$userId', resourceId='$resourceId', " +
               "serverEndpoint='$serverEndpoint', createdAt=$createdAt, " +
               "imageURL=$imageURL, backgroundColor=$backgroundColor, " +
               "policies=$policies, lockingPolicy=$lockingPolicy, isLocked=$isLocked)"
    }
}

/**
 * Custom serializer for the Date class to convert to/from Long (milliseconds since epoch)
 */
object DateSerializer : KSerializer<Date> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Date", PrimitiveKind.LONG)
    override fun serialize(encoder: Encoder, value: Date) = encoder.encodeLong(value.time)
    override fun deserialize(decoder: Decoder): Date = Date(decoder.decodeLong())
}

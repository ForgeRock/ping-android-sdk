/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.oath

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Transient
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import com.pingidentity.mfa.commons.json
import java.util.Date
import java.util.UUID

/**
 * Represents an OATH (TOTP/HOTP) credential.
 * This model holds all necessary information to generate OTP codes and identify the credential.
 *
 * @property id Unique identifier for the credential (local ID).
 * @property userId User identifier on the server.
 * @property resourceId Server-side device identifier.
 * @property issuer The name of the issuer for this credential.
 * @property displayIssuer The name of the issuer for this credential, editable by the user.
 * @property accountName The account name (username) associated with this credential.
 * @property displayAccountName The account name (username) associated with this credential, editable by the user.
 * @property oathType The type of credential (TOTP or HOTP).
 * @property secret The shared secret key used to generate OTP codes.
 * @property oathAlgorithm The HMAC algorithm used (SHA1, SHA256, SHA512).
 * @property digits The number of digits in the generated codes.
 * @property period For TOTP, the time period in seconds for which a code is valid.
 * @property counter For HOTP, the counter value used to generate the next code.
 * @property createdAt The timestamp when this credential was created.
 * @property imageURL Optional URL for the issuer's logo or image.
 * @property backgroundColor Optional background color for the credential.
 * @property policies Optional Authenticator Policies in a JSON String format for the credential.
 * @property lockingPolicy Optional lName of the Policy locking the credential.
 * @property isLocked Indicates whether the credential is locked.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class OathCredential(
    val id: String = UUID.randomUUID().toString(),
    val userId: String? = null,
    val resourceId: String? = null,
    val issuer: String,
    val displayIssuer: String = issuer,
    val accountName: String,
    val displayAccountName: String = accountName,
    @Serializable(with = OathTypeSerializer::class)
    val oathType: OathType,
    val secret: String,
    @Serializable(with = OathAlgorithmSerializer::class)
    val oathAlgorithm: OathAlgorithm = OathAlgorithm.SHA1,
    val digits: Int = 6,
    val period: Int = 30,
    val counter: Long = 0L,
    @Serializable(with = DateSerializer::class)
    val createdAt: Date = Date(),
    val imageURL: String? = null,
    val backgroundColor: String? = null,
    val policies: String? = null,
    var lockingPolicy: String? = null,
    @EncodeDefault
    var isLocked: Boolean = false
) {
    /**
     * The type of credential (TOTP or HOTP) as String.
     */
    @Transient
    val type: String = oathType.name

    /**
     * The HMAC algorithm used as String.
     */
    @Transient
    val algorithm: String = oathAlgorithm.name
    
    companion object {
        /**
         * Create an OathCredential from a URI.
         * Format: otpauth://totp/Issuer:AccountName?secret=SECRET&issuer=Issuer&algorithm=SHA1&digits=6&period=30
         * Format: otpauth://hotp/Issuer:AccountName?secret=SECRET&issuer=Issuer&algorithm=SHA1&digits=6&counter=0
         *
         * @param uri The URI string.
         * @return An OathCredential.
         * @throws IllegalArgumentException if the URI is invalid.
         */
        suspend fun fromUri(uri: String): OathCredential {
            return OathUriParser.parse(uri)
        }
        
        /**
         * Create an OathCredential from a JSON string.
         *
         * @param jsonString The JSON string.
         * @return An OathCredential.
         * @throws SerializationException if the JSON is invalid.
         */
        fun fromJson(jsonString: String): OathCredential {
            return json.decodeFromString(jsonString)
        }
    }

    /**
     * Lock this credential due to policy violations.
     *
     * @param policyName The name of the policy that caused the lock.
     */
    fun lockCredential(policyName: String) {
        isLocked = true
        lockingPolicy = policyName
    }

    /**
     * Unlock this credential.
     * Clears any locking policy information.
     */
    fun unlockCredential() {
        isLocked = false
        lockingPolicy = null
    }

    /**
     * Convert this credential to a URI string.
     *
     * @return A URI string for this credential.
     */
    suspend fun toUri(): String {
        return OathUriParser.format(this)
    }

    /**
     * Converts this credential to a JSON string representation.
     *
     * @return A JSON string representing this credential.
     */
    fun toJson(): String {
        // Use the built-in serialization directly
        return json.encodeToString(serializer(), this)
    }

    /**
     * Returns a string representation of this credential.
     *
     * @return String representation of this credential.
     */
    override fun toString(): String {
        return "OathCredential(id='$id', issuer='$issuer', displayIssuer='$displayIssuer', " +
               "accountName='$accountName', displayAccountName='$displayAccountName', " +
               "type=$oathType, userId='$userId', resourceId='$resourceId', " +
               "algorithm=$oathAlgorithm, digits=$digits, period=$period, counter=$counter, " +
               "createdAt=$createdAt, imageURL=$imageURL, backgroundColor=$backgroundColor, " +
               "policies=$policies, lockingPolicy=$lockingPolicy, isLocked=$isLocked)"
    }
}

/**
 * Custom serializer for the OathType enum
 */
object OathTypeSerializer : KSerializer<OathType> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("OathType", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: OathType) = encoder.encodeString(value.name)
    override fun deserialize(decoder: Decoder): OathType = OathType.fromString(decoder.decodeString())
}

/**
 * Custom serializer for the OathAlgorithm enum
 */
object OathAlgorithmSerializer : KSerializer<OathAlgorithm> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("OathAlgorithm", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: OathAlgorithm) = encoder.encodeString(value.name)
    override fun deserialize(decoder: Decoder): OathAlgorithm = OathAlgorithm.fromString(decoder.decodeString())
}

/**
 * Custom serializer for the Date class to convert to/from Long (milliseconds since epoch)
 */
object DateSerializer : KSerializer<Date> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Date", PrimitiveKind.LONG)
    override fun serialize(encoder: Encoder, value: Date) = encoder.encodeLong(value.time)
    override fun deserialize(decoder: Decoder): Date = Date(decoder.decodeLong())
}

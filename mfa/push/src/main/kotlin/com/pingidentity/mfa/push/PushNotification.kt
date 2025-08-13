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
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.Date
import java.util.UUID

/**
 * Represents a push notification authentication challenge.
 * This model holds all necessary information about a push notification challenge.
 *
 * @property id Unique identifier for this notification.
 * @property credentialId The ID of the associated credential.
 * @property ttl Time to live in seconds for this notification.
 * @property messageId Optional message ID from the push provider.
 * @property messageText Optional message to display to the user.
 * @property customPayload Optional additional custom payload data.
 * @property challenge Optional challenge data (e.g., verification code).
 * @property numbersChallenge Optional challenge with numeric format.
 * @property loadBalancer Optional cookie for load balancing. Used to route the request to the correct server in PingAM.
 * @property contextInfo Optional additional context information. In PingAM, this may contain details such as IP address,
 *                       user agent, location, etc.
 * @property pushType The type of push notification (DEFAULT, CHALLENGE, BIOMETRIC).
 * @property createdAt Timestamp when this notification was created.
 * @property respondedAt Timestamp when the user responded to this notification.
 * @property additionalData Optional additional custom data associated with this notification. This is a map of key-value pairs.
  *                         The values can be of any type (String, Number, Boolean, Map, List, etc.).
  *                         This field is serialized/deserialized as a JSON string.
 * @property approved Whether this notification has been approved by the user.
 * @property pending Whether this notification is pending (not yet approved or denied).
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class PushNotification(
    val id: String = UUID.randomUUID().toString(),
    val credentialId: String,
    val ttl: Int,
    val messageId: String,
    val messageText: String? = null,
    val customPayload: String? = null,
    val challenge: String? = null,
    var numbersChallenge: String? = null,
    val loadBalancer: String? = null,
    val contextInfo: String? = null,
    @Serializable(with = PushTypeSerializer::class)
    val pushType: PushType,
    @Serializable(with = DateSerializer::class)
    val createdAt: Date = Date(),
    @Serializable(with = NullableDateSerializer::class)
    var respondedAt: Date? = null,
    @Serializable(with = AdditionalDataSerializer::class)
    val additionalData: Map<String, Any>? = null,
    @EncodeDefault
    var approved: Boolean = false,
    @EncodeDefault
    var pending: Boolean = true
) {

    /**
     * String representation of the push notification type.
     */
    val type: String
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
            val elapsedTimeSeconds = (System.currentTimeMillis() - createdAt.time) / 1000
            return elapsedTimeSeconds > ttl
        }

    /**
     * Mark this notification as approved.
     */
    fun markApproved() {
        approved = true
        pending = false
        respondedAt = Date()
    }

    /**
     * Mark this notification as denied.
     */
    fun markDenied() {
        approved = false
        pending = false
        respondedAt = Date()
    }

    /**
     * Get numbers used for push challenge
     * @return The numbers as a read-only List<Int>.
     *         Returns empty list if "numbersChallenge" is not available or cannot be parsed.
     */
    fun getNumbersChallenge(): List<Int> {
        return this.numbersChallenge?.let { challengeString ->
            try {
                challengeString.split(",")
                    .mapNotNull { it.trim().toIntOrNull() }
            } catch (_: NumberFormatException) {
                emptyList()
            }
        } ?: emptyList()
    }

    companion object {
        /**
         * Create a PushNotification from a JSON string.
         *
         * @param jsonString The JSON string.
         * @return A PushNotification.
         * @throws SerializationException if the JSON is invalid.
         */
        @JvmStatic
        fun fromJson(jsonString: String): PushNotification {
            val json = Json { 
                ignoreUnknownKeys = true 
                isLenient = true
            }
            return json.decodeFromString(jsonString)
        }
    }

    /**
     * Converts this notification to a JSON string representation.
     *
     * @return A JSON string representing this notification.
     */
    fun toJson(): String {
        val json = Json { 
            prettyPrint = false
            encodeDefaults = true
            ignoreUnknownKeys = true
        }
        
        return json.encodeToString(serializer(), this)
    }

    /**
     * Custom serializer for Maps with Any values.
     */
    object AdditionalDataSerializer : KSerializer<Map<String, Any>?> {
        override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("additionalData", PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: Map<String, Any>?) {
            val jsonString = if (value != null) {
                val jsonObj = value.entries.associate { (key, value) ->
                    key to serializeValue(value)
                }
                Json { ignoreUnknownKeys = true }.encodeToString(JsonObject.serializer(), JsonObject(jsonObj))
            } else {
                null
            }
            encoder.encodeString(jsonString ?: "")
        }

        override fun deserialize(decoder: Decoder): Map<String, Any>? {
            val jsonString = decoder.decodeString()
            if (jsonString.isEmpty()) return null

            return try {
                val json = Json { ignoreUnknownKeys = true }
                val map = json.decodeFromString<Map<String, JsonElement>>(jsonString)
                map.mapValues { deserializeValue(it.value) }
            } catch (e: Exception) {
                null
            }
        }

        private fun serializeValue(value: Any): JsonElement {
            return when (value) {
                is String -> JsonPrimitive(value)
                is Number -> JsonPrimitive(value)
                is Boolean -> JsonPrimitive(value)
                is Map<*, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    JsonObject((value as Map<String, Any>).mapValues { serializeValue(it.value) })
                }
                is List<*> -> JsonArray(value.map { serializeValue(it ?: JsonNull) })
                else -> JsonPrimitive(value.toString())
            }
        }

        private fun deserializeValue(element: JsonElement): Any {
            return when (element) {
                is JsonPrimitive -> {
                    when {
                        element.isString -> element.content
                        element.content == "true" || element.content == "false" -> element.content.toBoolean()
                        element.content.toIntOrNull() != null -> element.content.toInt()
                        element.content.toLongOrNull() != null -> element.content.toLong()
                        element.content.toDoubleOrNull() != null -> element.content.toDouble()
                        else -> element.content
                    }
                }
                is JsonObject -> element.mapValues { deserializeValue(it.value) }
                is JsonArray -> element.map { deserializeValue(it) }
            }
        }
    }
}

/**
 * Custom serializer for the OathType enum
 */
object PushTypeSerializer : KSerializer<PushType> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("PushType", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: PushType) = encoder.encodeString(value.name)
    override fun deserialize(decoder: Decoder): PushType {
        val type = decoder.decodeString()
        try {
            return PushType.fromString(type)
        } catch (e: IllegalArgumentException) {
            throw SerializationException("Unknown Push type: $type", e)
        }
    }
}

/**
 * Custom serializer for nullable Date class to convert to/from Long (milliseconds since epoch)
 */
object NullableDateSerializer : KSerializer<Date?> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("NullableDate", PrimitiveKind.LONG)
    
    override fun serialize(encoder: Encoder, value: Date?) {
        if (value == null) {
            encoder.encodeLong(-1)
        } else {
            encoder.encodeLong(value.time)
        }
    }
    
    override fun deserialize(decoder: Decoder): Date? {
        val time = decoder.decodeLong()
        return if (time == -1L) null else Date(time)
    }
}

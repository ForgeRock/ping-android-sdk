/*
 * Copyright (c) 2024 - 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.oidc

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonIgnoreUnknownKeys
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import java.util.Calendar
import com.pingidentity.logger.Logger

/**
 * Data class representing an OIDC token.
 *
 * @property accessToken The access token.
 * @property tokenType The type of the token.
 * @property scope The scope of the token.
 * @property expiresIn The duration in seconds for which the token is valid.
 * @property refreshToken The refresh token.
 * @property idToken The ID token.
 * @property expireAt The timestamp when the token expires.
 * @property authorizationDetails The authorization details granted for the access token
 * (RFC 9396 §7), or null when the token response carries no `authorization_details`. A
 * malformed value does not fail the token decode: the field is null (or the malformed
 * entries dropped) and a warning is logged (see [AuthorizationDetailListSerializer]).
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonIgnoreUnknownKeys
data class Token(
    @SerialName("access_token")
    val accessToken: String = "",
    @SerialName("token_type")
    val tokenType: String? = null,
    @SerialName("scope")
    val scope: String? = null,
    @SerialName("expires_in")
    val expiresIn: Long = 0,
    @SerialName("refresh_token")
    val refreshToken: String? = null,
    @SerialName("id_token")
    val idToken: String? = null,
    @SerialName("expireAt")
    internal val expireAt: Long =  now() + expiresIn,
    @SerialName("authorization_details")
    @Serializable(with = AuthorizationDetailListSerializer::class)
    val authorizationDetails: List<AuthorizationDetail>? = null
) {
    /**
     * Checks if the token is expired.
     */
    val isExpired: Boolean
        get() = now() >= expireAt

    /**
     * Checks if the token is expired considering a threshold.
     *
     * @param threshold The threshold in seconds.
     * @return True if the token is expired considering the threshold, false otherwise.
     */
    fun isExpired(threshold: Long): Boolean = now() >= expireAt - threshold

    companion object {
        /**
         * Gets the current time in seconds.
         *
         * @return The current time in seconds.
         */
        internal fun now() = Calendar.getInstance().time.time / 1000
    }
}

/**
 * Lenient serializer for [Token.authorizationDetails] (RFC 9396 §7): a malformed
 * `authorization_details` value in the token response does not fail the token decode —
 * the access token itself is valid. A non-array value degrades to `null`; an entry that
 * fails to decode is dropped. Warnings go through the global [Logger.logger].
 */
internal object AuthorizationDetailListSerializer : KSerializer<List<AuthorizationDetail>?> {

    override val descriptor: SerialDescriptor = ListSerializer(AuthorizationDetail.serializer()).descriptor

    override fun deserialize(decoder: Decoder): List<AuthorizationDetail>? {
        val jsonDecoder = decoder as JsonDecoder
        val element: JsonElement = jsonDecoder.decodeJsonElement()
        if (element is JsonNull) return null
        if (element !is JsonArray) {
            Logger.logger.w(
                "Token response carries a malformed authorization_details value " +
                    "(expected a JSON array); ignoring it. Raw value: $element"
            )
            return null
        }
        // Decode entry by entry: one non-conforming entry degrades the list instead of the
        // whole token. An array of invalid entries yields an empty list, not an error.
        return element.mapNotNull { entry ->
            runCatching {
                jsonDecoder.json.decodeFromJsonElement(AuthorizationDetail.serializer(), entry)
            }.getOrElse {
                Logger.logger.w(
                    "Token response carries a malformed authorization_details entry; " +
                        "ignoring it. Raw value: $entry",
                    it,
                )
                null
            }
        }
    }

    override fun serialize(encoder: Encoder, value: List<AuthorizationDetail>?) {
        val jsonEncoder = encoder as JsonEncoder
        jsonEncoder.encodeJsonElement(
            buildJsonArray {
                value.orEmpty().forEach { detail ->
                    add(jsonEncoder.json.encodeToJsonElement(AuthorizationDetail.serializer(), detail))
                }
            }
        )
    }
}
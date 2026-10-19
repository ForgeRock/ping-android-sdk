/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.oidc

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * One entry of the OAuth 2.0 `authorization_details` array (RFC 9396 §2.2). An authorization
 * server echoes the granted entries back in the token response (RFC 9396 §7), where this model
 * exposes them on `Token`.
 *
 * Type-specific members beyond the common data fields modeled here (for example
 * `instructedAmount` of a `payment_initiation` entry) are captured in [additionalFields] during
 * deserialization and merged back into the JSON object on serialization, so vendor extensions
 * survive a decode/encode round trip.
 *
 * @property type The identifier for the authorization details type (RFC 9396 §2.2, REQUIRED).
 * @property locations The resource servers this authorization applies to (RFC 9396 §2.2).
 * @property actions The kinds of actions to be taken at the resource (RFC 9396 §2.2).
 * @property datatypes The kinds of data being requested from the resource (RFC 9396 §2.2).
 * @property privileges The types or levels of privilege being requested at the resource (RFC 9396 §2.2).
 * @property additionalFields The type-specific members not modeled above, keyed by their wire
 * name (RFC 9396 §2.2 permits arbitrary extension members). A key that shadows a modeled
 * property is dropped on serialization, where the typed property wins.
 */
@Serializable(with = AuthorizationDetailSerializer::class)
data class AuthorizationDetail(
    val type: String,
    val locations: List<String>? = null,
    val actions: List<String>? = null,
    val datatypes: List<String>? = null,
    val privileges: List<String>? = null,
    val additionalFields: Map<String, JsonElement> = emptyMap(),
)

/**
 * Serializer for [AuthorizationDetail] that preserves the type-specific members of an
 * authorization detail object: members other than the modeled ones are captured into
 * [AuthorizationDetail.additionalFields] on deserialization (independently of the caller's
 * `Json` configuration) and merged back into the JSON object on serialization. Extra keys that
 * shadow a modeled property are skipped on serialization so no member is emitted twice.
 */
internal object AuthorizationDetailSerializer : KSerializer<AuthorizationDetail> {

    private val KNOWN_KEYS = setOf("type", "locations", "actions", "datatypes", "privileges")

    override val descriptor: SerialDescriptor = AuthorizationDetailDto.serializer().descriptor

    override fun deserialize(decoder: Decoder): AuthorizationDetail {
        val jsonDecoder = decoder as JsonDecoder
        val element = jsonDecoder.decodeJsonElement().jsonObject
        val dto = jsonDecoder.json.decodeFromJsonElement(
            AuthorizationDetailDto.serializer(),
            JsonObject(element.filterKeys { it in KNOWN_KEYS }),
        )
        return AuthorizationDetail(
            type = dto.type,
            locations = dto.locations,
            actions = dto.actions,
            datatypes = dto.datatypes,
            privileges = dto.privileges,
            additionalFields = element.filterKeys { it !in KNOWN_KEYS },
        )
    }

    override fun serialize(encoder: Encoder, value: AuthorizationDetail) {
        val jsonEncoder = encoder as JsonEncoder
        val dtoElement = jsonEncoder.json.encodeToJsonElement(
            AuthorizationDetailDto.serializer(),
            AuthorizationDetailDto(
                type = value.type,
                locations = value.locations,
                actions = value.actions,
                datatypes = value.datatypes,
                privileges = value.privileges,
            ),
        )
        // Shadow guard: an additionalFields key that collides with a modeled property would be
        // emitted twice on the wire, which strict parsers reject; the typed property wins.
        val extras = value.additionalFields.filterKeys { it !in KNOWN_KEYS }
        jsonEncoder.encodeJsonElement(JsonObject(dtoElement.jsonObject + extras))
    }
}

/**
 * Mirror of [AuthorizationDetail]'s modeled fields, used by [AuthorizationDetailSerializer] to
 * decode and encode exactly the known members.
 */
@Serializable
private data class AuthorizationDetailDto(
    val type: String,
    val locations: List<String>? = null,
    val actions: List<String>? = null,
    val datatypes: List<String>? = null,
    val privileges: List<String>? = null,
)

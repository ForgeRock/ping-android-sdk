/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci.module

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Represents a DaVinci SDK Connector metadata payload received from the server during a
 * pause/resume flow.
 *
 * The wire format carries three required keys:
 * - `TYPE` — identifies the SDK connector type (e.g. `Metadata.Type.PINGONE_MFA_SDK`).
 * - `OPERATION` — the operation the connector is requesting.
 * - `configs` — an opaque JSON object whose shape is TYPE-specific.
 *
 * @property type    The metadata connector type string. Compare against [Type] constants.
 * @property operation The operation being requested by the connector.
 * @property configs   The opaque configuration object; callers decode it with
 *                     `kotlinx.serialization` as needed.
 */
data class Metadata(
    val type: String,
    val operation: String,
    val configs: JsonObject,
) {
    /**
     * Companion object that serves as the receiver for [parseOrNull] and [parseOrThrow] factory
     * extension functions.
     */
    companion object

    /**
     * Well-known `TYPE` string constants. Kept as `const val` rather than an enum so that new
     * server-side TYPE values are forward-compatible without requiring an SDK release (see D5 in
     * decisions.md).
     */
    object Type {
        /** Top-level metadata TYPE value returned by the server. */
        const val METADATA = "METADATA"

        /** An external (non-Ping) SDK is required to fulfil this step. */
        const val EXTERNAL_SDK = "EXTERNAL_SDK"

        /** PingOne MFA SDK is required to fulfil this step. */
        const val PINGONE_MFA_SDK = "PINGONE_MFA_SDK"

        /** Keyless SDK is required to fulfil this step. */
        const val KEYLESS_SDK = "KEYLESS_SDK"
    }
}

/**
 * Attempts to parse a [Metadata] from [input], returning `null` when any required key is absent
 * or has an unexpected JSON type.
 *
 * @param input The JSON object from the server payload.
 * @return A [Metadata] instance, or `null` if parsing cannot succeed.
 */
internal fun Metadata.Companion.parseOrNull(input: JsonObject): Metadata? {
    return try {
        val type = input["TYPE"]?.jsonPrimitive?.content ?: return null
        val operation = input["OPERATION"]?.jsonPrimitive?.content ?: return null
        val configs = input["configs"]?.jsonObject ?: return null
        Metadata(type = type, operation = operation, configs = configs)
    } catch (_: Exception) {
        null
    }
}

/**
 * Parses a [Metadata] from [input], throwing [MetadataException.Malformed] when a required key is
 * absent or has an unexpected JSON type.
 *
 * The exception message names the offending key but **never** embeds any value from [input]
 * (satisfies the security NFR that forbids logging opaque payloads).
 *
 * @param input The JSON object from the server payload.
 * @return A [Metadata] instance.
 * @throws MetadataException.Malformed if `TYPE`, `OPERATION`, or `configs` is missing or has the
 *   wrong JSON type.
 */
internal fun Metadata.Companion.parseOrThrow(input: JsonObject): Metadata {
    val type = input["TYPE"]?.let {
        try {
            it.jsonPrimitive.content
        } catch (_: Exception) {
            throw MetadataException.Malformed("Metadata payload has wrong type for required key 'TYPE'")
        }
    } ?: throw MetadataException.Malformed("Metadata payload is missing required key 'TYPE'")

    val operation = input["OPERATION"]?.let {
        try {
            it.jsonPrimitive.content
        } catch (_: Exception) {
            throw MetadataException.Malformed("Metadata payload has wrong type for required key 'OPERATION'")
        }
    } ?: throw MetadataException.Malformed("Metadata payload is missing required key 'OPERATION'")

    val configs = input["configs"]?.let {
        try {
            it.jsonObject
        } catch (_: Exception) {
            throw MetadataException.Malformed("Metadata payload has wrong type for required key 'configs'")
        }
    } ?: throw MetadataException.Malformed("Metadata payload is missing required key 'configs'")

    return Metadata(type = type, operation = operation, configs = configs)
}

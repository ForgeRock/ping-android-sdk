/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci.collector

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Collector for a DaVinci form field of type METADATA.
 *
 * When a DaVinci flow reaches an SDK Integrator connector node, the server pauses
 * and returns a form field of type METADATA carrying an arbitrary JSON payload.
 * The integrating app reads [metadata], performs on-device work, then sets either
 * [output] (success path) or [errorPayload] (failure path) before calling [com.pingidentity.orchestrate.ContinueNode.next].
 *
 * **Precedence**: [errorPayload] takes priority over [output]. When [errorPayload] is non-null it is
 * wrapped as `{ "error": { ... } }` in the resume body regardless of whether [output]
 * is also set. If neither is set, [payload] returns `null` and the field is omitted from
 * the form data.
 */
class MetadataCollector : FieldCollector<JsonObject>() {

    /**
     * The metadata payload sent by the server — arbitrary JSON the SDK must process.
     */
    var metadata: JsonObject = JsonObject(emptyMap())
        private set

    /**
     * The result to send back to the server on the success path.
     *
     * Set this to the JSON object the DaVinci flow expects, then call [com.pingidentity.orchestrate.ContinueNode.next].
     * Not populated by the SDK — the integrating app is responsible for setting it.
     * Ignored when [errorPayload] is also set ([errorPayload] takes precedence).
     */
    var output: JsonObject? = null

    /**
     * The error to send back to the server on the failure path.
     *
     * When set, [output] is ignored and the resume body wraps this value as
     * `{ "error": { ... } }` under `formData.<key>`. Set this before calling
     * [com.pingidentity.orchestrate.ContinueNode.next]. Not populated by the SDK — the integrating app is
     * responsible for setting it.
     */
    var errorPayload: JsonObject? = null

    override fun init(input: JsonObject): MetadataCollector {
        super.init(input)
        metadata = input["payload"]?.jsonObject ?: JsonObject(emptyMap())
        return this
    }

    /**
     * Returns the resume payload for the SDK Integrator connector.
     *
     * [errorPayload] takes priority: when set, returns `{ "error": { ... } }`.
     * Falls back to [output] when [errorPayload] is null.
     * Returns `null` when neither is set.
     */
    override fun payload(): JsonObject? =
        errorPayload?.let { buildJsonObject { put("error", it) } } ?: output
}

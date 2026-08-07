/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci.collector

import com.pingidentity.davinci.plugin.ActionKeyProvider
import com.pingidentity.davinci.plugin.Submittable
import com.pingidentity.orchestrate.Closeable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * Collector for a DaVinci form field of type METADATA.
 *
 * When a DaVinci flow reaches an SDK Integrator connector node, the server pauses
 * and returns a form field of type METADATA carrying an arbitrary JSON payload.
 * The integrating app reads [metadata], performs on-device work, then calls either
 * [setResult] (success path) or [setError] (failure path) before calling
 * [com.pingidentity.orchestrate.ContinueNode.next].
 *
 * The collector is considered ready to submit once either [setResult] or [setError]
 * has been called — [validate] returns a [Required] error until then.
 */
class MetadataCollector : FieldCollector<JsonObject>(), Submittable, ActionKeyProvider, Closeable {

    /**
     * The metadata payload sent by the server — arbitrary JSON the SDK must process.
     */
    var metadata: JsonObject = JsonObject(emptyMap())
        private set

    private var result: JsonObject? = null

    override val actionKey: String? get() = if (result == null) null else id()

    override fun init(input: JsonObject): MetadataCollector {
        super.init(input)
        metadata = input["payload"]?.jsonObject ?: JsonObject(emptyMap())
        return this
    }

    /**
     * Sets the result to POST back to DaVinci on the success path.
     *
     * @param result The JSON object representing the SDK's outcome.
     */
    fun setResult(result: JsonObject) {
        this.result = result
    }

    /**
     * Signals the connector's client-error branch.
     *
     * The resume body will contain `{ "error": { "code": ..., "message": ... } }`.
     *
     * @param errorCode A short error code string (e.g. `"USER_CANCELLED"`).
     * @param message A human-readable description of the error.
     */
    fun setError(errorCode: String, message: String) {
        result = buildJsonObject {
            put("error", buildJsonObject {
                put("code", errorCode)
                put("message", message)
            })
        }
    }

    /** Returns the resume payload, or `null` if neither [setResult] nor [setError] has been called. */
    override fun payload(): JsonObject? = result

    /** Always `"action"` — required by the SDK Integrator connector resume contract. */
    override fun eventType(): String = "action"

    /** Returns [Required] until [setResult] or [setError] has been called, regardless of the field's `required` flag. */
    override fun validate(): List<ValidationError> = if (result == null) listOf(Required) else emptyList()

    /** Clears the result, returning the collector to its initial unset state. */
    override fun close() {
        result = null
    }
}

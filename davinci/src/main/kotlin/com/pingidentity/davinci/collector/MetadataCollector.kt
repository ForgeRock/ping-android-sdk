/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci.collector

import com.pingidentity.davinci.plugin.Collector
import com.pingidentity.orchestrate.ContinueNode
import com.pingidentity.orchestrate.ContinueNodeAware
import com.pingidentity.orchestrate.FlowContext
import com.pingidentity.orchestrate.RequestInterceptor
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import com.pingidentity.network.HttpRequest as Request

/**
 * Collector for a DaVinci form field of type METADATA.
 *
 * When a DaVinci flow reaches an SDK Integrator connector node, the server pauses
 * and returns a form field of type METADATA carrying an arbitrary JSON payload.
 * The integrating app reads [metadata], performs on-device work, then sets either
 * [output] (success path) or [error] (failure path) before calling [ContinueNode.next].
 *
 * **Precedence**: [error] takes priority over [output]. When [error] is non-null it is
 * wrapped as `{ "error": { ... } }` in the resume body regardless of whether [output]
 * is also set. If neither is set, `formData.<key>` is sent as an empty object `{}`.
 *
 * On [ContinueNode.next] the [intercept] override replaces the standard form-POST
 * body with the resume contract expected by the SDK Integrator connector:
 * ```
 * {
 *   "id": "<nodeId>",
 *   "eventName": "continue",
 *   "parameters": {
 *     "eventType": "action",
 *     "data": {
 *       "actionKey": "sdkMetadata",
 *       "formData": {
 *         "sdkMetadata": { ... }   // output, or { "error": { ... } } when error is set
 *       }
 *     }
 *   }
 * }
 * ```
 */
class MetadataCollector : Collector<Nothing>, ContinueNodeAware, RequestInterceptor {

    override lateinit var continueNode: ContinueNode

    /**
     * The key of this field (typically "sdkMetadata").
     */
    var key = ""
        private set

    /**
     * The raw type string (always "METADATA").
     */
    var type = ""
        private set

    /**
     * The metadata payload sent by the server — arbitrary JSON the SDK must process.
     */
    var metadata: JsonObject = JsonObject(emptyMap())
        private set

    /**
     * The result to send back to the server on the success path.
     *
     * Set this to the JSON object the DaVinci flow expects, then call [ContinueNode.next].
     * Not populated by the SDK — the integrating app is responsible for setting it.
     * Ignored when [error] is also set ([error] takes precedence).
     */
    var output: JsonObject? = null

    /**
     * The error to send back to the server on the failure path.
     *
     * When set, [output] is ignored and the resume body wraps this value as
     * `{ "error": { ... } }` under `formData.<key>`. Set this before calling
     * [ContinueNode.next]. Not populated by the SDK — the integrating app is
     * responsible for setting it.
     */
    var error: JsonObject? = null

    override fun init(input: JsonObject): MetadataCollector {
        key = input["key"]?.jsonPrimitive?.contentOrNull ?: ""
        type = input["type"]?.jsonPrimitive?.contentOrNull ?: ""
        metadata = input["payload"]?.jsonObject ?: JsonObject(emptyMap())
        return this
    }

    override fun id(): String = key

    override fun payload(): Nothing? = null

    /**
     * Replaces the standard form-POST body with the SDK Integrator resume contract.
     */
    override var intercept: FlowContext.(Request) -> Request = { request ->
        val id = continueNode.input["id"]?.jsonPrimitive?.contentOrNull ?: ""
        val sdkMetadata = error?.let { buildJsonObject { put("error", it) } } ?: output ?: JsonObject(emptyMap())
        val body = buildJsonObject {
            put("id", id)
            put("eventName", "continue")
            put("parameters", buildJsonObject {
                put("eventType", "action")
                put("data", buildJsonObject {
                    put("actionKey", key)
                    put("formData", buildJsonObject {
                        put(key, sdkMetadata)
                    })
                })
            })
        }
        request.post(body)
        request
    }
}

/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci.module

import com.pingidentity.davinci.plugin.DaVinci
import com.pingidentity.orchestrate.ContinueNode
import com.pingidentity.orchestrate.FlowContext
import com.pingidentity.orchestrate.Node
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import com.pingidentity.network.HttpRequest as Request

/**
 * A [ContinueNode] subclass representing a DaVinci SDK Integrator metadata node.
 *
 * When a DaVinci flow reaches an SDK Connector step and the request carries
 * `X-Requested-With: ping-sdk`, the server pauses the flow and returns a metadata
 * payload. The SDK surfaces this as a [MetadataNode] so callers can:
 * 1. Inspect [metadata] to determine what external SDK operation is required.
 * 2. Perform that operation to obtain an output (and optionally an error).
 * 3. Call [resume] to post the result back and continue the flow.
 *
 * Because [MetadataNode] extends [ContinueNode], existing exhaustive `when` expressions
 * over `Node` continue to compile. Add an `is MetadataNode ->` arm **before**
 * `is ContinueNode ->` in any `when` expression that needs to distinguish the two.
 *
 * @property daVinci  The [DaVinci] workflow this node belongs to.
 * @property metadata The parsed [Metadata] payload describing the required SDK operation.
 */
open class MetadataNode internal constructor(
    context: FlowContext,
    val daVinci: DaVinci,
    input: JsonObject,
    val metadata: Metadata,
) : ContinueNode(context, daVinci, input, emptyList()) {

    private var pendingOutput: JsonElement? = null
    private var pendingError: JsonObject? = null

    /**
     * Stores an output payload to be included in the next [resume] call.
     *
     * This is the builder-style alternative to passing [output] directly to [resume].
     * The value is overwritten if [resume] is called with a non-null [output] argument.
     *
     * @param output Any JSON element produced by the external SDK operation.
     */
    fun setOutput(output: JsonElement) {
        this.pendingOutput = output
    }

    /**
     * Stores an error payload to be included in the next [resume] call.
     *
     * This is the builder-style alternative to passing [error] directly to [resume].
     * The value is overwritten if [resume] is called with a non-null [error] argument.
     *
     * @param error A JSON object describing the error from the external SDK operation.
     */
    fun setError(error: JsonObject) {
        this.pendingError = error
    }

    /**
     * Resumes the paused DaVinci flow by posting the supplied [output] and/or [error]
     * to `_links.next.href` and advancing to the next workflow node.
     *
     * Both parameters are optional; a `null` value omits the corresponding key from the
     * POST body entirely. Parameters supplied here take precedence over any values
     * previously set via [setOutput] / [setError].
     *
     * The full `next` handler chain (cookie persistence, custom headers, etc.) runs
     * unchanged — all modules registered on the workflow apply to the resume request.
     *
     * @param output Optional output payload from the external SDK operation.
     * @param error  Optional error object when the external SDK operation produced a failure.
     * @return The next [Node] in the workflow, or [com.pingidentity.orchestrate.FailureNode]
     *         wrapping [MetadataException.MissingResumeLink] if `_links.next.href` is absent.
     */
    suspend fun resume(
        output: JsonElement? = null,
        error: JsonObject? = null,
    ): Node {
        output?.let { pendingOutput = it }
        error?.let { pendingError = it }
        return next()
    }

    /**
     * Builds the POST request to `_links.next.href` with the pending [pendingOutput] and
     * [pendingError] payloads in the JSON body.
     *
     * A key is omitted from the body when the corresponding pending value is `null`.
     *
     * @throws MetadataException.MissingResumeLink when `_links.next.href` is absent from
     *   the original server response. The surrounding [com.pingidentity.orchestrate.Workflow]
     *   pipeline wraps this in a [com.pingidentity.orchestrate.FailureNode].
     */
    override fun asRequest(): Request {
        val href =
            input["_links"]?.jsonObject?.get("next")?.jsonObject?.get("href")?.jsonPrimitive?.content
                ?: throw MetadataException.MissingResumeLink()

        val body = buildJsonObject {
            pendingOutput?.let { put("output", it) }
            pendingError?.let { put("error", it) }
        }

        return daVinci.config.httpClient.request().apply {
            url = href
            header("Content-Type", "application/json")
            post(body)
        }
    }
}

/*
 * Copyright (c) 2024 - 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci.module

import com.pingidentity.davinci.collector.Form
import com.pingidentity.davinci.plugin.Collector
import com.pingidentity.davinci.plugin.CollectorFactory
import com.pingidentity.davinci.plugin.DaVinci
import com.pingidentity.davinci.plugin.collectors
import com.pingidentity.exception.ApiException
import com.pingidentity.oidc.exception.AuthorizeException
import com.pingidentity.orchestrate.ContinueNode
import com.pingidentity.orchestrate.ErrorNode
import com.pingidentity.orchestrate.FailureNode
import com.pingidentity.orchestrate.FlowContext
import com.pingidentity.orchestrate.Module
import com.pingidentity.orchestrate.Node
import com.pingidentity.orchestrate.Session
import com.pingidentity.orchestrate.SuccessNode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection

/**
 * Module for transforming the response from DaVinci to [Node].
 */
internal val NodeTransform =
    Module.of {
        transform {
            val statusCode = it.status
            val body = it.body()
            when (statusCode) {
                // Check for 4XX errors that are unrecoverable
                in 400..499 -> {
                    val jsonResponse: JsonObject = body.asJson()
                    val message: String = jsonResponse["message"]?.jsonPrimitive?.content ?: ""
                    val errorCode = jsonResponse["code"]?.jsonPrimitive?.intOrNull
                    val errorText = jsonResponse["code"]?.jsonPrimitive?.contentOrNull
                    // Filter out client-side "timeout" related unrecoverable failures
                    if (errorCode == 1999 || errorText == "requestTimedOut") {
                        return@transform FailureNode(ApiException(statusCode, body))
                    }
                    // Filter our "PingOne Authentication Connector" unrecoverable failures
                    val connectorId = jsonResponse["connectorId"]?.jsonPrimitive?.content
                    if (connectorId == "pingOneAuthenticationConnector") {
                        val capabilityName = jsonResponse["capabilityName"]?.jsonPrimitive?.content
                        if (capabilityName in listOf(
                                "returnSuccessResponseRedirect",
                                "setSession"
                            )
                        ) {
                            return@transform FailureNode(ApiException(statusCode, body))
                        }
                    }
                    // If we're still here, we have a 4XX failure that should be recoverable
                    return@transform ErrorNode(this, jsonResponse, message)
                }
                // Handle success (2XX) responses
                200 -> {
                    val jsonResponse: JsonObject = body.asJson()
                    // Filter out 2XX errors with 'failure' status
                    if (jsonResponse["status"]?.jsonPrimitive?.content == "FAILED") {
                        return@transform FailureNode(ApiException(statusCode, body))
                    }

                    // Filter out 2XX errors with error object
                    val error = jsonResponse["error"]?.jsonObject
                    if (error.isNullOrEmpty().not()) {
                        return@transform FailureNode(ApiException(HttpURLConnection.HTTP_OK, body))
                    }
                    return@transform transform(this, workflow, jsonResponse)
                }

                in 300..399 -> {
                    return@transform FailureNode(
                        ApiException(
                            statusCode,
                            "Location: ${it.header("Location")}"
                        )
                    )
                }
                else -> {
                    // 5XX errors are treated as unrecoverable failures
                    return@transform FailureNode(ApiException(statusCode, body))
                }
            }

        }
    }

private fun String.asJson(): JsonObject {
    return Json.parseToJsonElement(this).jsonObject
}

private fun transform(
    context: FlowContext,
    daVinci: DaVinci,
    json: JsonObject,
): Node {
    //If authorizeResponse is present, return success
    if ("authorizeResponse" in json) {
        return SuccessNode(
            json,
            object : Session {
                override val value: String =
                    json["authorizeResponse"]?.jsonObject?.get("code")?.jsonPrimitive?.content
                        ?: throw AuthorizeException("Authorization code is missing.")
            },
        )
    }

    val eventName = json["eventName"]?.jsonPrimitive?.content
    if (eventName == "rewindStateToLastRenderedUI" || eventName == "rewindStateToSpecificRenderedUI") {
        val existing = context.flowContext.getValue<ContinueNode>(CONTINUE_NODE)
            ?: return FailureNode(IllegalStateException("Rewind state to last rendered UI failed."))
        // Create a new Connector instance with the same so that Jetpack Compose
        // sees a different object reference and triggers recomposition and its collectors.
        return Connector(
            existing.context,
            (existing as Connector).daVinci,
            existing.input,
            existing.collectors,
        ).apply {
            CollectorFactory.inject(this)
        }
    }

    tryMetadataNode(context, daVinci, json)?.let { return it }

    val collectors = mutableListOf<Collector<*>>()
    if ("form" in json) collectors.addAll(Form.parse(daVinci, json))

    return Connector(context, daVinci, json, collectors.toList()).apply {
        CollectorFactory.inject(this)
    }

}

/**
 * Attempts to detect and construct a [MetadataNode] from a DaVinci 200-OK response.
 *
 * Detection strategy (layered probe):
 * 1. **Primary** — look for a field in `json.form.components.fields[]` whose `"type"` or
 *    `"inputType"` equals `"METADATA"` and whose sibling keys contain the required triple
 *    (`TYPE`, `OPERATION`, `configs`). This matches the documented DV-10961 wire shape.
 * 2. **Secondary (defensive)** — fall back to treating `json` itself as the metadata block
 *    when it directly contains `TYPE` and `configs` at the top level.
 *
 * Returns `null` when no metadata block is found, allowing [transform] to fall through to
 * the standard [Connector] path.
 *
 * Throws [MetadataException.Malformed] (via `Metadata.parseOrThrow`) when a structural cue
 * for metadata is unambiguously present but the required keys are malformed — the surrounding
 * `Workflow.start` / `Workflow.next` `catch { }` converts this to a [FailureNode].
 */
private fun tryMetadataNode(
    context: FlowContext,
    daVinci: DaVinci,
    json: JsonObject,
): MetadataNode? {
    // --- Primary probe: form.components.fields[].{type|inputType == "METADATA"} ---
    val fields = json["form"]
        ?.jsonObject?.get("components")
        ?.jsonObject?.get("fields")
        ?.jsonArray

    if (fields != null) {
        for (field in fields) {
            val fieldObj = field as? JsonObject ?: continue
            val fieldType = fieldObj["type"]?.jsonPrimitive?.contentOrNull
            val inputType = fieldObj["inputType"]?.jsonPrimitive?.contentOrNull
            if (fieldType == "METADATA" || inputType == "METADATA") {
                // This field is the metadata block — parse strictly
                val metadata = Metadata.parseOrThrow(fieldObj)
                // Only construct a MetadataNode when the resume link is present;
                // MetadataNode.asRequest() will read the href from input directly at resume time.
                json["_links"]?.jsonObject?.get("next")?.jsonObject
                    ?.get("href")?.jsonPrimitive?.contentOrNull
                    ?: return null
                return MetadataNode(context, daVinci, json, metadata)
            }
        }
    }

    // --- Secondary probe: top-level json contains both TYPE and configs ---
    if (json["TYPE"] != null && json["configs"] != null) {
        val metadata = Metadata.parseOrNull(json) ?: return null
        json["_links"]?.jsonObject?.get("next")?.jsonObject
            ?.get("href")?.jsonPrimitive?.contentOrNull
            ?: return null
        return MetadataNode(context, daVinci, json, metadata)
    }

    return null
}

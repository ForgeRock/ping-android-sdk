/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci.module

import com.pingidentity.exception.ApiException
import com.pingidentity.davinci.collector.Form
import com.pingidentity.davinci.plugin.Collector
import com.pingidentity.davinci.plugin.CollectorFactory
import com.pingidentity.oidc.exception.AuthorizeException
import com.pingidentity.orchestrate.ErrorNode
import com.pingidentity.orchestrate.FailureNode
import com.pingidentity.orchestrate.FlowContext
import com.pingidentity.orchestrate.Module
import com.pingidentity.orchestrate.Node
import com.pingidentity.orchestrate.Session
import com.pingidentity.orchestrate.SuccessNode
import com.pingidentity.orchestrate.Workflow
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Module for transforming the response from DaVinci to [Node].
 */
internal val NodeTransform =
    Module.of {
        transform {
            val statusCode = it.status()
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
                    return@transform ErrorNode(jsonResponse, message)
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
                        return@transform FailureNode(ApiException(HttpStatusCode.OK.value, body))
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
    workflow: Workflow,
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

    val collectors = mutableListOf<Collector<*>>()
    if ("form" in json) collectors.addAll(Form.parse(json))

    return Connector(context, workflow, json, collectors.toList()).apply {
        CollectorFactory.inject(workflow, this)
    }

}

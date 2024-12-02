/*
 * Copyright (c) 2024 Ping Identity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.journey.module

import com.pingidentity.exception.ApiException
import com.pingidentity.journey.Journey
import com.pingidentity.journey.options
import com.pingidentity.journey.SSOTokenImpl
import com.pingidentity.journey.callback.request
import com.pingidentity.journey.plugin.Callback
import com.pingidentity.journey.plugin.CallbackRegistry
import com.pingidentity.orchestrate.ContinueNode
import com.pingidentity.orchestrate.EmptySession
import com.pingidentity.orchestrate.ErrorNode
import com.pingidentity.orchestrate.FailureNode
import com.pingidentity.orchestrate.FlowContext
import com.pingidentity.orchestrate.Module
import com.pingidentity.orchestrate.Node
import com.pingidentity.orchestrate.Request
import com.pingidentity.orchestrate.SuccessNode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

internal val NodeTransform =
    Module.of {
        transform {

            when (it.status()) {
                400 -> {
                    error(it.body().asJson())
                }

                200 -> {
                    transform(this, workflow, it.body().asJson())
                }

                else -> {
                    FailureNode(ApiException(it.status(), it.body()))
                }
            }
        }
    }

private fun String.asJson(): JsonObject {
    return Json.parseToJsonElement(this).jsonObject
}

private fun error(json: JsonObject): ErrorNode {
    return ErrorNode(json, json["message"]?.jsonPrimitive?.content ?: "" )
}

private fun transform(
    context: FlowContext,
    journey: Journey,
    json: JsonObject,
): Node {
    val callbacks = mutableListOf<Callback>()
    if ("authId" in json) {
        json["callbacks"]?.jsonArray?.let {
            callbacks.addAll(CallbackRegistry.callback(it))
        }
        return object : ContinueNode(context, journey, json, callbacks) {
            private fun asJson(): JsonObject {
                return buildJsonObject {
                    put("authId", json["authId"]?.jsonPrimitive?.content ?: "")
                    putJsonArray("callbacks") {
                        callbacks.forEach {
                            add(it.asJson())
                        }
                    }
                }
            }

            override fun asRequest(): Request {
                val request = Request().apply {
                    url(
                        "${journey.options.serverUrl}/json/realms/${journey.options.realm}/authenticate"
                    )
                    header("Content-Type", "application/json")
                    if (journey.options.noSession) parameter("noSession", "true")
                    body(asJson())
                }
                return callbacks.request(context, request)
            }
        }.apply {
            CallbackRegistry.inject(journey, this)
        }
    }
    if ("tokenId" in json && json["tokenId"]?.jsonPrimitive?.content?.isNotEmpty() == true) {
        val ssoToken = SSOTokenImpl(
            value = json["tokenId"]?.jsonPrimitive?.content ?: "",
            successUrl = json["successUrl"]?.jsonPrimitive?.content ?: "",
            realm = json["realm"]?.jsonPrimitive?.content ?: "",
        )
        return SuccessNode(json, ssoToken)
    } else {
        return SuccessNode(json, EmptySession)
    }
}

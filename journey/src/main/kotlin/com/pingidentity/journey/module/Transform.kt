/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.journey.module

import com.pingidentity.journey.Constants.APPLICATION_JSON
import com.pingidentity.journey.Constants.AUTH_ID
import com.pingidentity.journey.Constants.CALLBACKS
import com.pingidentity.journey.Constants.CONTENT_TYPE
import com.pingidentity.journey.Constants.REALM_NAME
import com.pingidentity.journey.Constants.SUCCESS_URL
import com.pingidentity.journey.Constants.TOKEN_ID
import com.pingidentity.journey.Journey
import com.pingidentity.journey.SSOTokenImpl
import com.pingidentity.journey.callback.request
import com.pingidentity.journey.options
import com.pingidentity.journey.plugin.Callback
import com.pingidentity.journey.plugin.CallbackRegistry
import com.pingidentity.orchestrate.ContinueNode
import com.pingidentity.orchestrate.ErrorNode
import com.pingidentity.orchestrate.FlowContext
import com.pingidentity.orchestrate.Module
import com.pingidentity.orchestrate.Node
import com.pingidentity.orchestrate.Request
import com.pingidentity.orchestrate.SuccessNode
import com.pingidentity.orchestrate.catch
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
                200 -> {
                    transform(this, workflow, it.body().asJson())
                }

                else -> {
                    //For Network errors, unexpected errors like parsing response, return FailureNode
                    //For api errors, return ErrorNode
                    catch {
                        error(this, it.body().asJson())
                    }
                }
            }
        }
    }

private fun String.asJson(): JsonObject {
    return Json.parseToJsonElement(this).jsonObject
}

private fun error(flowContext: FlowContext, json: JsonObject): ErrorNode {
    return ErrorNode(flowContext, json, json["message"]?.jsonPrimitive?.content ?: "")
}


private fun transform(
    context: FlowContext,
    journey: Journey,
    json: JsonObject,
): Node {
    val callbacks = mutableListOf<Callback>()
    if (AUTH_ID in json) {
        json[CALLBACKS]?.jsonArray?.let {
            callbacks.addAll(CallbackRegistry.callback(it))
        }
        return object : ContinueNode(context, journey, json, callbacks) {
            private fun asJson(): JsonObject {
                return buildJsonObject {
                    put(AUTH_ID, json[AUTH_ID]?.jsonPrimitive?.content ?: "")
                    putJsonArray(CALLBACKS) {
                        callbacks.forEach {
                            if (it.payload().isNotEmpty()) {
                                add(it.payload())
                            }
                        }
                    }
                }
            }

            override fun asRequest(): Request {
                val request = Request().apply {
                    url(
                        "${journey.options.serverUrl}/json/realms/${journey.options.realm}/authenticate"
                    )
                    header(CONTENT_TYPE, APPLICATION_JSON)
                    body(asJson())
                }
                return callbacks.request(context, request)
            }
        }.apply {
            CallbackRegistry.inject(journey, this)
        }
    } else {
        // Expect success
        val ssoToken = SSOTokenImpl(
            value = json[TOKEN_ID]?.jsonPrimitive?.content ?: "",
            successUrl = json[SUCCESS_URL]?.jsonPrimitive?.content ?: "",
            realm = json[REALM_NAME]?.jsonPrimitive?.content ?: "",
        )
        return SuccessNode(json, ssoToken)
    }
}

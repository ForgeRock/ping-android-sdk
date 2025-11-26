/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.protect.journey

import com.pingidentity.journey.plugin.AbstractCallback
import com.pingidentity.journey.plugin.Callback
import com.pingidentity.journey.plugin.ContinueNodeAware
import com.pingidentity.journey.plugin.ValueCallback
import com.pingidentity.journey.plugin.callbacks
import com.pingidentity.orchestrate.ContinueNode
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

const val CLIENT_ERROR = "clientError"
const val PING_ONE_RISK_EVALUATION_SIGNALS = "pingone_risk_evaluation_signals"

/**
 * Abstract Protect Callback that provides the raw content of the Callback, and common methods
 * for sub classes to access.
 */
abstract class AbstractProtectCallback : ContinueNodeAware, AbstractCallback() {

    override lateinit var continueNode: ContinueNode
    private var derivedCallback: Boolean = false

    override fun init(jsonObject: JsonObject): Callback {
        val type = jsonObject["type"]?.jsonPrimitive?.content
        if (type == "MetadataCallback") {
            derivedCallback = true
            jsonObject["output"]?.jsonArray?.let { output ->
                output[0].jsonObject.let { nameValuePair ->
                    if (nameValuePair["name"]?.jsonPrimitive?.contentOrNull == "data") {
                        nameValuePair["value"]?.jsonObject?.let { value ->
                            value.forEach { attr ->
                                init(attr.key, attr.value)
                            }
                        }
                    }
                }
            }
            return this
        } else {
            return super.init(jsonObject)
        }
    }

    /**
     * Input the Client Error to the server
     *
     * @param value Protect ErrorType
     */
    fun error(value: String) {
        if (derivedCallback) {
            valueCallbackError(value);
        } else {
            input(value)
        }
    }

    /**
     * Input the Signal to the server
     * @param value The JWS value.
     */
    fun signal(value: String, error: String) {
        if (derivedCallback) {
            if (value.isNotEmpty()) {
                valueCallbackSignal(value)
            }
            if (error.isNotEmpty()) {
                valueCallbackError(error)
            }
        } else {
            input(value, error)
        }
    }

    /**
     * Set the signals to the [ValueCallback] which associated with the callback.
     *
     * @param value The Value to set to the [ValueCallback].
     */
    private fun valueCallbackSignal(value: String) {
        continueNode.callbacks.forEach { callback ->
            if (callback is ValueCallback) {
                if (callback.id.contains(PING_ONE_RISK_EVALUATION_SIGNALS)) {
                    callback.value = value
                }
            }
        }
    }


    /**
     * Set the client error to the [ValueCallback] which associated with the Protect
     * Callback.
     *
     * @param value The Value to set to the [ValueCallback]
     */
    private fun valueCallbackError(value: String) {
        continueNode.callbacks.forEach { callback ->
            if (callback is ValueCallback) {
                if (callback.id.contains(CLIENT_ERROR)) {
                    callback.value = value
                }
            }
        }
    }
}
/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.recognize.journey

import com.pingidentity.journey.plugin.AbstractCallback
import com.pingidentity.journey.plugin.Callback
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val FIELD_OPERATION_TYPE = "operationType"
private const val OPERATION_ENROLL = "ENROLL"
private const val OPERATION_AUTHENTICATE = "AUTHENTICATE"

/**
 * Factory callback for PingOne Recognize operations in Journey workflows.
 *
 * This callback acts as a factory that inspects the `operationType` field in the server output
 * and delegates to the appropriate specialised callback:
 * - `"ENROLL"` → [PingOneRecognizeEnrollCallback]
 * - `"AUTHENTICATE"` → [PingOneRecognizeAuthenticateCallback]
 *
 * It must be registered in [com.pingidentity.journey.plugin.CallbackRegistry] under the
 * key `"PingOneRecognizeCallback"` (matching the `type` field returned by the server).
 *
 * @see PingOneRecognizeEnrollCallback
 * @see PingOneRecognizeAuthenticateCallback
 */
class RecognizeCallback : AbstractCallback() {

    private var operationType: String = ""

    /**
     * Captures the `operationType` value from the callback output so it is available
     * when [init] selects the concrete delegate callback.
     */
    override fun init(name: String, value: JsonElement) {
        if (name == FIELD_OPERATION_TYPE) {
            operationType = value.jsonPrimitive.content
        }
    }

    /**
     * Parses `operationType` and returns the appropriate concrete callback, fully initialised.
     *
     * Supports two delivery modes:
     * - **Typed callback** (`type == "PingOneRecognizeCallback"`): reads `operationType` from
     *   the `output[]` array via [super.init], then delegates the full [jsonObject] to the
     *   concrete callback's own [init].
     * - **MetadataCallback** (`type == "MetadataCallback"`): reads `operationType` from the
     *   `output[0].value.data` object and delegates to the concrete callback, which will handle
     *   the MetadataCallback wrapping via [AbstractRecognizeCallback.init].
     *
     * @param jsonObject The full callback JSON object received from the server.
     * @return The concrete callback for the requested operation type, fully initialised.
     * @throws IllegalArgumentException if `operationType` is missing or unsupported.
     */
    override fun init(jsonObject: JsonObject): Callback {
        val type = jsonObject["type"]?.jsonPrimitive?.content
        if (type == "MetadataCallback") {
            jsonObject["output"]?.jsonArray
                ?.get(0)?.jsonObject
                ?.get("value")?.jsonObject
                ?.get(FIELD_OPERATION_TYPE)?.jsonPrimitive?.contentOrNull
                ?.let { operationType = it }
        } else {
            // Typed callback: populate operationType via init(name, value)
            super.init(jsonObject)
        }

        require(operationType.isNotEmpty()) {
            "\"$FIELD_OPERATION_TYPE\" is required in PingOneRecognizeCallback output"
        }

        return when (operationType) {
            OPERATION_ENROLL -> PingOneRecognizeEnrollCallback()
            OPERATION_AUTHENTICATE -> PingOneRecognizeAuthenticateCallback()
            else -> throw IllegalArgumentException(
                "\"$FIELD_OPERATION_TYPE\": \"$operationType\" is not supported"
            )
        }.apply {
            init(jsonObject)
        }
    }

    override fun payload(): JsonObject = json
}
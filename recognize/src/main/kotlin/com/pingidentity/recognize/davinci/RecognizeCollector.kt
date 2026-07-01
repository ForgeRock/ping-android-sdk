/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.recognize.davinci

import com.pingidentity.davinci.plugin.Collector
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Factory [Collector] for Recognize operations in DaVinci workflows.
 *
 * Reads the `operationType` discriminator from the server JSON and returns a fully initialised
 * concrete collector:
 * - `"ENROLL"` → [RecognizeEnrollCollector]
 * - `"AUTHENTICATE"` → [RecognizeAuthenticateCollector]
 *
 * The factory itself performs no SDK calls. Once [init] returns, the child collector replaces
 * the factory in the DaVinci pipeline — the factory's [id] and [payload] defaults are never
 * invoked at runtime.
 *
 * @throws IllegalArgumentException if `operationType` is missing or unsupported.
 */
class RecognizeCollector : Collector<JsonObject> {

    /**
     * Reads `operationType` from [input] and returns the appropriate concrete collector,
     * fully initialised via [AbstractRecognizeCollector.init].
     *
     * @param input The JSON object received from the DaVinci server.
     * @return A fully initialised [RecognizeEnrollCollector] or [RecognizeAuthenticateCollector].
     * @throws IllegalArgumentException if `operationType` is absent or not one of the supported values.
     */
    override fun init(input: JsonObject): Collector<JsonObject> {
        val operationType = input["operationType"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("operationType is required")
        return when (operationType) {
            "ENROLL" -> RecognizeEnrollCollector()
            "AUTHENTICATE" -> RecognizeAuthenticateCollector()
            else -> throw IllegalArgumentException("operationType: $operationType is not supported")
        }.apply {
            init(input)
        }
    }
}

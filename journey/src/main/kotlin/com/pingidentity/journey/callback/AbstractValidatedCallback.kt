/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.journey.callback

import com.pingidentity.journey.plugin.AbstractCallback
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Callbacks that accept user input often need to validate that input either on the client side, the server side
 * or both.  Such callbacks should extend this base class.
 */
abstract class AbstractValidatedCallback : AbstractCallback() {
    /**
     * Return the validation policies that should be applied to the input collected by this callback.  These policies
     * are represented by a name string.
     *
     * @return validation policies
     */
    var policies: JsonObject = buildJsonObject {}
        private set

    /**
     * Return the list of failed policies for this callback.
     *
     * @return list of failed policies
     */
    var failedPolicies: List<FailedPolicy> = emptyList()
        private set

    /**
     * Return whether the node should only validate the input or
     * should advance the journey if validation succeeds.
     *
     * @return true if the tree should not advance when validation passes
     */
    var validateOnly: Boolean = false

    override fun init(name: String, value: JsonElement) {
        when (name) {
            "policies" -> policies = value.jsonObject
            "failedPolicies" -> parseFailedPolicy(value.jsonArray)
            "validateOnly" -> validateOnly = value.jsonPrimitive.boolean
            else -> {}
        }
    }

    private fun parseFailedPolicy(array: JsonArray) {
        val result = mutableListOf<FailedPolicy>()
        array.forEach {
            //The failedPolicy is a Stringified json string
            val failedPolicy = Json.parseToJsonElement(it.jsonPrimitive.content) as JsonObject
            val params = failedPolicy["params"]?.jsonObject ?: buildJsonObject {}
            result.add(
                FailedPolicy(
                    params,
                    failedPolicy["policyRequirement"]?.jsonPrimitive?.content ?: ""
                )
            )
        }
        failedPolicies = result.toList()
    }
}

data class FailedPolicy(val params: JsonObject, val policyRequirement: String)

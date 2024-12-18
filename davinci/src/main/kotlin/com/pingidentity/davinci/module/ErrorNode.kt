/*
 * Copyright (c) 2024 PingIdentity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci.module

import com.pingidentity.davinci.json
import com.pingidentity.orchestrate.ErrorNode
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

fun ErrorNode.details(): List<Detail> {
    input["details"]?.jsonArray?.let {
        return it.map { jsonElement ->
            json.decodeFromJsonElement(jsonElement)
        }
    } ?: return emptyList()
}

@Serializable
data class Detail(
    val rawResponse: RawResponse,
    val statusCode: Int
)

@Serializable
data class RawResponse(
    val id: String?,
    val code: String?,
    val message: String?,
    val details: List<ErrorDetail>? = null
)

@Serializable
data class ErrorDetail(
    val code: String?,
    val target: String?,
    val message: String?,
    val innerError: InnerError? = null
)

@Serializable(with = InnerErrorSerializer::class)
data class InnerError(val errors: Map<String, String>)

object InnerErrorSerializer : KSerializer<InnerError> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("InnerError") {
        element("errors", MapSerializer(String.serializer(), String.serializer()).descriptor)
    }

    override fun serialize(encoder: Encoder, value: InnerError) {
        throw UnsupportedOperationException("Serialization is not supported for InnerError")
    }

    override fun deserialize(decoder: Decoder): InnerError {
        val jsonDecoder = decoder as JsonDecoder
        jsonDecoder.decodeJsonElement().jsonObject.let { jsonObject ->
            // Extract the unsatisfied requirements
            val unsatisfiedRequirements =
                jsonObject["unsatisfiedRequirements"]?.jsonArray?.map { element ->
                    element.jsonPrimitive.content
                } ?: emptyList()

            // Build the map of errors from the JSON object
            val errorsMap = unsatisfiedRequirements.associateWith { requirement ->
                jsonObject[requirement]?.jsonPrimitive?.content ?: "No message available"
            }

            return InnerError(errors = errorsMap)
        }
    }
}
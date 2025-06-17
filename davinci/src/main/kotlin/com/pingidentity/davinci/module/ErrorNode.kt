/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci.module

import com.pingidentity.davinci.json
import com.pingidentity.orchestrate.ContinueNode
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

/**
 * Extension function to get the [ContinueNode] from an [ErrorNode].
 * @return The [ContinueNode] from the [ErrorNode].
 */
fun ErrorNode.continueNode(): ContinueNode? {
    return context.flowContext.getValue(CONTINUE_NODE) as? ContinueNode
}

fun ErrorNode.details(): List<Detail> {
    input["details"]?.jsonArray?.let {
        return it.map { jsonElement ->
            json.decodeFromJsonElement(jsonElement)
        }
    } ?: return emptyList()
}

/**
 * Data class representing the error details.
 *
 * @property rawResponse The raw response of the error.
 * @property statusCode The status code of the error.
 */
@Serializable
data class Detail(
    val rawResponse: RawResponse,
    val statusCode: Int
)

/**
 * Data class representing the raw response of the error.
 * @property id The id of the error.
 * @property code The code of the error.
 * @property message The message of the error.
 * @property details The details of the error.
 */
@Serializable
data class RawResponse(
    val id: String?,
    val code: String?,
    val message: String?,
    val details: List<ErrorDetail>? = null
)

/**
 * Data class representing the error details.
 * @property code The code of the error.
 * @property target The target of the error.
 * @property message The message of the error.
 * @property innerError The inner error of the error.
 */
@Serializable
data class ErrorDetail(
    val code: String?,
    val target: String?,
    val message: String?,
    val innerError: InnerError? = null
)

/**
 * Data class representing the inner error.
 * @property errors The errors of the inner error.
 */
@Serializable(with = InnerErrorSerializer::class)
data class InnerError(val errors: Map<String, String>)

/**
 * Serializer for the InnerError class.
 */
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
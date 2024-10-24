/*
 * Copyright (c) 2024 PingIdentity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.idp.journey

import com.pingidentity.journey.plugin.AbstractCallback
import com.pingidentity.journey.plugin.Journey
import com.pingidentity.journey.plugin.JourneyAware
import com.pingidentity.journey.plugin.RequestAdapter
import com.pingidentity.orchestrate.FlowContext
import com.pingidentity.orchestrate.Request
import kotlinx.coroutines.yield
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive


class IdpCallback : AbstractCallback(), JourneyAware, RequestAdapter {
    var provider: String = ""
    var clientId: String = ""
    var redirectUri: String = ""
    var scopes = emptyList<String>()
    var nonce: String = ""
    var acrValues = emptyList<String>()
    var request: String = ""
    var requestUri: String = ""

    //Input
    private var result: IdpResult = IdpResult("", emptyMap())
    private var tokenType: String = ""

    override lateinit var journey: Journey

    override fun onAttribute(name: String, value: JsonElement) {
        when (name) {
            "provider" -> this.provider = value.jsonPrimitive.content
            "clientId" -> this.clientId = value.jsonPrimitive.content
            "redirectUri" -> this.redirectUri = value.jsonPrimitive.content
            "scopes" -> this.scopes = value.jsonArray.map { it.jsonPrimitive.content }
            "nonce" -> this.nonce = value.jsonPrimitive.content
            "acrValues" -> this.acrValues = value.jsonArray.map { it.jsonPrimitive.content }
            "request" -> this.request = value.jsonPrimitive.content
            "requestUri" -> this.requestUri = value.jsonPrimitive.content
        }
    }

    override fun asJson() = input(result.token, tokenType)

    /**
     * Overrides the request with the resume request if initialized, else return the input request.
     */
    override var asRequest: FlowContext.(Request) -> Request = { request ->
        result.additionalParameters.forEach { (key, value) ->
            request.parameter(key, value)
        }
        request
    }

    suspend fun authorize(): Result<Unit> {
        val handler: IdpHandler
        if (provider.lowercase().contains("google")) {
            handler = GoogleHandler()
        } else if (provider.lowercase().contains("facebook")) {
            handler = FacebookHandler()
        } else if (provider.lowercase().contains("apple")) {
            handler = AppleHandler()
        } else {
            return Result.failure(IllegalArgumentException("Unsupported provider: $provider"))
        }
        try {
            result = handler.authorize(IdpClient(clientId, redirectUri, scopes, nonce))
            tokenType = handler.tokenType
        } catch (e: Exception) {
            yield()
            journey.config.logger.e("Failed to authorize with $provider", e)
            return Result.failure(e)
        }

        return Result.success(Unit)

    }


}


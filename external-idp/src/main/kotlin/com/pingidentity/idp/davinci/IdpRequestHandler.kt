/*
 * Copyright (c) 2025 Ping Identity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.idp.davinci

import com.pingidentity.exception.ApiException
import com.pingidentity.idp.IdpClient
import com.pingidentity.orchestrate.Request
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Interface representing an Identity Provider (IdP) handler.
 * Implementations of this interface are responsible for handling
 * authorization requests to different IdPs.
 */
interface IdpRequestHandler {

    /**
     * Authorizes a user by making a request to the given authenticate URL.
     *
     * @param url The authenticate URL.
     * @return A [Request] object that can be used to continue the DaVinci flow.
     */
    suspend fun authorize(url: String): Request

    suspend fun fetch(httpClient: HttpClient, url: String): IdpClient {
        val response = httpClient.get(url) {
            header("x-requested-with", "ping-sdk")
            header("Accept", "application/json")
        }

        if (response.status.isSuccess()) {
            with(response) {
                val json = Json.parseToJsonElement(call.body()).jsonObject
                val clientId =
                    json["idp"]?.jsonObject?.get("clientId")?.jsonPrimitive?.content
                val nonce = json["idp"]?.jsonObject?.get("nonce")?.jsonPrimitive?.content
                //Should include email and public_profile for facebook
                val scopes =
                    json["idp"]?.jsonObject?.get("scopes")?.jsonArray?.map { it.jsonPrimitive.content }
                        ?: emptyList()
                val next =
                    json["_links"]?.jsonObject?.get("next")?.jsonObject?.get("href")?.jsonPrimitive?.content
                return IdpClient(clientId, next, scopes, nonce, next)
            }
        } else {
            throw ApiException(response.status.value, response.body())
        }
    }
}
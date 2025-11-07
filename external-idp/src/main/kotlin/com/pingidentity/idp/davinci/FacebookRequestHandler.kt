/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.idp.davinci

import com.pingidentity.idp.IdpHandler
import com.pingidentity.network.HttpClient
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import com.pingidentity.network.HttpRequest as Request

/**
 * A handler class for managing Google Identity Provider (IdP) authorization.
 * @property httpClient The HTTP client used for making requests.
 *
 */
internal class FacebookRequestHandler(private val httpClient: HttpClient, private val handler: IdpHandler) :
    IdpHandler by handler, IdpRequestHandler {

    override suspend fun authorize(url: String): Request {
        val idpClient = fetch(httpClient, url)
        val result = handler.authorize(idpClient)
        return httpClient.request().apply {
            this.url = idpClient.continueUrl ?: ""
            header("Accept", "application/json")
            post(buildJsonObject {
                put("accessToken", result.token)
            })
        }
    }
}


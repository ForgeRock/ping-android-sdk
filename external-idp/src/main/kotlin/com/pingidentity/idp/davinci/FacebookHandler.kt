/*
 * Copyright (c) 2024 PingIdentity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.idp.davinci

import com.facebook.login.LoginManager
import com.pingidentity.davinci.plugin.DaVinci
import com.pingidentity.exception.ApiException
import com.pingidentity.idp.UnsupportedIdPException
import com.pingidentity.idp.facebook.FacebookLoginManager.performFacebookLogin
import com.pingidentity.orchestrate.Request
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * A handler class for managing Facebook Identity Provider (IdP) authorization.
 *
 * @property davinci The DaVinci instance used for making HTTP requests and handling configurations.
 */
class FacebookHandler(val davinci: DaVinci) : IdpHandler {

    /**
     * Authorizes a user by using the Facebook SDK.
     *
     * @param url The URL to which the authorization request is made.
     * @return A [Request] object that can be used to continue the DaVinci flow.
     * @throws UnsupportedIdPException if the Facebook SDK is not available.
     * @throws ApiException if the HTTP response status is not successful.
     */
    override suspend fun authorize(url: String): Request {

        try {
            Class.forName("com.facebook.login.LoginManager")
        } catch (e: ClassNotFoundException) {
            throw UnsupportedIdPException("Google SDK is not available.")
        }

        LoginManager.getInstance().logOut()

        // Make a request to the given URL to retrieve the Facebook Client login information
        val response = davinci.config.httpClient.get(url) {
            header("x-requested-with", "ping-sdk")
            header("Accept", "application/json")
        }

        if (response.status.isSuccess()) {
            with(response) {
                //although the response include the client id, we are not using it,
                // Facebook SDK requires the client id to be set in the String.xml
                val json = Json.parseToJsonElement(call.body()).jsonObject
                //The next link after authenticate with Facebook
                val next =
                    json["_links"]?.jsonObject?.get("next")?.jsonObject?.get("href")?.jsonPrimitive?.content
                        ?: throw IllegalStateException("Next URL not found")
                //Should include email and public_profile
                val scopes =
                    json["idp"]?.jsonObject?.get("scopes")?.jsonArray?.map { it.jsonPrimitive.content }
                        ?: emptyList()

                val result = performFacebookLogin(scopes)
                return Request().apply {
                    url(next)
                    header("Accept", "application/json")
                    body(buildJsonObject {
                        put("accessToken", result.accessToken.token)
                    })
                }
            }
        } else {
            throw ApiException(response.status.value, response.body())
        }

    }


}
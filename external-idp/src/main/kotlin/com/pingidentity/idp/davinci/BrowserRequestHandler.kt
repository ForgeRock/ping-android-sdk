/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.idp.davinci

import com.pingidentity.browser.BrowserLauncher
import com.pingidentity.orchestrate.ContinueNode
import com.pingidentity.orchestrate.Request
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URL

/**
 * A handler class for managing browser-based Identity Provider (IdP) authorization.
 *
 * @property continueNode The continue node for the DaVinci flow.
 */
internal class BrowserRequestHandler(
    private val continueNode: ContinueNode
) : IdpRequestHandler {

    /**
     * Authorizes a user by making a request to the given URL.
     *
     * @param url The URL to which the authorization request is made.
     * @return A [Request] object that can be used to continue the DaVinci flow.
     */
    override suspend fun authorize(url: String): Request {
        // Extract the continue URL from the continue node
        val continueUrl = continueNode.input.jsonObject["_links"]
            ?.jsonObject?.get("continue")
            ?.jsonObject?.get("href")
            ?.jsonPrimitive?.content ?: throw IllegalStateException("Continue URL not found")

        val result = BrowserLauncher.launch(URL(url))
        return if (result.isSuccess) {
            Request().apply {
                val continueToken = result.getOrThrow().toString()
                    .substringAfter("continueToken=").substringBefore("&")
                url(continueUrl)
                header("Authorization", "Bearer $continueToken")
                body()
            }
        } else {
            result.exceptionOrNull()?.let {
                throw it
            } ?: throw IllegalStateException("Authorization failed")
        }
    }
}
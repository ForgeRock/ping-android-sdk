/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.idp.davinci

import android.net.Uri
import com.pingidentity.browser.BrowserLauncher
import com.pingidentity.orchestrate.ContinueNode
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URL
import com.pingidentity.network.HttpRequest as Request

/**
 * A handler class for managing browser-based Identity Provider (IdP) authorization.
 *
 * @property continueNode The continue node for the DaVinci flow.
 * @property redirectUri The redirect URI for the browser-based IdP authentication.
 * The redirect URI is used when using Auth Tab (https://developer.chrome.com/docs/android/custom-tabs/guide-auth-tab)
 * When using Auth Tab, it is not necessary to define the redirect scheme under AndroidManifest.xml
 */
internal class BrowserRequestHandler(
    private val continueNode: ContinueNode,
    private val redirectUri: Uri
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

        val result = BrowserLauncher.launch(URL(url), redirectUri)
        return if (result.isSuccess) {
            continueNode.workflow.config.httpClient.request().apply {
                val continueToken = result.getOrThrow().toString()
                    .substringAfter("continueToken=").substringBefore("&")
                this.url = continueUrl
                header("Authorization", "Bearer $continueToken")
                post()
            }
        } else {
            result.exceptionOrNull()?.let {
                throw it
            } ?: throw IllegalStateException("Authorization failed")
        }
    }
}
/*
 * Copyright (c) 2024 PingIdentity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.idp.davinci

import androidx.browser.customtabs.CustomTabsIntent
import com.pingidentity.idp.browser.BrowserLauncherActivity
import com.pingidentity.idp.browser.CustomTabActivity
import com.pingidentity.orchestrate.ContinueNode
import com.pingidentity.orchestrate.Request
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URL

/**
 * A handler class for managing browser-based Identity Provider (IdP) authorization.
 *
 * @property customizer A lambda function to customize the CustomTabsIntent.Builder.
 * @property continueNode The continue node for the DaVinci flow.
 */
class BrowserHandler(
    private val customizer: CustomTabsIntent.Builder.() -> Unit = {},
    private val continueNode: ContinueNode
) : IdpHandler {

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

        CustomTabActivity.customTabsCustomizer = customizer
        val result = BrowserLauncherActivity.authorize(URL(url))
        return if (result.isSuccess) {
            Request().apply {
                val continueToken = result.getOrThrow().toString()
                    .substringAfter("continueToken=").substringBefore("&")
                url(continueUrl)
                header("Authorization", "Bearer ${continueToken}")
                body()
            }
        } else {
            result.exceptionOrNull()?.let {
                throw it
            } ?: throw IllegalStateException("Authorization failed")
        }
    }
}
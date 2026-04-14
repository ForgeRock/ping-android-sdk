/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.oidc.module

import androidx.core.net.toUri
import com.pingidentity.browser.BrowserLauncher
import com.pingidentity.oidc.Constants.CODE
import com.pingidentity.orchestrate.Module
import com.pingidentity.orchestrate.Session
import com.pingidentity.orchestrate.SuccessNode
import com.pingidentity.orchestrate.module.Cookies
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.URL
import com.pingidentity.network.HttpResponse as Response

internal const val IS_WEB = "IS_WEB"

// Module to handle the request with browser interaction
val Web = Module.of(::WebModuleConfig) {

    init {
        // Indicate that this module is for web-based OIDC flows
        sharedContext[IS_WEB] = true
    }

    transport {
        // launch the browser and return a Response with authorization code
        // When there is exception, it will be handled by the orchestrate module, which will return [FailureNode]
        val url = URL(it.url)
        logger.d("Launching browser for OIDC authorization flow with url $url")
        BrowserLauncher.customTabsCustomizer = config.customTabsCustomizer
        BrowserLauncher.authTabCustomizer = config.authTabCustomizer
        BrowserLauncher.intentCustomizer = config.intentCustomizer
        BrowserLauncher.logger = workflow.config.logger
        val uri = BrowserLauncher.launch(url, workflow.oidcClientConfig().redirectUri.toUri())
            .getOrThrow()
        val code = uri.getQueryParameter(CODE)
            ?: throw IllegalStateException("No authorization code found in response")

        object : Response {
            override val request = it
            override val status: Int
                get() = 200

            override suspend fun body(): String {
                return buildJsonObject {
                    put(CODE, code)
                }.toString()
            }

            override fun cookies(): Cookies {
                return emptyList()
            }

            override fun header(name: String): String? {
                return null
            }

            override fun headers(): Set<Map.Entry<String, List<String>>> {
                return emptySet()
            }
        }
    }

    transform {
        val json = it.body().asJson()
        SuccessNode(
            json,
            object : Session {
                override val value: String =
                    json.jsonObject[CODE]?.jsonPrimitive?.content
                        ?: throw IllegalStateException("Authorization code is missing.")
            },
        )
    }
}

private fun String.asJson(): JsonObject {
    return Json.parseToJsonElement(this).jsonObject
}

/**
 * Extension function to check if the OIDC flow is web-based.
 * This checks the shared context for the IS_WEB flag.
 *
 * @return true if the flow is web-based, false otherwise.
 */
internal fun OidcFlow.isWeb(): Boolean = sharedContext.getValue<Boolean>(IS_WEB) == true

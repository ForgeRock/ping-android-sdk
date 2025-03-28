/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.idp.davinci

import com.pingidentity.browser.BrowserLauncher
import com.pingidentity.davinci.plugin.Collector
import com.pingidentity.davinci.plugin.ContinueNodeAware
import com.pingidentity.davinci.plugin.RequestInterceptor
import com.pingidentity.davinci.plugin.DaVinciAware
import com.pingidentity.idp.UnsupportedIdPException
import com.pingidentity.idp.FacebookHandler
import com.pingidentity.idp.GoogleHandler
import com.pingidentity.orchestrate.ContinueNode
import com.pingidentity.orchestrate.FlowContext
import com.pingidentity.orchestrate.Request
import com.pingidentity.orchestrate.Workflow
import kotlinx.coroutines.yield
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URL

/**
 * A collector class for handling Identity Provider (IdP) authorization.
 */
class IdpCollector : Collector, ContinueNodeAware, DaVinciAware, RequestInterceptor {

    /**
     * Indicates whether the IdP is enabled.
     */
    var idpEnabled = true

    /**
     * The IdP identifier.
     */
    lateinit var idpId: String

    /**
     * The type of IdP.
     */
    lateinit var idpType: String

    /**
     * The label for the IdP.
     */
    lateinit var label: String

    /**
     * The URL link for IdP authentication.
     */
    lateinit var link: URL

    /**
     * The continue node for the DaVinci flow.
     */
    override lateinit var continueNode: ContinueNode

    /**
     * The DaVinci workflow instance.
     */
    override lateinit var davinci: Workflow

    /**
     * The request to resume the DaVinci flow.
     */
    private lateinit var resumeRequest: Request

    /**
     * Initializes the IdP collector with the given input JSON object.
     *
     * @param input The JSON object containing initialization data.
     */
    override fun init(input: JsonObject) {
        idpEnabled = input["idpEnabled"]?.jsonPrimitive?.boolean ?: true
        idpId = input["idpId"]?.jsonPrimitive?.content ?: ""
        idpType = input["idpType"]?.jsonPrimitive?.content ?: ""
        label = input["label"]?.jsonPrimitive?.content ?: ""
        link = URL(
            input["links"]
                ?.jsonObject?.get("authenticate")
                ?.jsonObject?.get("href")?.jsonPrimitive?.content
                ?: ""
        )
    }

    /**
     * Overrides the request with the resume request if initialized, else return the input request.
     */
    override var intercept: FlowContext.(Request) -> Request = { r ->
        if (this@IdpCollector::resumeRequest.isInitialized) resumeRequest else r
    }

    /**
     * Authorizes the user using the specified IdP.
     *
     * @param idpRequestHandler The IdP request handler to use, if null will use the predefined handler.
     * @return A Result object indicating success or failure.
     */
    suspend fun authorize(
        idpRequestHandler: IdpRequestHandler? = null
    ): Result<Unit> {
        val requestHandler: IdpRequestHandler = idpRequestHandler ?: getRequestHandler()
        try {
            BrowserLauncher.logger = davinci.config.logger
            resumeRequest = requestHandler.authorize(link.toString())
            return Result.success(Unit)
        } catch (e: Exception) {
            yield()
            return Result.failure(e)
        }
    }

    private fun getRequestHandler(): IdpRequestHandler {

        return when (idpType) {
            "GOOGLE" -> {
                IdpRequestHandlerDelegate(
                    GoogleRequestHandler(
                        davinci.config.httpClient,
                        GoogleHandler()
                    ), BrowserRequestHandler(continueNode)
                )
            }

            "FACEBOOK" -> {
                IdpRequestHandlerDelegate(
                    FacebookRequestHandler(
                        davinci.config.httpClient,
                        FacebookHandler()
                    ), BrowserRequestHandler(continueNode)
                )
            }

            else -> {
                BrowserRequestHandler(continueNode)
            }
        }
    }
}

private class IdpRequestHandlerDelegate(
    val idpRequestHandler: IdpRequestHandler,
    val fallback: IdpRequestHandler
) : IdpRequestHandler by idpRequestHandler {
    override suspend fun authorize(url: String): Request {
        try {
            return idpRequestHandler.authorize(url)
        } catch (e: UnsupportedIdPException) {
            // Fallback to use browser
            return fallback.authorize(url)
        }
    }

}
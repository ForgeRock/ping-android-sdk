/*
 * Copyright (c) 2024 PingIdentity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.idp.davinci

import androidx.browser.customtabs.CustomTabsIntent
import com.pingidentity.davinci.plugin.Collector
import com.pingidentity.davinci.plugin.ContinueNodeAware
import com.pingidentity.davinci.plugin.RequestAdapter
import com.pingidentity.davinci.plugin.DaVinciAware
import com.pingidentity.idp.UnsupportedIdPException
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
class IdpCollector : Collector, ContinueNodeAware, DaVinciAware, RequestAdapter {

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
    override var request: FlowContext.(Request) -> Request = { r ->
        if (this@IdpCollector::resumeRequest.isInitialized) {
            resumeRequest
        } else {
            r
        }
    }

    /**
     * Authorizes the user using the specified IdP.
     *
     * @param customizer A lambda function to customize the CustomTabsIntent.Builder.
     * @return A Result object indicating success or failure.
     */
    suspend fun authorize(customizer: CustomTabsIntent.Builder.() -> Unit = {}): Result<Unit> {
        try {
            resumeRequest = when (idpType) {
                "GOOGLE" -> {
                    GoogleHandler(davinci).authorize(link.toString())
                }
                "FACEBOOK" -> {
                    FacebookHandler(davinci).authorize(link.toString())
                }
                else -> {
                    BrowserHandler(continueNode, customizer).authorize(link.toString())
                }
            }
            return Result.success(Unit)
        } catch (e: UnsupportedIdPException) {
            davinci.config.logger.w(e.message ?: "Unsupported IDP", e)
            // Fallback to use browser
            try { // We need the try-catch block to handle suspend function thrown exception
                resumeRequest =
                    BrowserHandler(continueNode, customizer).authorize(link.toString())
                return Result.success(Unit)
            } catch (e: Exception) {
                yield()
                return Result.failure(e)
            }
        } catch (e: Exception) {
            yield()
            return Result.failure(e)
        }
    }


}
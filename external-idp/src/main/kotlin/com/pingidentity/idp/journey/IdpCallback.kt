/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.idp.journey

import com.pingidentity.idp.FacebookHandler
import com.pingidentity.idp.GoogleHandler
import com.pingidentity.idp.IdpClient
import com.pingidentity.idp.IdpHandler
import com.pingidentity.idp.IdpResult
import com.pingidentity.journey.plugin.AbstractCallback
import com.pingidentity.journey.plugin.Journey
import com.pingidentity.journey.plugin.JourneyAware
import com.pingidentity.journey.plugin.RequestInterceptor
import com.pingidentity.orchestrate.FlowContext
import com.pingidentity.orchestrate.Request
import kotlinx.coroutines.yield
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * Callback that handles authentication via external Identity Providers (IdPs).
 *
 * This callback is responsible for processing authentication through third-party
 * identity providers such as Google, Facebook, and Apple. It receives configuration
 * parameters from the authentication server and uses the appropriate [IdpHandler]
 * to complete the authentication flow.
 *
 * The callback implements:
 * - [JourneyAware] to access the parent journey
 * - [RequestInterceptor] to add parameters to the request when submitting the callback
 */
class IdpCallback : AbstractCallback(), JourneyAware, RequestInterceptor {
    /**
     * The external identity provider identifier (e.g., "google", "facebook", "apple").
     * This value is received from the server and determines which [IdpHandler] to use.
     */
    var provider: String = ""
        private set

    /**
     * The client ID to use when authenticating with the external identity provider.
     * This is typically provided by the authentication server based on configured
     * integrations with external providers.
     */
    var clientId: String = ""
        private set

    /**
     * The URI where the IdP should redirect after authentication.
     * Used when configuring the external identity provider authentication flow.
     */
    var redirectUri: String = ""
        private set

    /**
     * The OAuth scopes to request from the external identity provider.
     * These determine what information and permissions are requested from the user.
     */
    var scopes = emptyList<String>()
        private set

    /**
     * A random string used to mitigate CSRF attacks in OAuth flows.
     * This value is validated when processing the authentication response.
     */
    var nonce: String = ""
        private set

    /**
     * Authentication Context Class References values.
     * These can be used to request specific authentication methods or levels of assurance.
     */
    var acrValues = emptyList<String>()
        private set

    /**
     * The JWT request object to be passed to the authorization server.
     * Used in some OAuth/OIDC advanced flows.
     */
    var request: String = ""
        private set

    /**
     * A URI that points to a JWT request object.
     * Used as an alternative to the [request] parameter in some OAuth/OIDC flows.
     */
    var requestUri: String = ""
        private set

    /**
     * The authentication result received from the external identity provider.
     * This will contain the authentication token and any additional parameters.
     */
    private var result: IdpResult = IdpResult("", emptyMap())

    /**
     * The type of token received from the identity provider (e.g., "Bearer").
     * This is used when constructing the payload to send back to the server.
     */
    private var tokenType: String = ""

    /**
     * Reference to the parent journey.
     * Required by the [JourneyAware] interface.
     */
    override lateinit var journey: Journey

    /**
     * Initializes the callback with data from the authentication server.
     *
     * @param name The name of the property being initialized
     * @param value The value for the property being initialized
     */
    override fun init(name: String, value: JsonElement) {
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

    /**
     * Generates a JSON object payload to be sent back to the authentication server.
     * This payload includes the token received from the external identity provider.
     *
     * @return A json object containing the authentication token and its type
     */
    override fun payload() = input(result.token, tokenType)

    /**
     * Intercepts and modifies the request that will be sent to the authentication server.
     * Adds any additional parameters received from the external identity provider.
     *
     * @return A modified request with additional parameters from the authentication result
     */
    override var intercept: FlowContext.(Request) -> Request = { request ->
        result.additionalParameters.forEach { (key, value) ->
            request.parameter(key, value)
        }
        request
    }

    /**
     * Initiates the authentication flow with the external identity provider.
     *
     * This method:
     * 1. Determines the appropriate handler for the configured provider
     * 2. Launches the external authentication flow
     * 3. Processes the authentication result
     *
     * @param idpHandler Optional custom handler; if not provided, one will be selected based on [provider]
     * @return A [Result] containing the [IdpResult] if successful, or an error if the authentication failed
     */
    suspend fun authorize(idpHandler: IdpHandler? = getIdpHandler()): Result<IdpResult> {
        idpHandler
            ?: return Result.failure(IllegalArgumentException("Unsupported provider: $provider"))
        try {
            result = idpHandler.authorize(IdpClient(clientId, redirectUri, scopes, nonce))
            tokenType = idpHandler.tokenType
        } catch (e: Exception) {
            yield()
            journey.config.logger.e("Failed to authorize with $provider", e)
            return Result.failure(e)
        }

        return Result.success(result)
    }

    /**
     * Returns the appropriate [IdpHandler] based on the configured [provider].
     *
     * @return An [IdpHandler] implementation for the specified provider, or null if not supported
     */
    private fun getIdpHandler(): IdpHandler? {
        return if (provider.lowercase().contains("google")) {
            GoogleHandler()
        } else if (provider.lowercase().contains("facebook")) {
            FacebookHandler()
        } else if (provider.lowercase().contains("apple")) {
            AppleHandler()
        } else {
            null
        }
    }
}

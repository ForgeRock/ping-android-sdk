/*
 * Copyright (c) 2024 Ping Identity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.oidc

import com.pingidentity.oidc.exception.AuthorizeException

/**
 * The [Agent] is an interface that is used to authenticate and end a session
 * with an OpenID Connect provider.
 *
 * T is the configuration object that is used to configure the [Agent].
 */
interface Agent<T> {
    /**
     * Provides the configuration object for the [Agent].
     *
     * @return A function that returns the configuration object.
     */
    fun config(): () -> T

    /**
     * End the session with the OpenID Connect provider.
     * Best effort is made to end the session.
     *
     * @param oidcConfig The configuration for the OpenID Connect client.
     * @param idToken The ID token used to end the session.
     * @return A boolean indicating whether the session was successfully ended.
     */
    suspend fun endSession(
        oidcConfig: OidcConfig<T>,
        idToken: String,
    ): Boolean

    /**
     * Authorize the [Agent] with the OpenID Connect provider.
     * Before returning the [AuthCode], the agent should verify the response from the OpenID Connect provider.
     *
     * @param oidcConfig The configuration for the OpenID Connect client.
     * @return The authorization code.
     */
    suspend fun authorize(oidcConfig: OidcConfig<T>): AuthCode
}

/**
 * Default implementation of the [Agent] interface.
 */
object DefaultAgent: Agent<Unit> {
    /**
     * Provides an empty configuration for the [DefaultAgent].
     *
     * @return A function that returns Unit.
     */
    override fun config(): () -> Unit = {}

    /**
     * End the session with the OpenID Connect provider.
     * This implementation always returns false.
     *
     * @param oidcConfig The configuration for the OpenID Connect client.
     * @param idToken The ID token used to end the session.
     * @return Always returns false.
     */
    override suspend fun endSession(
        oidcConfig: OidcConfig<Unit>,
        idToken: String,
    ): Boolean {
        return false
    }

    /**
     * Authorize the [DefaultAgent] with the OpenID Connect provider.
     * This implementation always throws an [AuthorizeException].
     *
     * @param oidcConfig The configuration for the OpenID Connect client.
     * @return Never returns normally.
     * @throws AuthorizeException Always thrown to indicate no authorization code is available.
     */
    override suspend fun authorize(oidcConfig: OidcConfig<Unit>): AuthCode {
        throw AuthorizeException("No AuthCode is available.")
    }
}

/**
 * Allow the [Agent] to run on [OidcConfig] so that it can access
 * the configuration object.
 *
 * @param T The type of the configuration object.
 * @property config The configuration object.
 * @property oidcClientConfig The client configuration for the OpenID Connect provider.
 */
class OidcConfig<T> internal constructor(val config: T, val oidcClientConfig: OidcClientConfig)

/**
 * Dispatch to [Agent] functions.
 *
 * @param T The type of the configuration object.
 * @property agent The [Agent] instance.
 * @property oidcConfig The [OidcConfig] instance.
 */
internal class AgentDelegate<T> internal constructor(
    private val agent: Agent<T>,
    agentConfig: T,
    oidcClientConfig: OidcClientConfig,
) {
    private val oidcConfig: OidcConfig<T> = OidcConfig(agentConfig, oidcClientConfig)

    /**
     * Authenticate with the OpenID Connect provider.
     *
     * @return The authorization code.
     */
    suspend fun authenticate(): AuthCode {
        return agent.authorize(oidcConfig)
    }

    /**
     * End the session with the OpenID Connect provider.
     *
     * @param idToken The ID token used to end the session.
     * @return A boolean indicating whether the session was successfully ended.
     */
    suspend fun endSession(idToken: IdToken) = agent.endSession(oidcConfig, idToken)
}
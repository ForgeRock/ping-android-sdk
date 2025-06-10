/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.oidc

import com.pingidentity.exception.ApiException
import com.pingidentity.logger.Logger
import com.pingidentity.logger.None
import com.pingidentity.oidc.agent.browser
import com.pingidentity.storage.EncryptedDataStoreStorage
import com.pingidentity.storage.EncryptedDataStoreStorageConfig
import com.pingidentity.storage.EncryptedDataStoreStorageFactory
import com.pingidentity.storage.Storage
import com.pingidentity.utils.PingDsl
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
import io.ktor.http.Url
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val COM_PING_SDK_V_1_TOKENS = "com.pingidentity.sdk.v1.tokens"

/**
 * Configuration class for OIDC client.
 */
@PingDsl
class OidcClientConfig {

    /**
     * OpenID configuration.
     */
    lateinit var openId: OpenIdConfiguration

    /**
     * Token refresh threshold in seconds.
     */
    var refreshThreshold: Long = 0 // In seconds

    /**
     * Agent delegate for handling OIDC operations.
     */
    internal lateinit var agent: AgentDelegate<*>

    /**
     * Updates the agent with the provided configuration.
     *
     * @param T The type of the agent configuration.
     * @param agent The agent to update.
     * @param config The configuration block for the agent.
     */
    fun <T : Any> updateAgent(
        agent: Agent<T>,
        config: T.() -> Unit = {},
    ) {
        this.agent =
            AgentDelegate(agent, agent.config()().apply(config), this)
    }

    /**
     * Logger instance for logging.
     */
    var logger: Logger = Logger.logger

    /**
     * Storage for storing tokens.
     */
    internal lateinit var tokenStorage: Storage<Token>

    fun isTokenStorageInitialized(): Boolean {
        return ::tokenStorage.isInitialized
    }

    /**
     * Storage function to create a DataStoreStorage instance.
     */
    var storage: () -> Storage<Token> = {
        EncryptedDataStoreStorage(storageOption)
    }

    /**
     * Default DataStore for OIDC tokens.
     */
    internal var storageOption: EncryptedDataStoreStorageConfig.() -> Unit = {
        fileName = COM_PING_SDK_V_1_TOKENS
        keyAlias = COM_PING_SDK_V_1_TOKENS
    }

    /**
     * Configures the storage for tokens.
     * @param block A lambda to configure the DataStoreStorageConfig.
     */
    fun storage(block: EncryptedDataStoreStorageConfig.() -> Unit) {
        val previous = storageOption
        storageOption = {
            previous()
            block()
        }
    }

    /**
     * Discovery endpoint URL.
     */
    lateinit var discoveryEndpoint: String

    /**
     * Client ID for OIDC.
     */
    lateinit var clientId: String

    /**
     * Set of scopes for OIDC.
     */
    var scopes = mutableSetOf<String>()

    /**
     * Redirect URI for OIDC.
     */
    lateinit var redirectUri: String

    /**
     * Sign-out redirect URI for OIDC.
     */
    var signOutRedirectUri: String? = null

    /**
     * Login hint for OIDC.
     */
    var loginHint: String? = null

    /**
     * State parameter for OIDC.
     */
    var state: String? = null

    /**
     * Nonce parameter for OIDC.
     */
    var nonce: String? = null

    /**
     * Display parameter for OIDC.
     */
    var display: String? = null

    /**
     * Prompt parameter for OIDC.
     */
    var prompt: String? = null

    /**
     * UI locales parameter for OIDC.
     */
    var uiLocales: String? = null

    /**
     * ACR values parameter for OIDC.
     */
    var acrValues: String? = null

    /**
     * Additional parameters for OIDC.
     */
    var additionalParameters = emptyMap<String, String>()

    /**
     * HTTP client for making network requests.
     */
    lateinit var httpClient: HttpClient

    /**
     * Adds a scope to the set of scopes.
     *
     * @param scope The scope to add.
     */
    fun scope(scope: String) {
        scopes.add(scope)
    }

    /**
     * Initializes the lazy properties to their default values.
     */
    suspend fun init() {

        if (!::httpClient.isInitialized) {
            httpClient = HttpClient(CIO) {
                val log = logger
                followRedirects = false
                if (logger !is None) {
                    install(Logging) {
                        logger =
                            object : io.ktor.client.plugins.logging.Logger {
                                override fun log(message: String) {
                                    log.d(message)
                                }
                            }
                        level = LogLevel.ALL
                    }
                }
            }
        }
        if (!::tokenStorage.isInitialized) {
            tokenStorage = storage()
        }
        if (!::openId.isInitialized) {
            openId = discover()
        }
        if (!::agent.isInitialized) {
            updateAgent(browser)
        }

    }

    /**
     * Discovers the OpenID configuration from the discovery endpoint.
     *
     * @return The discovered OpenID configuration.
     * @throws ApiException If the discovery request fails.
     */
    private suspend fun discover() =
        withContext(Dispatchers.IO) {
            val response = httpClient.get(Url(discoveryEndpoint))
            if (response.status.isSuccess()) {
                return@withContext with(response) {
                    json.decodeFromString<OpenIdConfiguration>(call.body())
                }
            }
            throw ApiException(response.status.value, response.body())
        }

    /**
     * Clones the current configuration.
     *
     * @return A new instance of OidcClientConfig with the same properties.
     */
    fun clone(): OidcClientConfig {
        val cloned = OidcClientConfig()
        cloned += this
        return cloned
    }

    /**
     * Merges another configuration into this one.
     *
     * @param other The other configuration to merge.
     */
    operator fun plusAssign(other: OidcClientConfig) {
        this.openId = other.openId
        this.refreshThreshold = other.refreshThreshold
        this.agent = other.agent
        this.logger = other.logger
        if (other.isTokenStorageInitialized()) this.tokenStorage = other.tokenStorage
        this.storage = other.storage
        this.storageOption = other.storageOption
        this.discoveryEndpoint = other.discoveryEndpoint
        this.clientId = other.clientId
        this.scopes = other.scopes
        this.redirectUri = other.redirectUri
        this.signOutRedirectUri = other.signOutRedirectUri
        this.loginHint = other.loginHint
        this.state = other.state
        this.nonce = other.nonce
        this.display = other.display
        this.prompt = other.prompt
        this.uiLocales = other.uiLocales
        this.acrValues = other.acrValues
        this.additionalParameters = other.additionalParameters
        this.httpClient = other.httpClient
    }
}
/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.commons

import com.pingidentity.logger.Logger
import com.pingidentity.logger.None
import com.pingidentity.mfa.commons.exception.MfaClientNotInitializedException
import com.pingidentity.mfa.commons.exception.MfaInitializationException
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Base abstract class for MFA client implementations.
 * This class provides common functionality for all MFA clients.
 *
 * @param config The configuration for this MFA client.
 * @param storage The storage implementation to use. If null, no storage will be used.
 */
abstract class BaseMfaClient(
    final override val config: MfaConfiguration,
    protected val storage: MfaStorage? = null
) : MfaClient {

    /**
     * Logger instance for logging messages.
     * This can be overridden by subclasses to use a different logger.
     */
    var logger: Logger = config.logger

    /**
     * HTTP client for making network requests.
     */
    lateinit var httpClient: HttpClient

    /**
     * Flag indicating whether this client has been initialized.
     */
    protected var isInitialized = false

    /**
     * Initialize the MFA client with the given configuration.
     * This method initializes the storage client if one is provided.
     *
     * @throws MfaInitializationException if initialization fails.
     */
    override suspend fun initialize() {
        withContext(Dispatchers.IO) {
            try {
                // Initialize storage if it exists
                storage?.initialize()
                
                // Additional initialization for subclasses
                initializeClient()
                
                isInitialized = true
            } catch (e: Exception) {
                coroutineContext.ensureActive()
                logger.e("Failed to initialize client: ${e.message}", e)
                throw MfaInitializationException("Failed to initialize client", e)
            }
        }
    }

    /**
     * Initializes the HTTP client for making network requests.
     * This method is called by [initializeClient] of the client implementation.
     */
    protected fun initHttp(timeout: Long) {
        // Create HTTP client, if not already initialized
        if (!::httpClient.isInitialized) {
            httpClient = HttpClient(CIO) {
                // Install logging plugin for debugging
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
                // Install timeout plugin
                install(HttpTimeout) {
                    requestTimeoutMillis = timeout
                }
            }
        }
    }

    /**
     * Additional initialization for specific client implementations.
     * This method is called by [initialize] after the storage client is initialized.
     * Subclasses should override this method to perform additional initialization.
     *
     * @throws MfaInitializationException if the client cannot be initialized.
     */
    protected abstract suspend fun initializeClient()

    /**
     * Clean up resources used by the MFA client.
     * This method closes the storage client, http client, and performs any additional cleanup.
     */
    override suspend fun close() = withContext(Dispatchers.IO) {
        // Close storage client
        storage?.close()

        // Close HTTP client, if initialized
        if (::httpClient.isInitialized) {
            httpClient.close()
        }

        isInitialized = false
    }

    /**
     * Check if the client is initialized and throw an exception if it's not.
     *
     * @throws MfaClientNotInitializedException if the client is not initialized.
     */
    protected fun checkInitialized() {
        if (!isInitialized) {
            throw MfaClientNotInitializedException("MFA client not initialized. Call initialize() first.")
        }
    }
}
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
import com.pingidentity.mfa.commons.storage.MfaStorageClient
import com.pingidentity.mfa.commons.storage.SQLStorageClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging

/**
 * Base abstract class for MFA client implementations.
 * This class provides common functionality for all MFA clients.
 *
 * @param config The configuration for this MFA client.
 */
abstract class BaseMfaClient(
    override val config: MfaConfiguration
) : MfaClient {

    /**
     * The storage client used for persistent storage.
     * his can be overridden by subclasses to use a different storage implementation.
     */
    protected var storageClient: MfaStorageClient? = null

    /**
     * Logger instance for logging messages.
     * This can be overridden by subclasses to use a different logger.
     */
    var logger: Logger = Logger.logger

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
     * This method creates and initializes the storage client based on the configuration.
     *
     * @return True if initialization was successful, false otherwise.
     */
    override fun initialize(): Boolean {
        try {
            // Create storage client based on configuration
            storageClient = when (config.storageType) {
                StorageType.SQL -> SQLStorageClient(
                    config.context,
                    encryptionEnabled = config.encryptionEnabled
                )
                StorageType.SHARED_PREFERENCES -> {
                    // TODO: Implement SharedPreferences storage
                    // For this POC, we'll always use SQLStorageClient
                    logger.w("SharedPreferences storage not implemented, using SQL storage")
                    SQLStorageClient(
                        config.context,
                        encryptionEnabled = config.encryptionEnabled
                    )
                }
                StorageType.IN_MEMORY -> {
                    // TODO: Implement in memory storage
                    // For this POC, we'll always use SQLStorageClient
                    logger.w("In-memory storage not implemented, using SQL storage")
                    SQLStorageClient(
                        config.context,
                        encryptionEnabled = config.encryptionEnabled
                    )
                }

                null -> TODO()
            }

            // Initialize storage
            storageClient?.initialize()
            
            // Additional initialization for subclasses
            initializeClient()
            
            isInitialized = true
            logger.d("MFA client initialized successfully")
            
            return true
        } catch (e: Exception) {
            logger.e("Failed to initialize MFA client: ${e.message}")
            throw MfaInitializationException("Failed to initialize MFA client", e)
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
    @Throws(MfaInitializationException::class)
    protected abstract fun initializeClient()

    /**
     * Clean up resources used by the MFA client.
     * This method closes the storage client, http client, and performs any additional cleanup.
     */
    override fun close() {
        // Close storage client
        storageClient?.close()
        storageClient = null

        // Close HTTP client, if initialized
        if (::httpClient.isInitialized) {
            httpClient.close()
        }

        isInitialized = false
        logger.d("MFA client closed")
    }

    /**
     * Check if the client is initialized and throw an exception if it's not.
     *
     * @throws MfaClientNotInitializedException if the client is not initialized.
     */
    @Throws(MfaClientNotInitializedException::class)
    protected fun checkInitialized() {
        if (!isInitialized) {
            throw MfaClientNotInitializedException("MFA client not initialized. Call initialize() first.")
        }
    }
}
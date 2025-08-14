/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.oath

import com.pingidentity.mfa.commons.BaseMfaClient
import com.pingidentity.mfa.commons.exception.MfaInitializationException
import com.pingidentity.mfa.oath.storage.OathStorage
import com.pingidentity.mfa.oath.storage.SQLOathStorage
import com.pingidentity.storage.sqlite.passphrase.KeyStorePassphraseProvider
import com.pingidentity.storage.sqlite.passphrase.NonePassphraseProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * Implementation of OathClient that provides OATH functionality.
 * This client handles TOTP and HOTP credential management and code generation.
 *
 * @param configuration The OATH configuration.
 * @param storage The OathStorage implementation to use. If null, a default SQLOathStorage will be created.
 */
class OathClient internal constructor(
    private val configuration: OathConfiguration,
) : BaseMfaClient(configuration, configuration.storage ?: defaultStorage(configuration)), OathMfaClient {

    // The storage used for persisting OATH credentials
    private lateinit var oathStorage: OathStorage

    // The service that handles OATH operations
    private lateinit var oathService: OathService

    companion object {
        /**
         * Create an OathClient instance with a customizable configuration block.
         * This allows for a more fluent, DSL-style configuration approach.
         *
         * @param block The configuration block to customize the client configuration.
         * @return An initialized OathClient instance.
         *
         * @sample
         * ```
         * val oathClient = OathClient {
         *     enableCredentialCache = true
         *     timeoutMs = 60000
         *     encryptionEnabled = false
         *     logger = CustomLogger()
         *     storage = SQLOathStorage()
         * }
         * ```
         */
        suspend operator fun invoke(block: OathConfiguration.() -> Unit = {}): OathMfaClient {
            // Create configuration
            val configuration = OathConfiguration(block)
            configuration.storage = configuration.storage ?: defaultStorage(configuration)

            return OathClient(configuration).apply {
                initialize()
            }
        }

        /**
         * Creates a default OathStorage implementation with the appropriate configuration.
         *
         * @param config The OATH configuration to use for the storage.
         * @return A configured OathStorage implementation.
         */
        private fun defaultStorage(config: OathConfiguration): OathStorage {
            return SQLOathStorage {
                context = config.context
                passphraseProvider = if (config.encryptionEnabled) {
                    KeyStorePassphraseProvider(config.context, logger = config.logger)
                } else {
                    NonePassphraseProvider()
                }
                logger = config.logger
            }
        }
    }

    /**
     * Initialize the OATH client.
     *
     * @throws MfaInitializationException if the client cannot be initialized.
     */
    override suspend fun initializeClient() = withContext(Dispatchers.IO) {
        try {
            // If storage is not an OathStorage implementation, throw an exception
            if (storage !is OathStorage) {
                throw MfaInitializationException("Storage implementation must implement OathStorage interface")
            }

            // Initialize the OathStorage
            oathStorage = storage as OathStorage

            // Create the OATH service with the MFA configuration
            oathService = OathService(
                oathStorage,
                config
            )

            logger.d("OATH client initialized successfully")
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            logger.e("Failed to initialize OATH client: ${e.message}", e)
            throw MfaInitializationException("Failed to initialize OATH client", e)
        }
    }

    /**
     * Add a new OATH credential from a URI.
     *
     * @param uri The URI string in the format otpauth://totp/issuer:accountName?secret=SECRET&issuer=issuer&algorithm=SHA1&digits=6&period=30
     * @return A Result containing the created OathCredential or an Exception in case of failure.
     */
    override suspend fun addCredentialFromUri(uri: String): Result<OathCredential> {
        return try {
            checkInitialized()
            val credential = oathService.parseUri(uri)
            saveCredential(credential)
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            logger.e("Failed to add credential from URI: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Save an OATH credential.
     *
     * @param credential The OathCredential to save.
     * @return A Result containing the saved OathCredential or an Exception in case of failure.
     */
    override suspend fun saveCredential(credential: OathCredential): Result<OathCredential> {
        return try {
            checkInitialized()
            Result.success(oathService.addCredential(credential))
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            logger.e("Failed to save credential: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Get all OATH credentials.
     *
     * @return A Result containing a list of all OathCredentials or an Exception in case of failure.
     */
    override suspend fun getCredentials(): Result<List<OathCredential>> {
        return try {
            checkInitialized()
            Result.success(oathService.getCredentials())
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            logger.e("Failed to get credentials: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Get an OATH credential by ID.
     *
     * @param credentialId The ID of the credential to get.
     * @return A Result containing the OathCredential (or null if not found) or an Exception in case of failure.
     */
    override suspend fun getCredential(credentialId: String): Result<OathCredential?> {
        return try {
            checkInitialized()
            Result.success(oathService.getCredential(credentialId))
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            logger.e("Failed to get credential: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Delete an OATH credential by ID.
     *
     * @param credentialId The ID of the credential to remove.
     * @return A Result containing a Boolean indicating success or an Exception in case of failure.
     */
    override suspend fun deleteCredential(credentialId: String): Result<Boolean> {
        return try {
            checkInitialized()
            Result.success(oathService.removeCredential(credentialId))
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            logger.e("Failed to delete credential: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Generate an OTP code for an OATH credential.
     *
     * @param credentialId The ID of the credential.
     * @return A Result containing the OTP code or an Exception in case of failure.
     */
    override suspend fun generateCode(credentialId: String): Result<String> {
        return try {
            checkInitialized()
            val codeInfo = oathService.generateCodeForCredential(credentialId)
            Result.success(codeInfo.code)
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            logger.e("Failed to generate code: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Generate an OTP code for an OATH credential and get its time validity information.
     *
     * @param credentialId The ID of the credential.
     * @return A Result containing the OTP code and validity information or an Exception in case of failure.
     */
    override suspend fun generateCodeWithValidity(credentialId: String): Result<OathCodeInfo> {
        return try {
            checkInitialized()
            Result.success(oathService.generateCodeForCredential(credentialId))
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            logger.e("Failed to generate code with validity: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Clean up resources used by the OATH client.
     * This method clears caches and then calls the parent close method.
     */
    override suspend fun close() {
        if (isInitialized) {
            try {
                // First clear the service cache
                oathService.clearCache()
                logger.d("OATH client cache cleared")
            } catch (e: Exception) {
                coroutineContext.ensureActive()
                // Just log any errors during cache clearing, but continue with closing
                logger.w("Error clearing OATH client cache: ${e.message}", e)
            }
        }
        // Call parent close method to handle the rest of cleanup
        super.close()
    }
}

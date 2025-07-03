/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.oath

import com.pingidentity.logger.Logger
import com.pingidentity.mfa.commons.BaseMfaClient
import com.pingidentity.mfa.commons.MfaConfiguration
import com.pingidentity.mfa.commons.exception.MfaException
import com.pingidentity.mfa.commons.exception.MfaInitializationException
import com.pingidentity.mfa.oath.storage.SQLOathStorage

/**
 * Implementation of OathClient that provides OATH functionality.
 * This client handles TOTP and HOTP credential management and code generation.
 *
 * @param configuration The MFA configuration.
 * @param storage The OathStorage implementation to use. If null, a default SQLOathStorage will be created.
 */
class OathClient(
    configuration: MfaConfiguration,
    storage: OathStorage? = null
) : BaseMfaClient(configuration, storage), MfaOathClient {

    // The storage used for persisting OATH credentials
    private lateinit var oathStorage: OathStorage

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
        @JvmStatic
        operator fun invoke(block: Builder.() -> Unit = {}): MfaOathClient {
            val builder = Builder()
            builder.apply(block)
            
            val configuration = MfaConfiguration.Builder().apply {
                enableCredentialCache(builder.enableCredentialCache)
                encryptionEnabled(builder.encryptionEnabled)
                timeoutMs(builder.timeoutMs)
                logger(builder.logger)
            }.build()
            
            // Create storage if not provided
            val storage = builder.storage ?: SQLOathStorage(configuration.context, encryptionEnabled = configuration.encryptionEnabled)
            
            val client = OathClient(configuration, storage)
            client.initialize()
            
            return client
        }
        
         /**
         * Create an OathClient instance with the default configuration or a specified configuration.
         * The client is not initialized automatically and requires a call to initialize() before use.
         *
         * @param configuration Optional MfaConfiguration. If not provided, a default configuration will be used.
         * @return An uninitialized OathClient instance.
         */
        @JvmStatic
        fun create(configuration: MfaConfiguration? = null): MfaOathClient {
            val config = configuration ?: MfaConfiguration.Builder().build()
            
            // Create default storage
            val storage = SQLOathStorage(config.context, encryptionEnabled = config.encryptionEnabled)
            
            // Return uninitialized client
            return OathClient(config, storage)
        }       
        
        /**
         * Builder class for OathClient configuration.
         */
        class Builder {
            /**
             * The OathStorage implementation to use.
             * If not set, a default SQLOathStorage implementation will be created.
             */
            var storage: OathStorage? = null
            
            /**
             * Whether credential caching is enabled in memory.
             * By default this is disabled for security reasons.
             */
            var enableCredentialCache: Boolean = false
            
            /**
             * Whether data encryption is enabled.
             */
            var encryptionEnabled: Boolean = true
            
            /**
             * The timeout for network operations in milliseconds.
             */
            var timeoutMs: Long = MfaConfiguration.DEFAULT_TIMEOUT_MS
            
            /**
             * The logger instance for logging messages.
             */
            var logger: Logger = Logger.logger
        }
    }
    
    // The service that handles OATH operations
    private lateinit var oathService: OathService
    
    /**
     * Initialize the OATH client.
     *
     * @throws MfaInitializationException if the client cannot be initialized.
     */
    @Throws(MfaInitializationException::class)
    override fun initializeClient() {
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
            logger.e("Failed to initialize OATH client: ${e.message}", e)
            throw MfaInitializationException("Failed to initialize OATH client", e)
        }
    }
    
    /**
     * Add a new OATH credential from a URI.
     *
     * @param uri The URI string in the format otpauth://totp/issuer:accountName?secret=SECRET&issuer=issuer&algorithm=SHA1&digits=6&period=30
     * @return The created OathCredential.
     * @throws MfaException if the credential cannot be created.
     */
    @Throws(MfaException::class)
    override fun addCredentialFromUri(uri: String): OathCredential {
        checkInitialized()
        try {
            val credential = oathService.parseUri(uri)
            return saveCredential(credential)
        } catch (e: Exception) {
            logger.e("Failed to add credential from URI: ${e.message}", e)
            throw MfaException("Failed to add credential from URI", e)
        }
    }
    
    /**
     * Save an OATH credential.
     *
     * @param credential The OathCredential to save.
     * @return The saved OathCredential.
     * @throws MfaException if the credential cannot be saved.
     */
    @Throws(MfaException::class)
    override fun saveCredential(credential: OathCredential): OathCredential {
        checkInitialized()
        return oathService.addCredential(credential)
    }
    
    /**
     * Get all OATH credentials.
     *
     * @return A list of all OathCredentials.
     * @throws MfaException if the credentials cannot be retrieved.
     */
    @Throws(MfaException::class)
    override fun getCredentials(): List<OathCredential> {
        checkInitialized()
        return oathService.getCredentials()
    }
    
    /**
     * Get an OATH credential by ID.
     *
     * @param credentialId The ID of the credential to get.
     * @return The OathCredential, or null if not found.
     * @throws MfaException if the credential cannot be retrieved.
     */
    @Throws(MfaException::class)
    override fun getCredential(credentialId: String): OathCredential? {
        checkInitialized()
        return oathService.getCredential(credentialId)
    }
    
    /**
     * Delete an OATH credential by ID.
     *
     * @param credentialId The ID of the credential to remove.
     * @return True if the credential was removed, false if it didn't exist.
     * @throws MfaException if the credential cannot be removed.
     */
    @Throws(MfaException::class)
    override fun deleteCredential(credentialId: String): Boolean {
        checkInitialized()
        return oathService.removeCredential(credentialId)
    }
    
    /**
     * Generate an OTP code for an OATH credential.
     *
     * @param credentialId The ID of the credential.
     * @return The OTP code.
     * @throws MfaException if the code cannot be generated.
     */
    @Throws(MfaException::class)
    override fun generateCode(credentialId: String): String {
        val codeInfo = generateCodeWithValidity(credentialId)
        return codeInfo.code
    }
    
    /**
     * Generate an OTP code for an OATH credential and get its time validity information.
     *
     * @param credentialId The ID of the credential.
     * @return The OTP code and validity information.
     * @throws MfaException if the code cannot be generated.
     */
    @Throws(MfaException::class)
    override fun generateCodeWithValidity(credentialId: String): OathCodeInfo {
        checkInitialized()
        return oathService.generateCodeForCredential(credentialId)
    }
    
    /**
     * Clean up resources used by the OATH client.
     * This method clears caches and then calls the parent close method.
     */
    override fun close() {
        if (isInitialized) {
            try {
                // First clear the service cache
                oathService.clearCache()
                logger.d("OATH client cache cleared")
            } catch (e: Exception) {
                // Just log any errors during cache clearing, but continue with closing
                logger.w("Error clearing OATH client cache: ${e.message}", e)
            }
        }
        // Call parent close method to handle the rest of cleanup
        super.close()
    }
}
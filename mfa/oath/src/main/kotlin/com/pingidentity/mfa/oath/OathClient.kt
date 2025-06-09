/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.oath

import com.pingidentity.mfa.commons.BaseMfaClient
import com.pingidentity.mfa.commons.MfaConfiguration
import com.pingidentity.mfa.commons.exception.MfaException
import com.pingidentity.mfa.commons.exception.MfaInitializationException

/**
 * Implementation of OathClient that provides OATH functionality.
 * This client handles TOTP and HOTP credential management and code generation.
 *
 * @param configuration The MFA configuration.
 */
class OathClient(
    configuration: MfaConfiguration
) : BaseMfaClient(configuration), MfaOathClient {

    companion object {
        /**
         * Create an OathClient instance with default configuration.
         *
         * @return An OathClient instance (not initialized).
         */
        @JvmStatic
        fun create(): MfaOathClient {
            // Create configuration
            val configuration = MfaConfiguration.Builder().build()
            
            // Create and initialize client
            val client = OathClient(configuration)
            
            return client
        }
        
        /**
         * Create an OathClient instance with the specified configuration.
         *
         * @param configuration The configuration for the client.
         * @return An OathClient instance (not initialized).
         */
        @JvmStatic
        fun create(configuration: MfaConfiguration): MfaOathClient {
            return OathClient(configuration)
        }
        
        /**
         * Create an OathClient instance with a customizable configuration block.
         * This allows for a more fluent, DSL-style configuration approach.
         * 
         * @param block The configuration block to customize the MFA configuration.
         * @return An initialized OathClient instance.
         * 
         * @sample
         * ```
         * val oathClient = OathClient {
         *     enableCredentialCache = true
         *     timeoutMs = 60000
         *     encryptionEnabled = false
         *     logger = CustomLogger()
         * }
         * ```
         */
        @JvmStatic
        operator fun invoke(block: MfaConfiguration.Builder.() -> Unit = {}): MfaOathClient {
            // Create configuration builder
            val builder = MfaConfiguration.Builder()
            
            // Apply custom configuration
            builder.apply(block)
            
            // Build the final configuration
            val configuration = builder.build()
            
            // Create and initialize client
            val client = OathClient(configuration)
            client.initialize()
            
            return client
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
            // Create the OATH service with the MFA configuration
            oathService = OathService(
                storageClient, 
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
            return addCredential(credential)
        } catch (e: Exception) {
            logger.e("Failed to add credential from URI: ${e.message}", e)
            throw MfaException("Failed to add credential from URI", e)
        }
    }
    
    /**
     * Add a new OATH credential.
     *
     * @param credential The OathCredential to add.
     * @return The created OathCredential.
     * @throws MfaException if the credential cannot be created.
     */
    @Throws(MfaException::class)
    override fun addCredential(credential: OathCredential): OathCredential {
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
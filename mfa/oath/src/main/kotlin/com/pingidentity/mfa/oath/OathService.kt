/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.oath

import com.pingidentity.mfa.commons.exception.MfaException
import com.pingidentity.mfa.commons.storage.MfaStorageClient
import com.pingidentity.mfa.commons.MfaConfiguration
import java.util.concurrent.ConcurrentHashMap

/**
 * Service class that provides OATH functionality including credential management
 * and OTP code generation.
 *
 * @param storageClient The storage client for persisting credentials
 * @param configuration The MFA configuration that includes settings like caching behavior
 */
internal class OathService(
    private val storageClient: MfaStorageClient?,
    private val configuration: MfaConfiguration
) {

    companion object {
        // No constants needed
    }

    // Logger for logging messages
    private val logger = configuration.logger

    // In-memory cache for credentials, only used if enableCredentialCache is true
    private val credentialsCache = ConcurrentHashMap<String, OathCredential>()

    /**
     * Parse an OATH URI and create a credential.
     *
     * @param uri The OATH URI string.
     * @return The created OathCredential.
     * @throws IllegalArgumentException if the URI is invalid.
     */
    fun parseUri(uri: String): OathCredential {
        return OathUriParser.parse(uri)
    }

    /**
     * Format an OATH credential as a URI string.
     *
     * @param credential The OathCredential to format.
     * @return A URI string.
     */
    fun formatUri(credential: OathCredential): String {
        return OathUriParser.format(credential)
    }

    /**
     * Add a new OATH credential.
     *
     * @param credential The OathCredential to add.
     * @return The created OathCredential.
     * @throws MfaException if the credential cannot be created.
     */
    @Throws(MfaException::class)
    fun addCredential(credential: OathCredential): OathCredential {
        try {
            // Add to cache only if caching is enabled
            if (configuration.enableCredentialCache) {
                credentialsCache[credential.id] = credential
            }

            // Store in persistent storage
            storageClient?.storeOathCredential(credential)

            logger.d("Added new OATH credential with ID: ${credential.id}")
            return credential
        } catch (e: Exception) {
            logger.e("Failed to add credential: ${e.message}", e)
            throw MfaException("Failed to add credential", e)
        }
    }

    /**
     * Get all OATH credentials.
     *
     * @return A list of all OathCredentials.
     * @throws MfaException if the credentials cannot be retrieved.
     */
    @Throws(MfaException::class)
    fun getCredentials(): List<OathCredential> {
        try {
            // If caching is enabled and cache has data, use it
            if (configuration.enableCredentialCache && credentialsCache.isNotEmpty()) {
                return credentialsCache.values.toList()
            }

            // Get all credentials from storage
            val mfaCredentials = storageClient?.getAllOathCredentials() ?: emptyList()
            val credentials = mutableListOf<OathCredential>()
            
            // Convert MfaOathCredential to OathCredential
            for (mfaCredential in mfaCredentials) {
                val credential = when (mfaCredential) {
                    is OathCredential -> mfaCredential
                    else -> convertToOathCredential(mfaCredential)
                }
                credentials.add(credential)
                
                // Update cache if enabled
                if (configuration.enableCredentialCache) {
                    credentialsCache[credential.id] = credential
                }
            }

            return credentials
        } catch (e: Exception) {
            logger.e("Failed to get credentials: ${e.message}", e)
            throw MfaException("Failed to get credentials", e)
        }
    }

    /**
     * Get an OATH credential by ID.
     *
     * @param credentialId The ID of the credential to get.
     * @return The OathCredential, or null if not found.
     * @throws MfaException if the credential cannot be retrieved.
     */
    @Throws(MfaException::class)
    fun getCredential(credentialId: String): OathCredential? {
        try {
            // Check cache first if caching is enabled
            var credential: OathCredential? = null
            if (configuration.enableCredentialCache) {
                credential = credentialsCache[credentialId]
            }

            // If not in cache or caching disabled, try to load from storage
            if (credential == null) {
                val mfaCredential = storageClient?.retrieveOathCredential(credentialId)
                
                // Convert MfaOathCredential to OathCredential if found
                if (mfaCredential != null) {
                    credential = when (mfaCredential) {
                        is OathCredential -> mfaCredential
                        else -> convertToOathCredential(mfaCredential)
                    }
                    
                    // Update cache if enabled
                    if (configuration.enableCredentialCache) {
                        credentialsCache[credentialId] = credential
                    }
                }
            }

            return credential
        } catch (e: Exception) {
            logger.e("Failed to get credential with ID $credentialId: ${e.message}", e)
            throw MfaException("Failed to get credential with ID $credentialId", e)
        }
    }
    
    /**
     * Helper method to convert MfaOathCredential to OathCredential.
     */
    private fun convertToOathCredential(credential: com.pingidentity.mfa.commons.model.MfaOathCredential): OathCredential {
        return OathCredential(
            id = credential.id,
            userId = credential.userId,
            resourceId = credential.resourceId,
            issuer = credential.issuer,
            displayIssuer = credential.displayIssuer,
            accountName = credential.accountName,
            displayAccountName = credential.displayAccountName,
            oathType = OathType.valueOf(credential.type),
            secret = credential.secret,
            oathAlgorithm = OathAlgorithm.valueOf(credential.algorithm),
            digits = credential.digits,
            period = credential.period,
            counter = credential.counter,
            createdAt = credential.createdAt,
            imageURL = credential.imageURL,
            backgroundColor = credential.backgroundColor,
            policies = credential.policies,
            lockingPolicy = credential.lockingPolicy,
            isLocked = credential.isLocked
        )
    }

    /**
     * Remove an OATH credential by ID.
     *
     * @param credentialId The ID of the credential to remove.
     * @return True if the credential was removed, false if it didn't exist.
     * @throws MfaException if the credential cannot be removed.
     */
    @Throws(MfaException::class)
    fun removeCredential(credentialId: String): Boolean {
        try {
            // Remove from cache if enabled
            if (configuration.enableCredentialCache) {
                credentialsCache.remove(credentialId)
            }

            // Remove from storage
            val removed = storageClient?.removeOathCredential(credentialId) ?: false

            if (removed) {
                logger.d("Removed OATH credential with ID: $credentialId")
            }

            return removed
        } catch (e: Exception) {
            logger.e("Failed to remove credential with ID $credentialId: ${e.message}", e)
            throw MfaException("Failed to remove credential with ID $credentialId", e)
        }
    }

    /**
     * Generate an OTP code for an OATH credential.
     *
     * @param credential The credential to generate a code for.
     * @return The OTP code with validity information.
     * @throws MfaException if the code cannot be generated.
     */
    @Throws(MfaException::class)
    fun generateCode(credential: OathCredential): OathCodeInfo {
        try {
            return OathAlgorithmHelper.generateCode(credential, configuration.logger)
        } catch (e: Exception) {
            logger.e("Failed to generate code for credential: ${e.message}", e)
            throw MfaException("Failed to generate code", e)
        }
    }

    /**
     * Generate an OTP code for an OATH credential by ID and update the counter if necessary.
     *
     * @param credentialId The ID of the credential.
     * @return The OTP code and validity information.
     * @throws MfaException if the code cannot be generated.
     */
    @Throws(MfaException::class)
    fun generateCodeForCredential(credentialId: String): OathCodeInfo {
        try {
            val credential = getCredential(credentialId)
                ?: throw MfaException("Credential with ID $credentialId not found")

            val codeInfo = generateCode(credential)

            // Update counter in storage for HOTP
            if (credential.oathType == OathType.HOTP && codeInfo.counter > credential.counter) {
                val updatedCredential = credential.copy(counter = codeInfo.counter)
                // Update cache if enabled
                if (configuration.enableCredentialCache) {
                    credentialsCache[credentialId] = updatedCredential
                }
                storageClient?.storeOathCredential(updatedCredential)
            }

            return codeInfo
        } catch (e: Exception) {
            logger.e("Failed to generate code for credential with ID $credentialId: ${e.message}", e)
            throw MfaException("Failed to generate code for credential with ID $credentialId", e)
        }
    }
    
    /**
     * Clear the internal memory caches.
     * This should be called when the client is being closed.
     */
    fun clearCache() {
        credentialsCache.clear()
        logger.d("OATH credentials cache cleared")
    }
    
}
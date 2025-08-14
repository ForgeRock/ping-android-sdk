/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.oath

import com.pingidentity.mfa.commons.exception.MfaException
import com.pingidentity.mfa.commons.MfaConfiguration
import com.pingidentity.mfa.oath.storage.OathStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext

/**
 * Service class that provides OATH functionality including credential management
 * and OTP code generation.
 *
 * @param storage The storage for persisting credentials
 * @param configuration The MFA configuration that includes settings like caching behavior
 */
internal class OathService(
    private val storage: OathStorage?,
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
    suspend fun parseUri(uri: String): OathCredential = withContext(Dispatchers.Default) {
        OathUriParser.parse(uri)
    }

    /**
     * Format an OATH credential as a URI string.
     *
     * @param credential The OathCredential to format.
     * @return A URI string.
     */
    suspend fun formatUri(credential: OathCredential): String = withContext(Dispatchers.Default) {
        OathUriParser.format(credential)
    }

    /**
     * Add a new OATH credential.
     *
     * @param credential The OathCredential to add.
     * @return The created OathCredential.
     * @throws MfaException if the credential cannot be created.
     */
    suspend fun addCredential(credential: OathCredential): OathCredential {
        return withContext(Dispatchers.IO) {
            try {
                // Add to cache only if caching is enabled
                if (configuration.enableCredentialCache) {
                    credentialsCache[credential.id] = credential
                }

                // Store in persistent storage
                storage?.storeOathCredential(credential)

                logger.d("Added new OATH credential with ID: ${credential.id}")
                credential
            } catch (e: Exception) {
                coroutineContext.ensureActive()
                logger.e("Failed to add credential: ${e.message}", e)
                throw MfaException("Failed to add credential", e)
            }
        }
    }

    /**
     * Get all OATH credentials.
     *
     * @return A list of all OathCredentials.
     * @throws MfaException if the credentials cannot be retrieved.
     */
    suspend fun getCredentials(): List<OathCredential> {
        return withContext(Dispatchers.IO) {
            try {
                // If caching is enabled and cache has data, use it
                if (configuration.enableCredentialCache && credentialsCache.isNotEmpty()) {
                    return@withContext credentialsCache.values.toList()
                }

                // Get all credentials from storage
                val credentials = storage?.getAllOathCredentials() ?: emptyList()
                
                // Update cache if enabled
                if (configuration.enableCredentialCache) {
                    credentials.forEach { credential ->
                        credentialsCache[credential.id] = credential
                    }
                }

                credentials
            } catch (e: Exception) {
                coroutineContext.ensureActive()
                logger.e("Failed to get credentials: ${e.message}", e)
                throw MfaException("Failed to get credentials", e)
            }
        }
    }

    /**
     * Get an OATH credential by ID.
     *
     * @param credentialId The ID of the credential to get.
     * @return The OathCredential, or null if not found.
     * @throws MfaException if the credential cannot be retrieved.
     */
    suspend fun getCredential(credentialId: String): OathCredential? {
        return withContext(Dispatchers.IO) {
            try {
                // Check cache first if caching is enabled
                var credential: OathCredential? = null
                if (configuration.enableCredentialCache) {
                    credential = credentialsCache[credentialId]
                }

                // If not in cache or caching disabled, try to load from storage
                if (credential == null) {
                    credential = storage?.retrieveOathCredential(credentialId)
                    
                    // Update cache if credential was found and caching is enabled
                    if (credential != null && configuration.enableCredentialCache) {
                        credentialsCache[credentialId] = credential
                    }
                }

                credential
            } catch (e: Exception) {
                logger.e("Failed to get credential with ID $credentialId: ${e.message}", e)
                throw MfaException("Failed to get credential with ID $credentialId", e)
            }
        }
    }
    


    /**
     * Remove an OATH credential by ID.
     *
     * @param credentialId The ID of the credential to remove.
     * @return True if the credential was removed, false if it didn't exist.
     * @throws MfaException if the credential cannot be removed.
     */
    suspend fun removeCredential(credentialId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Remove from cache if enabled
                if (configuration.enableCredentialCache) {
                    credentialsCache.remove(credentialId)
                }

                // Remove from storage
                val removed = storage?.removeOathCredential(credentialId) ?: false

                if (removed) {
                    logger.d("Removed OATH credential with ID: $credentialId")
                }

                removed
            } catch (e: Exception) {
                coroutineContext.ensureActive()
                logger.e("Failed to remove credential with ID $credentialId: ${e.message}", e)
                throw MfaException("Failed to remove credential with ID $credentialId", e)
            }
        }
    }

    /**
     * Generate an OTP code for an OATH credential.
     *
     * @param credential The credential to generate a code for.
     * @return The OTP code with validity information.
     * @throws MfaException if the code cannot be generated.
     */
    suspend fun generateCode(credential: OathCredential): OathCodeInfo {
        try {
            return withContext(Dispatchers.IO) {
                OathAlgorithmHelper.generateCode(credential, configuration.logger)
            }
        } catch (e: Exception) {
            coroutineContext.ensureActive()
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
    suspend fun generateCodeForCredential(credentialId: String): OathCodeInfo {
        return withContext(Dispatchers.IO) {
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
                    storage?.storeOathCredential(updatedCredential)
                }

                codeInfo
            } catch (e: Exception) {
                coroutineContext.ensureActive()
                logger.e("Failed to generate code for credential with ID $credentialId: ${e.message}", e)
                throw MfaException("Failed to generate code for credential with ID $credentialId", e)
            }
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
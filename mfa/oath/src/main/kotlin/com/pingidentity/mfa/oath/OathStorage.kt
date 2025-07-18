/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.oath

import com.pingidentity.mfa.commons.MfaStorage
import com.pingidentity.mfa.commons.exception.MfaStorageException

/**
 * Interface for OATH-specific storage operations.
 * Extends the base MfaStorage interface with OATH-specific functionality.
 */
interface OathStorage : MfaStorage {

    /**
     * Store an OATH credential.
     *
     * @param credential The OATH credential to be stored.
     * @throws MfaStorageException if the credential cannot be stored.
     */
    suspend fun storeOathCredential(credential: OathCredential)
    
    /**
     * Retrieve an OATH credential by its ID.
     *
     * @param credentialId The ID of the credential to retrieve.
     * @return The OATH credential, or null if not found.
     * @throws MfaStorageException if the credential cannot be retrieved.
     */
    suspend fun retrieveOathCredential(credentialId: String): OathCredential?
    
    /**
     * Get all OATH credentials.
     *
     * @return A list of all OATH credentials.
     * @throws MfaStorageException if the credentials cannot be retrieved.
     */
    suspend fun getAllOathCredentials(): List<OathCredential>
    
    /**
     * Remove an OATH credential by its ID.
     *
     * @param credentialId The ID of the credential to remove.
     * @return true if the credential was successfully removed, false if it didn't exist.
     * @throws MfaStorageException if the credential cannot be removed.
     */
    suspend fun removeOathCredential(credentialId: String): Boolean
    
    /**
     * Clear all OATH credentials from the storage.
     *
     * @throws MfaStorageException if the credentials cannot be cleared.
     */
    suspend fun clearOathCredentials()
}

/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.commons

import com.pingidentity.mfa.commons.exception.MfaStorageException

/**
 * Base interface for MFA storage operations.
 * This interface defines the common operations that all storage implementations must support.
 * Specific credential type storage functionality should be defined in extending interfaces.
 */
interface MfaStorage {
    
    /**
     * Initialize the storage.
     * This method must be called before any other method on the storage.
     *
     * @throws MfaStorageException if initialization fails.
     */
    suspend fun initialize()
    
    /**
     * Clear all data from all storage.
     *
     * @throws MfaStorageException if the storage cannot be cleared.
     */
    suspend fun clear()
    
    /**
     * Close the storage and release any resources.
     * This method should be called when the storage is no longer needed.
     */
    suspend fun close()

}

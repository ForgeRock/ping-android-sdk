/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.commons

/**
 * Base interface for all MFA client implementations.
 * This interface defines the common methods and properties that all MFA clients must implement.
 */
interface MfaClient {
    
    /**
     * The configuration for this MFA client.
     */
    val config: MfaConfiguration
    
    /**
     * Initialize the MFA client with the given configuration.
     * This method must be called before any other method on the client.
     *
     * @return True if initialization was successful, false otherwise.
     */
    suspend fun initialize(): Boolean
    
    /**
     * Clean up any resources used by the MFA client.
     * This method should be called when the client is no longer needed to ensure
     * proper cleanup of resources.
     */
    suspend fun close()
}

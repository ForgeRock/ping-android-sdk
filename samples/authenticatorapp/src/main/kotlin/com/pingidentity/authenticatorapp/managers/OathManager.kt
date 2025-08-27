/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.authenticatorapp.managers

import com.pingidentity.authenticatorapp.data.DiagnosticLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import com.pingidentity.mfa.oath.OathCodeInfo
import com.pingidentity.mfa.oath.OathCredential
import com.pingidentity.mfa.oath.OathMfaClient

/**
 * Manager class for handling all OATH credential operations.
 * Encapsulates OATH-specific business logic and state management.
 *
 * @param oathClient The OATH MFA client instance
 * @param diagnosticLogger DiagnosticLogger for logging
 */
class OathManager(
    private var oathClient: OathMfaClient? = null,
    private val diagnosticLogger: DiagnosticLogger
) {
    
    private val _oathCredentials = MutableStateFlow<List<OathCredential>>(emptyList())
    val oathCredentials: StateFlow<List<OathCredential>> = _oathCredentials.asStateFlow()
    
    private val _isLoadingOathCredentials = MutableStateFlow(false)
    val isLoadingOathCredentials: StateFlow<Boolean> = _isLoadingOathCredentials.asStateFlow()
    
    private val _generatedCodes = MutableStateFlow<Map<String, OathCodeInfo>>(emptyMap())
    val generatedCodes: StateFlow<Map<String, OathCodeInfo>> = _generatedCodes.asStateFlow()
    
    private val _lastAddedOathCredential = MutableStateFlow<OathCredential?>(null)
    val lastAddedOathCredential: StateFlow<OathCredential?> = _lastAddedOathCredential.asStateFlow()

    /**
     * Sets the OATH client instance.
     */
    fun setClient(client: OathMfaClient) {
        this.oathClient = client
    }

    /**
     * Loads all OATH credentials from the SDK.
     */
    suspend fun loadCredentials(): Result<List<OathCredential>> {
        val client = oathClient ?: return Result.failure(Exception("OATH client not initialized"))
        _isLoadingOathCredentials.value = true
        return try {
            val result = withContext(Dispatchers.IO) {
                diagnosticLogger.d("Loading OATH credentials from OathClient")
                client.getCredentials()
            }
            
            result.onSuccess { credentials ->
                _oathCredentials.value = credentials
            }
            
            _isLoadingOathCredentials.value = false
            result
        } catch (e: Exception) {
            _isLoadingOathCredentials.value = false
            Result.failure(e)
        }
    }

    /**
     * Adds an OATH credential from a URI.
     */
    suspend fun addCredentialFromUri(uri: String): Result<OathCredential> {
        val client = oathClient ?: return Result.failure(Exception("OATH client not initialized"))
        return try {
            val result = withContext(Dispatchers.IO) {
                diagnosticLogger.d("Adding OATH credential from URI: ${maskUri(uri)}")
                client.addCredentialFromUri(uri)
            }
            
            result.onSuccess { credential ->
                _lastAddedOathCredential.value = credential
                // Reload credentials to refresh the list
                loadCredentials()
            }
            
            result
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Removes an OATH credential from the SDK.
     */
    suspend fun removeCredential(credentialId: String): Result<Boolean> {
        val client = oathClient ?: return Result.failure(Exception("OATH client not initialized"))
        return try {
            val result = withContext(Dispatchers.IO) {
                diagnosticLogger.d("Removing OATH credential: $credentialId")
                client.deleteCredential(credentialId)
            }
            
            result.onSuccess { removed ->
                if (removed) {
                    // Reload credentials to refresh the list
                    loadCredentials()
                }
            }
            
            result
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Updates an OATH credential in the SDK.
     */
    suspend fun updateCredential(credential: OathCredential): Result<OathCredential> {
        val client = oathClient ?: return Result.failure(Exception("OATH client not initialized"))
        return try {
            val result = withContext(Dispatchers.IO) {
                diagnosticLogger.d("Updating OATH credential: $credential")
                client.saveCredential(credential)
            }
            
            result.onSuccess {
                // Reload credentials to refresh the list
                loadCredentials()
            }
            
            result
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Generates a code for a credential.
     */
    suspend fun generateCode(credentialId: String): Result<OathCodeInfo> {
        val client = oathClient ?: return Result.failure(Exception("OATH client not initialized"))
        return try {
            val result = withContext(Dispatchers.IO) {
                client.generateCodeWithValidity(credentialId)
            }
            
            result.onSuccess { codeInfo ->
                val updatedCodes = _generatedCodes.value.toMutableMap()
                updatedCodes[credentialId] = codeInfo
                _generatedCodes.value = updatedCodes
            }
            
            result
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Clears the last added OATH credential.
     */
    fun clearLastAddedCredential() {
        _lastAddedOathCredential.value = null
    }

    /**
     * Closes the OATH client and releases resources.
     */
    suspend fun close() {
        try {
            oathClient?.close()
        } catch (e: Exception) {
            diagnosticLogger.e("Error closing OATH client", e)
        }
    }

    /**
     * Masks sensitive information in a URI for logging.
     */
    private fun maskUri(uri: String): String {
        return uri.replace(Regex("secret=[^&]*"), "secret=*****")
    }
}
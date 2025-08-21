/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.authenticatorapp.data

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pingidentity.mfa.oath.OathMfaClient
import com.pingidentity.mfa.oath.OathClient
import com.pingidentity.mfa.oath.OathCodeInfo
import com.pingidentity.mfa.oath.OathCredential
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel for the Authenticator app.
 */
class AuthenticatorViewModel : ViewModel() {
    
    private lateinit var oathClient: OathMfaClient
    
    private val _uiState = MutableStateFlow(AuthenticatorUiState())
    val uiState: StateFlow<AuthenticatorUiState> = _uiState.asStateFlow()

    /**
     * Initializes the SDK.
     */
    fun initialize(context: Context) {
        // Context is automatically managed by the ContextProvider available in SDK,
        // But you can still pass context if you need. In that case, you need to add
        // dependency on :foundation:android" to your build.gradle.kts file and call:
        // ContextProvider.init(context)
        
        viewModelScope.launch {
            try {
                // Initialize OATH client
                oathClient = OathClient {
                    // Enable credential caching
                    enableCredentialCache = true
                }
                
                // Load all credentials
                loadCredentials()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to initialize") }
            }
        }
    }

    /**
     * Loads all credentials from the SDK.
     */
    private fun loadCredentials() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                oathClient.getCredentials()
            }.onSuccess { credentials ->
                _uiState.update { it.copy(credentials = credentials, error = null) }
            }.onFailure { e ->
                _uiState.update { it.copy(error = e.message ?: "Failed to load credentials") }
            }
        }
    }

    /**
     * Adds a credential from a URI.
     */
    fun addCredentialFromUri(uri: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                oathClient.addCredentialFromUri(uri)
            }.onSuccess { credential ->
                // Reload all credentials after adding a new one
                loadCredentials()
                _uiState.update { it.copy(lastAddedCredential = credential, error = null) }
            }.onFailure { e ->
                _uiState.update { it.copy(error = e.message ?: "Failed to add credential") }
            }
        }
    }

    /**
     * Removes a credential from the SDK.
     */
    fun removeCredential(credentialId: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                oathClient.deleteCredential(credentialId)
            }.onSuccess { removed ->
                if (removed) {
                    // Reload all credentials after removing one
                    loadCredentials()
                }
            }.onFailure { e ->
                _uiState.update { it.copy(error = e.message ?: "Failed to remove credential") }
            }
        }
    }

    /**
     * Generates a code for a credential.
     */
    fun generateCode(credentialId: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                oathClient.generateCodeWithValidity(credentialId)
            }.onSuccess { codeInfo ->
                // Update the code info in the UI state
                val updatedCodes = _uiState.value.generatedCodes.toMutableMap()
                updatedCodes[credentialId] = codeInfo
                
                _uiState.update { it.copy(generatedCodes = updatedCodes, error = null) }
            }.onFailure { e ->
                _uiState.update { it.copy(error = e.message ?: "Failed to generate code") }
            }
        }
    }

    /**
     * Clears the error message in the UI state.
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /**
     * Clears the last added credential in the UI state.
     */
    fun clearLastAddedCredential() {
        _uiState.update { it.copy(lastAddedCredential = null) }
    }
}

/**
 * Data class representing the UI state of the Authenticator app.
 */
data class AuthenticatorUiState(
    val credentials: List<OathCredential> = emptyList(),
    val generatedCodes: Map<String, OathCodeInfo> = emptyMap(),
    val lastAddedCredential: OathCredential? = null,
    val error: String? = null
)

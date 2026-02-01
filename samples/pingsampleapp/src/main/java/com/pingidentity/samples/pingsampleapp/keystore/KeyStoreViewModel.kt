/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.samples.pingsampleapp.keystore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.security.KeyStore
import java.util.Collections

class KeyStoreViewModel : ViewModel() {

    private val _keyAliases = MutableStateFlow<List<String>>(emptyList())
    val keyAliases: StateFlow<List<String>> = _keyAliases.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        loadKeyAliases()
    }

    fun loadKeyAliases() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                val keyStore = KeyStore.getInstance("AndroidKeyStore")
                keyStore.load(null)

                val aliases = Collections.list(keyStore.aliases())
                _keyAliases.value = aliases.sorted()
            } catch (e: Exception) {
                _error.value = "Failed to load key aliases: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun deleteKey(alias: String) {
        viewModelScope.launch {
            try {
                val keyStore = KeyStore.getInstance("AndroidKeyStore")
                keyStore.load(null)
                keyStore.deleteEntry(alias)

                // Reload the key aliases after deletion
                loadKeyAliases()
            } catch (e: Exception) {
                _error.value = "Failed to delete key '$alias': ${e.message}"
            }
        }
    }
}
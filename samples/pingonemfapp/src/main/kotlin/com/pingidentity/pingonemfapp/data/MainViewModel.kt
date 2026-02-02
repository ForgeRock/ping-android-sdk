/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.pingonemfapp.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pingidentity.logger.Logger
import com.pingidentity.logger.STANDARD
import com.pingidentity.pingonemfapp.managers.AccountsManager
import com.pingidentity.pingonemfapp.managers.OTPManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for the PingOneMFA Authenticator app.
 * Coordinates between different managers and handles UI-specific logic.
 * 
 * @param application The application context for accessing app-level resources
 * @param userPreferences Injected UserPreferences dependency for settings management
 * @param accountsManager Manager for PingOneMFA SDK paired accounts
 * @param otpManager Manager for OTP from PingOneMFA SDK
 */
class PingOneMFAViewModel(
    application: Application,
    private val userPreferences: UserPreferences,

    private val accountsManager: AccountsManager,
    private val otpManager: OTPManager,
) : AndroidViewModel(application), ViewModelProvider.Factory {

    private val _uiState = MutableStateFlow(AuthenticatorUiState())
    private val diagnosticLogger = DiagnosticLogger

    private var pingOneMfaAccountsLoaded = false

    val uiState: StateFlow<AuthenticatorUiState> = _uiState.asStateFlow()

    val otpState: StateFlow<OtpUiState> = otpManager.otpState

    // Expose all settings preferences as StateFlows
    val copyOtp: StateFlow<Boolean>
        get() = userPreferences.copyOtpFlow

    val diagnosticLogging: StateFlow<Boolean>
        get() = userPreferences.diagnosticLoggingFlow

    val themeMode: StateFlow<ThemeMode>
        get() = userPreferences.themeModeFlow


    /**
     * Initializes the ViewModel by setting up state flows and loading initial data.
     */
    init {
        setupStateFlows()
        loadInitialData()
    }

    /**
     * Sets up the state flows to observe manager states and update UI state accordingly.
     */
    private fun setupStateFlows() {

        // Observe PingOne MFA paired accounts from PingOneMFA SDK
        viewModelScope.launch {
            accountsManager.mfaAccountsUi.collect { accounts ->
                _uiState.update { it.copy(pingOneMfaAccounts = accounts) }
            }
        }
    }

    /**
     * Loads initial data from all managers.
     */
    private fun loadInitialData() {
        viewModelScope.launch {
            // Set initial loading state
            _uiState.update { it.copy(isInitialLoading = true) }
            try {
                loadPingOneMfaAccounts()
            } finally {
                // Clear initial loading state once everything is loaded
                _uiState.update { it.copy(isInitialLoading = false) }
            }
        }
    }

    /**
     * Loads all paired accounts from the PingOneMFA SDK.
     */
    private suspend fun loadPingOneMfaAccounts() {
        accountsManager.loadAccounts().onSuccess {
            pingOneMfaAccountsLoaded = true
            _uiState.update { it.copy(pingOneMfaAccounts = it.pingOneMfaAccounts, error = null) }
        }.onFailure { e ->
            _uiState.update { it.copy(error = e.message ?: "Failed to load MFA accounts") }
        }
    }

    fun startOtpSequence(){
        diagnosticLogger.d("startOtpSequence")
        otpManager.startAutoRefresh(viewModelScope)
    }
    fun stopOtpSequence(){
        diagnosticLogger.d("stopOtpSequence")
        otpManager.stop()
    }

    /**
     * Updates the diagnostic logging setting
     */
    fun setDiagnosticLogging(enabled: Boolean) {
        viewModelScope.launch {
            diagnosticLogger.d("SettingsScreen: setDiagnosticLogging: $enabled")
            userPreferences.setDiagnosticLogging(enabled)
            // Set the global logger based on the diagnostic logging setting
            Logger.logger = if (enabled) {
                DiagnosticLogger
            } else {
                Logger.STANDARD
            }
        }
    }

    /**
     * Updates the theme mode setting
     */
    fun setThemeMode(themeMode: ThemeMode) {
        viewModelScope.launch {
            diagnosticLogger.d("SettingsScreen: setThemeMode: $themeMode")
            userPreferences.setThemeMode(themeMode)
        }
    }


    fun tryToPairUserForPingOneMFA(pairingKey: String){
        diagnosticLogger.d("tryToPairUserForPingOneMFA: $pairingKey")
        // Attempt to pair user
        _uiState.update { it.copy(isLoadingPingOneAccounts = true) }
        viewModelScope.launch {
            accountsManager.addAccountFromPairingKeyScan(pairingKey).onSuccess {
                loadPingOneMfaAccounts()
                _uiState.update { it.copy(isLoadingPingOneAccounts = false, error = null) }
            }.onFailure {
                _uiState.update { it.copy(isLoadingPingOneAccounts = false, error = it.error ?: "Failed to pair user") }
            }
        }
    }

    /**
     * Sets the error message in the UI state.
     */
    fun setError(errorMessage: String) {
        _uiState.update { it.copy(error = errorMessage) }
    }

    /**
     * Clears the error message in the UI state.
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /**
     * Sets the message in the UI state.
     */
    fun setMessage(message: String) {
        _uiState.update { it.copy(message = message) }
    }

    /**
     * Clears the message in the UI state.
     */
    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }


    // clean-up OTP refresh Job on ViewModel destroy
    override fun onCleared() {
        otpManager.stop()
        super.onCleared()
    }

}

/**
 * Data class representing the UI state of the Authenticator app.
 */
data class AuthenticatorUiState(
    val pingOneMfaAccounts: List<AccountItem> = emptyList(),
    val error: String? = null,
    val message: String? = null,
    // Loading states for better UX
    val isInitialLoading: Boolean = false,
    val isLoadingPingOneAccounts: Boolean = false,
    val isRefreshing: Boolean = false
)

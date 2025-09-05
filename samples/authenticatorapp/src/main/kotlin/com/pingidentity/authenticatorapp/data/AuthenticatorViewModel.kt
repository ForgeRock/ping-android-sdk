/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.authenticatorapp.data

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pingidentity.authenticatorapp.R
import com.pingidentity.authenticatorapp.managers.AccountGroupingManager
import com.pingidentity.authenticatorapp.managers.OathManager
import com.pingidentity.authenticatorapp.managers.PushManager
import com.pingidentity.authenticatorapp.managers.TestAccountFactory
import com.pingidentity.logger.Logger
import com.pingidentity.logger.STANDARD
import com.pingidentity.mfa.commons.exception.CredentialLockedException
import com.pingidentity.mfa.oath.OathCodeInfo
import com.pingidentity.mfa.oath.OathCredential
import com.pingidentity.mfa.push.PushCredential
import com.pingidentity.mfa.push.PushNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for the Authenticator app.
 * Coordinates between different managers and handles UI-specific logic.
 * 
 * @param application The application context for accessing app-level resources
 * @param userPreferences Injected UserPreferences dependency for settings management
 * @param oathManager Manager for OATH credential operations
 * @param pushManager Manager for Push credential and notification operations
 * @param accountGroupingManager Manager for account grouping and ordering
 * @param testAccountFactory Factory for creating test accounts
 */
class AuthenticatorViewModel(
    application: Application,
    private val userPreferences: UserPreferences,
    private val oathManager: OathManager,
    private val pushManager: PushManager,
    private val accountGroupingManager: AccountGroupingManager,
    private val testAccountFactory: TestAccountFactory
) : AndroidViewModel(application), ViewModelProvider.Factory {

    private val _uiState = MutableStateFlow(AuthenticatorUiState())
    private val diagnosticLogger = DiagnosticLogger
    
    // Track loading states to batch account group updates
    private var oathCredentialsLoaded = false
    private var pushCredentialsLoaded = false

    val uiState: StateFlow<AuthenticatorUiState> = _uiState.asStateFlow()

    // Expose all settings preferences as StateFlows
    val copyOtp: StateFlow<Boolean>
        get() = userPreferences.copyOtpFlow

    val tapToReveal: StateFlow<Boolean>
        get() = userPreferences.tapToRevealFlow

    val combineAccounts: StateFlow<Boolean>
        get() = userPreferences.combineAccountsFlow

    val diagnosticLogging: StateFlow<Boolean>
        get() = userPreferences.diagnosticLoggingFlow

    val testMode: StateFlow<Boolean>
        get() = userPreferences.testModeFlow


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
        // Observe credential changes and update account groups
        viewModelScope.launch {
            combine(
                oathManager.oathCredentials,
                pushManager.pushCredentials
            ) { oathCreds, pushCreds ->
                Pair(oathCreds, pushCreds)
            }.collect { (oathCreds, pushCreds) ->
                accountGroupingManager.updateAccountGroups(oathCreds, pushCreds)
                // Update UI state when credentials change
                updateUiStateFromManagers()
            }
        }
        
        // Observe combine accounts setting changes and update account groups
        viewModelScope.launch {
            userPreferences.combineAccountsFlow.collect { _ ->
                // Force re-grouping when combine accounts setting changes
                val currentState = _uiState.value
                accountGroupingManager.updateAccountGroups(
                    currentState.oathCredentials, 
                    currentState.pushCredentials
                )
                updateUiStateFromManagers()
            }
        }
        
        // Observe account groups from AccountGroupingManager
        viewModelScope.launch {
            accountGroupingManager.accountGroups.collect { accountGroups ->
                _uiState.update { it.copy(accountGroups = accountGroups) }
            }
        }
        
        // Observe individual state changes
        viewModelScope.launch {
            oathManager.generatedCodes.collect { codes ->
                _uiState.update { it.copy(generatedCodes = codes) }
            }
        }
        
        viewModelScope.launch {
            oathManager.lastAddedOathCredential.collect { credential ->
                _uiState.update { it.copy(lastAddedOathCredential = credential) }
            }
        }
        
        viewModelScope.launch {
            pushManager.lastAddedPushCredential.collect { credential ->
                _uiState.update { it.copy(lastAddedPushCredential = credential) }
            }
        }
        
        viewModelScope.launch {
            oathManager.isLoadingOathCredentials.collect { loading ->
                _uiState.update { it.copy(isLoadingOathCredentials = loading) }
            }
        }
        
        viewModelScope.launch {
            pushManager.isLoadingPushCredentials.collect { loading ->
                _uiState.update { it.copy(isLoadingPushCredentials = loading) }
            }
        }
        
        viewModelScope.launch {
            pushManager.isLoadingNotifications.collect { loading ->
                _uiState.update { it.copy(isLoadingNotifications = loading) }
            }
        }
        
        viewModelScope.launch {
            pushManager.pushNotifications.collect { notifications ->
                _uiState.update { it.copy(pushNotifications = notifications) }
            }
        }
        
        viewModelScope.launch {
            pushManager.pendingNotifications.collect { notifications ->
                _uiState.update { it.copy(pendingNotifications = notifications) }
            }
        }
        
        viewModelScope.launch {
            pushManager.pushNotificationItems.collect { items ->
                _uiState.update { it.copy(pushNotificationItems = items) }
            }
        }
        
        viewModelScope.launch {
            pushManager.pendingNotificationItems.collect { items ->
                _uiState.update { it.copy(pendingNotificationItems = items) }
            }
        }
    }
    
    /**
     * Updates the UI state from all manager states.
     */
    private fun updateUiStateFromManagers() {
        _uiState.update { currentState ->
            currentState.copy(
                oathCredentials = oathManager.oathCredentials.value,
                pushCredentials = pushManager.pushCredentials.value
            )
        }
    }

    /**
     * Loads initial data from all managers.
     */
    private fun loadInitialData() {
        viewModelScope.launch {
            try {
                // Set initial loading state
                _uiState.update { it.copy(isInitialLoading = true) }
                
                // Load all credentials and notifications
                loadOathCredentials()
                loadPushCredentials()
                loadPushNotifications()
                
                // Clear initial loading state once everything is loaded
                _uiState.update { it.copy(isInitialLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to initialize", isInitialLoading = false) }
            }
        }
    }

    /**
     * Loads all OATH credentials from the SDK.
     */
    private fun loadOathCredentials() {
        viewModelScope.launch {
            oathManager.loadCredentials().onSuccess {
                oathCredentialsLoaded = true
                _uiState.update { it.copy(error = null) }
            }.onFailure { e ->
                _uiState.update { it.copy(error = e.message ?: "Failed to load OATH credentials") }
            }
        }
    }
    
    /**
     * Loads all Push credentials from the SDK.
     */
    private fun loadPushCredentials() {
        viewModelScope.launch {
            pushManager.loadCredentials().onSuccess {
                pushCredentialsLoaded = true
                _uiState.update { it.copy(error = null) }
            }.onFailure { e ->
                _uiState.update { it.copy(error = e.message ?: "Failed to load Push credentials") }
            }
        }
    }
    
    /**
     * Loads all push notifications from the SDK.
     */
    private fun loadPushNotifications() {
        viewModelScope.launch {
            pushManager.loadPushNotifications().onSuccess {
                _uiState.update { it.copy(error = null) }
            }.onFailure { e ->
                _uiState.update { it.copy(error = e.message ?: "Failed to load push notifications") }
            }
        }
    }


    /**
     * Update the account groups order immediately in the UI state.
     * This provides immediate feedback while the order is being persisted.
     */
    fun updateAccountGroupOrder(newAccountGroups: List<AccountGroup>) {
        accountGroupingManager.updateAccountGroupOrder(newAccountGroups)
        // Also save to preferences asynchronously
        viewModelScope.launch {
            accountGroupingManager.saveAccountOrder(newAccountGroups)
        }
    }

    /**
     * Updates the copy OTP setting
     */
    fun setCopyOtp(enabled: Boolean) {
        viewModelScope.launch {
            diagnosticLogger.d("SettingsScreen: setCopyOtp: $enabled")
            userPreferences.setCopyOtp(enabled)
        }
    }

    /**
     * Updates the tap to reveal setting
     */
    fun setTapToReveal(enabled: Boolean) {
        viewModelScope.launch {
            diagnosticLogger.d("SettingsScreen: setTapToReveal: $enabled")
            userPreferences.setTapToReveal(enabled)
        }
    }

    /**
     * Updates the combine accounts setting
     */
    fun setCombineAccounts(enabled: Boolean) {
        viewModelScope.launch {
            diagnosticLogger.d("SettingsScreen: setCombineAccounts: $enabled")
            userPreferences.setCombineAccounts(enabled)
        }
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
     * Updates the test mode setting
     */
    fun setTestMode(enabled: Boolean) {
        viewModelScope.launch {
            diagnosticLogger.d("SettingsScreen: setTestMode: $enabled")
            userPreferences.setTestMode(enabled)
        }
    }

    /**
     * Refreshes all credentials (OATH and Push).
     */
    fun refreshCredentials() {
        viewModelScope.launch {
            try {
                // Set refresh loading state
                _uiState.update { it.copy(isRefreshing = true) }
                
                // Reset loading states before refreshing
                oathCredentialsLoaded = false
                pushCredentialsLoaded = false
                loadOathCredentials()
                loadPushCredentials()
                
                // Clear refresh loading state
                _uiState.update { it.copy(isRefreshing = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(
                    isRefreshing = false,
                    error = e.message ?: "Failed to refresh credentials"
                ) }
            }
        }
    }

    /**
     * Refreshes push notifications, loading both pending and historical notifications.
     * Call this when entering the notifications screen to ensure all notifications are loaded.
     */
    fun refreshNotifications() {
        viewModelScope.launch {
            try {
                diagnosticLogger.d("Refreshing all notifications")
                pushManager.loadAllPushNotifications().onFailure { e ->
                    _uiState.update { it.copy(error = e.message ?: "Failed to refresh notifications") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(
                    error = e.message ?: "Failed to refresh notifications"
                ) }
            }
        }
    }

    /**
     * Gets the current device token used for push notifications.
     */
    internal fun getDeviceToken(onTokenReceived: (String?) -> Unit) {
        viewModelScope.launch {
            pushManager.getDeviceToken().onSuccess { token ->
                _uiState.update { it.copy(message = getApplication<Application>().getString(R.string.test_screen_device_token_retrieved)) }
                onTokenReceived(token)
            }.onFailure { e ->
                _uiState.update { it.copy(error = e.message ?: "Failed to get device token") }
                onTokenReceived(null)
            }
        }
    }


    /**
     * Forces a renewal of the Firebase device token.
     */
    internal fun forceDeviceTokenRenew() {
        viewModelScope.launch {
            pushManager.forceDeviceTokenRenew().onSuccess {
                _uiState.update { it.copy(message = getApplication<Application>().getString(R.string.test_screen_device_token_renewed)) }
            }.onFailure { e ->
                _uiState.update { it.copy(error = e.message ?: "Failed to renew device token") }
            }
        }
    }
    
    /**
     * Gets a specific push notification item by its ID.
     * This is used to retrieve the notification details for display in the UI.
     */
    fun getNotificationItemById(notificationId: String): PushNotificationItem? {
        return pushManager.getNotificationItemById(notificationId)
    }

    /**
     * Adds an OATH credential from a URI.
     */
    fun addOathCredentialFromUri(uri: String) {
        viewModelScope.launch {
            oathManager.addCredentialFromUri(uri).onSuccess {
                _uiState.update { it.copy(error = null) }
            }.onFailure { e ->
                _uiState.update { it.copy(error = e.message ?: "Failed to add OATH credential") }
            }
        }
    }

    /**
     * Adds a Push credential from a URI.
     */
    fun addPushCredentialFromUri(uri: String) {
        viewModelScope.launch {
            pushManager.addCredentialFromUri(uri).onSuccess {
                _uiState.update { it.copy(error = null) }
            }.onFailure { e ->
                _uiState.update { it.copy(error = e.message ?: "Failed to add Push credential") }
            }
        }
    }

    /**
     * Adds both OATH and Push credentials from a URI.
     * Ensures that at least the OATH credential is registered even if Push fails.
     */
    fun addMfaCredentialFromUri(uri: String) {
        viewModelScope.launch {
            try {
                // Attempt to add OATH credential first
                oathManager.addCredentialFromUri(uri).onSuccess {
                    _uiState.update { it.copy(error = null) }
                }.onFailure { e ->
                    _uiState.update { it.copy(error = e.message ?: "Failed to add OATH credential") }
                    return@launch
                }

                // Attempt to add Push credential
                pushManager.addCredentialFromUri(uri).onSuccess {
                    _uiState.update { it.copy(error = null) }
                }.onFailure { e ->
                    _uiState.update { it.copy(error = "OATH credential added, but failed to add Push credential: ${e.message}") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Unexpected error while adding MFA credential") }
            }
        }
    }

    /**
     * Removes an OATH credential from the SDK.
     */
    fun removeOathCredential(credentialId: String) {
        viewModelScope.launch {
            oathManager.removeCredential(credentialId).onFailure { e ->
                _uiState.update { it.copy(error = e.message ?: "Failed to remove OATH credential") }
            }
        }
    }

    /**
     * Removes a Push credential from the SDK.
     */
    fun removePushCredential(credentialId: String) {
        viewModelScope.launch {
            pushManager.removeCredential(credentialId).onFailure { e ->
                _uiState.update { it.copy(error = e.message ?: "Failed to remove Push credential") }
            }
        }
    }

    /**
     * Updates an OATH credential in the SDK.
     */
    fun updateOathCredential(credential: OathCredential) {
        viewModelScope.launch {
            oathManager.updateCredential(credential).onFailure { e ->
                _uiState.update { it.copy(error = e.message ?: "Failed to update OATH credential") }
            }
        }
    }

    /**
     * Updates a Push credential in the SDK.
     */
    fun updatePushCredential(credential: PushCredential) {
        viewModelScope.launch {
            pushManager.updateCredential(credential).onFailure { e ->
                _uiState.update { it.copy(error = e.message ?: "Failed to update Push credential") }
            }
        }
    }

    /**
     * Locks an account by applying the specified policy to all credentials in the account group.
     * 
     * @param accountGroup The account group to lock
     * @param policyName The name of the locking policy to apply
     */
    fun lockAccountGroup(accountGroup: AccountGroup, policyName: String) {
        viewModelScope.launch {
            try {
                // Lock all OATH credentials in the group
                accountGroup.oathCredentials.forEach { credential ->
                    val lockedCredential = credential.copy()
                    lockedCredential.lockCredential(policyName)
                    oathManager.updateCredential(lockedCredential).onFailure { e ->
                        throw e
                    }
                }
                
                // Lock all Push credentials in the group
                accountGroup.pushCredentials.forEach { credential ->
                    val lockedCredential = credential.copy()
                    lockedCredential.lockCredential(policyName)
                    pushManager.updateCredential(lockedCredential).onFailure { e ->
                        throw e
                    }
                }
                
                _uiState.update { 
                    it.copy(message = getApplication<Application>().getString(R.string.test_screen_account_locked_success))
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(error = e.message ?: "Failed to lock account") 
                }
            }
        }
    }

    /**
     * Unlocks an account by removing the lock from all credentials in the account group.
     * 
     * @param accountGroup The account group to unlock
     */
    fun unlockAccountGroup(accountGroup: AccountGroup) {
        viewModelScope.launch {
            try {
                // Unlock all OATH credentials in the group
                accountGroup.oathCredentials.forEach { credential ->
                    val unlockedCredential = credential.copy()
                    unlockedCredential.unlockCredential()
                    oathManager.updateCredential(unlockedCredential).onFailure { e ->
                        throw e
                    }
                }
                
                // Unlock all Push credentials in the group
                accountGroup.pushCredentials.forEach { credential ->
                    val unlockedCredential = credential.copy()
                    unlockedCredential.unlockCredential()
                    pushManager.updateCredential(unlockedCredential).onFailure { e ->
                        throw e
                    }
                }
                
                // Generate codes immediately for unlocked OATH credentials
                accountGroup.oathCredentials.forEach { credential ->
                    generateCode(credential.id)
                }
                
                _uiState.update { 
                    it.copy(message = getApplication<Application>().getString(R.string.test_screen_account_unlocked_success))
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(error = e.message ?: "Failed to unlock account") 
                }
            }
        }
    }

    /**
     * Generates a code for a credential.
     */
    fun generateCode(credentialId: String) {
        viewModelScope.launch {
            oathManager.generateCode(credentialId).onFailure { e ->
                if (e is CredentialLockedException) {
                    // Ignore locked credential errors for code generation
                    diagnosticLogger.d("Credential $credentialId is locked, cannot generate code")
                } else {
                    _uiState.update {
                        it.copy(error = e.message ?: "Failed to generate code")
                    }
                }
            }
        }
    }

    /**
     * Approves a push notification.
     */
    fun approveNotification(notificationId: String) {
        viewModelScope.launch {
            pushManager.approveNotification(notificationId).onSuccess { success ->
                if (!success) {
                    _uiState.update { it.copy(error = "Failed to approve notification") }
                }
            }.onFailure { e ->
                _uiState.update { it.copy(error = e.message ?: "Failed to approve notification") }
            }
        }
    }

    /**
     * Approves a push notification with a challenge response.
     */
    fun approveChallengeNotification(notificationId: String, challengeResponse: String) {
        viewModelScope.launch {
            pushManager.approveChallengeNotification(notificationId, challengeResponse).onSuccess { success ->
                if (!success) {
                    _uiState.update { it.copy(error = "Failed to approve challenge notification") }
                }
            }.onFailure { e ->
                _uiState.update { it.copy(error = e.message ?: "Failed to approve challenge notification") }
            }
        }
    }

    /**
     * Denies a push notification.
     */
    fun denyNotification(notificationId: String) {
        viewModelScope.launch {
            pushManager.denyNotification(notificationId).onSuccess { success ->
                if (!success) {
                    _uiState.update { it.copy(error = "Failed to deny notification") }
                }
            }.onFailure { e ->
                _uiState.update { it.copy(error = e.message ?: "Failed to deny notification") }
            }
        }
    }

    /**
     * Cleans up old notifications.
     */
    fun cleanupNotifications() {
        viewModelScope.launch {
            pushManager.cleanupNotifications().onSuccess {
                _uiState.update { it.copy(message = getApplication<Application>().getString(R.string.test_screen_notifications_cleaned_up)) }
            }.onFailure { e ->
                _uiState.update { it.copy(error = e.message ?: "Failed to clean up notifications") }
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

    /**
     * Clears the last added OATH credential in the UI state.
     */
    fun clearLastAddedOathCredential() {
        oathManager.clearLastAddedCredential()
    }
    
    /**
     * Copies the specified text to the clipboard
     */
    fun copyToClipboard(context: Context, text: String, label: String = "ADB Command") {
        diagnosticLogger.d("Copying code to Clipboard")
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
    }
    
    /**
     * Clears the last added Push credential in the UI state.
     */
    fun clearLastAddedPushCredential() {
        pushManager.clearLastAddedCredential()
    }

    /**
     * Test function: Creates a random OATH account for testing
     */
    fun createRandomOathAccount() {
        viewModelScope.launch {
            try {
                val (uri, message) = testAccountFactory.createRandomOathAccount()
                addOathCredentialFromUri(uri)
                _uiState.update { it.copy(message = message) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to create random OATH account") }
            }
        }
    }
    
    /**
     * Test function: Creates a random PUSH account for testing
     */
    fun createRandomPushAccount() {
        viewModelScope.launch {
            try {
                val (credential, message) = testAccountFactory.createRandomPushCredential()
                
                pushManager.updateCredential(credential).onSuccess {
                    _uiState.update { it.copy(message = message) }
                }.onFailure { e ->
                    _uiState.update { it.copy(error = e.message ?: "Failed to create test push account") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to create test push account") }
            }
        }
    }

    /**
     * Test function: Creates a random combined OATH + PUSH account for testing
     */
    fun createRandomCombinedMfaAccount() {
        viewModelScope.launch {
            try {
                val (pushCredential, oathCredential, message) = testAccountFactory.createRandomCombinedMfaCredentials()

                // Save both credentials
                var hasError = false
                pushManager.updateCredential(pushCredential).onFailure { e ->
                    _uiState.update { it.copy(error = e.message ?: "Failed to create test push account") }
                    hasError = true
                }
                
                if (!hasError) {
                    oathManager.updateCredential(oathCredential).onSuccess {
                        _uiState.update { it.copy(message = message) }
                    }.onFailure { e ->
                        _uiState.update { it.copy(error = e.message ?: "Failed to create test OATH account") }
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to create test combined account") }
            }
        }
    }


    /**
     * Called when the ViewModel is cleared.
     */
    override fun onCleared() {
        super.onCleared()
        
        viewModelScope.launch {
            try {
                // Close managers to release resources
                oathManager.close()
                pushManager.close()
            } catch (e: Exception) {
                // Log any errors during cleanup
                diagnosticLogger.e("Error closing managers", e)
            }
        }
    }
}

/**
 * Data class representing the UI state of the Authenticator app.
 */
data class AuthenticatorUiState(
    val oathCredentials: List<OathCredential> = emptyList(),
    val pushCredentials: List<PushCredential> = emptyList(),
    val accountGroups: List<AccountGroup> = emptyList(),
    val generatedCodes: Map<String, OathCodeInfo> = emptyMap(),
    val pushNotifications: List<PushNotification> = emptyList(),
    val pendingNotifications: List<PushNotification> = emptyList(),
    val pushNotificationItems: List<PushNotificationItem> = emptyList(),
    val pendingNotificationItems: List<PushNotificationItem> = emptyList(),
    val lastAddedOathCredential: OathCredential? = null,
    val lastAddedPushCredential: PushCredential? = null,
    val error: String? = null,
    val message: String? = null,
    // Loading states for better UX
    val isInitialLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingOathCredentials: Boolean = false,
    val isLoadingPushCredentials: Boolean = false,
    val isLoadingNotifications: Boolean = false
)

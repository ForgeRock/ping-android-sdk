/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.pingonemfapp.managers

import com.pingidentity.pingonemfa.commons.PingOneMFA
import com.pingidentity.pingonemfa.commons.PingOneMfaAccount
import com.pingidentity.pingonemfapp.data.AccountItem
import com.pingidentity.pingonemfapp.data.DiagnosticLogger
import com.pingidentity.pingonemfapp.data.toUiItems
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class AccountsManager(
    private val diagnosticLogger: DiagnosticLogger
) {

    private val _isLoadingMfaAccounts = MutableStateFlow(false)
    val isLoadingMfaAccounts: StateFlow<Boolean> = _isLoadingMfaAccounts.asStateFlow()

    private val _mfaAccounts = MutableStateFlow<List<PingOneMfaAccount>>(emptyList())
    val mfaAccounts: StateFlow<List<PingOneMfaAccount>> = _mfaAccounts.asStateFlow()

    private val _mfaAccountsUi = MutableStateFlow<List<AccountItem>>(emptyList())
    val mfaAccountsUi: StateFlow<List<AccountItem>> = _mfaAccountsUi.asStateFlow()

    suspend fun addAccountFromPairingKeyScan(pairingKey: String): Result<Unit> {
        diagnosticLogger.d("addAccountFromPairingKeyScan: $pairingKey")
        return PingOneMFA.pair(pairingKey)
    }

    suspend fun loadAccounts(): Result<List<PingOneMfaAccount>> {
        _isLoadingMfaAccounts.value = true
        return try {
            val result = withContext(Dispatchers.IO) {
                diagnosticLogger.d("Loading MFA accounts from PingOneMFA")
                PingOneMFA.getAccounts()
            }
            result.onSuccess { accounts ->
                _mfaAccounts.value = accounts
                diagnosticLogger.d("Loaded ${accounts.size} MFA accounts from PingOneMFA")
                updatePingOneMFAAccounts()
            }
            result.onFailure {
                diagnosticLogger.e("Failed to load MFA accounts from PingOneMFA", it)
                _mfaAccounts.value = emptyList()
            }
            _isLoadingMfaAccounts.value = false
            result
        } catch (e: Exception) {
            _isLoadingMfaAccounts.value = false
            Result.failure(e)
        }
    }

    private fun updatePingOneMFAAccounts() {
        val mfaAccountsUi = _mfaAccounts.value.toUiItems()
         _mfaAccountsUi.value = mfaAccountsUi
    }
}

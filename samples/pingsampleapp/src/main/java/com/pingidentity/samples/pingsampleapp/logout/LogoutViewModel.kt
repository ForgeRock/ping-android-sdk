/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.samples.pingsampleapp.logout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pingidentity.journey.user as journeyUser
import com.pingidentity.davinci.user as davinciUser
import com.pingidentity.samples.pingsampleapp.config.ConfigurationManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

data class LogoutState(
    val daVinci: Boolean = false,
    val journey: Boolean = false,
    val oidc: Boolean = false,
)
class LogoutViewModel: ViewModel() {
    val state = MutableStateFlow(LogoutState())

    fun listLogoutOptions() {
        viewModelScope.launch {
            state.value = LogoutState(
                daVinci = ConfigurationManager.daVinci?.davinciUser() != null,
                journey = ConfigurationManager.journey?.journeyUser() != null,
                oidc = ConfigurationManager.oidcWebClient?.user() != null,
            )
        }
    }

    fun logoutJourney(onCompleted: () -> Unit) {
        viewModelScope.launch {
            ConfigurationManager.journey?.journeyUser()?.logout()
            onCompleted()
        }
    }

    fun logoutDaVinci(onCompleted: () -> Unit) {
        viewModelScope.launch {
            ConfigurationManager.daVinci?.davinciUser()?.logout()
            onCompleted()
        }
    }

    fun logoutOidcWeb(onCompleted: () -> Unit) {
        viewModelScope.launch {
            ConfigurationManager.oidcWebClient?.user()?.logout()
            onCompleted()
        }
    }

    fun logoutAll(onCompleted: () -> Unit) {
        viewModelScope.launch {
            // Logout from all active sessions
            ConfigurationManager.journey?.journeyUser()?.logout()
            ConfigurationManager.daVinci?.davinciUser()?.logout()
            ConfigurationManager.oidcWebClient?.user()?.logout()
            onCompleted()
        }
    }

    companion object {
        fun factory() = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return LogoutViewModel() as T
            }
        }
    }
}
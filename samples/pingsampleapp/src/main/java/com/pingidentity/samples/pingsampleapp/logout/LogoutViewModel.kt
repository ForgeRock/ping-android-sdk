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
import com.pingidentity.samples.pingsampleapp.config.daVinci
import com.pingidentity.samples.pingsampleapp.config.journey
import com.pingidentity.samples.pingsampleapp.config.web
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
                daVinci = daVinci?.davinciUser() != null,
                journey = journey.journeyUser() != null,
                oidc = web?.user() != null,
            )
        }
    }

    fun logoutJourney(onCompleted: () -> Unit) {
        viewModelScope.launch {
            journey.journeyUser()?.logout()
            onCompleted()
        }
    }

    fun logoutDaVinci(onCompleted: () -> Unit) {
        viewModelScope.launch {
            daVinci?.davinciUser()?.logout()
            onCompleted()
        }
    }

    fun logoutOidcWeb(onCompleted: () -> Unit) {
        viewModelScope.launch {
            web?.user()?.logout()
            onCompleted()
        }
    }

    fun logoutAll(onCompleted: () -> Unit) {
        viewModelScope.launch {
            // Logout from all active sessions
            journey.journeyUser()?.logout()
            daVinci?.davinciUser()?.logout()
            web?.user()?.logout()
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
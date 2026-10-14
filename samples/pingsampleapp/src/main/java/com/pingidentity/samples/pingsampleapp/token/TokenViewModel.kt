/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.samples.pingsampleapp.token

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pingidentity.journey.user as journeyUser
import com.pingidentity.davinci.user as davinciUser
import com.pingidentity.samples.pingsampleapp.config.daVinci
import com.pingidentity.samples.pingsampleapp.config.journey
import com.pingidentity.samples.pingsampleapp.config.oidcDeviceClient
import com.pingidentity.samples.pingsampleapp.config.rarWeb
import com.pingidentity.samples.pingsampleapp.config.web
import com.pingidentity.utils.Result.Failure
import com.pingidentity.utils.Result.Success
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TokenViewModel : ViewModel() {
    var state = MutableStateFlow(TokenState())
        private set

    private var routeTabApplied = false

    fun selectTab(tabType: TokenType) {
        routeTabApplied = true
        state.update { it.copy(selectedTab = tabType) }
    }

    /**
     * Applies the route argument's tab only for a fresh destination: a new back-stack entry
     * gets a new ViewModel, so its `tab` argument still wins; on configuration changes the
     * retained ViewModel has already applied a tab and the user's selection is preserved.
     */
    fun selectTabFromRoute(tabType: TokenType) {
        if (!routeTabApplied) selectTab(tabType)
    }

    fun accessToken() {
        when (state.value.selectedTab) {
            TokenType.JOURNEY -> journeyAccessToken()
            TokenType.DAVINCI -> daVinciAccessToken()
            TokenType.OIDC -> oidcAccessToken()
            TokenType.OIDC_RAR -> oidcRarAccessToken()
            TokenType.AUTH_GRANT -> authGrantAccessToken()
        }
    }

    /**
     * Load tokens for all authentication types to ensure all sessions are displayed.
     * This is useful when you want to see all available tokens across different auth types.
     */
    fun loadAllTokens() {
        journeyAccessToken()
        daVinciAccessToken()
        oidcAccessToken()
        oidcRarAccessToken()
        authGrantAccessToken()
    }

    fun refresh() {
        when (state.value.selectedTab) {
            TokenType.JOURNEY -> journeyRefresh()
            TokenType.DAVINCI -> daVinciRefresh()
            TokenType.OIDC -> oidcRefresh()
            TokenType.OIDC_RAR -> oidcRarRefresh()
            TokenType.AUTH_GRANT -> authGrantRefresh()
        }
    }

    fun revoke() {
        when (state.value.selectedTab) {
            TokenType.JOURNEY -> journeyRevoke()
            TokenType.DAVINCI -> daVinciRevoke()
            TokenType.OIDC -> oidcRevoke()
            TokenType.OIDC_RAR -> oidcRarRevoke()
            TokenType.AUTH_GRANT -> authGrantRevoke()
        }
    }

    fun reset() {
        when (state.value.selectedTab) {
            TokenType.JOURNEY -> state.update { it.copy(journeyToken = null, journeyError = null) }
            TokenType.DAVINCI -> state.update { it.copy(daVinciToken = null, daVinciError = null) }
            TokenType.OIDC -> state.update { it.copy(oidcToken = null, oidcError = null) }
            TokenType.OIDC_RAR -> state.update { it.copy(oidcRarToken = null, oidcRarError = null) }
            TokenType.AUTH_GRANT -> state.update { it.copy(authGrantToken = null, authGrantError = null) }
        }
    }

    // Journey Token Operations
    private fun journeyAccessToken() {
        viewModelScope.launch {
            try {
                journey?.journeyUser()?.let {
                    when (val result = it.token()) {
                        is Failure -> state.update { state ->
                            state.copy(journeyToken = null, journeyError = result.value)
                        }
                        is Success -> state.update { state ->
                            state.copy(journeyToken = result.value, journeyError = null)
                        }
                    }
                } ?: state.update { it.copy(journeyToken = null, journeyError = null) }
            } catch (_: Exception) {
                state.update { it.copy(journeyToken = null, journeyError = null) }
            }
        }
    }

    private fun journeyRevoke() {
        viewModelScope.launch {
            try {
                journey?.journeyUser()?.revoke()
            } catch (_: Exception) {
                // ignore revoke errors
            }
            state.update { it.copy(journeyToken = null, journeyError = null) }
        }
    }

    private fun journeyRefresh() {
        viewModelScope.launch {
            try {
                journey?.journeyUser()?.let {
                    when (val result = it.refresh()) {
                        is Failure -> state.update { state ->
                            state.copy(journeyToken = null, journeyError = result.value)
                        }
                        is Success -> state.update { state ->
                            state.copy(journeyToken = result.value, journeyError = null)
                        }
                    }
                } ?: state.update { it.copy(journeyToken = null, journeyError = null) }
            } catch (_: Exception) {
                state.update { it.copy(journeyToken = null, journeyError = null) }
            }
        }
    }

    // DaVinci Token Operations
    private fun daVinciAccessToken() {
        viewModelScope.launch {
            try {
                daVinci?.davinciUser()?.let {
                    when (val result = it.token()) {
                        is Failure -> state.update { state ->
                            state.copy(daVinciToken = null, daVinciError = result.value)
                        }
                        is Success -> state.update { state ->
                            state.copy(daVinciToken = result.value, daVinciError = null)
                        }
                    }
                } ?: state.update { it.copy(daVinciToken = null, daVinciError = null) }
            } catch (_: Exception) {
                state.update { it.copy(daVinciToken = null, daVinciError = null) }
            }
        }
    }

    private fun daVinciRevoke() {
        viewModelScope.launch {
            try {
                daVinci?.davinciUser()?.revoke()
            } catch (_: Exception) {
                // ignore revoke errors
            }
            state.update { it.copy(daVinciToken = null, daVinciError = null) }
        }
    }

    private fun daVinciRefresh() {
        viewModelScope.launch {
            try {
                daVinci?.davinciUser()?.let {
                    when (val result = it.refresh()) {
                        is Failure -> state.update { state ->
                            state.copy(daVinciToken = null, daVinciError = result.value)
                        }
                        is Success -> state.update { state ->
                            state.copy(daVinciToken = result.value, daVinciError = null)
                        }
                    }
                } ?: state.update { it.copy(daVinciToken = null, daVinciError = null) }
            } catch (_: Exception) {
                state.update { it.copy(daVinciToken = null, daVinciError = null) }
            }
        }
    }

    // OIDC Token Operations
    private fun oidcAccessToken() {
        viewModelScope.launch {
            try {
                web?.user()?.let {
                    when (val result = it.token()) {
                        is Failure -> state.update { state ->
                            state.copy(oidcToken = null, oidcError = result.value)
                        }
                        is Success -> state.update { state ->
                            state.copy(oidcToken = result.value, oidcError = null)
                        }
                    }
                } ?: state.update { it.copy(oidcToken = null, oidcError = null) }
            } catch (_: Exception) {
                state.update { it.copy(oidcToken = null, oidcError = null) }
            }
        }
    }

    private fun oidcRevoke() {
        viewModelScope.launch {
            try {
                web?.user()?.revoke()
            } catch (_: Exception) {
                // ignore revoke errors
            }
            state.update { it.copy(oidcToken = null, oidcError = null) }
        }
    }

    private fun oidcRefresh() {
        viewModelScope.launch {
            try {
                web?.user()?.let {
                    when (val result = it.refresh()) {
                        is Failure -> state.update { state ->
                            state.copy(oidcToken = null, oidcError = result.value)
                        }
                        is Success -> state.update { state ->
                            state.copy(oidcToken = result.value, oidcError = null)
                        }
                    }
                } ?: state.update { it.copy(oidcToken = null, oidcError = null) }
            } catch (_: Exception) {
                state.update { it.copy(oidcToken = null, oidcError = null) }
            }
        }
    }

    // OIDC RAR Token Operations (dedicated RAR client, own storage — token B)
    private fun oidcRarAccessToken() {
        viewModelScope.launch {
            try {
                rarWeb?.user()?.let {
                    when (val result = it.token()) {
                        is Failure -> state.update { state ->
                            state.copy(oidcRarToken = null, oidcRarError = result.value)
                        }
                        is Success -> state.update { state ->
                            state.copy(oidcRarToken = result.value, oidcRarError = null)
                        }
                    }
                } ?: state.update { it.copy(oidcRarToken = null, oidcRarError = null) }
            } catch (_: Exception) {
                state.update { it.copy(oidcRarToken = null, oidcRarError = null) }
            }
        }
    }

    private fun oidcRarRevoke() {
        viewModelScope.launch {
            try {
                rarWeb?.user()?.revoke()
            } catch (_: Exception) {
                // ignore revoke errors
            }
            state.update { it.copy(oidcRarToken = null, oidcRarError = null) }
        }
    }

    private fun oidcRarRefresh() {
        viewModelScope.launch {
            try {
                rarWeb?.user()?.let {
                    when (val result = it.refresh()) {
                        is Failure -> state.update { state ->
                            state.copy(oidcRarToken = null, oidcRarError = result.value)
                        }
                        is Success -> state.update { state ->
                            state.copy(oidcRarToken = result.value, oidcRarError = null)
                        }
                    }
                } ?: state.update { it.copy(oidcRarToken = null, oidcRarError = null) }
            } catch (_: Exception) {
                state.update { it.copy(oidcRarToken = null, oidcRarError = null) }
            }
        }
    }

    // Auth Grant Token Operations
    private fun authGrantAccessToken() {
        viewModelScope.launch {
            try {
                oidcDeviceClient?.user()?.let {
                    when (val result = it.token()) {
                        is Failure -> state.update { state ->
                            state.copy(authGrantToken = null, authGrantError = result.value)
                        }
                        is Success -> state.update { state ->
                            state.copy(authGrantToken = result.value, authGrantError = null)
                        }
                    }
                } ?: state.update { it.copy(authGrantToken = null, authGrantError = null) }
            } catch (_: Exception) {
                state.update { it.copy(authGrantToken = null, authGrantError = null) }
            }
        }
    }

    private fun authGrantRevoke() {
        viewModelScope.launch {
            try {
                oidcDeviceClient?.user()?.revoke()
            } catch (_: Exception) {
                // ignore revoke errors
            }
            state.update { it.copy(authGrantToken = null, authGrantError = null) }
        }
    }

    private fun authGrantRefresh() {
        viewModelScope.launch {
            try {
                oidcDeviceClient?.user()?.let {
                    when (val result = it.refresh()) {
                        is Failure -> state.update { state ->
                            state.copy(authGrantToken = null, authGrantError = result.value)
                        }
                        is Success -> state.update { state ->
                            state.copy(authGrantToken = result.value, authGrantError = null)
                        }
                    }
                } ?: state.update { it.copy(authGrantToken = null, authGrantError = null) }
            } catch (_: Exception) {
                state.update { it.copy(authGrantToken = null, authGrantError = null) }
            }
        }
    }

    companion object {
        fun factory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return TokenViewModel() as T
            }
        }
    }
}

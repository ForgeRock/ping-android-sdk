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
import com.pingidentity.samples.pingsampleapp.config.web
import com.pingidentity.utils.Result.Failure
import com.pingidentity.utils.Result.Success
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TokenViewModel : ViewModel() {
    var state = MutableStateFlow(TokenState())
        private set

    fun selectTab(tabType: TokenType) {
        state.update { it.copy(selectedTab = tabType) }
    }

    fun accessToken() {
        when (state.value.selectedTab) {
            TokenType.JOURNEY -> journeyAccessToken()
            TokenType.DAVINCI -> daVinciAccessToken()
            TokenType.OIDC -> oidcAccessToken()
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
    }

    fun refresh() {
        when (state.value.selectedTab) {
            TokenType.JOURNEY -> journeyRefresh()
            TokenType.DAVINCI -> daVinciRefresh()
            TokenType.OIDC -> oidcRefresh()
        }
    }

    fun revoke() {
        when (state.value.selectedTab) {
            TokenType.JOURNEY -> journeyRevoke()
            TokenType.DAVINCI -> daVinciRevoke()
            TokenType.OIDC -> oidcRevoke()
        }
    }

    fun reset() {
        when (state.value.selectedTab) {
            TokenType.JOURNEY -> state.update { it.copy(journeyToken = null, journeyError = null) }
            TokenType.DAVINCI -> state.update { it.copy(daVinciToken = null, daVinciError = null) }
            TokenType.OIDC -> state.update { it.copy(oidcToken = null, oidcError = null) }
        }
    }

    // Journey Token Operations
    private fun journeyAccessToken() {
        viewModelScope.launch {
            journey.journeyUser()?.let {
                when (val result = it.token()) {
                    is Failure -> {
                        state.update { state ->
                            state.copy(journeyToken = null, journeyError = result.value)
                        }
                    }
                    is Success -> {
                        state.update { state ->
                            state.copy(journeyToken = result.value, journeyError = null)
                        }
                    }
                }
            } ?: run {
                state.update {
                    it.copy(journeyToken = null, journeyError = null)
                }
            }
        }
    }

    private fun journeyRevoke() {
        viewModelScope.launch {
            journey.journeyUser()?.revoke()
            state.update {
                it.copy(journeyToken = null, journeyError = null)
            }
        }
    }

    private fun journeyRefresh() {
        viewModelScope.launch {
            journey.journeyUser()?.let {
                when (val result = it.refresh()) {
                    is Failure -> {
                        state.update { state ->
                            state.copy(journeyToken = null, journeyError = result.value)
                        }
                    }
                    is Success -> {
                        state.update { state ->
                            state.copy(journeyToken = result.value, journeyError = null)
                        }
                    }
                }
            } ?: run {
                state.update {
                    it.copy(journeyToken = null, journeyError = null)
                }
            }
        }
    }

    // DaVinci Token Operations
    private fun daVinciAccessToken() {
        viewModelScope.launch {
            daVinci?.davinciUser()?.let {
                when (val result = it.token()) {
                    is Failure -> {
                        state.update { state ->
                            state.copy(daVinciToken = null, daVinciError = result.value)
                        }
                    }
                    is Success -> {
                        state.update { state ->
                            state.copy(daVinciToken = result.value, daVinciError = null)
                        }
                    }
                }
            } ?: run {
                state.update {
                    it.copy(daVinciToken = null, daVinciError = null)
                }
            }
        }
    }

    private fun daVinciRevoke() {
        viewModelScope.launch {
            daVinci?.davinciUser()?.revoke()
            state.update {
                it.copy(daVinciToken = null, daVinciError = null)
            }
        }
    }

    private fun daVinciRefresh() {
        viewModelScope.launch {
            daVinci?.davinciUser()?.let {
                when (val result = it.refresh()) {
                    is Failure -> {
                        state.update { state ->
                            state.copy(daVinciToken = null, daVinciError = result.value)
                        }
                    }
                    is Success -> {
                        state.update { state ->
                            state.copy(daVinciToken = result.value, daVinciError = null)
                        }
                    }
                }
            } ?: run {
                state.update {
                    it.copy(daVinciToken = null, daVinciError = null)
                }
            }
        }
    }

    // OIDC Token Operations
    private fun oidcAccessToken() {
        viewModelScope.launch {
            web?.user()?.let {
                when (val result = it.token()) {
                    is Failure -> {
                        state.update { state ->
                            state.copy(oidcToken = null, oidcError = result.value)
                        }
                    }
                    is Success -> {
                        state.update { state ->
                            state.copy(oidcToken = result.value, oidcError = null)
                        }
                    }
                }
            } ?: run {
                state.update {
                    it.copy(oidcToken = null, oidcError = null)
                }
            }
        }
    }

    private fun oidcRevoke() {
        viewModelScope.launch {
            web?.user()?.revoke()
            state.update {
                it.copy(oidcToken = null, oidcError = null)
            }
        }
    }

    private fun oidcRefresh() {
        viewModelScope.launch {
            web?.user()?.let {
                when (val result = it.refresh()) {
                    is Failure -> {
                        state.update { state ->
                            state.copy(oidcToken = null, oidcError = result.value)
                        }
                    }
                    is Success -> {
                        state.update { state ->
                            state.copy(oidcToken = result.value, oidcError = null)
                        }
                    }
                }
            } ?: run {
                state.update {
                    it.copy(oidcToken = null, oidcError = null)
                }
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

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
import com.pingidentity.journey.user
import com.pingidentity.oidc.Token
import com.pingidentity.samples.pingsampleapp.config.journey
import com.pingidentity.utils.Result.Failure
import com.pingidentity.utils.Result.Success
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class TokenViewModel : ViewModel() {
    private val json: Json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    var state = MutableStateFlow(TokenState())
        private set

    val formattedToken = state.map { tokenState ->
        tokenState.token?.let {
            json.encodeToString(Token.serializer(), it)
        } ?: tokenState.error?.toString() ?: "No token information is available"
    }

    fun accessToken() {
        viewModelScope.launch {
            journey.user()?.let {
                when (val result = it.token()) {
                    is Failure -> {
                        state.update { state ->
                            state.copy(token = null, error = result.value)
                        }
                    }

                    is Success -> {
                        state.update { state ->
                            state.copy(token = result.value, error = null)
                        }
                    }
                }
            } ?: run {
                state.update {
                    it.copy(token = null, error = null)
                }
            }
        }
    }

    fun revoke() {
        viewModelScope.launch {
            journey.user()?.revoke()
            state.update {
                it.copy(token = null, error = null)
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            journey.user()?.let {
                when (val result = it.refresh()) {
                    is Failure -> {
                        state.update { state ->
                            state.copy(token = null, error = result.value)
                        }
                    }

                    is Success -> {
                        state.update { state ->
                            state.copy(token = result.value, error = null)
                        }
                    }
                }
            } ?: run {
                state.update {
                    it.copy(token = null, error = null)
                }
            }
        }
    }

    fun reset() {
        state.update {
            it.copy(token = null, error = null)
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

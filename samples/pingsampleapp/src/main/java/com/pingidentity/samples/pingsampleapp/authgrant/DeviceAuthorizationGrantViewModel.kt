/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.samples.pingsampleapp.authgrant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pingidentity.oidc.DeviceFlowStatus
import com.pingidentity.samples.pingsampleapp.config.oidcDeviceClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DeviceAuthorizationGrantViewModel : ViewModel() {

    data class UiState(
        val hasStarted: Boolean = false,
        val userCode: String = "",
        val verificationUri: String = "",
        val statusMessage: String = "",
        val errorMessage: String = "",
        val isLoading: Boolean = false,
        val isSuccess: Boolean = false,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var deviceFlowJob: Job? = null

    fun startDeviceAuthorizationGrantFlow() {
        if (deviceFlowJob?.isActive == true) return

        _uiState.update {
            it.copy(
                isLoading = true,
                errorMessage = "",
                statusMessage = "Starting device authorization...",
            )
        }

        deviceFlowJob = viewModelScope.launch {
            val client = oidcDeviceClient ?: run {
                _uiState.update {
                    it.copy(
                        statusMessage = "",
                        errorMessage = "Device authorization client is not configured.",
                        isLoading = false,
                    )
                }
                return@launch
            }

            client.deviceAuthorization().collect { status ->
                when (status) {
                    is DeviceFlowStatus.Started -> {
                        val verificationUri = status.response.verificationUriComplete ?: status.response.verificationUri
                        _uiState.update {
                            it.copy(
                                hasStarted = true,
                                userCode = status.response.userCode,
                                verificationUri = verificationUri,
                                statusMessage = "Open the URL and enter the user code.",
                                errorMessage = "",
                                isLoading = false,
                            )
                        }
                    }

                    is DeviceFlowStatus.Polling -> {
                        _uiState.update {
                            it.copy(
                                statusMessage = "Waiting for approval... (attempt ${status.pollCount})",
                                errorMessage = "",
                                isLoading = true,
                            )
                        }
                    }

                    is DeviceFlowStatus.Success -> {
                        _uiState.update {
                            it.copy(
                                statusMessage = "Authorization successful.",
                                errorMessage = "",
                                isLoading = false,
                                isSuccess = true,
                            )
                        }
                        deviceFlowJob?.cancel()
                    }

                    DeviceFlowStatus.Expired -> {
                        _uiState.update {
                            it.copy(
                                hasStarted = false,
                                statusMessage = "",
                                errorMessage = "The device code has expired. Please start again.",
                                isLoading = false,
                            )
                        }
                    }

                    DeviceFlowStatus.AccessDenied -> {
                        _uiState.update {
                            it.copy(
                                hasStarted = false,
                                statusMessage = "",
                                errorMessage = "Access denied by user.",
                                isLoading = false,
                            )
                        }
                    }

                    is DeviceFlowStatus.Failure -> {
                        _uiState.update {
                            it.copy(
                                hasStarted = false,
                                statusMessage = "",
                                errorMessage = status.exception.message ?: "Device authorization failed.",
                                isLoading = false,
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        deviceFlowJob?.cancel()
    }
}
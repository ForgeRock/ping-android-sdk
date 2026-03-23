/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.pingonemfapp.managers

import com.pingidentity.pingonemfa.commons.PingOneMFA
import com.pingidentity.pingonemfapp.data.DiagnosticLogger
import com.pingidentity.pingonemfapp.data.OtpUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class OTPManager(
    private val diagnosticLogger: DiagnosticLogger
) {
    private val _otpState = MutableStateFlow(OtpUiState())
    val otpState: StateFlow<OtpUiState> = _otpState.asStateFlow()

    var otpRefreshJob: Job? = null

    fun startAutoRefresh(scope: CoroutineScope){
        if (otpRefreshJob?.isActive == true) return
        diagnosticLogger.d("startAutoRefresh")
        otpRefreshJob = scope.launch {
            fetchOtpAndStartCountDown()
        }
    }

    fun stop() {
        otpRefreshJob?.cancel()
        otpRefreshJob = null
    }
    suspend fun fetchOtpAndStartCountDown() {
        _otpState.update { it.copy(isLoading = true, error = null) }

        val result = PingOneMFA.collectOtp()
        if (result.isSuccess) {
            val otp = result.getOrThrow()
            _otpState.update { it.copy(
                otp = otp.code,
                secondsRemaining = otp.secondsRemaining,
                isLoading = false,
                error = null
            ) }
            startCountDown(otp.secondsRemaining)
            // when countdown ends → fetch again
            fetchOtpAndStartCountDown()
        } else {
            _otpState.update {
                it.copy(
                    isLoading = false,
                    error = result.exceptionOrNull()?.message
                )
            }
        }
    }
    private suspend fun startCountDown(seconds: Int) {
        var remaining = seconds
        while (remaining > 0 && currentCoroutineContext().isActive) {
            delay(1000)
            remaining--
            // if we want to show countdown in UI
            //_otpState.update { it.copy(secondsRemaining = remaining) }
        }
    }
}

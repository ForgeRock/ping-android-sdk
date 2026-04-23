/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.pingonemfapp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.pingidentity.pingonemfapp.R
import com.pingidentity.pingonemfapp.data.PingOneMFAViewModel
import com.pingidentity.pingonemfapp.ui.components.BackNavigationTopAppBar
import com.pingidentity.pingonemfapp.ui.components.ErrorAlertDialog
import com.pingidentity.pingonemfapp.ui.components.ExpiringOtpCode
import com.pingidentity.pingonemfapp.ui.components.LoadingIndicator

@Composable
fun OtpScreen(
    viewModel: PingOneMFAViewModel,
    onDismiss: () -> Unit
) {

    val state by viewModel.otpState.collectAsState()

    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.startOtpSequence()
                Lifecycle.Event.ON_STOP -> viewModel.stopOtpSequence()
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    Scaffold(
        topBar = {
            BackNavigationTopAppBar(
                title = stringResource(id = R.string.otp_screen_title),
                onBackClick = onDismiss
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            when {
                state.isLoading -> {
                    LoadingIndicator(
                        message = stringResource(id = R.string.otp_loading_message)
                    )
                }
                state.error != null -> {
                    ErrorAlertDialog(
                        errorMessage = state.error!!,
                        onDismiss = { viewModel.clearError() }
                    )
                }
                else -> {
                    ExpiringOtpCode(
                        code = state.otp,
                        remainingSeconds = state.secondsRemaining,
                        totalDurationMs = 30

                    )
                }
            }
        }
    }
}

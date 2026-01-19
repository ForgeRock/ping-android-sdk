/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.samples.pingsampleapp.davinci.collector

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pingidentity.davinci.collector.PollingCollector
import com.pingidentity.davinci.collector.PollingStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun Polling(
    field: PollingCollector,
    onNext: () -> Unit,
) {
    var isPolling by remember { mutableStateOf(true) }
    var statusMessage by remember { mutableStateOf("Polling...") }
    var pollingJob: Job? by remember { mutableStateOf(null) }

    LaunchedEffect(field) {
        pollingJob = launch {
            field.pollStatus().collect { status ->
                when (status) {
                    is PollingStatus.Continue -> {
                        statusMessage = "Polling... (${status.retryCount}/${status.maxRetries})"
                    }
                    is PollingStatus.Complete -> {
                        isPolling = false
                        onNext()
                    }
                    is PollingStatus.TimedOut -> {
                        isPolling = false
                        statusMessage = "Polling timedOut"
                        onNext()
                    }
                    is PollingStatus.Error -> {
                        isPolling = false
                        statusMessage = "Error: ${status.exception.message}"
                        onNext()
                    }

                    is PollingStatus.Expired -> {
                        isPolling = false
                        statusMessage = "Polling Expired"
                        onNext()
                    }

                }
            }
        }
    }

    // Cancel polling when the composable is disposed
    DisposableEffect(field) {
        onDispose {
            pollingJob?.cancel()
            isPolling = false
        }
    }

    if (isPolling) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = statusMessage,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}


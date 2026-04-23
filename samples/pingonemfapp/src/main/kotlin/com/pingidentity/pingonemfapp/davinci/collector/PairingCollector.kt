/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */
package com.pingidentity.pingonemfapp.davinci.collector

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pingidentity.davinci.collector.TextCollector
import com.pingidentity.pingonemfa.commons.PingOneMFA
import com.pingidentity.pingonemfapp.data.DiagnosticLogger
import com.pingidentity.pingonemfapp.R

private val diagnosticLogger = DiagnosticLogger
@Composable
fun PairingCollector(
    collector: TextCollector,
    onNodeUpdated: () -> Unit,
    onStatusChanged: (Boolean?) -> Unit,
) {
    var status by remember(collector) { mutableStateOf<Boolean?>(null) }
    /*
     * Updates the pairing status and triggers a UI refresh.
     */
    fun updateStatus(value: Boolean?) {
        status = value
        onStatusChanged(value)
    }

    LaunchedEffect(collector.value) {
        updateStatus(null)
        diagnosticLogger.d("Start pairing with pairing key: ${collector.value}")
        val result = PingOneMFA.pair(collector.value)
        updateStatus(result.isSuccess)
        onNodeUpdated()
    }

    when (status) {
        null -> {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
            }
            Text(
                text = stringResource(id = R.string.davinci_pairing_loading_message),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        false -> {
            Text(
                text = stringResource(id = R.string.davinci_pairing_error_message),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        true -> Unit
    }
}

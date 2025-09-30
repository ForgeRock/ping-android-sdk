/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.samples.app.davinci.collector

import android.util.Log
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pingidentity.fido2.davinci.AbstractFido2Collector
import com.pingidentity.fido2.davinci.Fido2AuthenticationCollector
import kotlinx.coroutines.launch

@Composable
fun Fido2Authentication(
    collector: Fido2AuthenticationCollector,
    onStart: () -> Unit,
    onNext: () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    var showErrorDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    if (showErrorDialog) {
        ErrorDialog(
            message = errorMessage,
            onDismiss = { showErrorDialog = false },
            onRetry = {
                showErrorDialog = false
                onStart()
            }
        )
    }

    Row(
        modifier =
            Modifier
                .padding(4.dp)
                .fillMaxWidth(),
    ) {
        Spacer(modifier = Modifier.weight(1f, true))
        Button(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .wrapContentWidth(Alignment.CenterHorizontally),
            onClick = {
                coroutineScope.launch {
                    val result = collector.authenticate()
                    result.onSuccess {
                        onNext()
                    }
                    result.onFailure {
                        Log.e(
                            "Fido2Authentication",
                            "Failed to Authenticate",
                            it
                        )
                        errorMessage = it.message ?: "Authentication failed"
                        showErrorDialog = true
                    }
                }
            },
        ) {
            androidx.compose.material3.Text((collector as AbstractFido2Collector).label)
        }
        Spacer(modifier = Modifier.weight(1f, true))
    }
}
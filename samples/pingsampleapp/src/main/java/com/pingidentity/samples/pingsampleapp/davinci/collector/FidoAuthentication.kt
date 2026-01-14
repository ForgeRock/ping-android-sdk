/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.samples.pingsampleapp.davinci.collector

import android.util.Log
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pingidentity.fido.davinci.AbstractFidoCollector
import com.pingidentity.fido.davinci.FidoAuthenticationCollector
import kotlinx.coroutines.launch

@Composable
fun FidoAuthentication(
    collector: FidoAuthenticationCollector,
    onStart: () -> Unit,
    onNext: () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    var showErrorDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    // Function to perform authentication
    val performAuthentication: () -> Unit = {
        coroutineScope.launch {
            val result = collector.authenticate()
            result.onSuccess {
                onNext()
            }
            result.onFailure {
                Log.e(
                    "FidoAuthentication",
                    "Failed to Authenticate",
                    it
                )
                errorMessage = it.message ?: "Authentication failed"
                showErrorDialog = true
            }
        }
    }

    // Trigger authentication immediately if not BUTTON trigger
    LaunchedEffect(collector) {
        if (collector.trigger != "BUTTON") {
            performAuthentication()
        }
    }

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

    // Only show button if trigger is BUTTON
    if (collector.trigger == "BUTTON") {
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
                onClick = performAuthentication,
            ) {
                androidx.compose.material3.Text((collector as AbstractFidoCollector).label)
            }
            Spacer(modifier = Modifier.weight(1f, true))
        }
    }
}
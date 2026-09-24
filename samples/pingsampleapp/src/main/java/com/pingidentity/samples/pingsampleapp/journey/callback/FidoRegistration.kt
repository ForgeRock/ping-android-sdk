/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.samples.pingsampleapp.journey.callback

import android.os.Build
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pingidentity.fido.journey.FidoRegistrationCallback
import com.pingidentity.journey.plugin.stage
import kotlinx.coroutines.launch

@Composable
fun FidoRegistration(
    callback: FidoRegistrationCallback,
    onNext: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val currentOnCompleted by rememberUpdatedState(onNext)
    val isRestoreCredential = callback.continueNode.stage == "RestoreCredential"
    var deviceName by remember {
        mutableStateOf(Build.MODEL)
    }
    var showProgress by remember {
        mutableStateOf(isRestoreCredential)
    }

    if (isRestoreCredential) {
        LaunchedEffect(Unit) {
            callback.createRestoreKey().onSuccess {
                currentOnCompleted()
            }.onFailure {
                Log.e(
                    "Fido2Registration",
                    "Failed to create restore key",
                    it
                )
                currentOnCompleted()
            }
        }
    }

    Box(
        modifier = Modifier
            .padding(4.dp)
            .fillMaxSize()
            .wrapContentSize(Alignment.TopStart)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (showProgress) {
                CircularProgressIndicator()
            }
        }
        if (!isRestoreCredential) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                OutlinedTextField(
                    modifier = Modifier,
                    value = deviceName,
                    onValueChange = { value ->
                        deviceName = value
                    },
                    label = { Text("Device Name") },
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    modifier = Modifier.align(Alignment.End),
                    onClick = {
                        showProgress = true
                        coroutineScope.launch {
                            callback.register(deviceName).onSuccess {
                                currentOnCompleted()
                            }.onFailure {
                                Log.e(
                                    "Fido2Registration",
                                    "Failed to register",
                                    it
                                )
                                currentOnCompleted()
                            }
                        }
                    }) {
                    Text("Continue")
                }
            }
        }
    }
}
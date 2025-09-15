/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.samples.journeyapp.journey.callback

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
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
import com.pingidentity.device.profile.DeviceProfileCallback
import kotlinx.coroutines.launch

/**
 * A Composable UI component for handling device profile collection during authentication flows.
 *
 * This composable automatically initiates device profile collection when displayed and shows
 * a loading indicator with progress message to inform the user that device profiling is in progress.
 * Once the collection completes successfully, it automatically proceeds to the next step in the journey.
 *
 * The UI displays a centered loading spinner with the message "Gathering Device Profile..."
 * during the collection process. The component handles the entire lifecycle of device profile
 * collection without requiring user interaction.
 *
 * @param deviceProfileCallback The DeviceProfileCallback instance that handles the actual
 *                             device profile collection process
 * @param onNext Callback function invoked when device profile collection completes successfully,
 *              typically used to proceed to the next step in the authentication journey
 */
@Composable
fun DeviceProfileCallback(
    deviceProfileCallback: DeviceProfileCallback,
    onNext: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(true) } // Start in the loading state

    // This effect runs ONCE when the composable enters the screen
    LaunchedEffect(key1 = true) {
        scope.launch {
            deviceProfileCallback.collect()
            isLoading = false
            onNext()
        }
    }

    // The UI will always show the loading indicator until collection is complete.
    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(16.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "Gathering Device Profile...")
            }
        }
    }
}
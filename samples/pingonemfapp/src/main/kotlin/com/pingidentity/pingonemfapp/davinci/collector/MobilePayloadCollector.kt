/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.pingonemfapp.davinci.collector

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pingidentity.davinci.collector.TextCollector
import com.pingidentity.pingonemfa.commons.PingOneMFA
import com.pingidentity.pingonemfapp.R
import com.pingidentity.pingonemfapp.ui.components.RetryButton

/**
 * Renders the special DaVinci payload collector used by the PingOne MFA flow.
 * It attempts to collect the mobile payload from the PingOne MFA SDK, updates
 * the backing [TextCollector] on success, and shows a retry button on failure.
 *
 * @param collector The DaVinci text collector that stores the collected payload.
 * @param onNodeUpdated Callback invoked after collector state changes.
 * @param onStatusChanged Callback used to report success, failure, or in-progress state to the parent UI.
 */
@Composable
fun PayloadCollector(
    collector: TextCollector,
    onNodeUpdated: () -> Unit,
    onStatusChanged: (Boolean?) -> Unit,
) {
    var status by remember { mutableStateOf<Boolean?>(null) }
    var retryTrigger by remember { mutableIntStateOf(0) }

    fun updateStatus(value: Boolean?) {
        status = value
        onStatusChanged(value)
    }

    val message = when (status) {
        true -> stringResource(id = R.string.davinci_payload_collected_message)
        false -> stringResource(id = R.string.davinci_payload_collection_error_message)
        null -> stringResource(id = R.string.davinci_payload_collecting_message)
    }

    Text(
        text = message,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
    )
     // Execute the loading task when the composable is first composed
    LaunchedEffect(key1 = retryTrigger) {
        updateStatus(null)
        val result = PingOneMFA.collectMobilePayload()
        if (result.isSuccess) {
            collector.value = result.getOrNull() ?: ""
            updateStatus(true)
            onNodeUpdated()
        } else {
            updateStatus(false)
            onNodeUpdated()
        }
    }

    if (status == false) {
        RetryButton {
            retryTrigger += 1
        }
    }
}

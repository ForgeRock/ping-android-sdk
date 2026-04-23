/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.pingonemfapp.davinci

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pingidentity.davinci.collector.SubmitCollector
import com.pingidentity.davinci.collector.TextCollector
import com.pingidentity.davinci.module.name
import com.pingidentity.davinci.plugin.Submittable
import com.pingidentity.davinci.plugin.collectors
import com.pingidentity.orchestrate.ContinueNode
import com.pingidentity.pingonemfapp.R
import com.pingidentity.pingonemfapp.davinci.collector.PairingCollector
import com.pingidentity.pingonemfapp.davinci.collector.PayloadCollector
import com.pingidentity.pingonemfapp.ui.components.SubmitButton

private const val DEVICE_PAYLOAD_KEY = "device-payload"
private const val PAIRING_KEY_VALUE = "device-pairing-code"
/**
 * Renders the PingOne MFA-specific DaVinci continue node.
 * This renderer handles the special `device-payload` text collector by collecting
 * the mobile payload from the PingOne MFA SDK and switching the primary action
 * between submit and retry based on the collection result.
 *
 * @param continueNode The DaVinci continue node being rendered.
 * @param onNodeUpdated Callback used to refresh the UI after collector values change.
 * @param onStart Reserved restart callback passed down from the parent DaVinci screen.
 * @param onNext Callback used to submit the current node.
 */
@Composable
fun PingOneMFAContinueNode(
    continueNode: ContinueNode,
    onNodeUpdated: () -> Unit,
    onStart: () -> Unit,
    onNext: () -> Unit,
    onFinish: () -> Unit,
) {
    val requiresPayloadCollection = remember(continueNode) {
        continueNode.collectors.any {
            it is TextCollector && it.key.equals(DEVICE_PAYLOAD_KEY, true)
        }
    }
    val requiresPairing = remember(continueNode) {
        continueNode.collectors.any {
            it is TextCollector && it.key.equals(PAIRING_KEY_VALUE, true)
        }
    }
    var payloadCollectionSuccessful by remember(continueNode) {
        mutableStateOf(if (requiresPayloadCollection) null else true)
    }
    var pairingSuccessful by remember(continueNode) {
        mutableStateOf(if (requiresPairing) null else true)
    }

    Row(
        modifier =
            Modifier
                .padding(4.dp)
                .fillMaxWidth()
    ) {
        Spacer(Modifier.width(8.dp))
        Text(
            text = continueNode.name,
            Modifier
                .wrapContentWidth(Alignment.CenterHorizontally)
                .weight(1f),
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleLarge,
        )
    }
    Row(
        modifier =
            Modifier
                .padding(4.dp)
                .fillMaxWidth(),
    ) {
        Spacer(Modifier.width(8.dp))
    }

    Column(
        modifier =
            Modifier
                .padding(4.dp)
                .fillMaxSize(),
    ) {
        var hasAction = false

        continueNode.collectors.forEach { collector ->
            when (collector) {
                is TextCollector -> {
                    if (collector.key.equals(DEVICE_PAYLOAD_KEY, true)) {
                        PayloadCollector(
                            collector = collector,
                            onNodeUpdated = onNodeUpdated,
                            onStatusChanged = { payloadCollectionSuccessful = it },
                        )
                    }
                    if (collector.key.equals(PAIRING_KEY_VALUE, true)) {
                        PairingCollector(
                            collector = collector,
                            onNodeUpdated = onNodeUpdated,
                            onStatusChanged = { pairingSuccessful = it }
                        )
                    }
                }
                is SubmitCollector -> {
                    if (payloadCollectionSuccessful == true && pairingSuccessful == true) {
                        SubmitButton(collector, onNext)
                    }
                }
            }
            if (collector is Submittable) {
                hasAction = true
            }
        }
        if (!hasAction) {
            Button(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                onClick = onFinish,
            ) {
                Text(stringResource(id = R.string.davinci_finish))
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        OutlinedTextField(
            value = continueNode.input.toString(),
            onValueChange = {},
            readOnly = true,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            label = {
                Text(stringResource(id = R.string.davinci_continue_node_input_debug_label))
            },
        )
    }
}

/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pingidentity.fido2.davinci.Fido2RegistrationCollector
import kotlinx.coroutines.launch

@Composable
fun Fido2Registration(
    collector: Fido2RegistrationCollector,
    onStart: () -> Unit,
    onNext: () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()

    Row(
        modifier =
            Modifier
                .padding(4.dp)
                .fillMaxWidth(),
    ) {
        Spacer(modifier = Modifier.weight(1f, true))
        //When image not found, display a button with the label
        Button(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .wrapContentWidth(Alignment.CenterHorizontally),
            onClick = {
                coroutineScope.launch {
                    val result = collector.register()
                    result.onSuccess {
                        onNext()
                    }
                    result.onFailure {
                        Log.e(
                            "Fido2Registration",
                            "Failed to register",
                            it
                        )
                        onStart() //restart the flow, cause the url may expired
                    }
                }
            },
        ) {
            androidx.compose.material3.Text(collector.label)
        }
        Spacer(modifier = Modifier.weight(1f, true))
    }
}
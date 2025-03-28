/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.samples.journeyapp.journey.callback

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pingidentity.idp.journey.IdpCallback
import com.pingidentity.idp.journey.SelectIdpCallback
import com.pingidentity.journey.callback.NameCallback
import com.pingidentity.journey.callback.PasswordCallback
import com.pingidentity.journey.plugin.callbacks
import com.pingidentity.orchestrate.ContinueNode

@Composable
fun ContinueNode(
    continueNode: ContinueNode,
    onNodeUpdated: () -> Unit,
    onNext: () -> Unit,
) {
    Column(
        modifier =
        Modifier
            .padding(4.dp)
            .fillMaxWidth(),
    ) {
        var showNext = true

        continueNode.callbacks.forEach {
            when (it) {
                is NameCallback -> NameCallback(it, onNodeUpdated)
                is PasswordCallback -> PasswordCallback(it, onNodeUpdated)
                is SelectIdpCallback -> SelectIdpCallback(it, onNext)
                is IdpCallback -> {
                    showNext = false
                    IdPCallback(it, onNext)
                }
            }
        }
        if (showNext) {
            Button(
                modifier = Modifier.align(Alignment.End),
                onClick = onNext,
            ) {
                Text("Next")
            }
        }
    }
}


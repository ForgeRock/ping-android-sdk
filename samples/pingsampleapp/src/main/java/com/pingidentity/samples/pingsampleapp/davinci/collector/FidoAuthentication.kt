/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.samples.pingsampleapp.davinci.collector

import android.text.InputType
import android.util.Log
import android.widget.EditText
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.credentials.pendingGetCredentialRequest
import com.pingidentity.fido.FidoPendingAuthentication
import com.pingidentity.fido.davinci.AbstractFidoCollector
import com.pingidentity.fido.davinci.FidoAuthenticationCollector
import com.pingidentity.fido.isConditionalMediationSupported
import kotlinx.coroutines.launch

@Composable
fun FidoAuthentication(
    collector: FidoAuthenticationCollector,
    onNext: () -> Unit,
) {
    val currentOnNext by rememberUpdatedState(onNext)
    val coroutineScope = rememberCoroutineScope()

    // The pending conditional-mediation request; null while it is being created or when
    // creation failed — the modal fallback below covers both.
    var pending by remember(collector) { mutableStateOf<FidoPendingAuthentication?>(null) }

    // Cancel the request when it is superseded (pending changes) or the composable leaves
    // composition — the workflow's close() covers node teardown, but a composable that leaves
    // composition while the node is still active must not keep a live ceremony attached.
    DisposableEffect(pending) {
        val current = pending
        onDispose { current?.cancel() }
    }

    // Function to perform modal authentication (the fallback path)
    val performAuthentication: () -> Unit = {
        coroutineScope.launch {
            val result = collector.authenticate()
            result.onSuccess {
                currentOnNext()
            }
            result.onFailure {
                Log.e(
                    "FidoAuthentication",
                    "Failed to Authenticate",
                    it
                )
                currentOnNext()
            }
        }
    }

    // Function to build and attach the pending conditional-mediation request
    val attachPending: suspend () -> Unit = {
        collector.pendingAuthenticate { useFido2ApiClient = false }
            .onSuccess { pending = it }
            .onFailure {
                Log.e(
                    "FidoAuthentication",
                    "Failed to create pending request",
                    it
                )
            }
    }

    if (isConditionalMediationSupported) {
        // Non-BUTTON triggers previously auto-launched the modal; on supported devices they
        // attach the pending request instead — suggestions appear when the user focuses the
        // username field below.
        LaunchedEffect(collector) {
            if (collector.trigger != "BUTTON") {
                attachPending()
            }
        }

        // Deliver the assertion when the user picks a suggestion from the field's suggestions.
        // A dismissed suggestion sheet never completes the await — the modal fallback below
        // stays visible in that case.
        LaunchedEffect(pending) {
            val result = pending?.await() ?: return@LaunchedEffect
            result.onSuccess {
                currentOnNext()
            }.onFailure {
                Log.e(
                    "FidoAuthentication",
                    "Pending authentication cancelled",
                    it
                )
            }
        }

        Column(
            modifier = Modifier
                .padding(4.dp)
                .fillMaxWidth(),
        ) {
            // The pendingGetCredentialRequest extension is View-only: wrap an EditText with
            // AndroidView, there is no Compose equivalent.
            AndroidView(
                modifier = Modifier.fillMaxWidth(),
                factory = { context ->
                    EditText(context).apply {
                        hint = "Username"
                        maxLines = 1
                        inputType = InputType.TYPE_CLASS_TEXT
                    }
                },
                update = { view ->
                    view.pendingGetCredentialRequest = pending?.request
                },
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier =
                Modifier
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
                            // Modal authentication supersedes any attached pending request
                            // (the collector cancels it), so no conditional attach is needed
                            // here — only the modal runs.
                            performAuthentication()
                        }
                    },
                ) {
                    Text(
                        if (collector.trigger == "BUTTON") {
                            (collector as AbstractFidoCollector).label
                        } else {
                            "Use a passkey"
                        }
                    )
                }
                Spacer(modifier = Modifier.weight(1f, true))
            }
        }
    } else {
        // Trigger modal authentication immediately if not BUTTON trigger
        LaunchedEffect(collector) {
            if (collector.trigger != "BUTTON") {
                performAuthentication()
            }
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
                    Text((collector as AbstractFidoCollector).label)
                }
                Spacer(modifier = Modifier.weight(1f, true))
            }
        }
    }
}

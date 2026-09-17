/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.samples.pingsampleapp.journey.callback

import android.util.Log
import android.widget.EditText
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.credentials.pendingGetCredentialRequest
import com.pingidentity.fido.FidoPendingAuthentication
import com.pingidentity.fido.isConditionalMediationSupported
import com.pingidentity.fido.journey.FidoAuthenticationCallback
import kotlinx.coroutines.launch

@Composable
fun FidoAuthentication(
    callback: FidoAuthenticationCallback,
    onNext: () -> Unit,
) {
    val currentOnCompleted by rememberUpdatedState(onNext)
    val coroutineScope = rememberCoroutineScope()

    // The pending conditional-mediation request; null while it is being created or when
    // creation failed — the modal fallback below covers both.
    var pending by remember { mutableStateOf<FidoPendingAuthentication?>(null) }

    if (isConditionalMediationSupported) {
        // Build the pending request up front so passkey suggestions appear when the user
        // focuses the username field below.
        LaunchedEffect(callback) {
            callback.pendingAuthenticate { useFido2ApiClient = false }
                .onSuccess { pending = it }
                .onFailure {
                    Log.e(
                        "Fido2Authentication",
                        "Failed to create pending request",
                        it
                    )
                }
        }

        // Deliver the assertion when the user picks a suggestion. A dismissed suggestion sheet
        // never completes the await — the modal fallback below stays visible in that case.
        LaunchedEffect(pending) {
            val result = pending?.await() ?: return@LaunchedEffect
            result.onSuccess {
                currentOnCompleted()
            }.onFailure {
                Log.e(
                    "Fido2Authentication",
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
                    }
                },
                update = { view ->
                    view.pendingGetCredentialRequest = pending?.request
                },
            )
            Spacer(modifier = Modifier.height(8.dp))
            // Modal fallback: the conditional path delivers no errors, so this stays visible to
            // cover dismissed suggestions.
            Button(
                modifier = Modifier.align(Alignment.End),
                onClick = {
                    coroutineScope.launch {
                        callback.authenticate { useFido2ApiClient = false }
                            .onSuccess { currentOnCompleted() }
                            .onFailure {
                                Log.e(
                                    "Fido2Authentication",
                                    "Failed to Authenticate",
                                    it
                                )
                                currentOnCompleted()
                            }
                    }
                }
            ) {
                Text("Use a passkey")
            }
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .wrapContentSize(Alignment.Center)
        ) {
            CircularProgressIndicator()
            LaunchedEffect(true) {
                launch {
                    callback.authenticate().onSuccess {
                        currentOnCompleted()
                    }.onFailure {
                        Log.e(
                            "Fido2Authentication",
                            "Failed to Authenticate",
                            it
                        )
                        currentOnCompleted()
                    }
                }
            }
        }
    }
}

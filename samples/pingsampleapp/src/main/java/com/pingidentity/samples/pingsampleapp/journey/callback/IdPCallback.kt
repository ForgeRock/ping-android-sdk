/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.samples.pingsampleapp.journey.callback

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import android.net.Uri
import com.pingidentity.idp.journey.IdpCallback
import com.pingidentity.samples.pingsampleapp.config.ConfigurationManager
import com.pingidentity.samples.pingsampleapp.config.ConfigType

@Composable
fun IdPCallback(
    callback: IdpCallback,
    onNodeUpdated: () -> Unit
) {
    val currentOnNodeUpdated by rememberUpdatedState(onNodeUpdated)

    Column(
        modifier = Modifier
            .padding(8.dp)
            .fillMaxHeight()
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Launching ${callback.provider} Social Login...")
        Spacer(Modifier.height(8.dp))
        CircularProgressIndicator()

        LaunchedEffect(true) {
            val redirectUri = ConfigurationManager.selectedConfig(ConfigType.JOURNEY)
                ?.redirectUri
                ?.let { Uri.parse(it) }
                ?: return@LaunchedEffect
            callback.authorize(redirectUri)
            currentOnNodeUpdated()
        }
    }
}
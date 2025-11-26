/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.samples.app.env

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pingidentity.device.id.DefaultDeviceIdentifier
import com.pingidentity.oidc.OidcClientConfig
import java.net.URL

@Composable
fun Env(envViewModel: EnvViewModel = viewModel<EnvViewModel>()) {
    var deviceId by remember { mutableStateOf("Loading Device ID...") }

    LaunchedEffect(Unit) {
        deviceId = try {
            DefaultDeviceIdentifier.id()
        } catch (_: Exception) {
            "Error loading Device ID"
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 48.dp) // Add padding to avoid overlap with the device ID
        ) {
            envViewModel.oidcConfigs.forEach {
                item {
                    ServerSetting(option = it, envViewModel.current.clientId == it.clientId) {
                        envViewModel.select(it)
                    }
                }
            }
        }
        Text(
            text = "Device ID: $deviceId",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ServerSetting(
    option: OidcClientConfig,
    selected: Boolean = false,
    onServerSelected: (OidcClientConfig) -> Unit
) {
    Column {
        val host = URL(option.discoveryEndpoint).host
        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "$host\n${option.clientId}",
                modifier = Modifier
                    .weight(1f)
                    .wrapContentHeight(),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.width(8.dp))
            SelectServerButton(option, selected, onServerSelected)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
    }
}

@Composable
private fun SelectServerButton(
    option: OidcClientConfig,
    selected: Boolean,
    onServerSelected: (OidcClientConfig) -> Unit
) {
    val icon = if (selected) Icons.Filled.Done else Icons.Filled.CheckBoxOutlineBlank
    IconButton(
        onClick = { onServerSelected(option) }) {
        Icon(icon, contentDescription = null)
    }
}
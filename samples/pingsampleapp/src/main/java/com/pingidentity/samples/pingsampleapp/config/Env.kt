/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.samples.pingsampleapp.config

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pingidentity.oidc.OidcClientConfig
import com.pingidentity.samples.pingsampleapp.R
import com.pingidentity.samples.pingsampleapp.theme.AppTheme
import java.net.URL

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Env(envViewModel: EnvViewModel = viewModel<EnvViewModel>()) {
    AppTheme {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Spacer(modifier = Modifier.padding(24.dp).fillMaxWidth())

            Text(
                text = stringResource(R.string.text_configuration_selected_environment),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(8.dp),
                color = MaterialTheme.colorScheme.onSurface
            )
            LazyColumn(modifier = Modifier) {
                envViewModel.oidcConfigs.forEach { config ->
                    item {
                        ServerSetting(
                            option = config,
                            envViewModel.current.display == config.display
                        ) {
                            envViewModel.select(it)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.padding(12.dp).fillMaxWidth())
        }

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
                text = "${option.display}\n$host\n${option.clientId}",
                modifier = Modifier
                    .weight(1f)
                    .wrapContentHeight(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
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
        Icon(
            icon,
            contentDescription = option.display,
            tint = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Preview
@Composable
fun PreviewEnv() {
    Env()
}
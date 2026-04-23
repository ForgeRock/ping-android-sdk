/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.pingonemfapp.config

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pingidentity.pingonemfapp.R
import com.pingidentity.pingonemfapp.ui.theme.PingIdentityAuthenticatorTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Env(
    envViewModel: EnvViewModel = viewModel<EnvViewModel>(),
    onBack: (() -> Unit)? = null,
) {
    PingIdentityAuthenticatorTheme {
        Scaffold(
            topBar = {
                if (onBack != null) {
                    TopAppBar(
                        title = { Text(stringResource(id = R.string.config_screen_title)) },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(id = R.string.back)
                                )
                            }
                        }
                    )
                }
            }
        ) { paddingValues ->
            var expandedConfigId by remember { mutableStateOf<String?>(null) }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(8.dp)
            ) {
                Text(
                    text = stringResource(id = R.string.configurations_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(8.dp),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Button(
                    onClick = {
                        expandedConfigId = envViewModel.addConfig()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(stringResource(id = R.string.config_add_configuration))
                }

                envViewModel.configs.forEach { config ->
                    ExpandableConfigCard(
                        title = config.display,
                        subtitle = config.discoveryEndpoint,
                        expanded = expandedConfigId == config.id,
                        onToggle = {
                            expandedConfigId =
                                if (expandedConfigId == config.id) {
                                    null
                                } else {
                                    config.id
                                }
                        },
                    ) {
                        ConfigurationField(
                            label = stringResource(id = R.string.config_timeout_seconds_label),
                            value = config.timeoutSeconds,
                            onValueChange = { value ->
                                envViewModel.updateConfig(config.id) { copy(timeoutSeconds = value) }
                            },
                            keyboardType = KeyboardType.Number,
                        )
                        ConfigurationField(
                            label = stringResource(id = R.string.config_client_id_label),
                            value = config.clientId,
                            onValueChange = { value ->
                                envViewModel.updateConfig(config.id) { copy(clientId = value) }
                            },
                        )
                        ConfigurationField(
                            label = stringResource(id = R.string.config_discovery_endpoint_label),
                            value = config.discoveryEndpoint,
                            onValueChange = { value ->
                                envViewModel.updateConfig(config.id) { copy(discoveryEndpoint = value) }
                            },
                        )
                        ConfigurationField(
                            label = stringResource(id = R.string.config_scopes_label),
                            value = config.scopes,
                            onValueChange = { value ->
                                envViewModel.updateConfig(config.id) { copy(scopes = value) }
                            },
                        )
                        ConfigurationField(
                            label = stringResource(id = R.string.config_redirect_uri_label),
                            value = config.redirectUri,
                            onValueChange = { value ->
                                envViewModel.updateConfig(config.id) { copy(redirectUri = value) }
                            },
                        )
                        ConfigurationField(
                            label = stringResource(id = R.string.config_display_name_label),
                            value = config.display,
                            onValueChange = { value ->
                                envViewModel.updateConfig(config.id) { copy(display = value) }
                            },
                        )
                        ConfigurationField(
                            label = stringResource(id = R.string.config_storage_file_name_label),
                            value = config.storageFileName,
                            onValueChange = { value ->
                                envViewModel.updateConfig(config.id) { copy(storageFileName = value) }
                            },
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            Button(
                                onClick = {
                                    if (envViewModel.removeConfig(config.id)) {
                                        expandedConfigId = null
                                    }
                                },
                            ) {
                                Text(stringResource(id = R.string.config_remove))
                            }
                            Spacer(Modifier.width(8.dp))
                            Button(onClick = { envViewModel.reset(config.id) }) {
                                Text(stringResource(id = R.string.config_reset))
                            }
                            Spacer(Modifier.width(8.dp))
                            Button(onClick = {
                                envViewModel.apply(config.id)
                                expandedConfigId = null
                            }) {
                                Text(stringResource(id = R.string.config_apply))
                            }
                        }
                    }
                }
            }
        }

    }
}

@Composable
private fun ExpandableConfigCard(
    title: String,
    subtitle: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = subtitle,
                    modifier = Modifier
                        .fillMaxWidth(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = stringResource(
                    id = if (expanded) {
                        R.string.content_description_collapse_configuration
                    } else {
                        R.string.content_description_expand_configuration
                    }
                ),
            )
        }
        HorizontalDivider()
        if (expanded) {
            content()
        }
    }
}

@Composable
private fun ConfigurationField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Preview
@Composable
fun PreviewEnv() {
    Env {

    }
}

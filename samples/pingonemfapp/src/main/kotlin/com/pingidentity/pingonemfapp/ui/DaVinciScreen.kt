/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.pingonemfapp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pingidentity.pingonemfapp.R
import com.pingidentity.pingonemfapp.config.EditableDaVinciConfig
import com.pingidentity.pingonemfapp.config.EnvViewModel
import com.pingidentity.pingonemfapp.ui.components.BackNavigationTopAppBar

@Composable
fun DaVinciScreen(
    envViewModel: EnvViewModel,
    onBack: () -> Unit,
    onLaunchDaVinci: () -> Unit,
    onEditConfigurations: () -> Unit,
) {
    Scaffold(
        topBar = {
            BackNavigationTopAppBar(
                title = stringResource(id = R.string.drawer_launch_davinci),
                onBackClick = onBack,
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(id = R.string.davinci_launch_description),
                style = MaterialTheme.typography.bodyLarge,
            )

            envViewModel.configs.forEach { config ->
                DaVinciConfigurationCard(
                    config = config,
                    onLaunch = {
                        envViewModel.apply(config.id)
                        onLaunchDaVinci()
                    },
                )
            }

            Button(
                onClick = onEditConfigurations,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(id = R.string.davinci_edit_configurations))
            }
        }
    }
}

@Composable
private fun DaVinciConfigurationCard(
    config: EditableDaVinciConfig,
    onLaunch: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onLaunch),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = config.display,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            Text(
                text = stringResource(id = R.string.davinci_configuration_launch_hint),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

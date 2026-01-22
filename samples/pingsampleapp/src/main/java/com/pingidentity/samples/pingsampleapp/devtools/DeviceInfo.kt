/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.samples.pingsampleapp.devtools

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pingidentity.device.profile.collector.HardwareCollector
import com.pingidentity.device.profile.collector.HardwareInfo
import com.pingidentity.device.profile.collector.PlatformCollector
import com.pingidentity.device.profile.collector.PlatformInfo
import com.pingidentity.samples.pingsampleapp.theme.AppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceInfo(onBack: (() -> Unit)? = null) {
    AppTheme {
        var platformInfo: PlatformInfo by remember {
            mutableStateOf(PlatformInfo("android"))
        }
        var hardwareInfo: HardwareInfo by remember {
            mutableStateOf(
                HardwareInfo(
                    storage = 0L,
                    memory = 0L,
                    cpu = 8,
                    display = mapOf(),
                    camera = mapOf(),
                )
            )
        }
        LaunchedEffect(Unit) {
            platformInfo = PlatformCollector().collect()
            hardwareInfo = HardwareCollector().collect()
        }
        Scaffold(
            topBar = {
                if (onBack != null) {
                    TopAppBar(
                        title = { Text("Device Information") },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back"
                                )
                            }
                        }
                    )
                }
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(paddingValues)
            ) {
                Text(
                    text = "Device Information",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(vertical = 24.dp, horizontal = 8.dp),
                )
                // Show Platform Information
                Text(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    text = "Platform Information",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Card(
                    modifier = Modifier.fillMaxWidth()
                        .padding(8.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Text(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        text = "Platform: ${platformInfo.platform}\nVersion: ${platformInfo.version}\nDevice: ${platformInfo.device}\nModel: ${platformInfo.model}\nBrand: ${platformInfo.brand}",
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                // Show Hardware Information
                Text(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    text = "Hardware Information",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Card(
                    modifier = Modifier.fillMaxWidth()
                        .padding(8.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Text(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        text = "Hardware: ${hardwareInfo.hardware}\nManufacturer: ${hardwareInfo.manufacturer}\nStorage: ${hardwareInfo.storage} GB\nMemory: ${hardwareInfo.memory} MB\nCPU: ${hardwareInfo.cpu}",
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Preview
@Composable
fun PreviewDeviceInfo() {
    DeviceInfo()
}
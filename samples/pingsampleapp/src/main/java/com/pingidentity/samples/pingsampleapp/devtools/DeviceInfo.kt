/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.samples.pingsampleapp.devtools

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pingidentity.device.profile.collector.HardwareCollector
import com.pingidentity.device.profile.collector.HardwareInfo
import com.pingidentity.device.profile.collector.PlatformCollector
import com.pingidentity.device.profile.collector.PlatformInfo
import com.pingidentity.samples.pingsampleapp.theme.AppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceInfo() {
    AppTheme {
        var deviceInfo by remember { mutableStateOf("Loading Device Info...") }
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Spacer(modifier = Modifier.padding(24.dp))
            // Show Platform Information
            Text(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                text = "Platform Information",
                style = MaterialTheme.typography.titleMedium,
            )
            Card(
                modifier = Modifier.fillMaxWidth()
                    .padding(8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                )
            ) {
                Text(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    text = "Platform: ${platformInfo.platform}\nVersion: ${platformInfo.version}\nDevice: ${platformInfo.device}\nModel: ${platformInfo.model}\nBrand: ${platformInfo.brand}",
                )
            }
            // Show Hardware Information
            Text(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                text = "Hardware Information",
                style = MaterialTheme.typography.titleMedium,
            )
            Card(
                modifier = Modifier.fillMaxWidth()
                    .padding(8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                )
            ) {
                Text(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    text = "Hardware: ${hardwareInfo.hardware}\nManufacturer: ${hardwareInfo.manufacturer}\nStorage: ${hardwareInfo.storage} GB\nMemory: ${hardwareInfo.memory} MB\nCPU: ${hardwareInfo.cpu}",
                )
            }
        }
    }
}

@Preview
@Composable
fun PreviewDeviceInfo() {
    DeviceInfo()
}
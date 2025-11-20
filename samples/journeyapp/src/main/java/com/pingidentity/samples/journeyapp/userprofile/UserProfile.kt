/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.samples.journeyapp.userprofile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.pingidentity.samples.journeyapp.json
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider

@Composable
fun UserProfile(userProfileViewModel: UserProfileViewModel) {
    val state by userProfileViewModel.state.collectAsState()

    LaunchedEffect(true) {
        // Not relaunch when recomposition
        userProfileViewModel.userinfo()
    }
    Row(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier =
                Modifier.padding(8.dp)
                    .fillMaxWidth(),
        ) {
            Card(
                elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                shape = MaterialTheme.shapes.medium,
            ) {
                Text(
                    "First name: ${state.user?.get("name")}",
                    Modifier.fillMaxWidth().padding(4.dp)
                )
                Text(
                    "Family name: ${state.user?.get("family_name")}",
                    Modifier.fillMaxWidth().padding(4.dp)
                )
                Text("Email: ${state.user?.get("email")}", Modifier.fillMaxWidth().padding(4.dp))
                DropdownMenu(
                    expanded = false,
                    onDismissRequest = { /* No-op */ }
                ) {
                    state.deviceList.forEach { device ->
                        DropdownMenuItem(
                            text = { Text(device) },
                            onClick = { }
                        )
                    }
                }
                Button(
                    modifier = Modifier.padding(8.dp).align(Alignment.End),
                    onClick = { userProfileViewModel.toggleDeviceInfo() }
                ) {
                    Text(text = if (state.showDeviceInfo) "Hide Info" else "Show Raw User Info")
                }
                if (state.showDeviceInfo) {
                    Text(
                        modifier = Modifier.padding(4.dp),
                        text =
                            state.user?.let {
                                json.encodeToString(it)
                            } ?: state.error?.toString() ?: "No user information available",
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Card(
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                            .selectableGroup()
                    ) {
                        Text(
                            text = "Filter by Device Type:",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        val deviceTypes = listOf(
                            DeviceType.OATH to "OATH (TOTP/HOTP)",
                            DeviceType.PUSH to "Push Notifications",
                            DeviceType.BOUND to "Bound Devices",
                            DeviceType.WEBAUTHN to "WebAuthn/FIDO2"
                        )

                        deviceTypes.forEach { (deviceType, label) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = (state.selectedDeviceType == deviceType),
                                        onClick = {
                                            userProfileViewModel.setDeviceType(
                                                deviceType
                                            )
                                        },
                                        role = Role.RadioButton
                                    )
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = (state.selectedDeviceType == deviceType),
                                    onClick = null // onClick is handled by Row's selectable
                                )
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }

                        // Show selected device type
                        Text(
                            text = "Selected: ${state.selectedDeviceType.name}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }

            // Device List Section
            if (state.deviceList.isNotEmpty()) {
                Card(
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                    ) {
                        Text(
                            text = "${state.selectedDeviceType.name} Devices (${state.deviceList.size})",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        LazyColumn(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(state.deviceList) { deviceName ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = deviceName,
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (deviceName != state.deviceList.last()) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 4.dp),
                                        thickness = 1.dp,
                                        color = MaterialTheme.colorScheme.outlineVariant
                                    )
                                }
                            }
                        }
                    }
                }
            } else if (state.selectedDeviceType != DeviceType.OATH) {
                // Show empty state when a device type is selected but no devices found
                Card(
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text(
                        text = "No ${state.selectedDeviceType.name} devices found",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}
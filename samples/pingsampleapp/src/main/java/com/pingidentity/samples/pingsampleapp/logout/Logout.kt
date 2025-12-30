/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.samples.pingsampleapp.logout

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pingidentity.samples.pingsampleapp.theme.AppTheme

@Composable
fun Logout(logoutViewModel: LogoutViewModel = viewModel<LogoutViewModel>()) {
    val state by logoutViewModel.state.collectAsState()

    LaunchedEffect(true) {
        logoutViewModel.listLogoutOptions()
    }

    AppTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            Spacer(modifier = Modifier.padding(24.dp).fillMaxWidth())
            Text(
                text = "Active Sessions",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(8.dp)
            )
            Text(
                text = "Select a session to logout",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )

            // Logout All button - enabled only when there are active sessions
            val hasActiveSessions = state.journey || state.daVinci || state.oidc
            Button(
                onClick = {
                    logoutViewModel.logoutAll {
                        // Refresh the list after logout
                        logoutViewModel.listLogoutOptions()
                    }
                },
                enabled = hasActiveSessions,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                Text(
                    text = if (hasActiveSessions) "Logout All Sessions" else "No Active Sessions",
                    modifier = Modifier.padding(8.dp)
                )
            }

            Spacer(modifier = Modifier.padding(8.dp))

            LazyColumn(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Journey Session
                if (state.journey) {
                    item {
                        LogoutOptionCard(
                            title = "Journey Session",
                            description = "Logout from ForgeRock Journey authentication",
                            onLogout = {
                                logoutViewModel.logoutJourney {
                                    // Refresh the list after logout
                                    logoutViewModel.listLogoutOptions()
                                }
                            }
                        )
                    }
                }

                // DaVinci Session
                if (state.daVinci) {
                    item {
                        LogoutOptionCard(
                            title = "DaVinci Session",
                            description = "Logout from PingOne DaVinci authentication",
                            onLogout = {
                                logoutViewModel.logoutDaVinci {
                                    // Refresh the list after logout
                                    logoutViewModel.listLogoutOptions()
                                }
                            }
                        )
                    }
                }

                // OIDC Web Session
                if (state.oidc) {
                    item {
                        LogoutOptionCard(
                            title = "OIDC Web Session",
                            description = "Logout from OIDC Web authentication",
                            onLogout = {
                                logoutViewModel.logoutOidcWeb {
                                    // Refresh the list after logout
                                    logoutViewModel.listLogoutOptions()
                                }
                            }
                        )
                    }
                }

                // No active sessions
                if (!state.journey && !state.daVinci && !state.oidc) {
                    item {
                        Card(
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "No Active Sessions",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.padding(4.dp))
                                Text(
                                    text = "You are not logged in to any session",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LogoutOptionCard(
    title: String,
    description: String,
    onLogout: () -> Unit
) {
    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.padding(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Button(
                onClick = onLogout,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text("Logout from $title")
            }
        }
    }
}

@Preview
@Composable
fun PreviewLogout() {
    Logout()
}
/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.samples.pingsampleapp.userprofile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfile(
    userProfileViewModel: UserProfileViewModel,
    onBack: (() -> Unit)? = null,
    onAction: ((UserProfileType) -> Unit)? = null,
    onSelectedUserProfileType: UserProfileType = UserProfileType.JOURNEY,
) {
    val state by userProfileViewModel.state.collectAsState()

    LaunchedEffect(true) {
        // Not relaunch when recomposition
        userProfileViewModel.selectTab(onSelectedUserProfileType)
        userProfileViewModel.userinfo()
    }

    Scaffold(
        topBar = {
            if (onBack != null) {
                TopAppBar(
                    title = { Text("User Profile") },
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
            TabRow(
                selectedTabIndex = state.selectedTab.ordinal,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = state.selectedTab == UserProfileType.JOURNEY,
                    onClick = {
                        userProfileViewModel.selectTab(UserProfileType.JOURNEY)
                        userProfileViewModel.userinfo()
                    },
                    text = { Text("Journey") }
                )
                Tab(
                    selected = state.selectedTab == UserProfileType.DAVINCI,
                    onClick = {
                        userProfileViewModel.selectTab(UserProfileType.DAVINCI)
                        userProfileViewModel.userinfo()
                    },
                    text = { Text("DaVinci") }
                )
                Tab(
                    selected = state.selectedTab == UserProfileType.OIDC,
                    onClick = {
                        userProfileViewModel.selectTab(UserProfileType.OIDC)
                        userProfileViewModel.userinfo()
                    },
                    text = { Text("OIDC") }
                )
                Tab(
                    selected = state.selectedTab == UserProfileType.AUTH_GRANT,
                    onClick = {
                        userProfileViewModel.selectTab(UserProfileType.AUTH_GRANT)
                        userProfileViewModel.userinfo()
                    },
                    text = { Text("Auth Grant") }
                )
            }

            // Content based on selected tab
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                when (state.selectedTab) {
                    UserProfileType.JOURNEY -> {
                        if (state.journeyUser != null) {
                            UserInfoCard(
                                title = "Journey User Info",
                                user = state.journeyUser,
                                showRawInfo = state.showRawJourneyUserInfo,
                                formattedInfo = userProfileViewModel.formattedJourneyUserInfo,
                                onToggle = { userProfileViewModel.toggleUserInfo() }
                            )
                        } else if (state.journeyError != null) {
                            ErrorCard(
                                title = "Journey Error",
                                error = state.journeyError.toString()
                            )
                        } else {
                            EmptyStateCard(
                                title = "No Journey User",
                                message = "Please authenticate using Journey to view user profile information.",
                                actionLabel = "Start Journey",
                                onAction = { onAction?.invoke(UserProfileType.JOURNEY) }
                            )
                        }
                    }
                    UserProfileType.DAVINCI -> {
                        if (state.daVinciUser != null) {
                            UserInfoCard(
                                title = "DaVinci User Info",
                                user = state.daVinciUser,
                                showRawInfo = state.showRawDaVinciUserInfo,
                                formattedInfo = userProfileViewModel.formattedDaVinciUserInfo,
                                onToggle = { userProfileViewModel.toggleUserInfo() }
                            )
                        } else if (state.daVinciError != null) {
                            ErrorCard(
                                title = "DaVinci Error",
                                error = state.daVinciError.toString()
                            )
                        } else {
                            EmptyStateCard(
                                title = "No DaVinci User",
                                message = "Please authenticate using DaVinci to view user profile information.",
                                actionLabel = "Start DaVinci",
                                onAction = { onAction?.invoke(UserProfileType.DAVINCI) }
                            )
                        }
                    }
                    UserProfileType.OIDC -> {
                        if (state.oidcUser != null) {
                            UserInfoCard(
                                title = "OIDC User Info",
                                user = state.oidcUser,
                                showRawInfo = state.showRawOidcUserInfo,
                                formattedInfo = userProfileViewModel.formattedOidcUserInfo,
                                onToggle = { userProfileViewModel.toggleUserInfo() }
                            )
                        } else if (state.oidcError != null) {
                            ErrorCard(
                                title = "OIDC Error",
                                error = state.oidcError.toString()
                            )
                        } else {
                            EmptyStateCard(
                                title = "No OIDC User",
                                message = "Please authenticate using OIDC to view user profile information.",
                                actionLabel = "Start OIDC",
                                onAction = { onAction?.invoke(UserProfileType.OIDC) }
                            )
                        }
                    }

                    UserProfileType.AUTH_GRANT -> {
                        if (state.authGrantUser != null) {
                            UserInfoCard(
                                title = "Auth Grant User Info",
                                user = state.authGrantUser,
                                showRawInfo = state.showRawAuthGrantUserInfo,
                                formattedInfo = userProfileViewModel.formattedAuthGrantUserInfo,
                                onToggle = { userProfileViewModel.toggleUserInfo() }
                            )
                        } else if (state.authGrantError != null) {
                            ErrorCard(
                                title = "Auth Grant Error",
                                error = state.authGrantError.toString()
                            )
                        } else {
                            EmptyStateCard(
                                title = "No Auth Grant User",
                                message = "Please authenticate using Device Authorization Grant to view user profile information.",
                                actionLabel = "Start Auth Grant",
                                onAction = { onAction?.invoke(UserProfileType.AUTH_GRANT) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UserInfoCard(
    title: String,
    user: kotlinx.serialization.json.JsonObject?,
    showRawInfo: Boolean,
    formattedInfo: String,
    onToggle: () -> Unit
) {
    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        shape = MaterialTheme.shapes.medium,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(8.dp)
        )
        Text(
            "First name: ${user?.get("name") ?: "N/A"}",
            Modifier.fillMaxWidth().padding(4.dp)
        )
        Spacer(modifier = Modifier.padding(horizontal = 4.dp))
        Text(
            "Family name: ${user?.get("family_name") ?: "N/A"}",
            Modifier.fillMaxWidth().padding(4.dp)
        )
        Spacer(modifier = Modifier.padding(horizontal = 4.dp))
        Text(
            "Email: ${user?.get("email") ?: "N/A"}",
            Modifier.fillMaxWidth().padding(4.dp)
        )

        Button(
            modifier = Modifier.padding(8.dp).align(Alignment.End),
            onClick = onToggle
        ) {
            Text(text = if (showRawInfo) "Hide Info" else "Show Raw User Info")
        }

        if (showRawInfo) {
            Text(
                modifier = Modifier.padding(4.dp),
                text = formattedInfo,
            )
        }
    }
}

@Composable
private fun ErrorCard(
    title: String,
    error: String
) {
    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(8.dp)
        )
        Text(
            text = error,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.fillMaxWidth().padding(8.dp)
        )
    }
}

@Composable
private fun EmptyStateCard(
    title: String,
    message: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Button(
                onClick = onAction,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text(text = actionLabel)
            }
        }
    }
}

@Preview
@Composable
fun PreviewUserProfile() {
    UserProfile(
        viewModel<UserProfileViewModel>(),
        onBack = {}
    )
}
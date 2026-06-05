/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.samples.pingsampleapp.userprofile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfile(
    userProfileViewModel: UserProfileViewModel,
    onBack: (() -> Unit)? = null,
    onAction: ((UserProfileType) -> Unit)? = null,
) {
    val state by userProfileViewModel.state.collectAsState()

    LaunchedEffect(true) {
        // Not relaunch when recomposition
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
            // Tab Row for Journey, DaVinci, and OIDC
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
                }
            }
        }
    }
}

@Composable
private fun UserInfoCard(
    title: String,
    user: JsonObject?,
    showRawInfo: Boolean,
    formattedInfo: String,
    onToggle: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        UserInfoField("First Name", user?.stringClaim("given_name")
            ?: user?.stringClaim("name"))
        UserInfoField("Family Name", user?.stringClaim("family_name"))
        UserInfoField("Email", user?.stringClaim("email"))
        UserInfoField("Username", user?.stringClaim("preferred_username"))

        Button(
            modifier = Modifier
                .padding(top = 12.dp)
                .align(Alignment.End),
            onClick = onToggle
        ) {
            Text(text = if (showRawInfo) "Hide Raw Info" else "Show Raw Info")
        }

        if (showRawInfo) {
            Text(
                modifier = Modifier.padding(top = 8.dp),
                text = formattedInfo,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun UserInfoField(label: String, value: String?) {
    if (value == null) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value.ifBlank { "N/A" },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
    androidx.compose.material3.HorizontalDivider(
        modifier = Modifier.padding(vertical = 4.dp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
    )
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

private fun JsonObject.stringClaim(key: String): String? =
    this[key]?.jsonPrimitive?.contentOrNull
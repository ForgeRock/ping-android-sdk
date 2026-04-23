/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.pingonemfapp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pingidentity.pingonemfapp.R
import com.pingidentity.pingonemfapp.data.PingOneMFAViewModel
import com.pingidentity.pingonemfapp.data.ThemeMode
import com.pingidentity.pingonemfapp.ui.components.BackNavigationTopAppBar
import com.pingidentity.pingonemfapp.ui.components.SettingItem

/**
 * The settings screen for the PingOneMFApp.
 * This screen allows users to configure various settings related to the app's behavior and appearance.
 *
 * @param viewModel The ViewModel that provides the settings state and handles updates.
 * @param onDismiss Callback invoked when the user wants to exit the settings screen.
 * @param onDiagnosticLogsClick Callback invoked when the user wants to view diagnostic logs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: PingOneMFAViewModel,
    onDismiss: () -> Unit,
    onDiagnosticLogsClick: () -> Unit = {}
) {
    // Collect all settings as state
    val diagnosticLogging by viewModel.diagnosticLogging.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    
    // Dialog state for theme selection
    var showThemeDialog by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            BackNavigationTopAppBar(
                title = stringResource(id = R.string.settings_screen_title),
                onBackClick = onDismiss
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {

            // Theme Setting
            SettingItem(
                icon = Icons.Default.DarkMode,
                title = stringResource(id = R.string.settings_theme_title),
                description = stringResource(
                    id = R.string.settings_theme_summary,
                    getThemeDisplayName(themeMode)
                ),
                hasNavigation = true,
                onNavigate = { showThemeDialog = true }
            )

            HorizontalDivider()

            // Diagnostic Logging Setting
            SettingItem(
                icon = Icons.Default.Dns,
                title = stringResource(id = R.string.settings_diagnostic_logging_title),
                description = stringResource(id = R.string.settings_diagnostic_logging_summary),
                checked = diagnosticLogging,
                onToggle = { viewModel.setDiagnosticLogging(it) }
            )
            
            // View Diagnostic Logs (only visible when diagnostic logging is enabled)
            if (diagnosticLogging) {
                SettingItem(
                    icon = Icons.AutoMirrored.Filled.ListAlt,
                    title = stringResource(id = R.string.settings_view_diagnostic_logs_title),
                    description = stringResource(id = R.string.settings_view_diagnostic_logs_summary),
                    hasNavigation = true,
                    onNavigate = onDiagnosticLogsClick
                )
            }

            HorizontalDivider()
        }
    }
    
    // Theme selection dialog
    if (showThemeDialog) {
        ThemeSelectionDialog(
            currentTheme = themeMode,
            onThemeSelected = { selectedTheme ->
                viewModel.setThemeMode(selectedTheme)
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false }
        )
    }
}

/**
 * Dialog for selecting the app theme
 */
@Composable
private fun ThemeSelectionDialog(
    currentTheme: ThemeMode,
    onThemeSelected: (ThemeMode) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(id = R.string.settings_choose_theme_title))
        },
        text = {
            Column {
                ThemeMode.entries.forEach { theme ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onThemeSelected(theme) }
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(
                            selected = currentTheme == theme,
                            onClick = { onThemeSelected(theme) }
                        )
                        Text(
                            text = getThemeDisplayName(theme),
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(id = R.string.login_cancel))
            }
        }
    )
}

/**
 * Get display name for theme mode
 */
@Composable
private fun getThemeDisplayName(themeMode: ThemeMode): String {
    return when (themeMode) {
        ThemeMode.LIGHT -> stringResource(id = R.string.theme_mode_light)
        ThemeMode.DARK -> stringResource(id = R.string.theme_mode_dark)
        ThemeMode.SYSTEM -> stringResource(id = R.string.theme_mode_system)
    }
}
/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.samples.pingsampleapp.config

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pingidentity.samples.pingsampleapp.theme.AppTheme
import kotlinx.coroutines.launch
import java.net.URL

// ---------------------------------------------------------------------------
// Bottom sheet content discriminator
// ---------------------------------------------------------------------------

// Blank Journey config used when opening the "Add" bottom sheet.
// Intentionally has no default values so the new entry is distinct from presets.
private val blankJourneyConfig = JourneyConfigState(
    serverUrl = "", realm = "", cookie = "", clientId = "",
    discoveryEndpoint = "", scopes = "", redirectUri = "", display = ""
)

private sealed class SheetContent {
    data class JourneySheet(
        val config: JourneyConfigState = blankJourneyConfig,
        val customIndex: Int? = null,
    ) : SheetContent()

    data class DaVinciSheet(
        val config: OidcConfigState = OidcConfigState(),
        val customIndex: Int? = null,
    ) : SheetContent()

    data class WebSheet(
        val config: OidcConfigState = OidcConfigState(),
        val customIndex: Int? = null,
    ) : SheetContent()

    data class DeviceAuthSheet(
        val config: DeviceAuthConfigState = DeviceAuthConfigState(),
        val customIndex: Int? = null,
    ) : SheetContent()
}

// ---------------------------------------------------------------------------
// Main screen
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Env(
    envViewModel: EnvViewModel = viewModel(),
    onBack: (() -> Unit)? = null,
) {
    var sheetContent by remember { mutableStateOf<SheetContent?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    fun dismiss() {
        scope.launch { sheetState.hide() }.invokeOnCompletion { sheetContent = null }
    }

    AppTheme {
        Scaffold(
            topBar = {
                if (onBack != null) {
                    TopAppBar(
                        title = { Text("Configuration") },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        },
                    )
                }
            },
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Journey card
                JourneyCard(
                    presets = envViewModel.journeyPresets,
                    customConfigs = envViewModel.customJourneyConfigs,
                    appliedConfig = envViewModel.appliedJourneyConfig,
                    onSelect = { envViewModel.selectJourneyConfig(it) },
                    onEdit = { cfg, idx -> sheetContent = SheetContent.JourneySheet(cfg, idx) },
                    onDelete = { envViewModel.deleteCustomJourneyConfig(it) },
                    onAdd = { sheetContent = SheetContent.JourneySheet() },
                )

                // DaVinci card
                OidcCard(
                    title = "DaVinci",
                    presets = envViewModel.daVinciPresets,
                    customConfigs = envViewModel.customDaVinciConfigs,
                    appliedConfig = envViewModel.appliedDaVinciConfig,
                    onSelect = { envViewModel.selectDaVinciConfig(it) },
                    onEdit = { cfg, idx -> sheetContent = SheetContent.DaVinciSheet(cfg, idx) },
                    onDelete = { envViewModel.deleteCustomDaVinciConfig(it) },
                    onAdd = { sheetContent = SheetContent.DaVinciSheet() },
                )

                // OIDC (Web) card
                OidcCard(
                    title = "OIDC (Web)",
                    presets = envViewModel.webPresets,
                    customConfigs = envViewModel.customWebConfigs,
                    appliedConfig = envViewModel.appliedWebConfig,
                    onSelect = { envViewModel.selectWebConfig(it) },
                    onEdit = { cfg, idx -> sheetContent = SheetContent.WebSheet(cfg, idx) },
                    onDelete = { envViewModel.deleteCustomWebConfig(it) },
                    onAdd = { sheetContent = SheetContent.WebSheet() },
                )

                // Device Authorization card
                DeviceAuthCard(
                    presets = envViewModel.deviceAuthPresets,
                    customConfigs = envViewModel.customDeviceAuthConfigs,
                    appliedConfig = envViewModel.appliedDeviceAuthConfig,
                    onSelect = { envViewModel.selectDeviceAuthConfig(it) },
                    onEdit = { cfg, idx -> sheetContent = SheetContent.DeviceAuthSheet(cfg, idx) },
                    onDelete = { envViewModel.deleteCustomDeviceAuthConfig(it) },
                    onAdd = { sheetContent = SheetContent.DeviceAuthSheet() },
                )

                Spacer(Modifier.height(8.dp))
            }
        }

        // Bottom sheet (outside Scaffold to avoid inset conflicts)
        if (sheetContent != null) {
            ModalBottomSheet(
                onDismissRequest = { sheetContent = null },
                sheetState = sheetState,
            ) {
                when (val content = sheetContent) {
                    is SheetContent.JourneySheet -> JourneySheetContent(
                        initial = content.config,
                        isEdit = content.customIndex != null,
                        onSave = { cfg ->
                            envViewModel.saveCustomJourneyConfig(cfg, content.customIndex)
                            dismiss()
                        },
                        onDismiss = ::dismiss,
                    )
                    is SheetContent.DaVinciSheet -> OidcSheetContent(
                        title = "DaVinci Config",
                        initial = content.config,
                        isEdit = content.customIndex != null,
                        showArcValue = true,
                        onSave = { cfg ->
                            envViewModel.saveCustomDaVinciConfig(cfg, content.customIndex)
                            dismiss()
                        },
                        onDismiss = ::dismiss,
                    )
                    is SheetContent.WebSheet -> OidcSheetContent(
                        title = "OIDC (Web) Config",
                        initial = content.config,
                        isEdit = content.customIndex != null,
                        onSave = { cfg ->
                            envViewModel.saveCustomWebConfig(cfg, content.customIndex)
                            dismiss()
                        },
                        onDismiss = ::dismiss,
                    )
                    is SheetContent.DeviceAuthSheet -> DeviceAuthSheetContent(
                        initial = content.config,
                        isEdit = content.customIndex != null,
                        onSave = { cfg ->
                            envViewModel.saveCustomDeviceAuthConfig(cfg, content.customIndex)
                            dismiss()
                        },
                        onDismiss = ::dismiss,
                    )
                    null -> Unit
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Journey card
// ---------------------------------------------------------------------------

@Composable
private fun JourneyCard(
    presets: List<JourneyConfigState>,
    customConfigs: List<JourneyConfigState>,
    appliedConfig: JourneyConfigState?,
    onSelect: (JourneyConfigState) -> Unit,
    onEdit: (JourneyConfigState, Int) -> Unit,
    onDelete: (Int) -> Unit,
    onAdd: () -> Unit,
) {
    ConfigCard(title = "Journey", appliedDisplay = appliedConfig?.display, onAdd = onAdd) {
        if (presets.isNotEmpty()) {
            SectionLabel("Presets")
            presets.forEach { config ->
                ConfigRow(
                    display = config.display,
                    subtitle = "${extractHost(config.discoveryEndpoint)} · ${config.clientId}",
                    isApplied = appliedConfig == config,
                    isPreset = true,
                    onSelect = { onSelect(config) },
                    onEdit = null,
                    onDelete = null,
                )
            }
        }
        if (customConfigs.isNotEmpty()) {
            if (presets.isNotEmpty()) HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            SectionLabel("Custom")
            customConfigs.forEachIndexed { index, config ->
                ConfigRow(
                    display = config.display,
                    subtitle = "${extractHost(config.discoveryEndpoint)} · ${config.clientId}",
                    isApplied = appliedConfig == config,
                    isPreset = false,
                    onSelect = { onSelect(config) },
                    onEdit = { onEdit(config, index) },
                    onDelete = { onDelete(index) },
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Generic OIDC card (DaVinci / Web)
// ---------------------------------------------------------------------------

@Composable
private fun OidcCard(
    title: String,
    presets: List<OidcConfigState>,
    customConfigs: List<OidcConfigState>,
    appliedConfig: OidcConfigState?,
    onSelect: (OidcConfigState) -> Unit,
    onEdit: (OidcConfigState, Int) -> Unit,
    onDelete: (Int) -> Unit,
    onAdd: () -> Unit,
) {
    ConfigCard(title = title, appliedDisplay = appliedConfig?.display, onAdd = onAdd) {
        if (presets.isNotEmpty()) {
            SectionLabel("Presets")
            presets.forEach { config ->
                ConfigRow(
                    display = config.display,
                    subtitle = "${extractHost(config.discoveryEndpoint)} · ${config.clientId}",
                    isApplied = appliedConfig == config,
                    isPreset = true,
                    onSelect = { onSelect(config) },
                    onEdit = null,
                    onDelete = null,
                )
            }
        }
        if (customConfigs.isNotEmpty()) {
            if (presets.isNotEmpty()) HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            SectionLabel("Custom")
            customConfigs.forEachIndexed { index, config ->
                ConfigRow(
                    display = config.display,
                    subtitle = "${extractHost(config.discoveryEndpoint)} · ${config.clientId}",
                    isApplied = appliedConfig == config,
                    isPreset = false,
                    onSelect = { onSelect(config) },
                    onEdit = { onEdit(config, index) },
                    onDelete = { onDelete(index) },
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Device Authorization card
// ---------------------------------------------------------------------------

@Composable
private fun DeviceAuthCard(
    presets: List<DeviceAuthConfigState>,
    customConfigs: List<DeviceAuthConfigState>,
    appliedConfig: DeviceAuthConfigState?,
    onSelect: (DeviceAuthConfigState) -> Unit,
    onEdit: (DeviceAuthConfigState, Int) -> Unit,
    onDelete: (Int) -> Unit,
    onAdd: () -> Unit,
) {
    ConfigCard(title = "Auth Grant", appliedDisplay = appliedConfig?.display, onAdd = onAdd) {
        if (presets.isNotEmpty()) {
            SectionLabel("Presets")
            presets.forEach { config ->
                ConfigRow(
                    display = config.display,
                    subtitle = "${extractHost(config.discoveryEndpoint)} · ${config.clientId}",
                    isApplied = appliedConfig == config,
                    isPreset = true,
                    onSelect = { onSelect(config) },
                    onEdit = null,
                    onDelete = null,
                )
            }
        }
        if (customConfigs.isNotEmpty()) {
            if (presets.isNotEmpty()) HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            SectionLabel("Custom")
            customConfigs.forEachIndexed { index, config ->
                ConfigRow(
                    display = config.display,
                    subtitle = "${extractHost(config.discoveryEndpoint)} · ${config.clientId}",
                    isApplied = appliedConfig == config,
                    isPreset = false,
                    onSelect = { onSelect(config) },
                    onEdit = { onEdit(config, index) },
                    onDelete = { onDelete(index) },
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Shared card shell
// ---------------------------------------------------------------------------

@Composable
private fun ConfigCard(
    title: String,
    appliedDisplay: String?,
    onAdd: () -> Unit,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: title, applied badge, add button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                if (appliedDisplay != null) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = appliedDisplay,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                IconButton(onClick = onAdd) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Add config",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            HorizontalDivider()
            Spacer(Modifier.height(4.dp))
            content()
        }
    }
}

// ---------------------------------------------------------------------------
// Single config row
// ---------------------------------------------------------------------------

@Composable
private fun ConfigRow(
    display: String,
    subtitle: String,
    isApplied: Boolean,
    isPreset: Boolean,
    onSelect: () -> Unit,
    onEdit: (() -> Unit)?,
    onDelete: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .wrapContentHeight(),
        ) {
            Text(
                text = display,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!isPreset) {
            if (onEdit != null) {
                IconButton(onClick = onEdit) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = "Edit",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Delete",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        IconButton(onClick = onSelect) {
            Icon(
                imageVector = if (isApplied) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                contentDescription = if (isApplied) "Applied" else "Select",
                tint = if (isApplied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}

// ---------------------------------------------------------------------------
// Bottom sheet: Journey
// ---------------------------------------------------------------------------

@Composable
private fun JourneySheetContent(
    initial: JourneyConfigState,
    isEdit: Boolean,
    onSave: (JourneyConfigState) -> Unit,
    onDismiss: () -> Unit,
) {
    var cfg by remember { mutableStateOf(initial) }
    val canSave =
        cfg.serverUrl.isNotBlank() &&
                cfg.realm.isNotBlank() &&
                cfg.clientId.isNotBlank() &&
                cfg.discoveryEndpoint.isNotBlank() &&
                cfg.redirectUri.isNotBlank() &&
                cfg.display.isNotBlank()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = if (isEdit) "Edit Journey Config" else "Add Journey Config",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        ConfigField("Server URL", cfg.serverUrl) { cfg = cfg.copy(serverUrl = it) }
        ConfigField("Realm", cfg.realm) { cfg = cfg.copy(realm = it) }
        ConfigField("Cookie", cfg.cookie) { cfg = cfg.copy(cookie = it) }
        ConfigField("Client ID", cfg.clientId) { cfg = cfg.copy(clientId = it) }
        ConfigField("Discovery Endpoint", cfg.discoveryEndpoint) { cfg = cfg.copy(discoveryEndpoint = it) }
        ConfigField("Scopes (comma-separated)", cfg.scopes) { cfg = cfg.copy(scopes = it) }
        ConfigField("Redirect URI", cfg.redirectUri) { cfg = cfg.copy(redirectUri = it) }
        ConfigField("Display Name", cfg.display) { cfg = cfg.copy(display = it) }
        SheetActions(onDismiss = onDismiss, onSave = { onSave(cfg) }, canSave = canSave)
        Spacer(Modifier.height(8.dp))
    }
}

// ---------------------------------------------------------------------------
// Bottom sheet: DaVinci / OIDC Web
// ---------------------------------------------------------------------------

@Composable
private fun OidcSheetContent(
    title: String,
    initial: OidcConfigState,
    isEdit: Boolean,
    showArcValue: Boolean = false,
    onSave: (OidcConfigState) -> Unit,
    onDismiss: () -> Unit,
) {
    var cfg by remember { mutableStateOf(initial) }
    val canSave =
                cfg.clientId.isNotBlank() &&
                cfg.discoveryEndpoint.isNotBlank() &&
                cfg.redirectUri.isNotBlank() &&
                cfg.display.isNotBlank()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = if (isEdit) "Edit $title" else "Add $title",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        ConfigField("Client ID", cfg.clientId) { cfg = cfg.copy(clientId = it) }
        ConfigField("Discovery Endpoint", cfg.discoveryEndpoint) { cfg = cfg.copy(discoveryEndpoint = it) }
        ConfigField("Scopes (comma-separated)", cfg.scopes) { cfg = cfg.copy(scopes = it) }
        ConfigField("Redirect URI", cfg.redirectUri) { cfg = cfg.copy(redirectUri = it) }
        ConfigField("Display Name", cfg.display) { cfg = cfg.copy(display = it) }
        if (showArcValue) {
            ConfigField("ACR Value", cfg.arcValue) { cfg = cfg.copy(arcValue = it) }
        }
        SheetActions(onDismiss = onDismiss, onSave = { onSave(cfg) }, canSave = canSave)
        Spacer(Modifier.height(8.dp))
    }
}

// ---------------------------------------------------------------------------
// Bottom sheet: Device Authorization
// ---------------------------------------------------------------------------

@Composable
private fun DeviceAuthSheetContent(
    initial: DeviceAuthConfigState,
    isEdit: Boolean,
    onSave: (DeviceAuthConfigState) -> Unit,
    onDismiss: () -> Unit,
) {
    var cfg by remember { mutableStateOf(initial) }
    val canSave =
        cfg.clientId.isNotBlank() &&
        cfg.discoveryEndpoint.isNotBlank() &&
        cfg.display.isNotBlank()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = if (isEdit) "Edit Device Authorization Config" else "Add Device Authorization Config",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        ConfigField("Client ID", cfg.clientId) { cfg = cfg.copy(clientId = it) }
        ConfigField("Discovery Endpoint", cfg.discoveryEndpoint) { cfg = cfg.copy(discoveryEndpoint = it) }
        ConfigField("Scopes (comma-separated)", cfg.scopes) { cfg = cfg.copy(scopes = it) }
        ConfigField("Display Name", cfg.display) { cfg = cfg.copy(display = it) }
        SheetActions(onDismiss = onDismiss, onSave = { onSave(cfg) }, canSave = canSave)
        Spacer(Modifier.height(8.dp))
    }
}

// ---------------------------------------------------------------------------
// Shared bottom sheet Save / Cancel row
// ---------------------------------------------------------------------------

@Composable
private fun SheetActions(
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    canSave: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
            Text("Cancel")
        }
        Button(onClick = onSave, modifier = Modifier.weight(1f), enabled = canSave) {
            Text("Save & Apply")
        }
    }
}

// ---------------------------------------------------------------------------
// Reusable single-line text field
// ---------------------------------------------------------------------------

@Composable
private fun ConfigField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium,
    )
}

// ---------------------------------------------------------------------------
// Helper
// ---------------------------------------------------------------------------

private fun extractHost(url: String): String =
    runCatching { URL(url).host }.getOrDefault(url)

@Preview
@Composable
fun PreviewEnv() {
    Env()
}
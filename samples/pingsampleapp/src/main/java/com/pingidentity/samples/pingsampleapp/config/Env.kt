/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
// Edit sheet state
// ---------------------------------------------------------------------------

/** Mutable form state used by the add/edit bottom sheet. */
private data class ConfigFormState(
    val name: String = "",
    val type: ConfigType = ConfigType.JOURNEY,
    val clientId: String = "",
    val scopes: String = "",
    val redirectUri: String = "",
    val discoveryEndpoint: String = "",
    val environment: String = "",
    // Journey-only
    val serverUrl: String = "",
    val realm: String = "",
    val cookieName: String = "",
    // DaVinci / OIDC Web
    val acrValues: String = "",
    val par: Boolean = false,
)

private fun ConfigFormState.toConfiguration(): Configuration = Configuration(
    name = name.trim(),
    type = type,
    clientId = clientId.trim(),
    scopes = scopes.split(",").map { it.trim() }.filter { it.isNotEmpty() },
    redirectUri = redirectUri.trim(),
    discoveryEndpoint = discoveryEndpoint.trim(),
    environment = environment.trim(),
    serverUrl = serverUrl.trim().ifEmpty { null },
    realm = realm.trim().ifEmpty { null },
    cookieName = cookieName.trim().ifEmpty { null },
    acrValues = acrValues.trim().ifEmpty { null },
    par = if (par) true else null,
)

private fun Configuration.toFormState(): ConfigFormState = ConfigFormState(
    name = name,
    type = type,
    clientId = clientId,
    scopes = scopes.joinToString(","),
    redirectUri = redirectUri,
    discoveryEndpoint = discoveryEndpoint,
    environment = environment,
    serverUrl = serverUrl ?: "",
    realm = realm ?: "",
    cookieName = cookieName ?: "",
    acrValues = acrValues ?: "",
    par = par ?: false,
)

/** Sentinel value for "Add new config" — no pre-existing name to update. */
private val AddMode: String? = null

// ---------------------------------------------------------------------------
// Main screen
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Env(
    envViewModel: EnvViewModel = viewModel(),
    onBack: (() -> Unit)? = null,
) {
    val configurations by envViewModel.configurations.collectAsState()
    val selections by envViewModel.selections.collectAsState()

    // editingOldName: null → Add mode; non-null → Edit mode (holds old name for update call)
    var editingOldName by remember { mutableStateOf<String?>(null) }
    var sheetFormState by remember { mutableStateOf<ConfigFormState?>(null) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    fun openAdd() {
        editingOldName = AddMode
        sheetFormState = ConfigFormState()
    }

    fun openEdit(config: Configuration) {
        editingOldName = config.name
        sheetFormState = config.toFormState()
    }

    fun dismiss() {
        scope.launch { sheetState.hide() }.invokeOnCompletion { sheetFormState = null }
    }

    fun onSave(form: ConfigFormState) {
        val config = form.toConfiguration()
        val old = editingOldName
        if (old == null) {
            envViewModel.add(config)
        } else {
            envViewModel.update(old, config)
        }
        dismiss()
    }

    AppTheme {
        Scaffold(
            topBar = {
                if (onBack != null) {
                    TopAppBar(
                        title = { Text("Configuration") },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                )
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
                ConfigType.entries.forEach { type ->
                    val typeConfigs = configurations.filter { it.type == type }
                    val selected = selections[type]
                    ConfigTypeCard(
                        type = type,
                        configs = typeConfigs,
                        selectedConfig = selected,
                        onSelect = { envViewModel.select(it) },
                        onEdit = { openEdit(it) },
                        onDelete = { envViewModel.delete(it) },
                        onAdd = { openAdd() },
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        if (sheetFormState != null) {
            ModalBottomSheet(
                onDismissRequest = { sheetFormState = null },
                sheetState = sheetState,
            ) {
                ConfigEditSheet(
                    initial = sheetFormState!!,
                    isEdit = editingOldName != null,
                    onSave = ::onSave,
                    onDismiss = ::dismiss,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Card per ConfigType
// ---------------------------------------------------------------------------

@Composable
private fun ConfigTypeCard(
    type: ConfigType,
    configs: List<Configuration>,
    selectedConfig: Configuration?,
    onSelect: (Configuration) -> Unit,
    onEdit: (Configuration) -> Unit,
    onDelete: (Configuration) -> Unit,
    onAdd: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = type.name.replace("_", " "),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                if (selectedConfig != null) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = selectedConfig.name,
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

            if (configs.isEmpty()) {
                Text(
                    text = "No configurations",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            } else {
                configs.forEach { config ->
                    val isSelected = selectedConfig?.name == config.name
                    val isDefault = ConfigurationDefaults.isDefault(config)
                    ConfigRow(
                        display = config.name,
                        subtitle = "${extractHost(config.discoveryEndpoint)} · ${config.clientId}",
                        isSelected = isSelected,
                        isDefault = isDefault,
                        onSelect = { onSelect(config) },
                        onEdit = if (!isDefault) ({ onEdit(config) }) else null,
                        onDelete = if (!isDefault) ({ onDelete(config) }) else null,
                    )
                }
            }
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
    isSelected: Boolean,
    isDefault: Boolean,
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
        if (!isDefault) {
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
                imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                contentDescription = if (isSelected) "Selected" else "Select",
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
}

// ---------------------------------------------------------------------------
// Add / Edit bottom sheet
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfigEditSheet(
    initial: ConfigFormState,
    isEdit: Boolean,
    onSave: (ConfigFormState) -> Unit,
    onDismiss: () -> Unit,
) {
    var form by remember { mutableStateOf(initial) }
    var typeMenuExpanded by remember { mutableStateOf(false) }

    val canSave = form.name.isNotBlank() &&
            form.clientId.isNotBlank() &&
            form.redirectUri.isNotBlank() &&
            form.discoveryEndpoint.isNotBlank() &&
            form.environment.isNotBlank() &&
            (form.type != ConfigType.JOURNEY || (form.serverUrl.isNotBlank() && form.realm.isNotBlank()))

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
            text = if (isEdit) "Edit Configuration" else "Add Configuration",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )

        ConfigField("Name", form.name) { form = form.copy(name = it) }

        // Type dropdown — locked when editing to avoid type mismatch
        ExposedDropdownMenuBox(
            expanded = typeMenuExpanded && !isEdit,
            onExpandedChange = { if (!isEdit) typeMenuExpanded = it },
        ) {
            OutlinedTextField(
                value = form.type.name.replace("_", " "),
                onValueChange = {},
                readOnly = true,
                label = { Text("Type") },
                trailingIcon = {
                    if (!isEdit) ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeMenuExpanded)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                textStyle = MaterialTheme.typography.bodyMedium,
            )
            if (!isEdit) {
                ExposedDropdownMenu(
                    expanded = typeMenuExpanded,
                    onDismissRequest = { typeMenuExpanded = false },
                ) {
                    ConfigType.entries.forEach { ct ->
                        DropdownMenuItem(
                            text = { Text(ct.name.replace("_", " ")) },
                            onClick = {
                                form = form.copy(type = ct)
                                typeMenuExpanded = false
                            },
                        )
                    }
                }
            }
        }

        ConfigField("Client ID", form.clientId) { form = form.copy(clientId = it) }
        ConfigField("Scopes (comma-separated)", form.scopes) { form = form.copy(scopes = it) }
        ConfigField("Redirect URI", form.redirectUri) { form = form.copy(redirectUri = it) }
        ConfigField("Discovery Endpoint", form.discoveryEndpoint) { form = form.copy(discoveryEndpoint = it) }
        ConfigField("Environment", form.environment) { form = form.copy(environment = it) }

        if (form.type == ConfigType.JOURNEY) {
            ConfigField("Server URL", form.serverUrl) { form = form.copy(serverUrl = it) }
            ConfigField("Realm", form.realm) { form = form.copy(realm = it) }
            ConfigField("Cookie Name", form.cookieName) { form = form.copy(cookieName = it) }
        }

        if (form.type == ConfigType.DAVINCI || form.type == ConfigType.OIDC_WEB) {
            ConfigField("ACR Values", form.acrValues) { form = form.copy(acrValues = it) }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                Text("Cancel")
            }
            Button(
                onClick = { onSave(form) },
                modifier = Modifier.weight(1f),
                enabled = canSave,
            ) {
                Text("Save & Apply")
            }
        }
        Spacer(Modifier.height(8.dp))
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

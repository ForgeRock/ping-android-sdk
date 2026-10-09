/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.samples.pingsampleapp.rar

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

/** The per-transaction `authorization_details` presets, mirroring the iOS sample's RarPreset. */
private enum class RarPreset(val label: String, val json: String) {
    PAYMENT(
        "payment_initiation",
        """[
  {
    "type": "payment_initiation",
    "locations": ["https://example.com/payments"],
    "instructedAmount": {"currency": "EUR", "amount": "123.50"},
    "creditorName": "Merchant A",
    "creditorAccount": {"iban": "DE02100100109307118603"},
    "remittanceInformationUnstructured": "Ref Number Merchant"
  }
]""",
    ),
    ACCOUNT(
        "account_information",
        """[
  {
    "type": "account_information",
    "actions": ["list_accounts", "read_balances"],
    "locations": ["https://example.com/accounts"],
    "datatypes": ["balances"]
  }
]""",
    ),
    CUSTOM("Custom", "[\n  {\n    \"type\": \"custom\"\n  }\n]"),
}

/**
 * RFC 9396 RAR login screen: paste authorization_details JSON (or pick a preset) and run the
 * login on the dedicated RAR client. Per-transaction details win over config-level details;
 * the RAR token (B) is stored separately, so any Journey / DaVinci / OIDC token (A) stays
 * valid — compare both on the Token screen's "OIDC RAR" tab.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RarLoginScreen(
    rarLoginViewModel: RarLoginViewModel = viewModel<RarLoginViewModel>(),
    onSuccess: () -> Unit = {},
    onBack: (() -> Unit)? = null,
) {
    val state by rarLoginViewModel.state.collectAsState()
    val config = rarLoginViewModel.webConfig
    var jsonText by remember { mutableStateOf(RarPreset.ACCOUNT.json) }
    var selectedPreset by remember { mutableStateOf(RarPreset.ACCOUNT) }

    LaunchedEffect(state.success) {
        if (state.success) {
            rarLoginViewModel.reset()
            onSuccess()
        }
    }

    Scaffold(
        topBar = {
            if (onBack != null) {
                TopAppBar(
                    title = { Text("OIDC RAR Login") },
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
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Status card: which config the RAR client was built from, and its flags.
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = config?.display?.ifBlank { config.clientId }
                            ?: "No Web config applied — select one in Configuration",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            text = if (config?.par == true) "PAR: on" else "PAR: off",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (config?.par == true) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = if (config?.authorizationDetails != null) "Config details: set"
                            else "Config details: not set",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (config?.authorizationDetails != null) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = "Per-transaction details below win over config-level details. " +
                            "The RAR token is stored separately — existing logins stay valid.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Horizontally scrollable so narrow screens don't crush the last chip's label
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RarPreset.entries.forEach { preset ->
                    FilterChip(
                        selected = selectedPreset == preset,
                        onClick = {
                            selectedPreset = preset
                            jsonText = preset.json
                        },
                        label = { Text(preset.label) },
                    )
                }
            }

            OutlinedTextField(
                value = jsonText,
                onValueChange = { jsonText = it },
                label = { Text("authorization_details (JSON array)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            )

            val parsed = remember(jsonText) { parseRarJson(jsonText) }
            val parseError = parsed == null && jsonText.isNotBlank()
            if (parseError) {
                Text(
                    text = "Invalid authorization_details JSON",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            // Non-blocking: type-only details are legal (RFC 9396 §2.2) but give the server
            // nothing to act on — e.g. the Custom preset ships `[{"type": "custom"}]`. Flags
            // any entry carrying only a type, so a mixed array still warns on its bare entries.
            val hasTypeOnlyEntry = parsed?.any {
                it.locations == null && it.actions == null && it.datatypes == null &&
                    it.privileges == null && it.additionalFields.isEmpty()
            } == true
            if (hasTypeOnlyEntry) {
                Text(
                    text = "Some details carry only a \"type\" and no other fields — the " +
                        "server may not be able to act on them.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }

            Button(
                onClick = { rarLoginViewModel.login(jsonText) },
                enabled = !state.running && !parseError,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.running) {
                    CircularProgressIndicator(modifier = Modifier.height(20.dp))
                } else {
                    Text("Login with RAR")
                }
            }

            state.error?.let { error ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                ) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }
}

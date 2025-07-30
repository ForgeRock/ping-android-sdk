/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.authenticatorapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pingidentity.authenticatorapp.data.AuthenticatorViewModel
import com.pingidentity.mfa.oath.OathAlgorithm
import com.pingidentity.mfa.oath.OathType

/**
 * Screen for manually adding an account.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualEntryScreen(
    viewModel: AuthenticatorViewModel,
    onEntryComplete: () -> Unit,
    onDismiss: () -> Unit
) {
    var issuer by remember { mutableStateOf("") }
    var accountName by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    var oathType by remember { mutableStateOf(OathType.TOTP) }
    var algorithm by remember { mutableStateOf(OathAlgorithm.SHA1) }
    var digits by remember { mutableStateOf("6") }
    var period by remember { mutableStateOf("30") }
    
    val uiState by viewModel.uiState.collectAsState()
    
    // Watch for credential addition success
    LaunchedEffect(uiState.lastAddedCredential) {
        if (uiState.lastAddedCredential != null) {
            viewModel.clearLastAddedCredential()
            onEntryComplete()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Add Account Manually") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Issuer field
            OutlinedTextField(
                value = issuer,
                onValueChange = { issuer = it },
                label = { Text("Issuer (e.g. Company)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            // Account name field
            OutlinedTextField(
                value = accountName,
                onValueChange = { accountName = it },
                label = { Text("Account Name (e.g. email@example.com)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            // Secret key field
            OutlinedTextField(
                value = secret,
                onValueChange = { secret = it },
                label = { Text("Secret Key") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            // OTP Type selection
            Text(
                text = "OTP Type",
                style = MaterialTheme.typography.bodyLarge
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OathType.values().forEach { type ->
                    FilterChip(
                        selected = oathType == type,
                        onClick = { oathType = type },
                        label = { Text(type.name) }
                    )
                }
            }
            
            // Algorithm selection
            Text(
                text = "Algorithm",
                style = MaterialTheme.typography.bodyLarge
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OathAlgorithm.values().forEach { alg ->
                    FilterChip(
                        selected = algorithm == alg,
                        onClick = { algorithm = alg },
                        label = { Text(alg.name) }
                    )
                }
            }
            
            // Digits selection
            OutlinedTextField(
                value = digits,
                onValueChange = { if (it.isBlank() || it.toIntOrNull() != null) digits = it },
                label = { Text("Digits") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            // Period selection (only for TOTP)
            if (oathType == OathType.TOTP) {
                OutlinedTextField(
                    value = period,
                    onValueChange = { if (it.isBlank() || it.toIntOrNull() != null) period = it },
                    label = { Text("Period (seconds)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
            
            // Submit button
            Button(
                onClick = {
                    // Create otpauth URI and add credential
                    val uri = buildOtpauthUri(
                        issuer = issuer,
                        accountName = accountName,
                        secret = secret,
                        oathType = oathType,
                        algorithm = algorithm,
                        digits = digits.toIntOrNull() ?: 6,
                        period = period.toIntOrNull() ?: 30
                    )
                    viewModel.addCredentialFromUri(uri)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                enabled = issuer.isNotBlank() && accountName.isNotBlank() && secret.isNotBlank()
            ) {
                Text("Add Account")
            }
        }
        
        // Error handling
        if (uiState.error != null) {
            AlertDialog(
                onDismissRequest = { viewModel.clearError() },
                title = { Text("Error") },
                text = { Text(uiState.error!!) },
                confirmButton = {
                    Button(onClick = { viewModel.clearError() }) {
                        Text("OK")
                    }
                }
            )
        }
    }
}

/**
 * Builds an otpauth URI from the provided parameters.
 * Format: otpauth://totp/Example:alice@google.com?secret=JBSWY3DPEHPK3PXP&issuer=Example&algorithm=SHA1&digits=6&period=30
 */
private fun buildOtpauthUri(
    issuer: String,
    accountName: String,
    secret: String,
    oathType: OathType,
    algorithm: OathAlgorithm,
    digits: Int,
    period: Int
): String {
    return buildString {
        append("otpauth://")
        append(oathType.name.lowercase())
        append("/")
        append(issuer)
        append(":")
        append(accountName)
        append("?secret=")
        append(secret)
        append("&issuer=")
        append(issuer)
        append("&algorithm=")
        append(algorithm.name)
        append("&digits=")
        append(digits)
        
        if (oathType == OathType.TOTP) {
            append("&period=")
            append(period)
        }
    }
}

/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.authenticatorapp.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timelapse
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pingidentity.authenticatorapp.data.AuthenticatorViewModel
import com.pingidentity.mfa.oath.OathCodeInfo
import com.pingidentity.mfa.oath.OathCredential
import com.pingidentity.mfa.oath.OathType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Screen for displaying a list of accounts.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AccountsScreen(
    viewModel: AuthenticatorViewModel,
    onScanQrCode: () -> Unit,
    onAddManually: () -> Unit,
    onAccountClick: (String) -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    
    // Initialize the ViewModel when first launched
    LaunchedEffect(Unit) {
        viewModel.initialize(context)
    }
    
    // Auto-refresh TOTP codes
    LaunchedEffect(uiState.credentials) {
        var loop = 1
        while (true) {
            // Only refresh codes for TOTP credentials
            uiState.credentials.forEach { credential ->
                if (credential.oathType == OathType.TOTP) {
                    viewModel.generateCode(credential.id)
                } else if (credential.oathType == OathType.HOTP && loop == 1) {
                    viewModel.generateCode(credential.id)
                }
            }
            // Increment loop counter
            loop++

            // Wait for 1 second before refreshing again
            delay(1000)
        }
    }
    
    // Show fab menu state
    var showFabMenu by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Authenticator Sample") }
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                AnimatedVisibility(
                    visible = showFabMenu,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Scan QR code option
                        FloatingActionButton(
                            onClick = {
                                showFabMenu = false
                                onScanQrCode()
                            },
                            modifier = Modifier.size(48.dp),
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = "Scan QR Code"
                            )
                        }
                        
                        // Manual entry option
                        FloatingActionButton(
                            onClick = {
                                showFabMenu = false
                                onAddManually()
                            },
                            modifier = Modifier.size(48.dp),
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Icon(
                                imageVector = Icons.Default.Keyboard,
                                contentDescription = "Add Manually"
                            )
                        }
                    }
                }
                
                // Primary FAB
                FloatingActionButton(
                    onClick = { showFabMenu = !showFabMenu },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Account"
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (uiState.credentials.isEmpty()) {
                // Empty state
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "No accounts added yet",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Add an account by scanning a QR code or entering details manually",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                // List of accounts
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = uiState.credentials,
                        key = { it.id }
                    ) { credential ->
                        AccountItem(
                            credential = credential,
                            codeInfo = uiState.generatedCodes[credential.id],
                            onRefreshCode = {
                                if (credential.oathType == OathType.HOTP) {
                                    coroutineScope.launch {
                                        viewModel.generateCode(credential.id)
                                    }
                                }
                            },
                            onItemClick = { onAccountClick(credential.id) },
                            modifier = Modifier.animateItemPlacement()
                        )
                    }
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
}

@Composable
fun AccountItem(
    credential: OathCredential,
    codeInfo: OathCodeInfo?,
    onRefreshCode: () -> Unit,
    onItemClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progress = if (codeInfo != null && credential.oathType == OathType.TOTP) {
        codeInfo.progress.toFloat()
    } else {
        0f
    }
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onItemClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Issuer and account name
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = credential.issuer,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Text(
                        text = credential.accountName,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                // Code or refresh button
                Box(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .wrapContentWidth()
                ) {
                    codeInfo?.let { info ->
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = info.code,
                                style = MaterialTheme.typography.headlineSmall
                            )
                            
                            if (credential.oathType == OathType.TOTP) {
                                // Progress indicator for TOTP
                                LinearProgressIndicator(
                                    progress = { 1f - progress },  // Reverse progress (countdown)
                                    modifier = Modifier
                                        .width(80.dp)
                                        .padding(top = 4.dp),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    } ?: run {
                        // If no code is generated yet, show a code placeholder button
                        TextButton(onClick = onRefreshCode) {
                            Text("* * * * * *")
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .wrapContentWidth()
                ) {
                    if (credential.oathType == OathType.TOTP) {
                        IconButton(onClick = {}) {
                            Icon(Icons.Default.Timelapse, contentDescription = null)
                        }
                    } else {
                        IconButton(onClick = onRefreshCode) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                        }
                    }

                }
            }
            
            // Show a horizontal line to indicate TOTP progress
            if (credential.oathType == OathType.TOTP && codeInfo != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .padding(top = 8.dp)
                ) {
                    // Background progress bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                    
                    // Actual progress
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(1f - progress)
                            .height(2.dp)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }
        }
    }
}

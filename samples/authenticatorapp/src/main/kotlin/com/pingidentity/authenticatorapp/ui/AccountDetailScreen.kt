/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.authenticatorapp.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.pingidentity.authenticatorapp.data.AuthenticatorViewModel
import com.pingidentity.mfa.oath.OathType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Screen for displaying account details.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountDetailScreen(
    accountId: String,
    viewModel: AuthenticatorViewModel,
    onDismiss: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    
    // Find the credential with the given ID
    val credential = uiState.credentials.find { it.id == accountId }
    val codeInfo = uiState.generatedCodes[accountId]
    
    // Clipboard manager to copy codes
    val clipboardManager = LocalClipboardManager.current
    var showCopyConfirmation by remember { mutableStateOf(false) }
    
    // Auto-refresh for TOTP codes
    LaunchedEffect(credential) {
        if (credential != null && credential.oathType == OathType.TOTP) {
            while (true) {
                viewModel.generateCode(credential.id)
                delay(1000)
            }
        } else if (credential != null && codeInfo == null) {
            // Generate initial code for HOTP
            viewModel.generateCode(credential.id)
        }
    }
    
    // Confirm deletion dialog state
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    
    // Copy toast timeout
    LaunchedEffect(showCopyConfirmation) {
        if (showCopyConfirmation) {
            delay(2000)
            showCopyConfirmation = false
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = credential?.issuer ?: "Account Details") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Delete action
                    IconButton(onClick = { showDeleteConfirmation = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Account")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (credential == null) {
                // Account not found
                Text(
                    text = "Account not found",
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp)
                )
            } else {
                // Account details
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // OTP Code display with timer
                    codeInfo?.let { info ->
                        // Calculate progress for TOTP
                        val progress = if (credential.oathType == OathType.TOTP) {
                            info.progress.toFloat()
                        } else {
                            0f
                        }
                        
                        // Code with countdown timer
                        Box(
                            modifier = Modifier
                                .padding(vertical = 32.dp)
                                .size(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            // Circular progress indicator for TOTP
                            if (credential.oathType == OathType.TOTP) {
                                CircularProgressTimer(
                                    progress = progress,
                                    modifier = Modifier.matchParentSize()
                                )
                            }
                            
                            // Show the actual code
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = info.code,
                                    style = MaterialTheme.typography.displaySmall
                                )
                                
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                // Copy button
                                Button(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(info.code))
                                        showCopyConfirmation = true
                                    }
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Copy Code")
                                }
                                
                                // Refresh button (for HOTP)
                                if (credential.oathType == OathType.HOTP) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        onClick = {
                                            coroutineScope.launch {
                                                viewModel.generateCode(credential.id)
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("New Code")
                                    }
                                }
                            }
                        }
                    } ?: run {
                        // No code info available, show a generate button
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    viewModel.generateCode(credential.id)
                                }
                            },
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Text("Generate Code")
                        }
                    }
                    
                    // Account details section
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            DetailRow(label = "Issuer", value = credential.issuer)
                            DetailRow(label = "Account", value = credential.accountName)
                            DetailRow(label = "Type", value = credential.oathType.name)
                            DetailRow(
                                label = "Algorithm", 
                                value = credential.oathAlgorithm.name
                            )
                            DetailRow(label = "Digits", value = credential.digits.toString())
                            
                            if (credential.oathType == OathType.TOTP) {
                                DetailRow(
                                    label = "Period", 
                                    value = "${credential.period} seconds"
                                )
                            }
                        }
                    }
                }
            }
            
            // Show copy confirmation
            if (showCopyConfirmation) {
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                ) {
                    Text("Code copied to clipboard")
                }
            }
            
            // Delete confirmation dialog
            if (showDeleteConfirmation) {
                AlertDialog(
                    onDismissRequest = { showDeleteConfirmation = false },
                    title = { Text("Delete Account") },
                    text = { Text("Are you sure you want to delete this account?") },
                    confirmButton = {
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    credential?.id?.let { 
                                        viewModel.removeCredential(it)
                                        onDismiss()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Delete")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteConfirmation = false }) {
                            Text("Cancel")
                        }
                    }
                )
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
fun DetailRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun CircularProgressTimer(
    progress: Float,
    modifier: Modifier = Modifier
) {
    val animatedProgress = remember(progress) {
        Animatable(initialValue = progress)
    }
    
    LaunchedEffect(progress) {
        animatedProgress.animateTo(
            targetValue = progress,
            animationSpec = tween(durationMillis = 500, easing = LinearEasing)
        )
    }
    
    val color = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    
    Box(
        modifier = modifier.drawBehind {
            // Draw background track
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = 10f, cap = StrokeCap.Round)
            )
            
            // Draw progress
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * (1f - animatedProgress.value),
                useCenter = false,
                style = Stroke(width = 10f, cap = StrokeCap.Round)
            )
        }
    )
}

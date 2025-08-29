/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.authenticatorapp.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.pingidentity.authenticatorapp.R
import com.pingidentity.authenticatorapp.data.AuthenticatorViewModel
import com.pingidentity.authenticatorapp.ui.components.AccountGroupItem
import com.pingidentity.authenticatorapp.ui.components.EmptyStateMessage
import com.pingidentity.authenticatorapp.ui.components.ErrorAlertDialog
import com.pingidentity.authenticatorapp.ui.components.LoadingIndicator
import com.pingidentity.mfa.oath.OathType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Screen for displaying a list of accounts and push notifications.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(
    viewModel: AuthenticatorViewModel,
    onScanQrCode: () -> Unit,
    onAddManually: () -> Unit,
    onAccountClick: (String) -> Unit,
    onNotificationsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAboutClick: () -> Unit,
    onEditAccountsClick: () -> Unit,
    onTestModeClick: () -> Unit = {},
    onNavigateToLogin: () -> Unit = {}
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    
    // Collect settings state
    val copyOtpEnabled by viewModel.copyOtp.collectAsState()
    val tapToRevealEnabled by viewModel.tapToReveal.collectAsState()
    
    
    
    // Auto-refresh TOTP codes
    LaunchedEffect(uiState.oathCredentials) {
        var loop = 1
        while (true) {
            // Only refresh codes for TOTP credentials
            uiState.oathCredentials.forEach { credential ->
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
    
    // Show hamburger menu state
    var showHamburgerMenu by remember { mutableStateOf(false) }
    
    // Snackbar state
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Handle success messages
    LaunchedEffect(uiState.message) {
        uiState.message?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearMessage()
        }
    }

    // Handle error messages
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.ping_logo),
                            contentDescription = "Ping Identity Logo",
                            modifier = Modifier
                                .size(32.dp)
                                .padding(end = 4.dp)
                        )
                                                Text(text = stringResource(id = R.string.accounts_screen_title))
                    }
                },
                actions = {
                    // Actions only visible when test mode is enabled
                    val testModeEnabled by viewModel.testMode.collectAsState()
                    if (testModeEnabled) {
                        // Refresh button to manually refresh codes and check for notifications
                        IconButton(onClick = {
                            viewModel.refreshCredentials()
                            viewModel.refreshNotifications()
                        }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh"
                            )
                        }
                        // Test mode button
                        IconButton(onClick = { onTestModeClick() }) {
                            Icon(
                                imageVector = Icons.Default.BugReport,
                                contentDescription = "Test Mode"
                            )
                        }
                    }
                    
                    // Hamburger menu
                    Box {
                        IconButton(onClick = { showHamburgerMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Menu"
                            )
                        }
                        
                        DropdownMenu(
                            expanded = showHamburgerMenu,
                            onDismissRequest = { showHamburgerMenu = false }
                        ) {
                            // Notifications with badge
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Notifications,
                                            contentDescription = null,
                                            modifier = Modifier.padding(end = 12.dp)
                                        )
                                        Text("Notifications")
                                        
                                        // Show a badge if there are pending notifications
                                        if (uiState.pushNotificationItems.isNotEmpty()) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .background(
                                                        color = MaterialTheme.colorScheme.error,
                                                        shape = CircleShape
                                                    )
                                                    .padding(start = 8.dp)
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    showHamburgerMenu = false
                                    onNotificationsClick()
                                }
                            )
                            
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = null,
                                            modifier = Modifier.padding(end = 12.dp)
                                        )
                                        Text("Edit Accounts")
                                    }
                                },
                                onClick = {
                                    showHamburgerMenu = false
                                    onEditAccountsClick()
                                }
                            )
                            
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Settings,
                                            contentDescription = null,
                                            modifier = Modifier.padding(end = 12.dp)
                                        )
                                        Text("Settings")
                                    }
                                },
                                onClick = {
                                    showHamburgerMenu = false
                                    onSettingsClick()
                                }
                            )
                            
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = null,
                                            modifier = Modifier.padding(end = 12.dp)
                                        )
                                                                                Text(stringResource(id = R.string.menu_about))
                                    }
                                },
                                onClick = {
                                    showHamburgerMenu = false
                                    onAboutClick()
                                }
                            )
                        }
                    }
                }
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
                        
                        // Login option
                        FloatingActionButton(
                            onClick = {
                                showFabMenu = false
                                onNavigateToLogin()
                            },
                            modifier = Modifier.size(48.dp),
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Journey Login"
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
                                                contentDescription = stringResource(id = R.string.content_description_add_account)
                    )
                }
            }
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Loading progress indicator at the top when refreshing
            if (uiState.isRefreshing) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )
            }
            
            when {
                uiState.isInitialLoading -> {
                    LoadingIndicator(
                        message = stringResource(id = R.string.loading_credentials)
                    )
                }
                uiState.accountGroups.isEmpty() -> {
                    EmptyStateMessage(
                        title = "No accounts added yet",
                        subtitle = stringResource(id = R.string.accounts_empty_state_subtitle)
                    )
                }
                else -> {
                    // List of account groups
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = uiState.accountGroups,
                        key = { accountGroup ->
                            // Create a unique key using issuer, account name, and all credential IDs
                            val oathIds = accountGroup.oathCredentials.map { it.id }.sorted().joinToString(",")
                            val pushIds = accountGroup.pushCredentials.map { it.id }.sorted().joinToString(",")
                            "${accountGroup.issuer}-${accountGroup.accountName}-oath:$oathIds-push:$pushIds"
                        }
                    ) { accountGroup ->
                        AccountGroupItem(
                            accountGroup = accountGroup,
                            codes = uiState.generatedCodes,
                            onRefreshCode = { credentialId ->
                                coroutineScope.launch {
                                    viewModel.generateCode(credentialId)
                                }
                            },
                            onItemClick = { 
                                // Pass the account group issuer and account name for navigation
                                // This allows the detail screen to display all credentials for this account
                                val encodedIssuer = java.net.URLEncoder.encode(accountGroup.issuer, "UTF-8")
                                val encodedAccountName = java.net.URLEncoder.encode(accountGroup.accountName, "UTF-8")
                                onAccountClick("$encodedIssuer/$encodedAccountName")
                            },
                            onCopyToClipboard = { text, label ->
                                viewModel.copyToClipboard(context, text, label)
                            },
                            copyOtpEnabled = copyOtpEnabled,
                            tapToRevealEnabled = tapToRevealEnabled,
                            modifier = Modifier.animateItem(
                                fadeInSpec = null, fadeOutSpec = null, placementSpec = spring(
                                    stiffness = Spring.StiffnessMediumLow,
                                    visibilityThreshold = IntOffset.VisibilityThreshold
                                )
                            )
                        )
                    }
                }
            }
            }
            
            // Error handling
            if (uiState.error != null) {
                ErrorAlertDialog(
                    errorMessage = uiState.error!!,
                    onDismiss = { viewModel.clearError() }
                )
            }
        }
    }
}
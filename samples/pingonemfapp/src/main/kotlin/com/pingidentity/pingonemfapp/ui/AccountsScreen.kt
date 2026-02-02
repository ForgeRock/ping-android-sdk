/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.pingonemfapp.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.QrCodeScanner
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pingidentity.pingonemfapp.R
import com.pingidentity.pingonemfapp.data.PingOneMFAViewModel
import com.pingidentity.pingonemfapp.ui.components.AccountCard
import com.pingidentity.pingonemfapp.ui.components.EmptyStateMessage
import com.pingidentity.pingonemfapp.ui.components.ErrorAlertDialog
import com.pingidentity.pingonemfapp.ui.components.LoadingIndicator
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Screen for displaying a list of accounts and push notifications.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(
    viewModel: PingOneMFAViewModel,
    onScanQrCode: () -> Unit,
    onAccountClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAboutClick: () -> Unit,
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    
    // Collect settings state
    val copyOtpEnabled by viewModel.copyOtp.collectAsState()

    // State for triggering progress bar updates
    var currentTimeMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Update progress bars every second for smooth countdown without regenerating codes
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(1000)
            currentTimeMillis = System.currentTimeMillis() // Trigger recomposition
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
                    }
                }
                
                // Primary FAB
                FloatingActionButton(
                    onClick = {
                        showFabMenu = false
                        onScanQrCode()
                    },
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
                        message = stringResource(id = R.string.loading_accounts)
                    )
                }
                uiState.isLoadingPingOneAccounts -> {
                    LoadingIndicator(
                        message = stringResource(id = R.string.loading_accounts)
                    )
                }
                uiState.pingOneMfaAccounts.isEmpty() -> {
                    EmptyStateMessage(
                        title = "No accounts added yet",
                        subtitle = stringResource(id = R.string.accounts_empty_state_subtitle)
                    )
                }
                else -> {
                    // List of accounts
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = uiState.pingOneMfaAccounts,
                        key = { account ->
                            // Create a unique key using issuer, account name, and all credential IDs
                            val userId = account.id
                            val deviceId = account.deviceId
                            "$userId-$deviceId"
                        }
                    ) { account ->
                        AccountCard(
                            accountItem = account,
                            onCardClick = {
                                // Navigate to the OTP screen
                                onAccountClick()
                            }
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
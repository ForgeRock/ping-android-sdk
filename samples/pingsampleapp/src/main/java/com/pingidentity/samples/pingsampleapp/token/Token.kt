/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.samples.pingsampleapp.token

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RemoveCircleOutline
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pingidentity.oidc.Token

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TokenScreen(
    tokenViewModel: TokenViewModel = viewModel<TokenViewModel>(),
    onBack: (() -> Unit)? = null,
) {
    val tokenState by tokenViewModel.state.collectAsState()

    LaunchedEffect(true) {
        tokenViewModel.loadAllTokens()
    }

    Scaffold(
        topBar = {
            if (onBack != null) {
                TopAppBar(
                    title = { Text("Token") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { tokenViewModel.accessToken() }) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = "Access Token",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        IconButton(onClick = { tokenViewModel.refresh() }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        IconButton(onClick = { tokenViewModel.revoke() }) {
                            Icon(
                                imageVector = Icons.Default.RemoveCircleOutline,
                                contentDescription = "Revoke",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                        IconButton(onClick = { tokenViewModel.reset() }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Clear",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
        ) {
            TabRow(
                selectedTabIndex = tokenState.selectedTab.ordinal,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Tab(
                    selected = tokenState.selectedTab == TokenType.JOURNEY,
                    onClick = {
                        tokenViewModel.selectTab(TokenType.JOURNEY)
                        tokenViewModel.loadAllTokens()
                    },
                    text = { Text("Journey") },
                )
                Tab(
                    selected = tokenState.selectedTab == TokenType.DAVINCI,
                    onClick = {
                        tokenViewModel.selectTab(TokenType.DAVINCI)
                        tokenViewModel.loadAllTokens()
                    },
                    text = { Text("DaVinci") },
                )
                Tab(
                    selected = tokenState.selectedTab == TokenType.OIDC,
                    onClick = {
                        tokenViewModel.selectTab(TokenType.OIDC)
                        tokenViewModel.loadAllTokens()
                    },
                    text = { Text("OIDC") },
                )
            }

            val token = when (tokenState.selectedTab) {
                TokenType.JOURNEY -> tokenState.journeyToken
                TokenType.DAVINCI -> tokenState.daVinciToken
                TokenType.OIDC -> tokenState.oidcToken
            }
            val error = when (tokenState.selectedTab) {
                TokenType.JOURNEY -> tokenState.journeyError
                TokenType.DAVINCI -> tokenState.daVinciError
                TokenType.OIDC -> tokenState.oidcError
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when {
                    token != null -> TokenCard(token)
                    error != null -> ErrorCard(error.toString())
                    else -> EmptyCard(tokenState.selectedTab)
                }
            }
        }
    }
}

@Composable
private fun TokenCard(token: Token) {
    TokenFieldCard(label = "Access Token", value = token.accessToken)
    token.refreshToken?.let { TokenFieldCard(label = "Refresh Token", value = it) }
    token.idToken?.let { TokenFieldCard(label = "ID Token", value = it) }
    token.tokenType?.let { TokenFieldCard(label = "Token Type", value = it, truncate = false, copyable = false) }
    token.scope?.let { TokenFieldCard(label = "Scope", value = it, truncate = false, copyable = false) }
    ExpiryCountdownCard(token = token)
}

@Composable
private fun ExpiryCountdownCard(token: Token) {
    // Derive remaining seconds via the public isExpired(threshold) API:
    // isExpired(threshold) == true  →  now >= expireAt - threshold  →  threshold >= expireAt - now
    // So the smallest threshold where isExpired returns true equals the remaining seconds.
    fun remainingSeconds(): Long {
        if (token.isExpired) return 0L
        var lo = 0L
        var hi = token.expiresIn
        while (lo < hi) {
            val mid = (lo + hi) / 2
            if (token.isExpired(mid)) hi = mid else lo = mid + 1
        }
        return lo
    }

    val secondsLeft = remember(token) { remainingSeconds() }

    val expired = secondsLeft <= 0
    val days = secondsLeft / 86400
    val hours = (secondsLeft % 86400) / 3600
    val minutes = (secondsLeft % 3600) / 60
    val seconds = secondsLeft % 60
    val countdownText = if (expired) "Expired"
        else if (days > 0) "%dd %02d:%02d:%02d".format(days, hours, minutes, seconds)
        else "%02d:%02d:%02d".format(hours, minutes, seconds)
    val valueColor = if (expired) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary

    TokenFieldCard(
        label = "Expires In",
        value = countdownText,
        truncate = false,
        copyable = false,
        valueColor = valueColor,
    )
}

@Composable
private fun TokenFieldCard(
    label: String,
    value: String,
    truncate: Boolean = true,
    copyable: Boolean = true,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = valueColor,
                    maxLines = if (truncate) 2 else Int.MAX_VALUE,
                    overflow = if (truncate) TextOverflow.Ellipsis else TextOverflow.Clip,
                    modifier = Modifier.weight(1f),
                )
                if (copyable) {
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
                        },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy $label",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun EmptyCard(tab: TokenType) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Text(
            text = "No ${tab.name.lowercase().replaceFirstChar { it.uppercase() }} token available",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview
@Composable
fun PreviewToken() {
    TokenScreen()
}

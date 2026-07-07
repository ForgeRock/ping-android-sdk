/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.samples.pingsampleapp.authgrant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pingidentity.browser.BrowserLauncher
import com.pingidentity.samples.pingsampleapp.R
import java.net.URL

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApproveDeviceScreen(
    initialVerificationUri: String = "",
    onApproveWithDaVinci: (verificationUriComplete: String) -> Unit,
    onApproveWithJourney: (verificationUriComplete: String) -> Unit,
    onApproveWithBrowser: (verificationUriComplete: String) -> Unit,
    onBack: (() -> Unit)? = null,
) {
    var verificationUri by rememberSaveable { mutableStateOf(initialVerificationUri) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Approve Device") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        ApproveDeviceContent(
            initialVerificationUri = verificationUri,
            onVerificationUriChange = { verificationUri = it },
            onApproveWithDaVinci = onApproveWithDaVinci,
            onApproveWithJourney = onApproveWithJourney,
            onApproveWithBrowser = onApproveWithBrowser,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        )
    }
}

@Composable
internal fun ApproveDeviceContent(
    initialVerificationUri: String,
    onVerificationUriChange: (String) -> Unit,
    onApproveWithDaVinci: (verificationUriComplete: String) -> Unit,
    onApproveWithJourney: (verificationUriComplete: String) -> Unit,
    onApproveWithBrowser: (verificationUriComplete: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val primaryColor = colorResource(R.color.primary)
    var launchBrowser by rememberSaveable { mutableStateOf(false) }

    if (launchBrowser && initialVerificationUri.isNotBlank()) {
        LaunchedEffect(Unit) {
            BrowserLauncher.launch(
                url = URL(initialVerificationUri),
                redirectUri = BrowserLauncher.redirectUri,
            )
            launchBrowser = false
            onApproveWithBrowser(initialVerificationUri)
        }
    }

    Column(
        modifier = modifier
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        Icon(
            imageVector = Icons.Default.VerifiedUser,
            contentDescription = null,
            tint = primaryColor,
            modifier = Modifier.size(72.dp),
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Approve on This Device",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Paste the verification URL from another device (including the user_code) and tap Approve to authorize it here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(32.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "VERIFICATION URL",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        OutlinedTextField(
            value = initialVerificationUri,
            onValueChange = onVerificationUriChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            placeholder = {
                Text(
                    "https://...",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                )
            },
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = { onApproveWithDaVinci(initialVerificationUri) },
            enabled = initialVerificationUri.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
        ) {
            Icon(
                imageVector = Icons.Default.Key,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
            Text("Approve with DaVinci")
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = { onApproveWithJourney(initialVerificationUri) },
            enabled = initialVerificationUri.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
        ) {
            Icon(
                imageVector = Icons.Default.Map,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
            Text("Approve with Journey")
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = { launchBrowser = true },
            enabled = initialVerificationUri.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
        ) {
            Icon(
                imageVector = Icons.Default.Language,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
            Text("Approve with Browser")
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Preview(showBackground = true)
@Composable
fun ApproveDeviceScreenPreview() {
    ApproveDeviceScreen(
        initialVerificationUri = "https://auth.pingone.ca/02fb4743/device?user_code=ABCD-1234",
        onApproveWithDaVinci = {},
        onApproveWithJourney = {},
        onApproveWithBrowser = {},
        onBack = {},
    )
}

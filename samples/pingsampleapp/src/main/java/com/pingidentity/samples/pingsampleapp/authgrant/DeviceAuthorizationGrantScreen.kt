/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.samples.pingsampleapp.authgrant

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceAuthorizationGrantScreen(
    deviceAuthorizationGrantViewModel: DeviceAuthorizationGrantViewModel = viewModel(),
    onBack: (() -> Unit)? = null,
    onSuccess: (() -> Unit)? = null,
    onApproveWithDaVinci: ((verificationUri: String) -> Unit)? = null,
    onApproveWithJourney: ((verificationUri: String) -> Unit)? = null,
) {
    val uiState by deviceAuthorizationGrantViewModel.uiState.collectAsState()
    val currentOnSuccess by rememberUpdatedState(onSuccess)
    var showVerifySheet by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(uiState.isSuccess) {
        if (uiState.isSuccess && uiState.hasStarted) {
            currentOnSuccess?.invoke()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Device Authorization") },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StartCard(
                onStart = deviceAuthorizationGrantViewModel::startDeviceAuthorizationGrantFlow,
                onCancel = deviceAuthorizationGrantViewModel::cancelDeviceAuthorizationGrantFlow,
                isLoading = uiState.isLoading && !uiState.hasStarted,
                errorMessage = if (!uiState.hasStarted) uiState.errorMessage else "",
                hasStarted = uiState.hasStarted,
                userCode = uiState.userCode,
                verificationUri = uiState.verificationUri,
                statusMessage = uiState.statusMessage,
                codeErrorMessage = if (uiState.hasStarted) uiState.errorMessage else "",
                isPolling = uiState.isLoading && uiState.hasStarted,
            )

            VerifyCard(onVerify = { showVerifySheet = true })
        }
    }

    if (showVerifySheet) {
        var sheetVerificationUri by rememberSaveable { mutableStateOf(uiState.verificationUri) }
        ModalBottomSheet(
            onDismissRequest = { showVerifySheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            ApproveDeviceContent(
                initialVerificationUri = sheetVerificationUri,
                onVerificationUriChange = { sheetVerificationUri = it },
                onApproveWithDaVinci = { uri ->
                    onApproveWithDaVinci?.invoke(uri)
                },
                onApproveWithJourney = { uri ->
                    onApproveWithJourney?.invoke(uri)
                },
                onApproveWithBrowser = { showVerifySheet = false },
            )
        }
    }
}

@Composable
private fun StartCard(
    onStart: () -> Unit,
    onCancel: () -> Unit,
    isLoading: Boolean,
    errorMessage: String,
    hasStarted: Boolean,
    userCode: String,
    verificationUri: String,
    statusMessage: String,
    codeErrorMessage: String,
    isPolling: Boolean,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Default.PhoneAndroid,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Start",
                style = MaterialTheme.typography.titleLarge,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Initiate the Device Authorization flow to get a user code and verification URL.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onStart,
                    enabled = !isLoading && !hasStarted,
                    modifier = Modifier.weight(1f),
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text(if (hasStarted) "Started" else "Start")
                    }
                }

                if (hasStarted) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Cancel")
                    }
                }
            }

            if (errorMessage.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
            }

            if (hasStarted) {
                Spacer(modifier = Modifier.height(20.dp))
                DeviceAuthorizationCodeView(
                    userCode = userCode,
                    verificationUri = verificationUri,
                    statusMessage = statusMessage,
                    errorMessage = codeErrorMessage,
                    isLoading = isPolling,
                )
            }
        }
    }
}

@Composable
private fun VerifyCard(onVerify: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Default.VerifiedUser,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.secondary,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Verify",
                style = MaterialTheme.typography.titleLarge,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Perform verification via DaVinci, Journey, or Browser to complete the authorization.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = onVerify,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Verify")
            }
        }
    }
}

@Composable
private fun rememberQrCodeBitmap(content: String, sizePx: Int = 512): Bitmap? {
    var bitmap by remember(content, sizePx) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(content, sizePx) {
        bitmap = null
        if (content.isBlank()) return@LaunchedEffect
        bitmap = withContext(Dispatchers.Default) {
            runCatching {
                val hints = mapOf(EncodeHintType.MARGIN to 1)
                val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
                val bmp = createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565)
                for (x in 0 until sizePx) {
                    for (y in 0 until sizePx) {
                        bmp[x, y] = if (bitMatrix[x, y]) Color.BLACK else Color.WHITE
                    }
                }
                bmp
            }.getOrNull()
        }
    }
    return bitmap
}

@Composable
private fun CopyableValueRow(
    label: String,
    value: String,
    displayValue: String = value,
    displayType: DisplayType,
) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            if (displayType == DisplayType.CODE) {
                Text(
                    text = displayValue,
                    style = MaterialTheme.typography.displayMedium,
                    fontFamily = FontFamily.SansSerif,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Text(
                    text = displayValue,
                    style = MaterialTheme.typography.headlineSmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f),
                )
            }
            IconButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
                },
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy $label",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DeviceAuthorizationCodeView(
    userCode: String,
    verificationUri: String,
    statusMessage: String,
    errorMessage: String,
    isLoading: Boolean,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        val qrBitmap = rememberQrCodeBitmap(content = verificationUri)

        Text(
            text = "Activate Your Device",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Scan the QR code or visit the URL below and enter the code.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .size(220.dp)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(12.dp),
                )
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                )
                .padding(8.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (qrBitmap != null) {
                Image(
                    bitmap = qrBitmap.asImageBitmap(),
                    contentDescription = "QR Code for device authorization",
                    modifier = Modifier.size(200.dp),
                )
            } else if (verificationUri.isNotBlank()) {
                CircularProgressIndicator()
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        CopyableValueRow(
            label = "User Code",
            value = userCode.uppercase(),
            displayType = DisplayType.CODE,
        )

        Spacer(modifier = Modifier.height(16.dp))

        CopyableValueRow(
            label = "Verification URL",
            value = verificationUri,
            displayValue = verificationUri,
            displayType = DisplayType.URL,
        )

        if (statusMessage.isNotBlank()) {
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = statusMessage,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (errorMessage.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (isLoading) {
            Spacer(modifier = Modifier.height(16.dp))
            CircularProgressIndicator()
        }
    }
}

private enum class DisplayType {
    CODE,
    URL
}

@Preview
@Composable
fun PreviewDeviceAuthorizationGrantScreen() {
    DeviceAuthorizationGrantScreen()
}
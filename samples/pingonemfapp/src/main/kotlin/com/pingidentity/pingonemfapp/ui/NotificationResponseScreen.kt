/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.pingonemfapp.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AlarmOn
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pingidentity.pingonemfa.push.PushNotification
import com.pingidentity.pingonemfapp.R
import com.pingidentity.pingonemfapp.ui.components.AccountAvatar

/**
 * Unified screen for displaying push notification details.
 * Handles both standard authentication and challenge-based notifications.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationResponseScreen(
    notificationItem: PushNotification,
    onDismiss: () -> Unit,
    onApprove: (() -> Unit)? = null,
    onBiometricApprove: (() -> Unit)? = null,
    onDeny: (() -> Unit)? = null,
    onChallengeSolution: ((String) -> Unit)? = null
) {

    val isChallenge = notificationItem.isChallenge()
    val challengeNumbers = if (isChallenge) notificationItem.getNumbersChallenge()?.toList() else emptyList()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.notification_response_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.back)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = if (isChallenge) Alignment.CenterHorizontally else Alignment.Start
        ) {
            // Header with issuer, account, and location map
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AccountAvatar(
                            issuer = notificationItem.title ?:stringResource(id = R.string.notification_response_unknown_issuer),
                            accountName = notificationItem.message ?: stringResource(id = R.string.notification_response_unknown_account),
                            imageUrl = null
                                //notificationItem.credential?.imageURL,
                            //size = 36.dp
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            val issuer = notificationItem.title ?:stringResource(id = R.string.notification_response_unknown_issuer)
                            val accountName = notificationItem.message ?: stringResource(id = R.string.notification_response_unknown_account)

                            Text(
                                text = issuer,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = accountName,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }

                    // Divider
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 16.dp),
                        thickness = DividerDefaults.Thickness,
                        color = DividerDefaults.color
                    )

                    // Message
                    Text(
                        text = //notificationItem.messageText ?:
                            if (isChallenge) stringResource(id = R.string.notification_response_message_verify) else stringResource(id = R.string.notification_response_message_default),
                        style = MaterialTheme.typography.bodyLarge,
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Time sent
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Alarm,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = notificationItem.sentAt.toString(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Response time
                    notificationItem.respondedAt?.let { respondedAt ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AlarmOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = respondedAt.toString(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Authentication method
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val (icon, text) = when {
                            notificationItem.requiresBiometric() -> Pair(
                                Icons.Outlined.Fingerprint,
                                stringResource(id = R.string.notification_response_auth_method_biometric)
                            )
                            notificationItem.isChallenge() -> Pair(
                                Icons.Default.Pin,
                                stringResource(id = R.string.notification_response_auth_method_challenge)
                            )
                            else -> Pair(
                                Icons.Default.CheckCircle,
                                stringResource(id = R.string.notification_response_auth_method_standard)
                            )
                        }
                        Icon(
                            icon,
                            text,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Action buttons based on type
            if (isChallenge) {
                // Challenge selection UI
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = stringResource(id = R.string.notification_response_challenge_prompt),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    if (challengeNumbers!=null && challengeNumbers.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            challengeNumbers.forEach { number ->
                                ChallengeNumberButton(
                                    number = number,
                                    onClick = { onChallengeSolution?.invoke(number.toString()) }
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth(0.7f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                        ) {
                            Text(stringResource(id = R.string.notification_response_cancel_authentication))
                        }
                    } else {
                        Text(
                            text = stringResource(id = R.string.notification_response_no_challenge_numbers),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth(0.7f)
                        ) {
                            Text(stringResource(id = R.string.close))
                        }
                    }
                }
            //} else if (notificationItem.credential?.isLocked == true) {
//                // Show lock message for locked credentials
//                Column(
//                    modifier = Modifier.fillMaxWidth(),
//                    horizontalAlignment = Alignment.CenterHorizontally
//                ) {
//                    Spacer(modifier = Modifier.height(16.dp))
//
//                    Row(
//                        modifier = Modifier
//                            .fillMaxWidth(0.9f)
//                            .background(
//                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
//                                shape = RoundedCornerShape(8.dp)
//                            )
//                            .padding(16.dp),
//                        verticalAlignment = Alignment.CenterVertically
//                    ) {
//                        Icon(
//                            imageVector = Icons.Default.Lock,
//                            contentDescription = stringResource(id = R.string.account_locked_indicator),
//                            tint = MaterialTheme.colorScheme.error,
//                            modifier = Modifier.size(20.dp)
//                        )
//                        Spacer(modifier = Modifier.width(12.dp))
//                        val lockMessage = when (notificationItem.credential?.lockingPolicy?.lowercase()) {
//                            BiometricAvailablePolicy.POLICY_NAME -> stringResource(id = R.string.account_locked_biometric_available)
//                            DeviceTamperingPolicy.POLICY_NAME -> stringResource(id = R.string.account_locked_device_tampering)
//                            null -> stringResource(id = R.string.account_locked_unknown_policy)
//                            else -> stringResource(id = R.string.account_locked_generic_policy, notificationItem.credential?.lockingPolicy!!)
//                        }
//                        Text(
//                            text = lockMessage,
//                            style = MaterialTheme.typography.bodyMedium,
//                            color = MaterialTheme.colorScheme.error
//                        )
//                    }
//
//                    Spacer(modifier = Modifier.height(16.dp))
//
//                    Button(
//                        onClick = onDismiss,
//                        modifier = Modifier.fillMaxWidth(0.7f)
//                    ) {
//                        Text(stringResource(id = R.string.close))
//                    }
//                }
            } else{
                // Standard approve/deny buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Button(
                        onClick = {
                            onDeny?.invoke()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(id = R.string.deny)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = stringResource(id = R.string.deny))
                    }

                    Button(
                        onClick = {
                            when {
                                onBiometricApprove != null && notificationItem.requiresBiometric() -> {
                                    onBiometricApprove()
                                }
                                onApprove != null -> {
                                    onApprove()
                                }
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 8.dp)
                    ) {
                        Icon(
                            imageVector = if (notificationItem.requiresBiometric())
                                Icons.Outlined.Fingerprint else Icons.Outlined.Check,
                            contentDescription = stringResource(id = R.string.approve)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = if (notificationItem.requiresBiometric()) stringResource(id = R.string.verify) else stringResource(id = R.string.approve))
                    }
                }
            }
        }
    }
}

/**
 * A button displaying a challenge number.
 */
@Composable
private fun ChallengeNumberButton(
    number: Int,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.size(80.dp),
        shape = CircleShape,
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.primary,
            containerColor = Color.Transparent
        )
    ) {
        Text(
            text = number.toString(),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
    }
}


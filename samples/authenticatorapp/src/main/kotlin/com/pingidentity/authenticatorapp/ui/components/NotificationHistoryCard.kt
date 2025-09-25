/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.authenticatorapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pingidentity.authenticatorapp.data.NotificationStatus
import com.pingidentity.authenticatorapp.data.PushNotificationItem

/**
 * A card that displays a summary of a push notification, including issuer, account name,
 * message, status, time ago, and indicators for biometric/challenge authentication and location info.
 *
 * @param notificationItem The push notification item to display.
 * @param onNotificationClick Callback invoked when the card is clicked.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationHistoryCard(
    notificationItem: PushNotificationItem,
    onNotificationClick: () -> Unit
) {
    Card(
        onClick = onNotificationClick,
        modifier = Modifier.Companion
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.Companion
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Status indicator and issuer header
            Row(
                verticalAlignment = Alignment.Companion.CenterVertically,
                modifier = Modifier.Companion.fillMaxWidth()
            ) {
                AccountAvatar(
                    issuer = notificationItem.credential?.displayIssuer ?: "Unknown Issuer",
                    accountName = notificationItem.credential?.displayAccountName
                        ?: "Unknown Account",
                    imageUrl = notificationItem.credential?.imageURL,
                    size = 32.dp
                )

                Spacer(modifier = Modifier.Companion.width(8.dp))

                // Issuer and account name
                Column(modifier = Modifier.Companion.weight(1f)) {
                    val issuer = notificationItem.credential?.displayIssuer ?: "Unknown Issuer"
                    val accountName =
                        notificationItem.credential?.displayAccountName ?: "Unknown Account"

                    Text(
                        text = issuer,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Companion.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Companion.Ellipsis
                    )
                    Text(
                        text = accountName,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Companion.Ellipsis
                    )
                }

                // Status indicator
                StatusIndicator(status = notificationItem.status)
            }

            Spacer(modifier = Modifier.Companion.height(8.dp))

            // Message
            Text(
                text = notificationItem.notification.messageText ?: "Authentication request",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Companion.Ellipsis
            )

            Spacer(modifier = Modifier.Companion.height(4.dp))

            // Row for time ago and location indicator
            Row(
                verticalAlignment = Alignment.Companion.CenterVertically,
                modifier = Modifier.Companion.padding(top = 4.dp)
            ) {
                // Created time
                Row(
                    verticalAlignment = Alignment.Companion.CenterVertically,
                    modifier = Modifier.Companion.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.AccessTime,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.Companion.size(16.dp)
                    )
                    Spacer(modifier = Modifier.Companion.width(4.dp))
                    Text(
                        text = notificationItem.notification.createdAt.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Authentication type indicators
                Row(
                    verticalAlignment = Alignment.Companion.CenterVertically,
                    modifier = Modifier.Companion.padding(start = 8.dp)
                ) {
                    val (icon, text) = when {
                        notificationItem.requiresBiometric -> Pair(
                            Icons.Outlined.Fingerprint,
                            "Biometric authentication"
                        )

                        notificationItem.requiresChallenge -> Pair(
                            Icons.Default.Pin,
                            "Challenge authentication"
                        )

                        else -> Pair(
                            Icons.Default.CheckCircle,
                            "Standard authentication"
                        )
                    }
                    Icon(
                        icon,
                        text,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.Companion.size(16.dp)
                    )
                }

                // Location indicator if available
                if (notificationItem.hasLocationInfo) {
                    Row(
                        verticalAlignment = Alignment.Companion.CenterVertically,
                        modifier = Modifier.Companion.padding(start = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = "Location information available",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.Companion.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Status indicator for push notifications.
 */
@Composable
fun StatusIndicator(status: NotificationStatus) {
    val (icon, color, label) = when (status) {
        NotificationStatus.PENDING -> Triple(
            Icons.Default.AccessTime,
            MaterialTheme.colorScheme.tertiary,
            "Pending"
        )
        NotificationStatus.APPROVED -> Triple(
            Icons.Default.CheckCircle,
            MaterialTheme.colorScheme.primary,
            "Approved"
        )
        NotificationStatus.DENIED -> Triple(
            Icons.Outlined.Close,
            MaterialTheme.colorScheme.error,
            "Denied"
        )
        NotificationStatus.EXPIRED -> Triple(
            Icons.Default.Error,
            MaterialTheme.colorScheme.onSurfaceVariant,
            "Expired"
        )
    }

    Box(
        contentAlignment = Alignment.Companion.Center,
        modifier = Modifier.Companion
            .size(32.dp)
            .background(color = color.copy(alpha = 0.1f), shape = CircleShape)
            .padding(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = color,
            modifier = Modifier.Companion.size(16.dp)
        )
    }
}
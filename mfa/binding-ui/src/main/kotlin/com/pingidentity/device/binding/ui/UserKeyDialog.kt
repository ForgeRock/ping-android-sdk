/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.binding.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * A Jetpack Compose dialog for selecting a user key from multiple registered device bindings.
 *
 * This composable displays a Material Design 3 modal dialog that allows users to select
 * one of their registered device binding keys when multiple keys exist. The dialog includes:
 * - A title indicating the purpose of the selection
 * - An exposed dropdown menu showing all available user keys
 * - Cancel and Confirm action buttons
 * - Full Material Theme integration
 *
 * ## UI Components
 *
 * ### Title
 * - Displayed as "Select User Key" in headlineSmall typography
 * - Provides context for the selection action
 *
 * ### User Key Dropdown (ExposedDropdownMenuBox)
 * - **Display Format**: Shows user keys as "{username} ({authenticationType})"
 *   - Example: "john.doe@example.com (BIOMETRIC_ONLY)"
 *   - Example: "alice@example.com (APPLICATION_PIN)"
 * - **Behavior**:
 *   - Read-only text field that opens a dropdown menu on click
 *   - Menu displays all available user keys
 *   - Selected key is displayed in the text field
 * - **Styling**: Outlined text field with Material 3 theming
 *
 * ### Action Buttons
 * - **Cancel**: TextButton that dismisses the dialog without selecting a key
 * - **Confirm**: Primary Button that submits the selected user key
 *   - Disabled when no key is selected
 *   - Enabled only after a user selects a key from the dropdown
 *
 * ## Dialog Behavior
 *
 * - **Dismissal**: Can be dismissed by pressing the back button (triggers cancellation callback)
 * - **Click Outside**: Clicking outside the dialog does NOT dismiss it (dismissOnClickOutside = false)
 * - **Confirmation**: Clicking Confirm only works when a user key is selected
 * - **Selection State**: Maintains the selected key until user confirms or cancels
 *
 * ## Use Cases
 *
 * ### Multiple Users on Shared Device
 * When multiple users have registered their credentials on a shared device:
 * ```
 * User Keys:
 * - alice@example.com (BIOMETRIC_ONLY)
 * - bob@example.com (APPLICATION_PIN)
 * - charlie@example.com (BIOMETRIC_OR_DEVICE_CREDENTIAL)
 * ```
 *
 * @param userKeys The list of available [UserKeyOption] objects to display for selection.
 *                 Each option represents a registered device binding with associated metadata.
 *                 The dropdown will show all options with their username and authentication type.
 * @param onUserKeySelected Callback invoked when the user confirms their selection.
 *                          Receives the selected [UserKeyOption] object. The caller should
 *                          use this to proceed with authentication using the selected key.
 * @param onCancelled Callback invoked when the user cancels the selection by pressing
 *                    Cancel, back button, or dismissing the dialog. No user key is provided.
 *
 * @see UserKeyOption
 * @see collectUserKey
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserKeyDialog(
    userKeys: List<UserKeyOption>,
    onUserKeySelected: (UserKeyOption) -> Unit,
    onCancelled: () -> Unit
) {
    var selectedUserKey by remember { mutableStateOf<UserKeyOption?>(null) }
    var expanded by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onCancelled,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Title
                Text(
                    text = "Select User Key",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // User Key Dropdown
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = selectedUserKey?.let { "${it.username} (${it.authenticationType})" } ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = {
                            Text(
                                text = "Select User Key",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable, true),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        ),
                        shape = MaterialTheme.shapes.medium
                    )

                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        userKeys.forEach { userKey ->
                            DropdownMenuItem(
                                text = { Text("${userKey.username} (${userKey.authenticationType})") },
                                onClick = {
                                    selectedUserKey = userKey
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Cancel Button
                    TextButton(
                        onClick = onCancelled,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("Cancel")
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Confirm Button
                    Button(
                        onClick = {
                            selectedUserKey?.let { onUserKeySelected(it) }
                        },
                        enabled = selectedUserKey != null,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text("Confirm")
                    }
                }
            }
        }
    }
}

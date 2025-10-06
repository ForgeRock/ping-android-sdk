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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * A Jetpack Compose dialog for collecting an application PIN from the user.
 *
 * This composable displays a Material Design 3 modal dialog that prompts the user to enter
 * their application PIN. The dialog includes:
 * - Configurable title, subtitle, and description text
 * - A password input field with show/hide toggle
 * - Cancel and Confirm action buttons
 * - Full Material Theme integration
 *
 * ## UI Components
 *
 * ### Text Display
 * - **Title**: Displayed in headlineSmall typography (optional, shown if not empty)
 * - **Subtitle**: Displayed in bodyLarge typography (optional, shown if not empty)
 * - **Description**: Displayed in bodyMedium typography (optional, shown if not empty)
 *
 * ### PIN Input Field
 * - Accepts numeric input via NumberPassword keyboard type
 * - Obscures PIN by default using PasswordVisualTransformation
 * - Includes a visibility toggle icon button to show/hide the PIN
 * - Single-line input with Material 3 outlined text field styling
 *
 * ### Action Buttons
 * - **Cancel**: TextButton that dismisses the dialog without entering a PIN
 * - **Confirm**: Primary Button that submits the entered PIN (disabled when PIN is empty)
 *
 * ## Dialog Behavior
 *
 * - **Dismissal**: Can be dismissed by pressing the back button (triggers cancellation callback)
 * - **Click Outside**: Clicking outside the dialog does NOT dismiss it (dismissOnClickOutside = false)
 * - **Confirmation**: Clicking Confirm only works when a non-empty PIN is entered
 *
 * This ensures the dialog adapts to light/dark themes and custom color schemes
 * defined in the application.
 *
 * @param prompt A [PinPrompt] object containing the title, subtitle, and description
 *               text to display in the dialog. Empty strings for any field will cause
 *               that component to not be displayed.
 * @param onPinEntered Callback invoked when the user confirms their PIN entry.
 *                     Receives the entered PIN as a String. The caller should convert
 *                     this to a CharArray and clear it from memory after use for security.
 * @param onCancelled Callback invoked when the user cancels the dialog by pressing
 *                    Cancel, back button, or dismissing the dialog. No PIN is provided.
 *
 * @see PinPrompt
 * @see collectPin
 */
@Composable
fun PinCollectorDialog(
    prompt: PinPrompt,
    onPinEntered: (String) -> Unit,
    onCancelled: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var showPin by remember { mutableStateOf(false) }

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
                if (prompt.title.isNotEmpty()) {
                    Text(
                        text = prompt.title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Subtitle
                if (prompt.subtitle.isNotEmpty()) {
                    Text(
                        text = prompt.subtitle,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Description
                if (prompt.description.isNotEmpty()) {
                    Text(
                        text = prompt.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // PIN Input Field
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it },
                    label = {
                        Text(
                            text = "Enter PIN",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (showPin) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    ),
                    trailingIcon = {
                        IconButton(onClick = { showPin = !showPin }) {
                            Icon(
                                imageVector = if (showPin) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                contentDescription = if (showPin) "Hide PIN" else "Show PIN",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium
                )

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
                            if (pin.isNotEmpty()) {
                                onPinEntered(pin)
                            }
                        },
                        enabled = pin.isNotEmpty(),
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
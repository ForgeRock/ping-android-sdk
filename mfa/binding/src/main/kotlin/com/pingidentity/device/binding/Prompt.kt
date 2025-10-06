/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.binding

/**
 * Represents user interface prompt information for authentication dialogs and user interactions.
 *
 * This data class encapsulates the textual content displayed to users during authentication
 * flows, providing consistent messaging across different authentication methods such as
 * biometric authentication, PIN entry, and device binding operations.
 *
 * The prompt information is used to:
 * - Configure BiometricPrompt dialogs with appropriate context
 * - Display meaningful messages during PIN collection
 * - Provide user guidance during device binding and signing operations
 * - Ensure consistent user experience across authentication flows
 *
 * Example usage:
 * ```kotlin
 * val prompt = Prompt(
 *     title = "Authenticate for Device Binding",
 *     subtitle = "Secure your account",
 *     description = "Use your fingerprint to complete device registration"
 * )
 * ```
 *
 * @see androidx.biometric.BiometricPrompt.PromptInfo
 * @see DeviceBindingCallback
 * @see DeviceSigningVerifierCallback
 *
 * @param title The primary title displayed prominently in authentication dialogs.
 *              Should be concise and clearly indicate the purpose of the authentication.
 *              Defaults to an empty string if not specified.
 *
 * @param subtitle The secondary text displayed below the title in authentication dialogs.
 *                 Provides additional context about the authentication request.
 *                 Defaults to an empty string if not specified.
 *
 * @param description The detailed explanatory text shown in authentication dialogs.
 *                    Should explain why authentication is required and what will happen.
 *                    This property is mutable to allow dynamic updates based on context.
 *                    Defaults to an empty string if not specified.
 */
data class Prompt(val title: String = "", val subtitle: String = "", var description: String = "")
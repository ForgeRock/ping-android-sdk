/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.pingonemfapp.notification

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.pingidentity.pingonemfa.push.PushNotification
import com.pingidentity.pingonemfapp.R
import com.pingidentity.pingonemfapp.data.DiagnosticLogger
import com.pingidentity.pingonemfapp.ui.theme.PingIdentityAuthenticatorTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Activity to handle biometric authentication for push notifications.
 * Shows a biometric prompt and approves/denies the notification based on the result.
 */
class BiometricPromptActivity : AppCompatActivity() {
    
    private val diagnosticLogger = DiagnosticLogger

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Get notification object from intent
        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(NotificationActionReceiver.EXTRA_NOTIFICATION, PushNotification::class.java)
        } else {
            @Suppress("DEPRECATION") // Suppress deprecation warning for backward compatibility
            intent?.getParcelableExtra(NotificationActionReceiver.EXTRA_NOTIFICATION)
        }
        // If no notification, log and finish
        if (notification == null) {
            diagnosticLogger.w("No notification provided")
            finish()
            return
        }
        
        setContent {
            val context = LocalContext.current
            val coroutineScope = rememberCoroutineScope()
            var isLoading by remember { mutableStateOf(true) }
            var errorMessage by remember { mutableStateOf<String?>(null) }
            var failureMessage by remember { mutableStateOf<String?>(null) }
            
            // Initialize and handle biometric authentication
            LaunchedEffect(Unit) {
                try {

                    // Check if biometric authentication is available
                    val biometricManager = BiometricManager.from(context)
                    when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
                        BiometricManager.BIOMETRIC_SUCCESS -> {
                            isLoading = false
                            showBiometricPrompt(notification, coroutineScope) { message ->
                                failureMessage = message
                            }
                        }
                        else -> {
                            diagnosticLogger.w("Biometric authentication not available")
                            errorMessage = context.getString(R.string.biometric_authentication_unavailable)
                            isLoading = false
                            finish()
                        }
                    }
                } catch (e: Exception) {
                    diagnosticLogger.e("Failed to initialize PushClient: ${e.message}", e)
                    errorMessage = context.getString(R.string.biometric_initialize_failed)
                    isLoading = false
                    finish()
                }
            }
            
            PingIdentityAuthenticatorTheme {
                Surface {
                    when {
                        isLoading -> {
                            // Show loading indicator
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                        errorMessage != null -> {
                            // Show error message
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = errorMessage!!)
                            }
                        }
                        failureMessage != null -> {
                            // Show failure message
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = failureMessage!!)
                            }
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Shows the biometric prompt on the main thread.
     */
    private fun showBiometricPrompt(
        notification: PushNotification?,
        coroutineScope: CoroutineScope,
        onFailure: (String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(this)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                coroutineScope.launch {
                    try {
                        // Approve the notification with biometric authentication
                        val authMethod = getBiometricMethodName()
                        approveBiometricNotification(notification, authMethod)
                        finish()
                    } catch (e: Exception) {
                        diagnosticLogger.e("Failed to process approval: ${e.message}", e)
                        onFailure(getString(R.string.biometric_approve_failed, e.message))
                    }
                }
            }
            
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                diagnosticLogger.w("Authentication error: $errString")
                
                // Show error message for non-cancellation errors
                if (errorCode != BiometricPrompt.ERROR_USER_CANCELED && 
                    errorCode != BiometricPrompt.ERROR_CANCELED &&
                    errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                    onFailure(getString(R.string.biometric_authentication_error, errString))
                } else {
                    finish()
                }
            }
            
            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                diagnosticLogger.w("Authentication failed")
                onFailure(getString(R.string.biometric_authentication_failed))
            }
        }
        
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.system_notification_authenticate))
            .setSubtitle(getString(R.string.biometric_prompt_subtitle))
            .setNegativeButtonText(getString(R.string.login_cancel))
            .setConfirmationRequired(true)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()
        
        val biometricPrompt = BiometricPrompt(this, executor, callback)
        biometricPrompt.authenticate(promptInfo)
    }
    
    /**
     * Determines the biometric method name from the authentication result.
     * Note: Android's BiometricPrompt API doesn't directly expose which method was used.
     * This implementation checks device capabilities to make an educated guess.
     */
    private fun getBiometricMethodName(): String {
        // Check device features to determine likely biometric method
        val packageManager = packageManager
        
        val hasFingerprint = packageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)
        val hasFace = packageManager.hasSystemFeature(PackageManager.FEATURE_FACE)
        val hasIris = packageManager.hasSystemFeature(PackageManager.FEATURE_IRIS)
        
        return when {
            // If only one type is available, likely that was used
            hasFingerprint && !hasFace && !hasIris -> "fingerprint"
            hasFace && !hasFingerprint && !hasIris -> "face"
            hasIris && !hasFingerprint && !hasFace -> "iris"
            
            // If multiple are available, fingerprint is most common default
            hasFingerprint -> "fingerprint"
            hasFace -> "face"
            
            // Fallback for unknown or generic biometric
            else -> "biometric"
        }
    }
    
    /**
     * Approves the notification with biometric authentication.
     */
    private suspend fun approveBiometricNotification(notification: PushNotification?, authMethod: String) {
        val result = notification?.approveNotification(
            this,
            authMethod
        )
        when {
            result?.isSuccess == true -> {
                finish()
            }
            result?.isFailure == true -> {
                diagnosticLogger.e("Error approving with challenge: ${result.exceptionOrNull()?.stackTrace}")
            }
        }
    }

}

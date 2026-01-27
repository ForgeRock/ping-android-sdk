/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.pingonemfapp.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/**
 * Theme modes for the app
 */
enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM
}

/**
 * Manages user preferences for the Authenticator app using SharedPreferences.
 */
class UserPreferences(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )
    
    // StateFlows for all settings
    private val _copyOtpFlow = MutableStateFlow(isCopyOtpEnabled())
    val copyOtpFlow: StateFlow<Boolean> = _copyOtpFlow
    
    private val _tapToRevealFlow = MutableStateFlow(isTapToRevealEnabled())
    val tapToRevealFlow: StateFlow<Boolean> = _tapToRevealFlow

    private val _diagnosticLoggingFlow = MutableStateFlow(isDiagnosticLoggingEnabled())
    val diagnosticLoggingFlow: StateFlow<Boolean> = _diagnosticLoggingFlow
    
    private val _testModeFlow = MutableStateFlow(isTestModeEnabled())
    val testModeFlow: StateFlow<Boolean> = _testModeFlow
    
    private val _themeModeFlow = MutableStateFlow(getThemeMode())
    val themeModeFlow: StateFlow<ThemeMode> = _themeModeFlow

    /**
     * Check if copy OTP on tap is enabled.
     * Defaults to false if not set.
     */
    fun isCopyOtpEnabled(): Boolean {
        return prefs.getBoolean(KEY_COPY_OTP, false)
    }
    
    /**
     * Set whether copy OTP on tap is enabled.
     */
    suspend fun setCopyOtp(enabled: Boolean) {
        withContext(Dispatchers.IO) {
            prefs.edit {
                putBoolean(KEY_COPY_OTP, enabled)
            }
            _copyOtpFlow.value = enabled
        }
    }
    
    /**
     * Check if tap to reveal is enabled.
     * Defaults to false if not set.
     */
    fun isTapToRevealEnabled(): Boolean {
        return prefs.getBoolean(KEY_TAP_TO_REVEAL, false)
    }
    
    /**
     * Set whether tap to reveal is enabled.
     */
    suspend fun setTapToReveal(enabled: Boolean) {
        withContext(Dispatchers.IO) {
            prefs.edit {
                putBoolean(KEY_TAP_TO_REVEAL, enabled)
            }
            _tapToRevealFlow.value = enabled
        }
    }
    
    /**
     * Check if accounts should be combined.
     * Defaults to false if not set.
     */
    fun isCombineAccountsEnabled(): Boolean {
        return prefs.getBoolean(KEY_COMBINE_ACCOUNTS, false)
    }

    /**
     * Check if diagnostic logging is enabled.
     * Defaults to false if not set.
     */
    fun isDiagnosticLoggingEnabled(): Boolean {
        return prefs.getBoolean(KEY_DIAGNOSTIC_LOGGING, false)
    }
    
    /**
     * Set whether diagnostic logging is enabled.
     */
    suspend fun setDiagnosticLogging(enabled: Boolean) {
        withContext(Dispatchers.IO) {
            prefs.edit {
                putBoolean(KEY_DIAGNOSTIC_LOGGING, enabled)
            }
            _diagnosticLoggingFlow.value = enabled
        }
    }
    
    /**
     * Check if test mode is enabled.
     * Defaults to false if not set.
     */
    fun isTestModeEnabled(): Boolean {
        return prefs.getBoolean(KEY_TEST_MODE, false)
    }

    /**
     * Get the current theme mode.
     * Defaults to SYSTEM if not set.
     */
    fun getThemeMode(): ThemeMode {
        val themeName = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
        return try {
            ThemeMode.valueOf(themeName)
        } catch (e: IllegalArgumentException) {
            ThemeMode.SYSTEM
        }
    }
    
    /**
     * Set the theme mode.
     */
    suspend fun setThemeMode(themeMode: ThemeMode) {
        withContext(Dispatchers.IO) {
            prefs.edit {
                putString(KEY_THEME_MODE, themeMode.name)
            }
            _themeModeFlow.value = themeMode
        }
    }

    companion object {
        private const val PREFS_NAME = "authenticator_preferences"
        private const val KEY_COPY_OTP = "copy_otp"
        private const val KEY_TAP_TO_REVEAL = "tap_to_reveal"
        private const val KEY_COMBINE_ACCOUNTS = "combine_accounts"
        private const val KEY_DIAGNOSTIC_LOGGING = "diagnostic_logging"
        private const val KEY_TEST_MODE = "test_mode"
        private const val KEY_THEME_MODE = "theme_mode"
    }
}

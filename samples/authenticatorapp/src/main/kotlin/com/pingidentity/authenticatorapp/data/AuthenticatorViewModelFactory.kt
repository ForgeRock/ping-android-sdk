/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.authenticatorapp.data

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.pingidentity.authenticatorapp.managers.AccountGroupingManager
import com.pingidentity.authenticatorapp.managers.OathManager
import com.pingidentity.authenticatorapp.managers.PushManager
import com.pingidentity.authenticatorapp.managers.TestAccountFactory

/**
 * Factory for creating AuthenticatorViewModel with injected dependencies.
 * This follows the MVVM pattern by allowing dependency injection.
 *
 * @param application The application context
 * @param userPreferences Injected UserPreferences dependency
 */
class AuthenticatorViewModelFactory(
    private val application: Application,
    private val userPreferences: UserPreferences
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AuthenticatorViewModel::class.java)) {
            val diagnosticLogger = DiagnosticLogger
            
            // Create manager instances - the clients will be initialized inside the ViewModel
            val oathManager = OathManager(diagnosticLogger = diagnosticLogger)
            val pushManager = PushManager(diagnosticLogger = diagnosticLogger)
            val accountGroupingManager = AccountGroupingManager(userPreferences, diagnosticLogger)
            val testAccountFactory = TestAccountFactory()
            
            return AuthenticatorViewModel(
                application, 
                userPreferences, 
                oathManager, 
                pushManager, 
                accountGroupingManager, 
                testAccountFactory
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
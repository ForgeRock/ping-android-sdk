/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.authenticatorapp.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pingidentity.authenticatorapp.data.AuthenticatorViewModel
import com.pingidentity.authenticatorapp.util.NavigationAnimations

/**
 * Main entry point for the app.
 */
@Preview
@Composable
fun AuthenticatorApp(
    viewModel: AuthenticatorViewModel = viewModel()
) {
    val navController = rememberNavController()
    
    NavHost(navController = navController, startDestination = "accounts") {
        composable("accounts") {
            AccountsScreen(
                viewModel = viewModel,
                onScanQrCode = { navController.navigate("scanner") },
                onAddManually = { navController.navigate("manual-entry") },
                onAccountClick = { accountId -> navController.navigate("account/$accountId") }
            )
        }
        
        composable(
            route = "scanner",
            enterTransition = NavigationAnimations.enterTransition,
            exitTransition = NavigationAnimations.exitTransition,
            popEnterTransition = NavigationAnimations.popEnterTransition,
            popExitTransition = NavigationAnimations.popExitTransition
        ) {
            QrScannerScreen(
                viewModel = viewModel,
                onScanComplete = { navController.popBackStack() },
                onDismiss = { navController.popBackStack() }
            )
        }
        
        composable(
            route = "manual-entry",
            enterTransition = NavigationAnimations.enterTransition,
            exitTransition = NavigationAnimations.exitTransition,
            popEnterTransition = NavigationAnimations.popEnterTransition,
            popExitTransition = NavigationAnimations.popExitTransition
        ) {
            ManualEntryScreen(
                viewModel = viewModel,
                onEntryComplete = { navController.popBackStack() },
                onDismiss = { navController.popBackStack() }
            )
        }
        
        composable(
            route = "account/{accountId}",
            enterTransition = NavigationAnimations.enterTransition,
            exitTransition = NavigationAnimations.exitTransition,
            popEnterTransition = NavigationAnimations.popEnterTransition,
            popExitTransition = NavigationAnimations.popExitTransition
        ) { backStackEntry ->
            val accountId = backStackEntry.arguments?.getString("accountId") ?: ""
            AccountDetailScreen(
                accountId = accountId,
                viewModel = viewModel,
                onDismiss = { navController.popBackStack() }
            )
        }
    }
}

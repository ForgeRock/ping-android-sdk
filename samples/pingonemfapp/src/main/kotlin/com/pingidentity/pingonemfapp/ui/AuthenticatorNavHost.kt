/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.pingonemfapp.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pingidentity.pingonemfapp.data.PingOneMFAViewModel
import com.pingidentity.pingonemfapp.util.NavigationAnimations

/**
 * Main entry point for the app.
 */
@Composable
fun AuthenticatorNavHost(
    authenticatorViewModel: PingOneMFAViewModel = viewModel(),
    initialDestination: String = "accounts"
) {
    // Create the NavController
    val navController = rememberNavController()

    // Define the navigation
    NavHost(navController = navController, startDestination = initialDestination) {

        // Main accounts list screen
        composable("accounts") {
            AccountsScreen(
                viewModel = authenticatorViewModel,
                onScanQrCode = { navController.navigate("scanner") },
                onAccountClick = {navController.navigate("otp") },
                onSettingsClick = { navController.navigate("settings") },
                onAboutClick = { navController.navigate("about") }
            )
        }

        // QR code scanner screen
        composable(
            route = "scanner",
            enterTransition = NavigationAnimations.enterTransition,
            exitTransition = NavigationAnimations.exitTransition,
            popEnterTransition = NavigationAnimations.popEnterTransition,
            popExitTransition = NavigationAnimations.popExitTransition
        ) {
            QrScannerScreen(
                viewModel = authenticatorViewModel,
                onScanComplete = {
                    navController.popBackStack() },
                onDismiss = { navController.popBackStack() }
            )
        }

        // OTP screen
        composable(
            route = "otp",
            enterTransition = NavigationAnimations.enterTransition,
            exitTransition = NavigationAnimations.exitTransition,
            popEnterTransition = NavigationAnimations.popEnterTransition,
            popExitTransition = NavigationAnimations.popExitTransition
        ) {
            OtpScreen(
                viewModel = authenticatorViewModel,
                onDismiss = { navController.popBackStack() }
            )
        }
        
        // Settings screen
        composable(
            route = "settings",
            enterTransition = NavigationAnimations.enterTransition,
            exitTransition = NavigationAnimations.exitTransition,
            popEnterTransition = NavigationAnimations.popEnterTransition,
            popExitTransition = NavigationAnimations.popExitTransition
        ) {
            SettingsScreen(
                viewModel = authenticatorViewModel,
                onDismiss = { navController.popBackStack() },
                onDiagnosticLogsClick = { navController.navigate("diagnostic-logs") }
            )
        }
        
        // Diagnostic logs screen
        composable(
            route = "diagnostic-logs",
            enterTransition = NavigationAnimations.enterTransition,
            exitTransition = NavigationAnimations.exitTransition,
            popEnterTransition = NavigationAnimations.popEnterTransition,
            popExitTransition = NavigationAnimations.popExitTransition
        ) {
            DiagnosticLogsScreen(
                onDismiss = { navController.popBackStack() }
            )
        }

        // About screen
        composable(
            route = "about",
            enterTransition = NavigationAnimations.enterTransition,
            exitTransition = NavigationAnimations.exitTransition,
            popEnterTransition = NavigationAnimations.popEnterTransition,
            popExitTransition = NavigationAnimations.popExitTransition
        ) {
            AboutScreen(
                onDismiss = { navController.popBackStack() }
            )
        }
    }
}

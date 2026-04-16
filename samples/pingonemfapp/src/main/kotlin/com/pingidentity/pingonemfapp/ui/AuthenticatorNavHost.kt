/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.pingonemfapp.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.pingidentity.logger.Logger
import com.pingidentity.pingonemfapp.config.Env
import com.pingidentity.pingonemfapp.config.EnvViewModel
import com.pingidentity.pingonemfapp.data.PingOneMFAViewModel
import com.pingidentity.pingonemfapp.davinci.DaVinci
import com.pingidentity.pingonemfapp.util.NavigationAnimations

/**
 * Main entry point for the app.
 */
@Composable
fun AuthenticatorNavHost(
    navController: NavHostController,
    authenticatorViewModel: PingOneMFAViewModel = viewModel(),
    initialDestination: String = "accounts",
    onOpenDrawer: () -> Unit = {},
) {
    val envViewModel: EnvViewModel = viewModel()

    // Define the navigation
    NavHost(navController = navController, startDestination = initialDestination) {

        // Main accounts list screen
        composable("accounts") {
            AccountsScreen(
                viewModel = authenticatorViewModel,
                onMenuClick = onOpenDrawer,
                onScanQrCode = { navController.navigate("scanner") },
                onAccountClick = {navController.navigate("otp") },
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

        composable(
            route = "davinci-launcher",
            enterTransition = NavigationAnimations.enterTransition,
            exitTransition = NavigationAnimations.exitTransition,
            popEnterTransition = NavigationAnimations.popEnterTransition,
            popExitTransition = NavigationAnimations.popExitTransition
        ) {
            DaVinciScreen(
                envViewModel = envViewModel,
                onBack = { navController.popBackStack() },
                onLaunchDaVinci = { navController.navigate("davinci") },
                onEditConfigurations = { navController.navigate("environment-config") },
            )
        }

        composable (
            route = "davinci",
            enterTransition = NavigationAnimations.enterTransition,
            exitTransition = NavigationAnimations.exitTransition,
            popEnterTransition = NavigationAnimations.popEnterTransition,
            popExitTransition = NavigationAnimations.popExitTransition
        ) {
            DaVinci(
                logger = Logger.logger,
                onSuccess = {
                    navController.navigate("accounts") {
                        popUpTo("accounts") { inclusive = false }
                        launchSingleTop = true
                    }
                },
                onBack = { navController.popBackStack() },
                onFinish = {
                    navController.navigate("accounts") {
                        popUpTo("accounts") { inclusive = false }
                        launchSingleTop = true
                    }
                },
            )
        }

        // Environment configuration screen
        composable(
            route = "environment-config",
            enterTransition = NavigationAnimations.enterTransition,
            exitTransition = NavigationAnimations.exitTransition,
            popEnterTransition = NavigationAnimations.popEnterTransition,
            popExitTransition = NavigationAnimations.popExitTransition
        ) {
            Env(
                envViewModel = envViewModel,
                onBack = { navController.popBackStack() }
            )
        }

    }
}

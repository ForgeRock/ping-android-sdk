/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.authenticatorapp.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pingidentity.authenticatorapp.data.AuthenticatorViewModel
import com.pingidentity.authenticatorapp.util.NavigationAnimations

/**
 * Main entry point for the app.
 */
@Composable
fun AuthenticatorNavHost(
    viewModel: AuthenticatorViewModel = viewModel(),
    initialDestination: String = "accounts"
) {
    // Create the NavController
    val navController = rememberNavController()

    // Define the navigation
    NavHost(navController = navController, startDestination = initialDestination) {

        // Main accounts list screen
        composable("accounts") {
            AccountsScreen(
                viewModel = viewModel,
                onScanQrCode = { navController.navigate("scanner") },
                onAddManually = { navController.navigate("manual-entry") },
                onAccountClick = { accountInfo -> navController.navigate("account/$accountInfo") },
                onNotificationsClick = { navController.navigate("notifications") },
                onSettingsClick = { navController.navigate("settings") },
                onAboutClick = { navController.navigate("about") },
                onEditAccountsClick = { navController.navigate("edit-accounts") },
                onTestModeClick = { navController.navigate("test") }
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
                viewModel = viewModel,
                onScanComplete = { navController.popBackStack() },
                onDismiss = { navController.popBackStack() }
            )
        }

        // Manual entry screen
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

        // Account detail screen with encoded parameters
        composable(
            route = "account/{issuer}/{accountName}",
            enterTransition = NavigationAnimations.enterTransition,
            exitTransition = NavigationAnimations.exitTransition,
            popEnterTransition = NavigationAnimations.popEnterTransition,
            popExitTransition = NavigationAnimations.popExitTransition
        ) { backStackEntry ->
            val encodedIssuer = backStackEntry.arguments?.getString("issuer") ?: ""
            val encodedAccountName = backStackEntry.arguments?.getString("accountName") ?: ""
            val issuer = java.net.URLDecoder.decode(encodedIssuer, "UTF-8")
            val accountName = java.net.URLDecoder.decode(encodedAccountName, "UTF-8")

            AccountDetailScreen(
                issuer = issuer,
                accountName = accountName,
                viewModel = viewModel,
                onDismiss = { navController.popBackStack() }
            )
        }

        // Push notification screens
        composable(
            route = "notifications",
            enterTransition = NavigationAnimations.enterTransition,
            exitTransition = NavigationAnimations.exitTransition,
            popEnterTransition = NavigationAnimations.popEnterTransition,
            popExitTransition = NavigationAnimations.popExitTransition
        ) {
            PushNotificationsScreen(
                viewModel = viewModel,
                onNotificationClick = { notificationId ->
                    navController.navigate("notification/$notificationId")
                },
                onDismiss = { navController.popBackStack() }
            )
        }

        // Individual notification screen
        composable(
            route = "notification/{notificationId}",
            enterTransition = NavigationAnimations.enterTransition,
            exitTransition = NavigationAnimations.exitTransition,
            popEnterTransition = NavigationAnimations.popEnterTransition,
            popExitTransition = NavigationAnimations.popExitTransition
        ) { backStackEntry ->
            val notificationId = backStackEntry.arguments?.getString("notificationId") ?: ""
            viewModel.getNotificationItemById(notificationId)?.let { notificationItem ->
                NotificationResponseScreen(
                    notificationItem = notificationItem,
                    onDismiss = { navController.popBackStack() },
                    onApprove = {
                        viewModel.approveNotification(notificationId)
                        navController.popBackStack()
                    },
                    onDeny = {
                        viewModel.denyNotification(notificationId)
                        navController.popBackStack()
                    },
                    onChallengeSolution = { solution ->
                        viewModel.approveChallengeNotification(notificationId, solution)
                        navController.popBackStack()
                    }
                )
            }
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
                viewModel = viewModel,
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
        
        // Test mode screen
        composable(
            route = "test",
            enterTransition = NavigationAnimations.enterTransition,
            exitTransition = NavigationAnimations.exitTransition,
            popEnterTransition = NavigationAnimations.popEnterTransition,
            popExitTransition = NavigationAnimations.popExitTransition
        ) {
            TestScreen(
                viewModel = viewModel,
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
        
        // Edit Accounts screen
        composable(
            route = "edit-accounts",
            enterTransition = NavigationAnimations.enterTransition,
            exitTransition = NavigationAnimations.exitTransition,
            popEnterTransition = NavigationAnimations.popEnterTransition,
            popExitTransition = NavigationAnimations.popExitTransition
        ) {
            EditAccountsScreen(
                viewModel = viewModel,
                onDismiss = { navController.popBackStack() }
            )
        }
    }
}

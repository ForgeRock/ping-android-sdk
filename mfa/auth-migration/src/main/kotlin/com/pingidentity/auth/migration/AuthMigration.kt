/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.auth.migration

import android.content.Context
import com.pingidentity.logger.Logger
import com.pingidentity.logger.STANDARD
import com.pingidentity.migration.Migration
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Main object for FR Authenticator data migration.
 * Orchestrates the migration of accounts and mechanisms.
 */
object AuthMigration {
    var logger: Logger = Logger.STANDARD
    private val mutex = Mutex()

    /**
     * Starts the authenticator migration process.
     *
     * This suspending function executes all configured migration steps in sequence.
     * The migration is idempotent and safe to run multiple times.
     *
     * @param context The Android context used to access storage services.
     * @param block Optional DSL block to configure [LegacyAuthenticationConfig].
     *
     * ## Example — default migration (no configuration needed)
     * ```kotlin
     * lifecycleScope.launch {
     *     AuthMigration.start(applicationContext)
     * }
     * ```
     *
     * ## Example — custom key alias
     * ```kotlin
     * lifecycleScope.launch {
     *     AuthMigration.start(applicationContext) {
     *         keyAlias = "com.myapp.custom.STORAGE_KEY"
     *     }
     * }
     * ```
     *
     * ## Example — custom storage provider
     * ```kotlin
     * lifecycleScope.launch {
     *     AuthMigration.start(applicationContext) {
     *         legacyStorageProvider = MyCustomStorageProvider(applicationContext)
     *     }
     * }
     * ```
     *
     * @see com.pingidentity.migration.MigrationProgress
     */
    suspend fun start(context: Context, block: LegacyAuthenticationConfig.() -> Unit = {}) {
        mutex.withLock {
            val config = LegacyAuthenticationConfig().apply(block)
            val provider = config.legacyStorageProvider
                ?: DefaultLegacyStorageProvider(context)
            this.logger = config.logger

            val migration = Migration {
                logger = config.logger
                step(startMigrationStep(provider))    // Import legacy authenticator data
                step(migrateMechanismsStep)           // Migrate mechanisms to OATH and Push storage
                step(cleanupLegacyDataStep(provider)) // Cleanup legacy authenticator data
            }

            migration.migrate(context).collect { progress ->
                when (progress) {
                    is com.pingidentity.migration.MigrationProgress.Started -> {
                        logger.i("Migration started")
                    }
                    is com.pingidentity.migration.MigrationProgress.InProgress -> {
                        logger.i("Migration in progress: Step ${progress.currentStep} of ${progress.totalSteps} - ${progress.message}")
                    }
                    is com.pingidentity.migration.MigrationProgress.Error -> {
                        logger.i("Migration error: ${progress.error.message}")
                    }
                    is com.pingidentity.migration.MigrationProgress.Success -> {
                        logger.i("Migration completed successfully: ${progress.message}")
                    }
                    is com.pingidentity.migration.MigrationProgress.StepCompleted -> {
                        logger.i("Step completed: ${progress.step.description}")
                    }
                }
            }
        }
    }
}
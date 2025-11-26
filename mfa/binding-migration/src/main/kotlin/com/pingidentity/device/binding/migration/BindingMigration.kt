/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.binding.migration

import android.content.Context
import com.pingidentity.device.binding.migration.BindingMigration.start
import com.pingidentity.logger.Logger
import com.pingidentity.logger.STANDARD
import com.pingidentity.migration.Migration

/**
 * Manages the migration of device binding data from the Legacy SDK to the new SDK format.
 *
 * This object orchestrates a multi-step migration process that transfers:
 * - Biometric authentication keys
 * - Application PIN keys
 * - User key metadata
 *
 * The migration runs automatically during app initialization via [androidx.startup.Initializer]
 * and can also be triggered manually by calling [start].
 *
 * ## Migration Steps
 *
 * The migration executes three sequential steps:
 *
 * 1. **Step 1 - Migration Preparation**: Check if migration is required and prepare the necessary
 *    state which required for subsequent steps, including loading existing keys from the Legacy SDK.
 *
 * 2. **Step 3 - User Key Metadata**: Migrates user key metadata (UserKey objects) from the
 *      Legacy SDK's repository to the new storage system, ensuring continuity of device bindings.
 *
 * 3. **Step 2 - Application PIN Keys**: Migrates application PIN keys from the Legacy SDK's
 *    encrypted SharedPreferences to the new DataStore-based storage with BouncyCastle encryption.
 *
 *
 * ## Usage
 *
 * Automatic migration (recommended):
 * ```kotlin
 * // Migration starts automatically when the app initializes
 * // via MigrationInitializer in the manifest
 * ```
 *
 * Manual migration:
 * ```kotlin
 * // Trigger migration programmatically
 * BindingMigration.start(context)
 * ```
 *
 * ## Progress Monitoring
 *
 * The migration emits progress updates through a Flow that can be observed:
 * - [com.pingidentity.migration.MigrationProgress.Started]: Migration has begun
 * - [com.pingidentity.migration.MigrationProgress.InProgress]: Step is executing
 * - [com.pingidentity.migration.MigrationProgress.StepCompleted]: Step finished successfully
 * - [com.pingidentity.migration.MigrationProgress.Success]: All steps completed
 * - [com.pingidentity.migration.MigrationProgress.Error]: Migration failed
 *
 * @see Migration
 * @see step1
 * @see step2
 * @see step3
 */
object BindingMigration {
    /**
     * Logger instance for migration progress and error reporting.
     */
    val logger = Logger.STANDARD

    /**
     * The migration configuration that defines the sequence of migration steps.
     */
    val migration = Migration {
        logger = this@BindingMigration.logger
        step(step1)
        step(step2)
        step(step3)
    }

    /**
     * Starts the device binding migration process.
     *
     * This suspending function executes all configured migration steps in sequence,
     * emitting progress updates through a Flow. Each step is executed only once and
     * will be skipped on subsequent invocations.
     *
     * The migration is idempotent - running it multiple times is safe and will not
     * duplicate or corrupt data.
     *
     * @param context The Android context used to access storage and encryption services.
     *                Typically the application context.
     *
     * ## Example
     *
     * ```kotlin
     * lifecycleScope.launch {
     *     BindingMigration.start(applicationContext)
     * }
     * ```
     *
     * @see com.pingidentity.migration.MigrationProgress
     */
    suspend fun start(context: Context) {
        migration.migrate(context).collect { progress ->
            when (progress) {
                is com.pingidentity.migration.MigrationProgress.Started -> {
                    // Handle migration started
                    logger.i("Migration started")
                }

                is com.pingidentity.migration.MigrationProgress.InProgress -> {
                    // Handle migration in progress
                    logger.i("Migration in progress: Step ${progress.currentStep} of ${progress.totalSteps} - ${progress.message}")
                }

                is com.pingidentity.migration.MigrationProgress.Error -> {
                    // Handle migration error
                    logger.i("Migration error: ${progress.error.message}")
                }

                is com.pingidentity.migration.MigrationProgress.Success -> {
                    // Handle migration completed
                    logger.i("Migration completed successfully: ${progress.message}")
                }

                is com.pingidentity.migration.MigrationProgress.StepCompleted -> {
                    // Handle step completed
                    logger.i("Step completed: ${progress.step.description}")
                }
            }
        }
    }
}
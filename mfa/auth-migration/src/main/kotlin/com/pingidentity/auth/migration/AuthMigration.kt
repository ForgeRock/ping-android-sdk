/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.auth.migration

import android.content.Context
import com.pingidentity.auth.migration.AuthMigration.logger
import com.pingidentity.auth.migration.AuthMigration.start
import com.pingidentity.logger.Logger
import com.pingidentity.logger.STANDARD
import com.pingidentity.migration.Migration
import com.pingidentity.migration.MigrationProgress
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Entry point for FR Authenticator data migration.
 *
 * `AuthMigration` orchestrates the three-step pipeline that imports legacy FR Authenticator data,
 * converts it to the new OATH and Push credential storage format, and cleans up the legacy files.
 *
 * ### Migration pipeline
 * 1. **Import** — checks [LegacyStorageProvider.isMigrationRequired]; if true, calls
 *    [LegacyStorageProvider.getMigrationData].
 * 2. **Migrate** — converts [LegacyMechanism] entries to [com.pingidentity.mfa.oath.OathCredential]
 *    and [com.pingidentity.mfa.push.PushCredential] objects and writes them to SQLite.
 * 3. **Cleanup** — calls [LegacyStorageProvider.cleanUp] to remove legacy SharedPreferences
 *    files and AndroidKeyStore entries.
 *
 * The migration is **idempotent**: if no legacy data is found, or if migration has already run,
 * the pipeline aborts cleanly without error. A [kotlinx.coroutines.sync.Mutex] ensures that
 * concurrent calls to [start] do not execute the pipeline simultaneously.
 *
 * ### Logging
 * All pipeline events are logged through [logger], which defaults to `Logger.STANDARD`.
 * Override it via the [LegacyAuthenticationConfig] DSL block:
 * ```kotlin
 * AuthMigration.start(context) { logger = Logger.WARN }
 * ```
 *
 * @see LegacyAuthenticationConfig
 * @see LegacyStorageProvider
 * @see StorageClientProvider
 */
object AuthMigration {
    /**
     * Logger used throughout the migration pipeline.
     *
     * Defaults to `Logger.STANDARD`. This property is updated at the start of each [start] call
     * to match the logger configured in [LegacyAuthenticationConfig].
     */
    var logger: Logger = Logger.STANDARD
    private val mutex = Mutex()

    /**
     * Executes the full authenticator migration pipeline.
     *
     * The pipeline consists of three sequential steps:
     * 1. **Import legacy data** — calls [LegacyStorageProvider.isMigrationRequired]; aborts if
     *    `false`. If migration is required, calls [LegacyStorageProvider.getMigrationData].
     * 2. **Migrate mechanisms** — converts legacy OATH and Push mechanisms to the new storage format.
     * 3. **Cleanup** — calls [LegacyStorageProvider.cleanUp] passing the [LegacyAuthenticationConfig.backup]
     *    callback so the provider can persist data before clearing it.
     *
     * This function **suspends** until the migration is fully complete (or cleanly aborted). It is
     * safe to call multiple times — the [kotlinx.coroutines.sync.Mutex] prevents concurrent runs,
     * and the pipeline exits early if there is nothing to migrate.
     *
     * @param context The Android application context used to access storage, SharedPreferences,
     *   and the AndroidKeyStore.
     * @param block Optional DSL block to configure [LegacyAuthenticationConfig]. If omitted, all
     *   properties use their default values, which is suitable for standard FR Authenticator
     *   installations using the default [StorageClientProvider].
     *
     * ## Example — default migration (no configuration needed)
     * ```kotlin
     * lifecycleScope.launch {
     *     AuthMigration.start(applicationContext)
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
     * ## Example — backup before cleanup
     * ```kotlin
     * lifecycleScope.launch {
     *     AuthMigration.start(applicationContext) {
     *         backup = { ctx -> MyBackupHelper.backup(ctx) }
     *     }
     * }
     * ```
     *
     * @see LegacyAuthenticationConfig
     * @see com.pingidentity.migration.MigrationProgress
     */
    suspend fun start(context: Context, block: LegacyAuthenticationConfig.() -> Unit = {}) {
        mutex.withLock {
            val config = LegacyAuthenticationConfig(context).apply(block)
            this.logger = config.logger
            val migration = Migration {
                logger = config.logger
                step(startMigrationStep(config.legacyStorageProvider))
                step(migrateMechanismsStep)
                step(cleanupLegacyDataStep(config.legacyStorageProvider, config.backup))
            }

            val collector = config.progress ?: FlowCollector { progress ->
                when (progress) {
                    is MigrationProgress.Started -> {
                        logger.i("Migration started")
                    }
                    is MigrationProgress.InProgress -> {
                        logger.i("Migration in progress: Step ${progress.currentStep} of ${progress.totalSteps} - ${progress.message}")
                    }
                    is MigrationProgress.StepCompleted -> {
                        logger.i("Step completed: ${progress.step.description}")
                    }
                    is MigrationProgress.Success -> {
                        logger.i("Migration completed successfully: ${progress.message}")
                    }
                    is MigrationProgress.Error -> {
                        logger.e(
                            "Migration failed at step '${progress.step.description}': ${progress.error.message}",
                            progress.error
                        )
                    }
                }
            }

            migration.migrate(context).collect(collector)
        }
    }
}
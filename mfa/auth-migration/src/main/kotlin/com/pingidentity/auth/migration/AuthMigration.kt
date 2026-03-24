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
import com.pingidentity.migration.MigrationProgress
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Entry point for FR Authenticator data migration.
 *
 * `AuthMigration` orchestrates the three-step pipeline that imports legacy FR Authenticator data,
 * converts it to the new OATH and Push credential storage format, and cleans up the legacy files.
 *
 * ### Migration pipeline
 * 1. **Import** — checks [LegacyStorageProvider.isMigrationRequired]; if true, invokes the
 *    optional `restore` lambda and then calls [LegacyStorageProvider.getMigrationData].
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

    private val _migrationResult = CompletableDeferred<Unit>()

    /**
     * A [Deferred] that completes once the migration pipeline finishes.
     *
     * - Completes normally (`Unit`) on success or when there is nothing to migrate.
     * - Completes **exceptionally** with the causing [Throwable] if any pipeline step fails.
     *
     * Useful in **automatic migration mode** (via [AuthenticationMigrationInitializer]) where
     * [start] is launched in a background coroutine and other parts of the app need to
     * wait before reading credentials:
     *
     * ```kotlin
     * viewModelScope.launch {
     *     try {
     *         AuthMigration.awaitMigration()
     *         loadCredentials()
     *     } catch (e: Exception) {
     *         showMigrationError(e)
     *     }
     * }
     * ```
     *
     * In **manual migration mode** the caller already suspends on [start] directly and does not
     * need this deferred — the exception is thrown straight from [start].
     *
     * This is a one-shot signal per process lifetime. Once resolved it never resets, so
     * subsequent `await()` calls return (or rethrow) immediately.
     */
    val migrationResult: Deferred<Unit> get() = _migrationResult

    /**
     * Suspends until the migration pipeline finishes.
     *
     * - Returns normally on success or when no migration is needed.
     * - Throws the causing exception if the pipeline failed.
     *
     * Intended for **automatic migration mode**: call this anywhere you need credentials to be
     * ready before proceeding. For **manual migration mode** simply `try/catch` around [start]
     * — the exception propagates directly from there.
     */
    suspend fun awaitMigration(): Unit = migrationResult.await()

    /**
     * Executes the full authenticator migration pipeline.
     *
     * The pipeline consists of three sequential steps:
     * 1. **Import legacy data** — calls [LegacyStorageProvider.isMigrationRequired]; aborts if
     *    `false`. If migration is required, invokes the optional [LegacyAuthenticationConfig.restore]
     *    lambda (e.g. to reinstate a backup), then calls [LegacyStorageProvider.getMigrationData].
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
     * ## Example — allow backup and restore
     * ```kotlin
     * lifecycleScope.launch {
     *     AuthMigration.start(applicationContext) {
     *         backup  = { ctx -> MyBackupHelper.backup(ctx) }
     *         restore = { ctx -> MyBackupHelper.restore(ctx) }
     *     }
     * }
     * ```
     *
     * @see LegacyAuthenticationConfig
     * @see com.pingidentity.migration.MigrationProgress
     */
    suspend fun start(context: Context, block: LegacyAuthenticationConfig.() -> Unit = {}) {
        mutex.withLock {
            // Tracks whether the error was already logged by the MigrationProgress.Error
            // branch so the outer catch does not produce a duplicate log line.
            var flowErrorLogged = false
            try {
                val config = LegacyAuthenticationConfig(context).apply(block)
                this.logger = config.logger
                val migration = Migration {
                    logger = config.logger
                    step(startMigrationStep(config.legacyStorageProvider, config.restore))
                    step(migrateMechanismsStep)
                    step(cleanupLegacyDataStep(config.legacyStorageProvider, config.backup))
                }

                migration.migrate(context).collect { progress ->
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
                            _migrationResult.complete(Unit)
                        }
                        is MigrationProgress.Error -> {
                            logger.e(
                                "Migration failed at step '${progress.step.description}': ${progress.error.message}",
                                progress.error
                            )
                            flowErrorLogged = true
                            // Throw so that:
                            //   • direct (manual) callers see the exception from start()
                            //   • the outer catch can also complete the deferred for
                            //     awaitMigration() callers in automatic mode
                            throw progress.error
                        }
                    }
                }
            } catch (e: Exception) {
                if (!flowErrorLogged) {
                    // Unexpected exception that didn't come from a MigrationProgress.Error
                    logger.e("Migration pipeline terminated unexpectedly", e)
                }
                // Complete the deferred so automatic-mode callers unblock with the error …
                _migrationResult.completeExceptionally(e)
                // … and rethrow so manual-mode callers also see the exception.
                throw e
            }
        }
    }
}
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

/**
 * Main object for FR Authenticator data migration.
 * Orchestrates the migration of accounts, mechanisms, notifications, and device tokens.
 */
object AuthMigration {
    val logger = Logger.STANDARD

    /**
     * Configuration for the migration process.
     * Set this before starting migration if using custom storage with a different key alias.
     */
    @Volatile
    var config: LegacyAuthenticationConfig = LegacyAuthenticationConfig()

    /** The migration configuration that defines the sequence of migration steps. */
    val migration = Migration {
        logger = this@AuthMigration.logger
        step(exportLegacyDataStep)  // Export legacy authenticator data
        step(migrateMechanismsStep)  // Migrate mechanisms to OATH and Push storage
        step(cleanupLegacyDataStep)  // Cleanup legacy authenticator data
    }

    /**
     * Configures the migration with custom settings.
     *
     * @param migrationType Type of migration to perform (XML, SQL, or AUTO).
     *                      Default: AUTO (auto-detect based on available data)
     * @param keyAlias The AndroidKeyStore alias used for encrypted XML storage.
     *                 Default: "org.forgerock.android.authenticator.KEYS"
     *                 Only used for XML migration type.
     * @param backupFiles Whether to create backup copies before deletion.
     *                    Default: false
     *                    Backups are saved with timestamp suffix.
     * @param databaseName Name of the SQLCipher database file (for SQL migration).
     *                     Default: "forgerock_authenticator.db"
     *                     Only used for SQL migration type.
     * @param databasePassphrase Optional passphrase for SQLCipher database encryption.
     *                           If null, will automatically retrieve from KeyStore/DataStore
     *                           (same method used by Legacy SDK's PassphraseManager).
     *                           Only used for SQL migration type.
     *
     * ## Example - XML Migration with Custom Key Alias
     *
     * ```kotlin
     * AuthMigration.configure(
     *     migrationType = MigrationType.XML,
     *     keyAlias = "com.myapp.custom.KEY_ALIAS"
     * )
     *
     * lifecycleScope.launch {
     *     AuthMigration.start(applicationContext)
     * }
     * ```
     *
     * ## Example - SQL Migration with Database Configuration
     *
     * ```kotlin
     * AuthMigration.configure(
     *     migrationType = MigrationType.SQL,
     *     databaseName = "forgerock_authenticator.db",
     *     databasePassphrase = "your-secure-passphrase",
     *     backupFiles = true
     * )
     *
     * lifecycleScope.launch {
     *     AuthMigration.start(applicationContext)
     * }
     * ```
     *
     * ## Example - Auto-Detect Migration Type
     *
     * ```kotlin
     * // Automatically detects SQL or XML based on available data
     * AuthMigration.configure(
     *     migrationType = MigrationType.AUTO,
     *     databaseName = "forgerock_authenticator.db",
     *     databasePassphrase = "your-secure-passphrase",
     *     backupFiles = true
     * )
     *
     * lifecycleScope.launch {
     *     AuthMigration.start(applicationContext)
     * }
     * ```
     */
    fun configure(
        migrationType: MigrationType = MigrationType.AUTO,
        keyAlias: String = LegacyAuthenticationConfig.DEFAULT_KEY_ALIAS,
        backupFiles: Boolean = false,
        databaseName: String = LegacyAuthenticationConfig.DEFAULT_DATABASE_NAME,
        databasePassphrase: String? = null
    ) {
        config = LegacyAuthenticationConfig(
            migrationType = migrationType,
            keyAlias = keyAlias,
            backupFiles = backupFiles,
            databaseName = databaseName,
            databasePassphrase = databasePassphrase
        )
        logger.i("Migration configured: type=$migrationType, keyAlias=$keyAlias, backupFiles=$backupFiles, databaseName=$databaseName, hasPassphrase=${databasePassphrase != null}")
    }

    /**
     * Starts the authenticator migration process.
     *
     * This suspending function executes all configured migration steps in sequence,
     * emitting progress updates through a Flow. Each step is executed only once and
     * will be skipped on subsequent invocations.
     *
     * The migration is idempotent and safe to run multiple times.
     *
     * @param context The Android context used to access storage services.
     * ## Example
     *
     * ```kotlin
     * lifecycleScope.launch {
     *     AuthMigration.start(applicationContext)
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

    /**
     * Starts migration from a pre-exported JSON file.
     *
     * This method is useful for custom storage implementations where the developer
     * manually exports their legacy data to JSON format and provides it for migration.
     *
     * The JSON should contain mechanisms with nested account data.
     *
     * @param context The Android context used to access storage services.
     * @param exportedDataJson JSON string containing the exported legacy data.
     *
     * ## Example - Custom Storage Migration
     *
     * ```kotlin
     * // Export from custom storage first
     * val customStorage = CustomStorageClient(context)
     * val mechanisms = customStorage.getAllMechanisms()
     * val accounts = customStorage.getAllAccounts()
     *
     * // Build JSON manually or using a converter
     * val exportedJson = buildLegacyExportJson(mechanisms, accounts)
     *
     * // Migrate using the JSON
     * lifecycleScope.launch {
     *     AuthMigration.startWithJson(applicationContext, exportedJson)
     * }
     * ```
     *
     * @see LegacyExportedData
     */
    suspend fun startWithJson(context: Context, exportedDataJson: String) {
        val customMigration = Migration {
            logger = this@AuthMigration.logger
            step(importJsonDataStep(exportedDataJson))
            step(migrateMechanismsStep)
            step(cleanupLegacyDataStep)
        }

        customMigration.migrate(context).collect { progress ->
            when (progress) {
                is com.pingidentity.migration.MigrationProgress.Started -> {
                    logger.i("Custom JSON migration started")
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

    /**
     * Exports legacy data to JSON format for manual migration.
     *
     * This is useful when you need to:
     * - Review the data before migration
     * - Migrate at a later time
     * - Transform data from custom storage implementations
     *
     * @param context The Android context used to access storage services.
     * @return JSON string containing all exported legacy data
     *
     * ## Example
     *
     * ```kotlin
     * lifecycleScope.launch {
     *     val exportedJson = AuthMigration.exportToJson(applicationContext)
     *     // Save to file or process as needed
     *     File(context.filesDir, "legacy_export.json").writeText(exportedJson)
     *
     *     // Later, migrate using the JSON
     *     AuthMigration.startWithJson(applicationContext, exportedJson)
     * }
     * ```
     */
    suspend fun exportToJson(context: Context): String {
        val repo = LegacyAuthenticationRepository(context, config)
        return repo.exportToJson()
    }
}
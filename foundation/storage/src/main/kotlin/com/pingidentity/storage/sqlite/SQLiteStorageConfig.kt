/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.storage.sqlite

import android.content.Context
import com.pingidentity.android.ContextProvider
import com.pingidentity.logger.Logger
import com.pingidentity.storage.sqlite.passphrase.KeyStorePassphraseProvider
import com.pingidentity.storage.sqlite.passphrase.PassphraseProvider

/**
 * Configuration class for SQLiteStorage and its subclasses.
 * Contains all the configuration parameters needed to initialize SQLite-based storage.
 *
 * This shared configuration class is used by:
 * - SQLOathStorage
 * - SQLPushStorage
 * - Any custom SQLiteStorage implementations
 *
 * Example usage:
 * ```
 * val storage = SQLOathStorage {
 *     context = applicationContext
 *     databaseName = "custom_mfa.db"
 *     allowDestructiveRecovery = true
 *     onDatabaseError = { exception, canRecover ->
 *         logger.error("Database error", exception)
 *     }
 * }
 * ```
 */
class SQLiteStorageConfig {
    /**
     * Android context used to access the database.
     * Defaults to the global ContextProvider.context.
     */
    var context: Context = ContextProvider.context

    /**
     * Name of the database file.
     * Each storage type has its own default:
     * - SQLOathStorage: "pingidentity_oath.db"
     * - SQLPushStorage: "pingidentity_push.db"
     *
     * Can be customized to share databases between storage types:
     * ```
     * SQLOathStorage { databaseName = "shared_mfa.db" }
     * SQLPushStorage { databaseName = "shared_mfa.db" }
     * ```
     */
    var databaseName: String = "pingidentity_storage.db"

    /**
     * Database schema version number.
     * Used for database migrations.
     */
    var databaseVersion: Int = 1

    /**
     * Optional initial passphrase for the database.
     * If null, a secure passphrase will be generated automatically.
     */
    var initialPassphrase: String? = null

    /**
     * Provider for the database encryption passphrase.
     * Defaults to KeyStorePassphraseProvider which uses Android KeyStore for secure storage.
     */
    var passphraseProvider: PassphraseProvider = KeyStorePassphraseProvider(context, initialPassphrase)

    /**
     * If true, automatically attempts to restore from the most recent backup when database
     * initialization fails. If false, backup restoration must be triggered manually.
     * Default is true.
     */
    var autoRestoreFromBackup: Boolean = true

    /**
     * If true, allows the SDK to delete corrupted databases and start fresh.
     * WARNING: This will cause data loss. Only enable if you have external backup strategies.
     * Default is false (safe by default).
     */
    var allowDestructiveRecovery: Boolean = false

    /**
     * Maximum number of backup files to retain. Older backups beyond this limit are automatically deleted.
     * Set to 0 to disable backup creation entirely. Default is 3.
     */
    var maxBackupCount: Int = 3

    /**
     * If true, creates a backup of the database before attempting destructive recovery.
     * Only applies when allowDestructiveRecovery is true. Default is true.
     */
    var backupOnError: Boolean = true

    /**
     * Optional callback invoked when a database error occurs during initialization.
     * Receives the exception and a boolean indicating whether recovery will be attempted.
     * Useful for logging, telemetry, and custom error handling.
     *
     * Example:
     * ```
     * onDatabaseError = { exception, canRecover ->
     *     logger.error("Database error: ${exception.message}", exception)
     *     if (!canRecover) {
     *         showErrorDialog("Database error - please reinstall the app")
     *     }
     * }
     * ```
     */
    var onDatabaseError: (suspend (Exception, Boolean) -> Unit)? = null

    /**
     * Logger instance for diagnostic output.
     * Defaults to the global Logger instance.
     */
    var logger: Logger = Logger.logger
}

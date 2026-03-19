/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.auth.migration

import android.content.Context
import com.pingidentity.logger.Logger
import com.pingidentity.logger.STANDARD
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Configuration for the legacy FR Authenticator data migration.
 *
 * Configure via the DSL block passed to [AuthMigration.start]. All properties have sensible
 * defaults for standard FR Authenticator installations — no block is required for most apps.
 *
 * @property legacyStorageProvider Defines how the migration pipeline sources legacy data and
 *   performs cleanup. If not set, [DefaultLegacyStorageProvider] is used, which reads directly
 *   from the legacy encrypted SharedPreferences. Override when your application stores
 *   authenticator data in a custom backend (e.g. a different
 *   [org.forgerock.android.auth.StorageClient], an encrypted database, or a remote store).
 * @property logger Logger instance to use for logging. Defaults to [Logger.Companion.STANDARD].
 *
 * ## Example — default migration (no block needed)
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
 * ## Example — logger
 *  ```kotlin
 * lifecycleScope.launch {
 *     AuthMigration.start(applicationContext) {
 *        logger = Logger.WARN
 *    }
 * }
 *  * ```
 *
 * @see AuthMigration
 * @see LegacyStorageProvider
 * @see DefaultLegacyStorageProvider
 */
class LegacyAuthenticationConfig {
    var legacyStorageProvider: LegacyStorageProvider? = null
    var logger: Logger = Logger.STANDARD
}

private const val AUTH_DATA_ACCOUNT = "org.forgerock.android.authenticator.DATA.ACCOUNT"
private const val AUTH_DATA_MECHANISM = "org.forgerock.android.authenticator.DATA.MECHANISM"
/** Default key alias used by the ForgeRock Authenticator SDK. */
private const val DEFAULT_KEY_ALIAS = "org.forgerock.android.authenticator.KEYS"


/**
 * Repository for reading and exporting Legacy SDK authenticator data.
 * Supports both encrypted (default storage) and unencrypted (custom storage) data.
 *
 * @param context Android application context
 */
class LegacyAuthenticationRepository(
    private val context: Context
) {
    private val decryptor = LegacyAuthenticationDecryptor(
        context,
        DEFAULT_KEY_ALIAS
    )

    /**
     * Checks if legacy data exists (either encrypted or unencrypted).
     */
    suspend fun isExists(): Boolean = withContext(Dispatchers.IO) {
        // Check for encrypted data (has encryption key)
        if (decryptor.keyExists()) {
            return@withContext true
        }

        // Check for unencrypted data (custom storage - plain SharedPreferences)
        val prefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
        return@withContext listOf(
            AUTH_DATA_ACCOUNT,
            AUTH_DATA_MECHANISM,
        ).any { prefName ->
            File(prefsDir, "$prefName.xml").exists()
        }
    }

    /**
     * Export all FR Authenticator data from Legacy SDK.
     * Supports both encrypted (default) and unencrypted (custom storage) data.
     */
    suspend fun exportAllData(): LegacyExportedData = withContext(Dispatchers.IO) {
        try {
            // Try encrypted data first (default storage)
            val isEncrypted = decryptor.keyExists()

            val accountsMap = readPreferences(AUTH_DATA_ACCOUNT, isEncrypted)
            val mechanismsMap = readPreferences(AUTH_DATA_MECHANISM, isEncrypted)

            // Build mechanisms list with nested account data
            val mechanisms = LegacyDataConverter.buildMechanismsList(mechanismsMap, accountsMap)

            LegacyExportedData(
                mechanisms = mechanisms,
                metadata = LegacyExportMetadata(
                    totalMechanisms = mechanisms.size
                )
            )
        } catch (e: Exception) {
            AuthMigration.logger.e("Failed to export legacy data: ${e.message}", e)
            throw e
        }
    }

    /**
     * Delete legacy data files after successful migration.
     * Handles both encrypted (default storage) and unencrypted (custom storage) data.
     */
    suspend fun deleteLegacyData() = withContext(Dispatchers.IO) {
        // Delete SharedPreferences files (works for both encrypted and unencrypted)
        context.deleteSharedPreferences(AUTH_DATA_ACCOUNT)
        context.deleteSharedPreferences(AUTH_DATA_MECHANISM)
        return@withContext
    }


    /**
     * Creates backup copies of SharedPreferences XML files.
     * Backups are stored in the same directory with a timestamp suffix.
     *
     * @return List of backup file paths that were created
     */
    internal suspend fun backupSharedPreferences(): List<String> = withContext(Dispatchers.IO) {
        val backupPaths = mutableListOf<String>()
        val prefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
        val timestamp = System.currentTimeMillis()

        listOf(AUTH_DATA_ACCOUNT, AUTH_DATA_MECHANISM).forEach { prefName ->
            val sourceFile = File(prefsDir, "$prefName.xml")
            if (sourceFile.exists()) {
                try {
                    val backupFile = File(prefsDir, "${prefName}_backup_$timestamp.xml")
                    sourceFile.copyTo(backupFile, overwrite = false)
                    backupPaths.add(backupFile.absolutePath)
                    AuthMigration.logger.i("Created backup: ${backupFile.name} (${backupFile.length()} bytes)")
                } catch (e: Exception) {
                    AuthMigration.logger.e("Failed to backup $prefName: ${e.message}", e)
                }
            }
        }

        AuthMigration.logger.i("Created ${backupPaths.size} backup file(s)")
        backupPaths
    }

    /**
     * Reads SharedPreferences data, handling both encrypted and unencrypted formats.
     */
    private fun readPreferences(preferenceName: String, isEncrypted: Boolean): Map<String, String> {
        // Check if file exists
        val prefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
        val prefsFile = File(prefsDir, "$preferenceName.xml")

        if (!prefsFile.exists()) {
            return emptyMap()
        }

        return try {
            if (isEncrypted) {
                // Decrypt encrypted SharedPreferences (default storage)
                AuthMigration.logger.d("Reading encrypted data from $preferenceName")
                decryptor.decryptAll(preferenceName)
            } else {
                // Read plain SharedPreferences (custom storage)
                AuthMigration.logger.d("Reading unencrypted data from $preferenceName")
                readPlainSharedPreferences(preferenceName)
            }
        } catch (e: Exception) {
            AuthMigration.logger.e("Failed to read $preferenceName: ${e.message}", e)
            emptyMap()
        }
    }

    /**
     * Reads plain (unencrypted) SharedPreferences used by custom storage.
     */
    private fun readPlainSharedPreferences(preferenceName: String): Map<String, String> {
        val prefs = context.getSharedPreferences(preferenceName, Context.MODE_PRIVATE)
        val result = mutableMapOf<String, String>()

        prefs.all.forEach { (key, value) ->
            if (value is String) {
                result[key] = value
            }
        }

        AuthMigration.logger.d("Read ${result.size} entries from plain SharedPreferences: $preferenceName")
        return result
    }
}
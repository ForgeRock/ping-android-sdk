/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.auth.migration

import android.content.Context
import com.pingidentity.auth.migration.LegacyAuthenticationConfig.Companion.DEFAULT_KEY_ALIAS
import com.pingidentity.logger.Logger
import com.pingidentity.logger.STANDARD
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * Configuration for the legacy FR Authenticator data migration.
 *
 * Configure via the DSL block passed to [AuthMigration.start]. All properties have sensible
 * defaults for standard FR Authenticator installations — no block is required for most apps.
 *
 * @property keyAlias AndroidKeyStore alias of the master key used to decrypt the legacy
 *   encrypted SharedPreferences. Defaults to [DEFAULT_KEY_ALIAS], which is the alias used
 *   by the ForgeRock Authenticator SDK. Only change this if your application stored legacy
 *   data under a different key alias.
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
 * @see AuthMigration.start
 * @see LegacyStorageProvider
 * @see DefaultLegacyStorageProvider
 */
class LegacyAuthenticationConfig {
    var keyAlias: String = DEFAULT_KEY_ALIAS
    var legacyStorageProvider: LegacyStorageProvider? = null
    var logger: Logger = Logger.STANDARD

    companion object {
        /** Default key alias used by the ForgeRock Authenticator SDK. */
        const val DEFAULT_KEY_ALIAS = "org.forgerock.android.authenticator.KEYS"
    }
}

private const val AUTH_DATA_ACCOUNT = "org.forgerock.android.authenticator.DATA.ACCOUNT"
private const val AUTH_DATA_MECHANISM = "org.forgerock.android.authenticator.DATA.MECHANISM"


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
            val mechanisms = buildMechanismsList(mechanismsMap, accountsMap)

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
     * Delete legacy data after successful migration.
     * Handles both encrypted (default storage) and unencrypted (custom storage) data.
     */
    suspend fun deleteLegacyData() = withContext(Dispatchers.IO) {
        // Delete SharedPreferences files (works for both encrypted and unencrypted)
        context.deleteSharedPreferences(AUTH_DATA_ACCOUNT)
        context.deleteSharedPreferences(AUTH_DATA_MECHANISM)

        // Delete encryption key if it exists (only for default storage)
        try {
            if (decryptor.keyExists()) {
                val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore")
                keyStore.load(null)
                keyStore.deleteEntry(DEFAULT_KEY_ALIAS)
                AuthMigration.logger.d("Deleted legacy encryption key: $DEFAULT_KEY_ALIAS")
            }
        } catch (e: Exception) {
            AuthMigration.logger.w("Failed to delete encryption key (may not exist): ${e.message}")
        }
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
     * Build mechanisms list with nested account data.
     */
    private fun buildMechanismsList(
        mechanismsMap: Map<String, String>,
        accountsMap: Map<String, String>
    ): List<LegacyMechanism> {
        return mechanismsMap.mapNotNull { (mechanismId, mechanismJson) ->
            try {
                val mechanismData = legacyJson.parseToJsonElement(mechanismJson).jsonObject

                // Extract mechanism fields
                val mechanismIssuer = mechanismData["issuer"]?.jsonPrimitive?.content ?: ""
                val mechanismAccountName = mechanismData["accountName"]?.jsonPrimitive?.content ?: ""

                // Find associated account (Account ID format: issuer + "-" + accountName)
                val accountId = "$mechanismIssuer-$mechanismAccountName"
                val accountJson = accountsMap[accountId]
                val accountData = accountJson?.let {
                    legacyJson.parseToJsonElement(it).jsonObject
                }

                // Build nested account
                val account = accountData?.let {
                    LegacyAccount(
                        id = it["id"]?.jsonPrimitive?.content ?: accountId,
                        issuer = it["issuer"]?.jsonPrimitive?.content ?: mechanismIssuer,
                        displayIssuer = it["displayIssuer"]?.jsonPrimitive?.content,
                        accountName = it["accountName"]?.jsonPrimitive?.content ?: mechanismAccountName,
                        displayAccountName = it["displayAccountName"]?.jsonPrimitive?.content,
                        imageURL = it["imageURL"]?.jsonPrimitive?.content,
                        backgroundColor = it["backgroundColor"]?.jsonPrimitive?.content,
                        timeAdded = it["timeAdded"]?.jsonPrimitive?.content?.toLongOrNull(),
                        policies = it["policies"]?.jsonPrimitive?.content,
                        lockingPolicy = it["lockingPolicy"]?.jsonPrimitive?.content,
                        lock = it["lock"]?.jsonPrimitive?.content?.toBoolean() ?: false
                    )
                }

                // Build mechanism with all fields
                LegacyMechanism(
                    id = mechanismData["id"]?.jsonPrimitive?.content ?: mechanismId,
                    issuer = mechanismIssuer,
                    accountName = mechanismAccountName,
                    mechanismUID = mechanismData["mechanismUID"]?.jsonPrimitive?.content
                        ?: mechanismData["id"]?.jsonPrimitive?.content
                        ?: mechanismId,
                    secret = mechanismData["secret"]?.jsonPrimitive?.content ?: "",
                    type = mechanismData["type"]?.jsonPrimitive?.content ?: "",

                    // OATH fields
                    oathType = mechanismData["oathType"]?.jsonPrimitive?.content,
                    algorithm = mechanismData["algorithm"]?.jsonPrimitive?.content,
                    digits = mechanismData["digits"]?.jsonPrimitive?.content?.toIntOrNull(),
                    period = mechanismData["period"]?.jsonPrimitive?.content?.toIntOrNull(),
                    counter = mechanismData["counter"]?.jsonPrimitive?.content?.toLongOrNull(),

                    // Push fields
                    registrationEndpoint = mechanismData["registrationEndpoint"]?.jsonPrimitive?.content,
                    authenticationEndpoint = mechanismData["authenticationEndpoint"]?.jsonPrimitive?.content,
                    platform = mechanismData["platform"]?.jsonPrimitive?.content,

                    // Common fields
                    uid = mechanismData["uid"]?.jsonPrimitive?.content,
                    resourceId = mechanismData["resourceId"]?.jsonPrimitive?.content,
                    timeAdded = mechanismData["timeAdded"]?.jsonPrimitive?.content?.toLongOrNull(),

                    // Nested account
                    account = account
                )
            } catch (e: Exception) {
                AuthMigration.logger.e("Failed to parse mechanism $mechanismId: ${e.message}", e)
                null
            }
        }
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
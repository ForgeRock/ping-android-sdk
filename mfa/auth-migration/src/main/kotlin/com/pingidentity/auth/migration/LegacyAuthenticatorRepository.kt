/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.auth.migration

import android.content.Context
import com.pingidentity.storage.sqlite.passphrase.KeyStorePassphraseProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.zetetic.database.sqlcipher.SQLiteDatabase
import java.io.File

/**
 * Migration type for legacy authenticator data.
 */
enum class MigrationType {
    /** XML-based migration using encrypted SharedPreferences (default ForgeRock storage) */
    XML,

    /** SQL-based migration using SQLStorageClient or custom StorageClient implementation */
    SQL,

    /** Auto-detect migration type based on available data */
    AUTO
}

/**
 * Configuration for legacy authenticator data migration.
 *
 * @property migrationType Type of migration to perform (XML, SQL, or AUTO)
 * @property keyAlias The AndroidKeyStore alias used for encrypting SharedPreferences.
 *                    Default: "org.forgerock.android.authenticator.KEYS" (ForgeRock SDK default)
 *                    Only used for XML migration type.
 * @property backupFiles Whether to create backup files before deletion
 * @property databaseName Name of the SQLCipher database file (for SQL migration).
 *                        Default: "forgerock_authenticator.db"
 *                        Only used for SQL migration type.
 * @property databasePassphrase Optional passphrase for SQLCipher database encryption.
 *                              If null, will automatically retrieve from KeyStore/DataStore
 *                              (same method used by Legacy SDK's PassphraseManager).
 *                              Only used for SQL migration type.
 */
data class LegacyAuthenticationConfig(
    val migrationType: MigrationType = MigrationType.AUTO,
    val keyAlias: String = DEFAULT_KEY_ALIAS,
    val backupFiles: Boolean = false,
    val databaseName: String = DEFAULT_DATABASE_NAME,
    val databasePassphrase: String? = null
) {
    companion object {
        /** Default key alias used by ForgeRock Authenticator SDK */
        const val DEFAULT_KEY_ALIAS = "org.forgerock.android.authenticator.KEYS"

        /** Default database name used by SQLStorageClient */
        const val DEFAULT_DATABASE_NAME = "forgerock_authenticator.db"
    }
}

private const val AUTH_DATA_ACCOUNT = "org.forgerock.android.authenticator.DATA.ACCOUNT"
private const val AUTH_DATA_MECHANISM = "org.forgerock.android.authenticator.DATA.MECHANISM"

/**
 * Repository for reading and exporting Legacy SDK authenticator data.
 * Supports both XML-based (SharedPreferences) and SQL-based (SQLStorageClient/StorageClient) migration.
 *
 * @param context Android application context
 * @param config Configuration including migration type and custom storage client
 */
class LegacyAuthenticationRepository(
    private val context: Context,
    private val config: LegacyAuthenticationConfig = LegacyAuthenticationConfig()
) {

    private val decryptor = LegacyAuthenticationDecryptor(
        context,
        config.keyAlias
    )

    /**
     * Determines the actual migration type to use based on configuration and available data.
     */
    private suspend fun determineMigrationType(): MigrationType = withContext(Dispatchers.IO) {
        when (config.migrationType) {
            MigrationType.XML -> MigrationType.XML
            MigrationType.SQL -> MigrationType.SQL
            MigrationType.AUTO -> {
                // Check for SQL database first (using configured database name)
                val dbFile = context.getDatabasePath(config.databaseName)
                if (dbFile.exists()) {
                    AuthMigration.logger.d("Auto-detected SQL migration type (database file exists: ${config.databaseName})")
                    return@withContext MigrationType.SQL
                }

                // Check for XML SharedPreferences
                val prefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
                val hasXmlData = listOf(AUTH_DATA_ACCOUNT, AUTH_DATA_MECHANISM).any { prefName ->
                    File(prefsDir, "$prefName.xml").exists()
                }

                if (hasXmlData) {
                    AuthMigration.logger.d("Auto-detected XML migration type (SharedPreferences files exist)")
                    return@withContext MigrationType.XML
                }

                // Default to XML if nothing detected
                AuthMigration.logger.d("No legacy data detected, defaulting to XML migration type")
                MigrationType.XML
            }
        }
    }

    /**
     * Checks if legacy data exists (XML or SQL based on migration type).
     */
    suspend fun isExists(): Boolean = withContext(Dispatchers.IO) {
        val migrationType = determineMigrationType()

        when (migrationType) {
            MigrationType.XML -> isXmlDataExists()
            MigrationType.SQL -> isSqlDataExists()
            MigrationType.AUTO -> isXmlDataExists() || isSqlDataExists()
        }
    }

    /**
     * Checks if XML-based legacy data exists.
     */
    private fun isXmlDataExists(): Boolean {
        // Check for encrypted data (has encryption key)
        if (decryptor.keyExists()) {
            return true
        }

        // Check for unencrypted data (custom storage - plain SharedPreferences)
        val prefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
        return listOf(
            AUTH_DATA_ACCOUNT,
            AUTH_DATA_MECHANISM,
        ).any { prefName ->
            File(prefsDir, "$prefName.xml").exists()
        }
    }

    /**
     * Checks if SQL-based legacy data exists.
     */
    private fun isSqlDataExists(): Boolean {
        val dbFile = context.getDatabasePath(config.databaseName)
        return dbFile.exists()
    }

    /**
     * Export all FR Authenticator data from Legacy SDK in structured JSON format.
     * Returns mechanisms with nested account data for 1-to-1 mapping.
     * Supports both XML (SharedPreferences) and SQL (StorageClient) migration.
     */
    suspend fun exportAllData(): LegacyExportedData = withContext(Dispatchers.IO) {
        try {
            val migrationType = determineMigrationType()

            AuthMigration.logger.i("Exporting legacy data using $migrationType migration")

            when (migrationType) {
                MigrationType.XML -> exportXmlData()
                MigrationType.SQL -> exportSqlData()
                MigrationType.AUTO -> {
                    // Try SQL first, fall back to XML
                    if (isSqlDataExists()) {
                        exportSqlData()
                    } else {
                        exportXmlData()
                    }
                }
            }
        } catch (e: Exception) {
            AuthMigration.logger.e("Failed to export legacy data: ${e.message}", e)
            throw e
        }
    }

    /**
     * Export data from XML-based storage (SharedPreferences).
     */
    private suspend fun exportXmlData(): LegacyExportedData = withContext(Dispatchers.IO) {
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
    }

    /**
     * Export data from SQL-based storage (SQLCipher database).
     * Reads directly from the database tables without requiring StorageClient.
     */
    private suspend fun exportSqlData(): LegacyExportedData = withContext(Dispatchers.IO) {
        val dbFile = context.getDatabasePath(config.databaseName)

        if (!dbFile.exists()) {
            throw IllegalStateException("SQL database not found: ${config.databaseName}")
        }

        val accountsMap = mutableMapOf<String, String>()
        val mechanismsMap = mutableMapOf<String, String>()

        var database: SQLiteDatabase? = null

        try {
            // Load SQLCipher native library
            System.loadLibrary("sqlcipher")

            // Get passphrase for SQLCipher
            val passphrase = getDatabasePassphrase()

            AuthMigration.logger.d("Opening SQLCipher database: ${config.databaseName}")

            // Open SQLCipher database using net.zetetic API (same as SQLiteStorage)
            database = SQLiteDatabase.openOrCreateDatabase(
                dbFile.absolutePath,
                passphrase,
                null,  // CursorFactory
                null   // DatabaseErrorHandler
            )

            // Query accounts table
            // Query accounts table
            // Try to detect column names first to handle schema variations
            val tableInfo = database.rawQuery("PRAGMA table_info(accounts)", null)
            val columnNames = mutableSetOf<String>()

            try {
                while (tableInfo.moveToNext()) {
                    val columnName = tableInfo.getString(1) // Column name is at index 1
                    columnNames.add(columnName)
                }
            } finally {
                tableInfo.close()
            }

            AuthMigration.logger.d("Detected account columns: $columnNames")

            // Build SELECT query based on available columns
            val accountColumns = buildList {
                add("id")
                add("issuer")
                if (columnNames.contains("displayIssuer")) add("displayIssuer")
                else if (columnNames.contains("display_issuer")) add("display_issuer")

                add("account_name")
                if (columnNames.contains("displayAccountName")) add("displayAccountName")
                else if (columnNames.contains("display_account_name")) add("display_account_name")

                if (columnNames.contains("imageURL")) add("imageURL")
                else if (columnNames.contains("image_url")) add("image_url")

                if (columnNames.contains("backgroundColor")) add("backgroundColor")
                else if (columnNames.contains("background_color")) add("background_color")

                add("time_added")

                if (columnNames.contains("policies")) add("policies")

                if (columnNames.contains("lockingPolicy")) add("lockingPolicy")
                else if (columnNames.contains("locking_policy")) add("locking_policy")

                if (columnNames.contains("lock")) add("lock")
                else if (columnNames.contains("is_locked")) add("is_locked")
            }.joinToString(", ")

            val accountsCursor = database.rawQuery(
                "SELECT $accountColumns FROM accounts",
                null
            )

            try {
                while (accountsCursor.moveToNext()) {
                    try {
                        val id = accountsCursor.getString(0)
                        val accountJson = buildAccountJson(accountsCursor)
                        accountsMap[id] = accountJson
                        AuthMigration.logger.d("Exported account: $id")
                    } catch (e: Exception) {
                        AuthMigration.logger.e("Failed to export account: ${e.message}", e)
                    }
                }
            } finally {
                accountsCursor.close()
            }

            AuthMigration.logger.d("Found ${accountsMap.size} accounts in SQL storage")

            // Query mechanisms table
            // Detect column names for mechanisms table
            val mechTableInfo = database.rawQuery("PRAGMA table_info(mechanisms)", null)
            val mechColumnNames = mutableSetOf<String>()

            try {
                while (mechTableInfo.moveToNext()) {
                    val columnName = mechTableInfo.getString(1)
                    mechColumnNames.add(columnName)
                }
            } finally {
                mechTableInfo.close()
            }

            AuthMigration.logger.d("Detected mechanism columns: $mechColumnNames")

            // Build SELECT query based on available columns
            val mechanismColumns = buildList {
                add("id")
                add("mechanism_uid")
                add("issuer")
                add("account_name")
                add("type")
                add("secret")

                if (mechColumnNames.contains("uid")) add("uid")
                if (mechColumnNames.contains("resourceId")) add("resourceId")
                else if (mechColumnNames.contains("resource_id")) add("resource_id")

                add("time_added")

                if (mechColumnNames.contains("oathType")) add("oathType")
                else if (mechColumnNames.contains("oath_type")) add("oath_type")

                if (mechColumnNames.contains("algorithm")) add("algorithm")
                if (mechColumnNames.contains("digits")) add("digits")
                if (mechColumnNames.contains("counter")) add("counter")
                if (mechColumnNames.contains("period")) add("period")

                if (mechColumnNames.contains("registrationEndpoint")) add("registrationEndpoint")
                else if (mechColumnNames.contains("registration_endpoint")) add("registration_endpoint")

                if (mechColumnNames.contains("authenticationEndpoint")) add("authenticationEndpoint")
                else if (mechColumnNames.contains("authentication_endpoint")) add("authentication_endpoint")
            }.joinToString(", ")

            val mechanismsCursor = database.rawQuery(
                "SELECT $mechanismColumns FROM mechanisms",
                null
            )

            try {
                while (mechanismsCursor.moveToNext()) {
                    try {
                        val id = mechanismsCursor.getString(0)
                        val mechanismJson = buildMechanismJson(mechanismsCursor)
                        mechanismsMap[id] = mechanismJson
                        AuthMigration.logger.d("Exported mechanism: $id")
                    } catch (e: Exception) {
                        AuthMigration.logger.e("Failed to export mechanism: ${e.message}", e)
                    }
                }
            } finally {
                mechanismsCursor.close()
            }

            AuthMigration.logger.i("Exported ${mechanismsMap.size} mechanisms from SQL storage")

            // Build mechanisms list with nested account data
            val mechanisms = buildMechanismsList(mechanismsMap, accountsMap)

            LegacyExportedData(
                mechanisms = mechanisms,
                metadata = LegacyExportMetadata(
                    totalMechanisms = mechanisms.size
                )
            )
        } catch (e: Exception) {
            AuthMigration.logger.e("Failed to export SQL data: ${e.message}", e)
            throw IllegalStateException(
                "Failed to read SQLCipher database. Ensure the database exists and passphrase is correct.",
                e
            )
        } finally {
            database?.close()
        }
    }

    /**
     * Gets the database passphrase for SQLCipher.
     * Priority:
     * 1. Configured passphrase (if provided)
     * 2. Retrieve from DataStore using KeyStorePassphraseProvider (new Ping SDK storage)
     * 3. Retrieve from Legacy SDK's PassphraseManager storage (LocalKeyStore or BlockStore)
     */
    private suspend fun getDatabasePassphrase(): String {
        // Use configured passphrase if provided
        if (!config.databasePassphrase.isNullOrEmpty()) {
            AuthMigration.logger.d("Using configured database passphrase ${config.databasePassphrase}")
            return config.databasePassphrase
        }

        // Try to retrieve passphrase from Legacy SDK's PassphraseManager storage
        try {
            val legacyPassphraseManager = LegacyPassphraseManager(context)

            if (legacyPassphraseManager.hasPassphrase()) {
                val passphrase = legacyPassphraseManager.getPassphrase()
                if (passphrase != null) {
                    AuthMigration.logger.d("Retrieved passphrase from Legacy SDK PassphraseManager (LocalKeyStore): $passphrase")
                    return passphrase
                }
            }
        } catch (e: Exception) {
            AuthMigration.logger.w("Failed to retrieve passphrase from Legacy PassphraseManager: ${e.message}", e)
        }

        // Try to retrieve passphrase from new Ping SDK's DataStore using KeyStore encryption
        try {
            val passphraseProvider = KeyStorePassphraseProvider(
                context = context,
                logger = AuthMigration.logger
            )

            val passphrase = passphraseProvider.getPassphrase()
            AuthMigration.logger.d("Retrieved passphrase from Ping SDK DataStore using KeyStore encryption: $passphrase")
            return passphrase
        } catch (e: Exception) {
            AuthMigration.logger.w("Failed to retrieve passphrase from Ping SDK DataStore: ${e.message}", e)
        }

        throw IllegalStateException(
            "Unable to retrieve database passphrase. " +
            "Please provide it via config.databasePassphrase or ensure the passphrase is stored in DataStore or Legacy PassphraseManager."
        )
    }

    /**
     * Builds Account JSON from database cursor.
     * Uses column names instead of indices to handle schema variations.
     */
    private fun buildAccountJson(cursor: android.database.Cursor): String {
        fun getStringOrNull(columnName: String): String? {
            val index = cursor.getColumnIndex(columnName)
            return if (index >= 0 && !cursor.isNull(index)) cursor.getString(index) else null
        }

        fun getLongOrNull(columnName: String): Long? {
            val index = cursor.getColumnIndex(columnName)
            return if (index >= 0 && !cursor.isNull(index)) cursor.getLong(index) else null
        }

        fun getBoolean(columnName: String): Boolean {
            val index = cursor.getColumnIndex(columnName)
            return if (index >= 0 && !cursor.isNull(index)) cursor.getInt(index) == 1 else false
        }

        val id = getStringOrNull("id") ?: throw IllegalStateException("Account ID is required")
        val issuer = getStringOrNull("issuer") ?: throw IllegalStateException("Issuer is required")
        val displayIssuer = getStringOrNull("displayIssuer") ?: getStringOrNull("display_issuer")
        val accountName = getStringOrNull("account_name") ?: throw IllegalStateException("Account name is required")
        val displayAccountName = getStringOrNull("displayAccountName") ?: getStringOrNull("display_account_name")
        val imageURL = getStringOrNull("imageURL") ?: getStringOrNull("image_url")
        val backgroundColor = getStringOrNull("backgroundColor") ?: getStringOrNull("background_color")
        val timeAdded = getLongOrNull("time_added")
        val policies = getStringOrNull("policies")
        val lockingPolicy = getStringOrNull("lockingPolicy") ?: getStringOrNull("locking_policy")
        val lock = getBoolean("lock") || getBoolean("is_locked")

        return buildString {
            append("{")
            append("\"id\":\"$id\",")
            append("\"issuer\":\"$issuer\",")
            if (displayIssuer != null) append("\"displayIssuer\":\"$displayIssuer\",")
            append("\"accountName\":\"$accountName\",")
            if (displayAccountName != null) append("\"displayAccountName\":\"$displayAccountName\",")
            if (imageURL != null) append("\"imageURL\":\"$imageURL\",")
            if (backgroundColor != null) append("\"backgroundColor\":\"$backgroundColor\",")
            if (timeAdded != null) append("\"timeAdded\":$timeAdded,")
            if (policies != null) append("\"policies\":${escapeJson(policies)},")
            if (lockingPolicy != null) append("\"lockingPolicy\":\"$lockingPolicy\",")
            append("\"lock\":$lock")
            append("}")
        }
    }

    /**
     * Builds Mechanism JSON from database cursor.
     * Uses column names instead of indices to handle schema variations.
     */
    private fun buildMechanismJson(cursor: android.database.Cursor): String {
        fun getStringOrNull(columnName: String): String? {
            val index = cursor.getColumnIndex(columnName)
            return if (index >= 0 && !cursor.isNull(index)) cursor.getString(index) else null
        }

        fun getIntOrNull(columnName: String): Int? {
            val index = cursor.getColumnIndex(columnName)
            return if (index >= 0 && !cursor.isNull(index)) cursor.getInt(index) else null
        }

        fun getLongOrNull(columnName: String): Long? {
            val index = cursor.getColumnIndex(columnName)
            return if (index >= 0 && !cursor.isNull(index)) cursor.getLong(index) else null
        }

        val id = getStringOrNull("id") ?: throw IllegalStateException("Mechanism ID is required")
        val mechanismUID = getStringOrNull("mechanism_uid") ?: throw IllegalStateException("Mechanism UID is required")
        val issuer = getStringOrNull("issuer") ?: throw IllegalStateException("Issuer is required")
        val accountName = getStringOrNull("account_name") ?: throw IllegalStateException("Account name is required")
        val type = getStringOrNull("type") ?: throw IllegalStateException("Type is required")
        val secret = getStringOrNull("secret") ?: throw IllegalStateException("Secret is required")
        val uid = getStringOrNull("uid")
        val resourceId = getStringOrNull("resourceId") ?: getStringOrNull("resource_id")
        val timeAdded = getLongOrNull("time_added")
        val oathType = getStringOrNull("oathType") ?: getStringOrNull("oath_type")
        val algorithm = getStringOrNull("algorithm")
        val digits = getIntOrNull("digits")
        val counter = getLongOrNull("counter")
        val period = getIntOrNull("period")
        val registrationEndpoint = getStringOrNull("registrationEndpoint") ?: getStringOrNull("registration_endpoint")
        val authenticationEndpoint = getStringOrNull("authenticationEndpoint") ?: getStringOrNull("authentication_endpoint")

        return buildString {
            append("{")
            append("\"id\":\"$id\",")
            append("\"mechanismUID\":\"$mechanismUID\",")
            append("\"issuer\":\"$issuer\",")
            append("\"accountName\":\"$accountName\",")
            append("\"type\":\"$type\",")
            append("\"secret\":\"$secret\",")
            if (uid != null) append("\"uid\":\"$uid\",")
            if (resourceId != null) append("\"resourceId\":\"$resourceId\",")
            if (timeAdded != null) append("\"timeAdded\":$timeAdded,")
            if (oathType != null) append("\"oathType\":\"$oathType\",")
            if (algorithm != null) append("\"algorithm\":\"$algorithm\",")
            if (digits != null) append("\"digits\":$digits,")
            if (counter != null) append("\"counter\":$counter,")
            if (period != null) append("\"period\":$period,")
            if (registrationEndpoint != null) append("\"registrationEndpoint\":\"$registrationEndpoint\",")
            if (authenticationEndpoint != null) append("\"authenticationEndpoint\":\"$authenticationEndpoint\",")
            // Remove trailing comma
            setLength(length - 1)
            append("}")
        }
    }

    /**
     * Escapes JSON string values.
     */
    private fun escapeJson(value: String): String {
        return value.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    /**
     * Export legacy data to JSON string.
     * This can be saved to a file and provided to the migration process later.
     */
    suspend fun exportToJson(): String = withContext(Dispatchers.IO) {
        val exportedData = exportAllData()
        legacyJson.encodeToString(LegacyExportedData.serializer(), exportedData)
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

    /**
     * Creates backup copies of SharedPreferences XML files.
     * Backups are stored in the same directory with a timestamp suffix.
     *
     * @return List of backup file paths that were created
     */
    private suspend fun backupSharedPreferences(): List<String> = withContext(Dispatchers.IO) {
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
     * Delete legacy data after successful migration.
     * Handles both XML (SharedPreferences) and SQL (database) based on migration type.
     * Optionally creates backup copies before deletion based on configuration.
     */
    suspend fun deleteLegacyData() = withContext(Dispatchers.IO) {
        val migrationType = determineMigrationType()

        AuthMigration.logger.i("Cleaning up legacy data using $migrationType migration")

        when (migrationType) {
            MigrationType.XML -> deleteXmlData()
            MigrationType.SQL -> deleteSqlData()
            MigrationType.AUTO -> {
                // Clean up both if they exist
                if (isXmlDataExists()) {
                    deleteXmlData()
                }
                if (isSqlDataExists()) {
                    deleteSqlData()
                }
            }
        }
    }

    /**
     * Delete XML-based legacy data (SharedPreferences).
     */
    private suspend fun deleteXmlData() = withContext(Dispatchers.IO) {
        // Create backups if configured
        if (config.backupFiles) {
            AuthMigration.logger.i("Backing up SharedPreferences files before deletion")
            val backupPaths = backupSharedPreferences()
            if (backupPaths.isNotEmpty()) {
                AuthMigration.logger.i("Backup files created: ${backupPaths.joinToString()}")
            }
        }

        // Delete SharedPreferences files (works for both encrypted and unencrypted)
        context.deleteSharedPreferences(AUTH_DATA_ACCOUNT)
        context.deleteSharedPreferences(AUTH_DATA_MECHANISM)

        // Delete encryption key if it exists (only for default storage)
        try {
            if (decryptor.keyExists()) {
                val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore")
                keyStore.load(null)
                keyStore.deleteEntry(config.keyAlias)
                AuthMigration.logger.d("Deleted legacy encryption key: ${config.keyAlias}")
            }
        } catch (e: Exception) {
            AuthMigration.logger.w("Failed to delete encryption key (may not exist): ${e.message}")
        }
    }

    /**
     * Delete SQL-based legacy data (database files).
     */
    private suspend fun deleteSqlData() = withContext(Dispatchers.IO) {
        val dbFile = context.getDatabasePath(config.databaseName)
        val dbDir = dbFile.parentFile

        if (dbDir == null || !dbFile.exists()) {
            AuthMigration.logger.w("Legacy database not found: ${config.databaseName}, nothing to cleanup")
            return@withContext
        }

        // Create backup if configured
        if (config.backupFiles) {
            try {
                val timestamp = System.currentTimeMillis()
                val backupName = config.databaseName.removeSuffix(".db")
                val backupFile = File(dbDir, "${backupName}_backup_$timestamp.db")
                dbFile.copyTo(backupFile, overwrite = false)
                AuthMigration.logger.i("Created database backup: ${backupFile.absolutePath}")
            } catch (e: Exception) {
                AuthMigration.logger.e("Failed to create database backup: ${e.message}", e)
            }
        }

        AuthMigration.logger.i("Cleaning up legacy SQL database: ${config.databaseName}")

        var cleanedUp = false

        // Delete main database file
        if (dbFile.exists() && dbFile.delete()) {
            AuthMigration.logger.d("Deleted: ${config.databaseName}")
            cleanedUp = true
        }

        // Delete journal file
        val journalFile = File(dbDir, "${config.databaseName}-journal")
        if (journalFile.exists() && journalFile.delete()) {
            AuthMigration.logger.d("Deleted: ${config.databaseName}-journal")
        }

        // Delete WAL file (Write-Ahead Logging)
        val walFile = File(dbDir, "${config.databaseName}-wal")
        if (walFile.exists() && walFile.delete()) {
            AuthMigration.logger.d("Deleted: ${config.databaseName}-wal")
        }

        // Delete SHM file (Shared Memory)
        val shmFile = File(dbDir, "${config.databaseName}-shm")
        if (shmFile.exists() && shmFile.delete()) {
            AuthMigration.logger.d("Deleted: ${config.databaseName}-shm")
        }

        if (cleanedUp) {
            AuthMigration.logger.i("Legacy database cleanup completed successfully")
        }
    }
}


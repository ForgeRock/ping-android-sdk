/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.oath.storage

import android.content.Context
import com.pingidentity.android.ContextProvider
import com.pingidentity.logger.Logger
import com.pingidentity.mfa.commons.exception.MfaStorageException
import com.pingidentity.mfa.oath.OathAlgorithm
import com.pingidentity.mfa.oath.OathCredential
import com.pingidentity.mfa.oath.storage.OathStorage
import com.pingidentity.mfa.oath.OathType
import com.pingidentity.storage.sqlite.passphrase.KeyStorePassphraseProvider
import com.pingidentity.storage.sqlite.passphrase.PassphraseProvider
import com.pingidentity.storage.sqlite.SQLiteStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import net.sqlcipher.Cursor
import java.util.Date
import kotlin.coroutines.coroutineContext

/**
 * SQLite-based implementation of [OathStorage].
 * This class directly extends [SQLiteStorage] with OATH-specific functionality.
 */
class SQLOathStorage private constructor(
    context: Context,
    databaseName: String,
    databaseVersion: Int = 1,
    passphraseProvider: PassphraseProvider,
    override val logger: Logger = Logger.logger
) : SQLiteStorage(
    context = context,
    databaseName = databaseName,
    databaseVersion = databaseVersion,
    passphraseProvider = passphraseProvider,
    logger = logger
), OathStorage {

    /**
     * Builder-style DSL constructor for SQLOathStorage.
     */
    constructor(block: Builder.() -> Unit) : this(
        Builder().apply(block)
    )

    /**
     * Internal constructor to support creation from Builder.
     */
    private constructor(builder: Builder) : this(
        builder.context,
        builder.databaseName,
        builder.databaseVersion,
        builder.passphraseProvider,
        builder.logger
    )

    companion object {
        private const val DEFAULT_DATABASE_NAME = "pingidentity_mfa.db"
        
        // OATH credential specific columns
        private const val OATH_COLUMN_ID = "id"
        private const val OATH_COLUMN_USER_ID = "user_id"
        private const val OATH_COLUMN_RESOURCE_ID = "resource_id"
        private const val OATH_COLUMN_ISSUER = "issuer"
        private const val OATH_COLUMN_DISPLAY_ISSUER = "display_issuer"
        private const val OATH_COLUMN_ACCOUNT_NAME = "account_name"
        private const val OATH_COLUMN_DISPLAY_ACCOUNT_NAME = "display_account_name"
        private const val OATH_COLUMN_TYPE = "type"
        private const val OATH_COLUMN_SECRET = "secret"
        private const val OATH_COLUMN_ALGORITHM = "algorithm"
        private const val OATH_COLUMN_DIGITS = "digits"
        private const val OATH_COLUMN_PERIOD = "period"
        private const val OATH_COLUMN_COUNTER = "counter"
        private const val OATH_COLUMN_CREATED_AT = "created_at"
        private const val OATH_COLUMN_IMAGE_URL = "image_url"
        private const val OATH_COLUMN_BACKGROUND_COLOR = "background_color"
        private const val OATH_COLUMN_POLICIES = "policies"
        private const val OATH_COLUMN_LOCKING_POLICY = "locking_policy"
        private const val OATH_COLUMN_IS_LOCKED = "is_locked"
        
        // Table name for OATH credentials
        private const val TABLE_PREFIX = "mfa_"
        private const val OATH_TABLE = "${TABLE_PREFIX}oath_data"
    }
    
    /**
     * Builder class for configuring SQLOathStorage.
     */
    class Builder {
        var context: Context = ContextProvider.context
        var databaseName: String = DEFAULT_DATABASE_NAME
        var databaseVersion: Int = 1
        var initialPassphrase: String? = null // Default is null, in case developer does not want to supply their own passphrase
        var passphraseProvider: PassphraseProvider = KeyStorePassphraseProvider(context, initialPassphrase)
        var logger: Logger = Logger.logger
    }
    
    init {
        // Register the OATH table creator
        registerTableCreator { db ->
            // Create OATH table
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS $OATH_TABLE (" +
                        "$OATH_COLUMN_ID TEXT PRIMARY KEY, " +
                        "$OATH_COLUMN_USER_ID TEXT, " +
                        "$OATH_COLUMN_RESOURCE_ID TEXT, " +
                        "$OATH_COLUMN_ISSUER TEXT NOT NULL, " +
                        "$OATH_COLUMN_DISPLAY_ISSUER TEXT NOT NULL, " +
                        "$OATH_COLUMN_ACCOUNT_NAME TEXT NOT NULL, " +
                        "$OATH_COLUMN_DISPLAY_ACCOUNT_NAME TEXT NOT NULL, " +
                        "$OATH_COLUMN_TYPE TEXT NOT NULL, " + // Will store 'TOTP' or 'HOTP'
                        "$OATH_COLUMN_SECRET TEXT NOT NULL, " +
                        "$OATH_COLUMN_ALGORITHM TEXT NOT NULL, " + // Will store 'SHA1', 'SHA256', or 'SHA512'
                        "$OATH_COLUMN_DIGITS INTEGER NOT NULL DEFAULT 6, " +
                        "$OATH_COLUMN_PERIOD INTEGER DEFAULT 30, " +
                        "$OATH_COLUMN_COUNTER INTEGER DEFAULT 0, " +
                        "$OATH_COLUMN_CREATED_AT INTEGER NOT NULL, " + // Will store timestamp
                        "$OATH_COLUMN_IMAGE_URL TEXT, " +
                        "$OATH_COLUMN_BACKGROUND_COLOR TEXT, " +
                        "$OATH_COLUMN_POLICIES TEXT, " +
                        "$OATH_COLUMN_LOCKING_POLICY TEXT, " +
                        "$OATH_COLUMN_IS_LOCKED INTEGER NOT NULL DEFAULT 0)" // SQLite doesn't have a boolean type
            )
            
            // Create OATH indexes
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_oath_issuer ON $OATH_TABLE ($OATH_COLUMN_ISSUER)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_oath_user_id ON $OATH_TABLE ($OATH_COLUMN_USER_ID)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_oath_resource_id ON $OATH_TABLE ($OATH_COLUMN_RESOURCE_ID)")
        }
        
        logger.d("OATH SQL storage created")
    }
    
    /**
     * Initialize the OATH storage.
     * This method initializes the database and tables.
     *
     * @throws MfaStorageException if initialization fails.
     */
    override suspend fun initialize() {
        try {
            // Initialize the database
            initializeDatabase()
            logger.d("OATH storage initialized")
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            throw MfaStorageException("Failed to initialize OATH storage", e)
        }
    }
    
    /**
     * Clear all data from the storage.
     * This method clears all OATH credential tables.
     *
     * @throws MfaStorageException if the storage cannot be cleared.
     */
    override suspend fun clear() {
        try {
            clearOathCredentials()
            logger.d("Cleared all data from OATH storage")
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            logger.e("Failed to clear OATH storage: ${e.message}", e)
            throw MfaStorageException("Failed to clear OATH storage", e)
        }
    }
    
    /**
     * Close the OATH storage.
     * This method closes the database connection.
     */
    override suspend fun close() {
        try {
            closeDatabase() 
            logger.d("OATH SQL storage closed")
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            logger.e("Error closing OATH storage: ${e.message}", e)
        }
    }

    /**
     * Store an OATH credential.
     *
     * @param credential The OATH credential to be stored.
     * @throws MfaStorageException if the credential cannot be stored.
     */
    override suspend fun storeOathCredential(credential: OathCredential) {
        try {
            // Convert the boolean to an integer (0 or 1)
            val isLockedAsInt = if (credential.isLocked) 1L else 0L
            
            val data = mapOf(
                OATH_COLUMN_ID to credential.id,
                OATH_COLUMN_USER_ID to credential.userId,
                OATH_COLUMN_RESOURCE_ID to credential.resourceId,
                OATH_COLUMN_ISSUER to credential.issuer,
                OATH_COLUMN_DISPLAY_ISSUER to credential.displayIssuer,
                OATH_COLUMN_ACCOUNT_NAME to credential.accountName,
                OATH_COLUMN_DISPLAY_ACCOUNT_NAME to credential.displayAccountName,
                OATH_COLUMN_TYPE to credential.oathType.name,
                OATH_COLUMN_SECRET to credential.secret,
                OATH_COLUMN_ALGORITHM to credential.oathAlgorithm.name,
                OATH_COLUMN_DIGITS to credential.digits.toLong(),
                OATH_COLUMN_PERIOD to credential.period.toLong(),
                OATH_COLUMN_COUNTER to credential.counter,
                OATH_COLUMN_CREATED_AT to credential.createdAt.time,
                OATH_COLUMN_IMAGE_URL to credential.imageURL,
                OATH_COLUMN_BACKGROUND_COLOR to credential.backgroundColor,
                OATH_COLUMN_POLICIES to credential.policies,
                OATH_COLUMN_LOCKING_POLICY to credential.lockingPolicy,
                OATH_COLUMN_IS_LOCKED to isLockedAsInt
            )

            storeOathCredentialData(credential.id, data)
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            throw MfaStorageException("Failed to store OATH credential with ID ${credential.id}", e)
        }
    }
    
    /**
     * Store OATH credential data in the database.
     *
     * @param credentialId The ID of the credential.
     * @param data The credential data.
     * @throws MfaStorageException if the credential cannot be stored.
     */
    private suspend fun storeOathCredentialData(credentialId: String, data: Map<String, Any?>) = withContext(Dispatchers.IO) {
        checkDatabase()
        
        try {
            // Begin a transaction to ensure data consistency
            beginTransaction()
            
            try {
                // Delete any existing credential with this ID
                database.delete(
                    OATH_TABLE,
                    "$OATH_COLUMN_ID = ?",
                    arrayOf(credentialId)
                )
                
                // Build the column names and placeholders for the SQL statement
                val columns = data.keys.joinToString(", ")
                val placeholders = data.keys.joinToString(", ") { "?" }
                
                // Extract values in the same order as columns
                val values = data.values.toTypedArray()
                
                // Log the SQL statement for debugging
                val sqlStatement = "INSERT INTO $OATH_TABLE ($columns) VALUES ($placeholders)"
                logger.d("Executing SQL: $sqlStatement")
                logger.d("With values: ${values.joinToString(", ") { it?.toString() ?: "null" }}")
                
                // Insert the new credential
                executeSQL(sqlStatement, values)
                
                // Mark the transaction as successful
                setTransactionSuccessful()
                
                logger.d("Stored OATH credential with ID: $credentialId")
            } finally {
                // End the transaction (will be rolled back if not marked successful)
                endTransaction()
            }
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            logger.e("Failed to store OATH credential with ID $credentialId: ${e.message}", e)
            throw MfaStorageException("Failed to store OATH credential with ID $credentialId", e)
        }
    }
    
    /**
     * Retrieve an OATH credential by its ID.
     *
     * @param credentialId The ID of the credential to retrieve.
     * @return The OATH credential, or null if not found.
     * @throws MfaStorageException if the credential cannot be retrieved.
     */
    override suspend fun retrieveOathCredential(credentialId: String): OathCredential? {
        try {
            val data = retrieveOathCredentialData(credentialId) ?: return null
            return createOathCredentialFromData(data)
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            throw MfaStorageException("Failed to retrieve OATH credential with ID $credentialId", e)
        }
    }
    
    /**
     * Retrieve OATH credential data from the database.
     *
     * @param credentialId The ID of the credential to retrieve.
     * @return The credential data, or null if not found.
     * @throws MfaStorageException if the credential cannot be retrieved.
     */
    private suspend fun retrieveOathCredentialData(credentialId: String): Map<String, Any?>? = withContext(Dispatchers.IO) {
        checkDatabase()
        
        try {
            // Use the query method from the parent class
            val results = query(
                "SELECT * FROM $OATH_TABLE WHERE $OATH_COLUMN_ID = ?",
                arrayOf(credentialId)
            ) { cursor ->
                extractDataFromCursor(cursor)
            }
            
            return@withContext results.firstOrNull()
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            logger.e("Failed to retrieve OATH credential with ID $credentialId: ${e.message}", e)
            throw MfaStorageException("Failed to retrieve OATH credential with ID $credentialId", e)
        }
    }
    
    /**
     * Get all OATH credentials.
     *
     * @return A list of all OATH credentials.
     * @throws MfaStorageException if the credentials cannot be retrieved.
     */
    override suspend fun getAllOathCredentials(): List<OathCredential> {
        try {
            val dataList = retrieveAllOathCredentialsData()
            return dataList.mapNotNull { createOathCredentialFromData(it) }
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            throw MfaStorageException("Failed to retrieve all OATH credentials", e)
        }
    }
    
    /**
     * Retrieve all OATH credential data from the database.
     *
     * @return A list of credential data.
     * @throws MfaStorageException if the credentials cannot be retrieved.
     */
    private suspend fun retrieveAllOathCredentialsData(): List<Map<String, Any?>> = withContext(Dispatchers.IO) {
        checkDatabase()
        
        try {
            // Use the query method from the parent class
            return@withContext query(
                "SELECT * FROM $OATH_TABLE",
                null
            ) { cursor ->
                extractDataFromCursor(cursor)
            }
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            logger.e("Failed to retrieve all OATH credentials: ${e.message}", e)
            throw MfaStorageException("Failed to retrieve all OATH credentials", e)
        }
    }
    
    /**
     * Remove an OATH credential by its ID.
     *
     * @param credentialId The ID of the credential to remove.
     * @return true if the credential was successfully removed, false if it didn't exist.
     * @throws MfaStorageException if the credential cannot be removed.
     */
    override suspend fun removeOathCredential(credentialId: String): Boolean {
        try {
            return deleteOathCredential(credentialId)
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            throw MfaStorageException("Failed to remove OATH credential with ID $credentialId", e)
        }
    }
    
    /**
     * Delete an OATH credential from the database.
     *
     * @param credentialId The ID of the credential to delete.
     * @return true if the credential was successfully deleted, false if it didn't exist.
     * @throws MfaStorageException if the credential cannot be deleted.
     */
    private suspend fun deleteOathCredential(credentialId: String): Boolean = withContext(Dispatchers.IO) {
        checkDatabase()
        
        try {
            val deletedRows = database.delete(
                OATH_TABLE,
                "$OATH_COLUMN_ID = ?",
                arrayOf(credentialId)
            )
            
            val wasDeleted = deletedRows > 0
            if (wasDeleted) {
                logger.d("Deleted OATH credential with ID: $credentialId")
            } else {
                logger.d("No OATH credential with ID: $credentialId found to delete")
            }
            
            return@withContext wasDeleted
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            logger.e("Failed to delete OATH credential with ID $credentialId: ${e.message}", e)
            throw MfaStorageException("Failed to delete OATH credential with ID $credentialId", e)
        }
    }
    
    /**
     * Clear all OATH credentials from the storage.
     *
     * @throws MfaStorageException if the credentials cannot be cleared.
     */
    override suspend fun clearOathCredentials() {
        try {
            clearAllOathCredentials()
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            throw MfaStorageException("Failed to clear OATH credentials", e)
        }
    }
    
    /**
     * Clear all OATH credentials from the database.
     *
     * @throws MfaStorageException if the credentials cannot be cleared.
     */
    private suspend fun clearAllOathCredentials() = withContext(Dispatchers.IO) {
        checkDatabase()
        
        try {
            database.delete(OATH_TABLE, null, null)
            logger.d("Cleared all OATH credentials")
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            logger.e("Failed to clear OATH credentials: ${e.message}", e)
            throw MfaStorageException("Failed to clear OATH credentials", e)
        }
    }
    
    /**
     * Extract data from a cursor into a map.
     *
     * @param cursor The cursor to extract data from.
     * @return A map of column names to values.
     */
    private fun extractDataFromCursor(cursor: Cursor): Map<String, Any?> {
        val data = mutableMapOf<String, Any?>()
        
        // Extract column names
        val columnNames = cursor.columnNames
        
        // Extract data for each column
        for (i in 0 until cursor.columnCount) {
            val columnName = columnNames[i]
            val columnType = cursor.getType(i)
            
            // Extract the value based on column type
            val value: Any? = when (columnType) {
                Cursor.FIELD_TYPE_NULL -> null
                Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(i)
                Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(i)
                Cursor.FIELD_TYPE_STRING -> cursor.getString(i)
                Cursor.FIELD_TYPE_BLOB -> cursor.getBlob(i)
                else -> null
            }
            
            data[columnName] = value
        }
        
        return data
    }
    
    /**
     * Create an OATH credential from a data map.
     *
     * @param data The data map to create the credential from.
     * @return The created OATH credential.
     */
    private fun createOathCredentialFromData(data: Map<String, Any?>): OathCredential? {
        try {
            val id = data[OATH_COLUMN_ID] as String
            val userId = data[OATH_COLUMN_USER_ID] as? String
            val resourceId = data[OATH_COLUMN_RESOURCE_ID] as? String
            val issuer = data[OATH_COLUMN_ISSUER] as String
            val displayIssuer = data[OATH_COLUMN_DISPLAY_ISSUER] as String
            val accountName = data[OATH_COLUMN_ACCOUNT_NAME] as String
            val displayAccountName = data[OATH_COLUMN_DISPLAY_ACCOUNT_NAME] as String
            val typeStr = data[OATH_COLUMN_TYPE] as String
            val secret = data[OATH_COLUMN_SECRET] as String
            val algorithmStr = data[OATH_COLUMN_ALGORITHM] as String
            val digits = (data[OATH_COLUMN_DIGITS] as Long).toInt()
            val period = (data[OATH_COLUMN_PERIOD] as Long).toInt()
            val counter = data[OATH_COLUMN_COUNTER] as Long
            val createdAt = Date(data[OATH_COLUMN_CREATED_AT] as Long)
            val imageURL = data[OATH_COLUMN_IMAGE_URL] as String?
            val backgroundColor = data[OATH_COLUMN_BACKGROUND_COLOR] as String?
            val policies = data[OATH_COLUMN_POLICIES] as String?
            val lockingPolicy = data[OATH_COLUMN_LOCKING_POLICY] as String?
            val isLocked = (data[OATH_COLUMN_IS_LOCKED] as Long) == 1L
            
            // Parse enum values
            val type = OathType.valueOf(typeStr)
            val algorithm = OathAlgorithm.valueOf(algorithmStr)
            
            // Create and return the credential
            return OathCredential(
                id = id,
                userId = userId,
                resourceId = resourceId,
                issuer = issuer,
                displayIssuer = displayIssuer,
                accountName = accountName,
                displayAccountName = displayAccountName,
                oathType = type,
                secret = secret,
                oathAlgorithm = algorithm,
                digits = digits,
                period = period,
                counter = counter,
                createdAt = createdAt,
                imageURL = imageURL,
                backgroundColor = backgroundColor,
                policies = policies,
                lockingPolicy = lockingPolicy,
                isLocked = isLocked
            )
        } catch (e: Exception) {
            throw MfaStorageException("Error creating OATH credential from data: ${e.message}")
        }
    }
}
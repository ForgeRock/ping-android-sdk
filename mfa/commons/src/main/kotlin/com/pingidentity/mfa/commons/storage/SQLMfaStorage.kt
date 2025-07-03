/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.commons.storage

import android.content.Context
import android.util.Log
import com.pingidentity.mfa.commons.MfaStorage
import com.pingidentity.mfa.commons.exception.MfaStorageException
import com.pingidentity.storage.sqlite.SQLiteStorage
import net.sqlcipher.Cursor
import net.sqlcipher.database.SQLiteDatabase

/**
 * Implementation of [MfaStorage] that uses the foundation SQLiteStorage for underlying storage.
 * This class provides a secure storage solution for MFA credentials.
 * Specific credential type implementations should extend this class and provide
 * their own table definitions through table creator functions.
 */
open class SQLMfaStorage(
    context: Context,
    databaseName: String = DEFAULT_DATABASE_NAME,
    encryptionEnabled: Boolean = true,
    secretKey: String? = null,
    blockStorePreferred: Boolean = false
) : SQLiteStorage(context, databaseName, encryptionEnabled, secretKey, blockStorePreferred), MfaStorage {

    companion object {
        private const val TAG = "SQLMfaStorage"
        private const val DEFAULT_DATABASE_NAME = "pingidentity_mfa.db"
    }

    // Map of credential types to table names
    protected val tableNames = mutableMapOf<String, String>()
    
    init {
        // Don't initialize the database in the init block - it will be initialized
        // when the initialize() method is called
        Log.d(TAG, "MFA SQL storage created")
    }

    /**
     * Register a credential table for a specific credential type.
     * This method must be called to register tables for credential types before using them.
     *
     * @param credentialType The type of credential.
     * @param tableName The name of the table for this credential type.
     * @param tableCreator A function that creates the table for this credential type.
     */
    fun registerCredentialTable(credentialType: String, tableName: String, tableCreator: (SQLiteDatabase) -> Unit) {
        tableNames[credentialType] = tableName
        registerTableCreator(tableCreator)
        Log.d(TAG, "Registered table '$tableName' for credential type: $credentialType")
    }

    /**
     * Get the table name for a credential type.
     * If no specific table is registered for the type, throws an exception.
     */
    protected fun getTableNameForType(credentialType: String): String {
        return tableNames[credentialType] ?: throw MfaStorageException("No table defined for credential type: $credentialType")
    }

    /**
     * Initialize the MFA storage.
     * This method delegates to the SQLiteStorage initializeDatabase method and then performs
     * MFA-specific initialization.
     *
     * @throws MfaStorageException if initialization fails.
     */
    @Throws(MfaStorageException::class)
    override fun initialize() {
        try {
            // Call the parent's initializeDatabase method to set up the database
            initializeDatabase()
            Log.d(TAG, "MFA storage initialized")
        } catch (e: Exception) {
            throw MfaStorageException("Failed to initialize MFA storage", e)
        }
    }
    
    /**
     * Clear all data from MFA storage.
     * This method clears all credential tables.
     *
     * @throws MfaStorageException if the storage cannot be cleared.
     */
    @Throws(MfaStorageException::class)
    override fun clear() {
        try {
            // Clear each registered credential table
            checkDatabase()
            
            database?.let { db ->
                tableNames.values.forEach { tableName ->
                    try {
                        db.delete(tableName, null, null)
                        Log.d(TAG, "Cleared table: $tableName")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error clearing table $tableName: ${e.message}")
                        // Continue with other tables even if one fails
                    }
                }
            }
            
            Log.d(TAG, "Cleared all data from MFA storage")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear MFA storage: ${e.message}")
            throw MfaStorageException("Failed to clear MFA storage", e)
        }
    }
    
    /**
     * Close the MFA storage.
     * This method delegates to the SQLiteStorage close method.
     */
    override fun close() {
        // Note: we can't call SQLiteStorage.closeDatabase() directly because it's not virtual
        // but we can wrap it and log extra information
        try {
            closeDatabase() // Call the parent close method 
            Log.d(TAG, "MFA SQL storage closed")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing MFA storage: ${e.message}")
        }
    }

    /**
     * Generic method to store a credential.
     * This implementation expects subclasses to define appropriate tables.
     *
     * @param credentialType The type of credential.
     * @param credentialId The ID of the credential.
     * @param data The credential data.
     * @throws MfaStorageException if the credential cannot be stored.
     */
    @Throws(MfaStorageException::class)
    fun storeCredential(credentialType: String, credentialId: String, data: Map<String, Any?>) {
        try {
            val tableName = getTableNameForType(credentialType)
            
            // Begin a transaction to ensure data consistency
            beginTransaction()
            
            try {
                // Delete any existing credential with this ID
                database?.delete(
                    tableName,
                    "id = ?",
                    arrayOf(credentialId)
                )
                
                // Build the column names and placeholders for the SQL statement
                val columns = data.keys.joinToString(", ")
                val placeholders = data.keys.joinToString(", ") { "?" }
                
                // Extract values in the same order as columns
                val values = data.values.toTypedArray()
                
                // Log the SQL statement for debugging
                val sqlStatement = "INSERT INTO $tableName ($columns) VALUES ($placeholders)"
                Log.d(TAG, "Executing SQL: $sqlStatement")
                Log.d(TAG, "With values: ${values.joinToString(", ") { it?.toString() ?: "null" }}")
                
                try {
                    // Insert the new credential using parent class method
                    executeSQL(sqlStatement, values)
                    Log.d(TAG, "Insert successful")
                } catch (e: Exception) {
                    Log.e(TAG, "SQL execution error: ${e.message}", e)
                    throw e
                }
                
                // Mark the transaction as successful
                setTransactionSuccessful()
                
                Log.d(TAG, "Stored credential of type '$credentialType' with ID: $credentialId")
            } finally {
                // End the transaction (will be rolled back if not marked successful)
                endTransaction()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to store credential of type '$credentialType' with ID $credentialId: ${e.message}")
            throw MfaStorageException("Failed to store credential of type '$credentialType' with ID $credentialId", e)
        }
    }
    
    /**
     * Generic method to retrieve a credential.
     * This implementation expects subclasses to define appropriate tables.
     *
     * @param credentialType The type of credential.
     * @param credentialId The ID of the credential.
     * @return The credential data, or null if not found.
     * @throws MfaStorageException if the credential cannot be retrieved.
     */
    @Throws(MfaStorageException::class)
    fun retrieveCredential(credentialType: String, credentialId: String): Map<String, Any?>? {
        try {
            val tableName = getTableNameForType(credentialType)
            
            // Use the query method from the parent class
            val results = query(
                "SELECT * FROM $tableName WHERE id = ?",
                arrayOf(credentialId)
            ) { cursor ->
                extractDataFromCursor(cursor)
            }
            
            return results.firstOrNull()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to retrieve credential of type '$credentialType' with ID $credentialId: ${e.message}")
            throw MfaStorageException("Failed to retrieve credential of type '$credentialType' with ID $credentialId", e)
        }
    }
    
    /**
     * Generic method to retrieve all credentials of a specific type.
     * This implementation expects subclasses to define appropriate tables.
     *
     * @param credentialType The type of credential.
     * @return A list of credential data.
     * @throws MfaStorageException if the credentials cannot be retrieved.
     */
    @Throws(MfaStorageException::class)
    fun retrieveAllCredentials(credentialType: String): List<Map<String, Any?>> {
        try {
            val tableName = getTableNameForType(credentialType)
            
            // Use the query method from the parent class
            return query(
                "SELECT * FROM $tableName",
                null
            ) { cursor ->
                extractDataFromCursor(cursor)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to retrieve all credentials of type '$credentialType': ${e.message}")
            throw MfaStorageException("Failed to retrieve all credentials of type '$credentialType'", e)
        }
    }
    
    /**
     * Generic method to delete a credential.
     * This implementation expects subclasses to define appropriate tables.
     *
     * @param credentialType The type of credential.
     * @param credentialId The ID of the credential.
     * @return true if the credential was successfully deleted, false if it didn't exist.
     * @throws MfaStorageException if the credential cannot be deleted.
     */
    @Throws(MfaStorageException::class)
    fun deleteCredential(credentialType: String, credentialId: String): Boolean {
        try {
            val tableName = getTableNameForType(credentialType)
            
            val deletedRows = database?.delete(
                tableName,
                "id = ?",
                arrayOf(credentialId)
            ) ?: 0
            
            val wasDeleted = deletedRows > 0
            if (wasDeleted) {
                Log.d(TAG, "Deleted credential of type '$credentialType' with ID: $credentialId")
            } else {
                Log.d(TAG, "No credential of type '$credentialType' with ID: $credentialId found to delete")
            }
            
            return wasDeleted
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete credential of type '$credentialType' with ID $credentialId: ${e.message}")
            throw MfaStorageException("Failed to delete credential of type '$credentialType' with ID $credentialId", e)
        }
    }
    
    /**
     * Generic method to clear all credentials of a specific type.
     * This implementation expects subclasses to define appropriate tables.
     *
     * @param credentialType The type of credential.
     * @throws MfaStorageException if the credentials cannot be cleared.
     */
    @Throws(MfaStorageException::class)
    fun clearCredentials(credentialType: String) {
        try {
            val tableName = getTableNameForType(credentialType)
            
            database?.delete(tableName, null, null)
            
            Log.d(TAG, "Cleared all credentials of type: $credentialType")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear credentials of type '$credentialType': ${e.message}")
            throw MfaStorageException("Failed to clear credentials of type '$credentialType'", e)
        }
    }
    
    /**
     * Generic method to update a credential.
     * This delegates to the subclass implementation of storeCredential.
     *
     * @param credentialType The type of credential.
     * @param credentialId The ID of the credential.
     * @param data The credential data.
     * @throws MfaStorageException if the credential cannot be updated.
     */
    @Throws(MfaStorageException::class)
    fun updateCredential(credentialType: String, credentialId: String, data: Map<String, Any?>) {
        // Reuse storeCredential for updating
        storeCredential(credentialType, credentialId, data)
    }
    
    /**
     * Extract data from a cursor into a map.
     *
     * @param cursor The cursor to extract data from.
     * @return A map of column names to values.
     */
    protected fun extractDataFromCursor(cursor: Cursor): Map<String, Any?> {
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
}
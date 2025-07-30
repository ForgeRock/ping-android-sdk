/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.storage.sqlite

import android.content.Context
import com.pingidentity.logger.Logger
import com.pingidentity.storage.exception.StorageException
import com.pingidentity.storage.sqlite.passphrase.KeyStorePassphraseProvider
import com.pingidentity.storage.sqlite.passphrase.PassphraseProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import net.sqlcipher.Cursor
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SQLiteOpenHelper
import kotlin.coroutines.coroutineContext

/**
 * Base implementation for SQLite storage that uses SQLCipher for encrypted storage.
 * This class provides a secure storage solution with a generic approach for different data types.
 * It supports multiple tables through a table registration mechanism.
 *
 * This is an abstract base class that provides common SQLite database functionality.
 * Subclasses should implement their specific storage operations based on their table structure.
 */
open class SQLiteStorage(
    protected val context: Context,
    protected val databaseName: String = DEFAULT_DATABASE_NAME,
    protected val databaseVersion: Int = DATABASE_VERSION,
    protected val passphraseProvider: PassphraseProvider = KeyStorePassphraseProvider(context),
    protected open val logger: Logger = Logger.logger
) {
    companion object {
        private const val DEFAULT_DATABASE_NAME = "pingidentity_storage.db"
        private const val DATABASE_VERSION = 1
    }

    // List of table creator functions
    private val tableCreators = mutableListOf<(SQLiteDatabase) -> Unit>()

    // Database helper for managing the SQLite database
    protected lateinit var dbHelper: SQLiteOpenHelper

    // Internal database property to be accessed through the getter
    private lateinit var internalDatabase: SQLiteDatabase

    // Safe accessor for the database
    val database: SQLiteDatabase
        get() = internalDatabase


    /**
     * Initialize the SQL database storage.
     * This method creates the database and tables if they don't exist.
     *
     * @throws StorageException if the database cannot be opened or the tables cannot be created.
     */
    suspend fun initializeDatabase() = executeOnIO {
        try {
            // Load SQLCipher libraries
            SQLiteDatabase.loadLibs(context)

            // Create database helper
            dbHelper = createDatabaseHelper(context, databaseName, databaseVersion)

            // Get the passphrase from the provider
            val passphrase = passphraseProvider.getPassphrase()
            logger.d("Using passphrase from provider for database initialization")

            try {
                // Open or create the database
                internalDatabase = dbHelper.getWritableDatabase(passphrase)
            } catch (e: Exception) {
                coroutineContext.ensureActive()
                
                // If opening fails, try to recover
                logger.e("Database initialization failed: ${e.message}", e)
                closeAndCleanupDatabase()
                
                // Create a new helper and try again
                dbHelper = createDatabaseHelper(context, databaseName, databaseVersion)
                internalDatabase = dbHelper.getWritableDatabase(passphrase)
            }

            logger.d("SQL storage initialized successfully")

            // Validate database is properly initialized by testing a simple query
            validateDatabaseInitialization()
            
            // Call all registered table creators
            tableCreators.forEach { creator ->
                try {
                    creator(internalDatabase)
                } catch (e: Exception) {
                    coroutineContext.ensureActive()
                    logger.e("Failed to create table: ${e.message}", e)
                }
            }
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            logger.e("Failed to initialize database: ${e.message}", e)
            throw StorageException("Failed to initialize database", e)
        }
    }

    /**
     * Factory method to create the appropriate database helper.
     * This method follows the factory method pattern to allow subclasses to provide their own 
     * DatabaseHelper implementation without modifying the base class.
     * 
     * For example, a subclass might override this to provide a helper with custom migration logic
     * or specialized table creation behavior.
     *
     * @param context The Android context used to create the database
     * @param databaseName The name of the database file
     * @param version The database version
     * @return A SQLiteOpenHelper instance that will manage the database
     */
    protected open fun createDatabaseHelper(context: Context, databaseName: String, version: Int): SQLiteOpenHelper {
        return DatabaseHelper(context, databaseName, version)
    }

    /**
     * Validate that the database is properly initialized by running a simple query.
     */
    private suspend fun validateDatabaseInitialization() {
        executeOnIO {
            if (!internalDatabase.isOpen) {
                throw StorageException("Database was not opened successfully")
            }

            try {
                // Try a simple SQLite query to validate db is operational
                internalDatabase.rawQuery("SELECT count(*) FROM sqlite_master", null).use { cursor ->
                    if (cursor.moveToFirst()) {
                        val count = cursor.getInt(0)
                        logger.d("Database validation query successful, found $count tables")
                    }
                }
            } catch (e: Exception) {
                coroutineContext.ensureActive()
                logger.e("Database validation query failed: ${e.message}", e)
                closeAndCleanupDatabase()
                throw e
            }
        }
    }

    /**
     * Close and cleanup database resources.
     */
    private suspend fun closeAndCleanupDatabase() {
        executeOnIO {
            // Attempt to close internalDatabase
            try {
                if (::internalDatabase.isInitialized && internalDatabase.isOpen) {
                    internalDatabase.use { /* it.close() is called automatically */ }
                }
            } catch (e: Exception) {
                coroutineContext.ensureActive()
                logger.e("Error during internalDatabase.close(): ${e.message}", e)
            }

            // Attempt to close dbHelper
            try {
                if (::dbHelper.isInitialized) {
                    dbHelper.close()
                }
            } catch (e: Exception) {
                coroutineContext.ensureActive()
                logger.e("Error during dbHelper.close(): ${e.message}", e)
            }

            // Delete the database file
            try {
                context.deleteDatabase(databaseName)
                logger.d("Database file deleted successfully")
            } catch (e: Exception) {
                coroutineContext.ensureActive()
                logger.e("Failed to delete database file: ${e.message}", e)
            }

            // Re-initialize the properties - we need to be careful to set up new instances
            // since these are now lateinit
            initializeEmptyInstances()
        }
    }

    /**
     * Initialize empty instances for lateinit properties after cleanup.
     * This is needed because we can't directly set lateinit properties to null.
     */
    private suspend fun initializeEmptyInstances() {
        executeOnIO {
            // Create temporary instances to satisfy lateinit requirements
            dbHelper = createDatabaseHelper(context, databaseName, databaseVersion)
            try {
                internalDatabase = dbHelper.getReadableDatabase("")
                internalDatabase.close()
            } catch (e: Exception) {
                coroutineContext.ensureActive()
                logger.e("Failed to create temporary database instance: ${e.message}", e)
            }
        }
    }

    /**
     * Close the database connection.
     */
    suspend fun closeDatabase() {
        executeOnIO {
            internalDatabase.close()
            dbHelper.close()
            
            logger.d("SQL storage closed")
        }
        
        // Reinitialize with empty instances to satisfy lateinit requirements
        initializeEmptyInstances()
    }

    /**
     * Check if the database is open and throw an exception if it's not.
     * This provides a more informative error message than the default UninitializedPropertyAccessException.
     *
     * @throws StorageException if the database is not open or not properly initialized.
     */
    protected fun checkDatabase() {
        if (!::internalDatabase.isInitialized) {
            throw StorageException("Database has not been initialized. Call initializeDatabase() first.")
        }
        
        if (!internalDatabase.isOpen) {
            throw StorageException("Database is not open. Call initializeDatabase() first or check for errors during initialization.")
        }
    }

    /**
     * Register a table creator function that will be called when the database is created.
     * This allows multiple tables to be created in the same database.
     *
     * @param creator A function that creates a table in the given SQLiteDatabase.
     */
    fun registerTableCreator(creator: (SQLiteDatabase) -> Unit) {
        tableCreators.add(creator)
        
        // If the database is already initialized, call the creator immediately
        if (::internalDatabase.isInitialized && internalDatabase.isOpen) {
            try {
                creator(internalDatabase)
                logger.d("Called table creator on already initialized database")
            } catch (e: Exception) {
                logger.e("Error calling table creator: ${e.message}", e)
            }
        }
    }
    
    /**
     * Execute SQL with the given arguments.
     * This is a convenience method for executing SQL statements.
     *
     * @param sql The SQL statement to execute.
     * @param args The arguments for the SQL statement.
     * @throws StorageException if the SQL execution fails.
     */
    fun executeSQL(sql: String, args: Array<Any?>) {
        try {
            internalDatabase.execSQL(sql, args)
        } catch (e: Exception) {
            logger.e("Failed to execute SQL: ${e.message}", e)
            throw StorageException("Failed to execute SQL", e)
        }
    }
    
    /**
     * Begin a database transaction.
     *
     * @throws StorageException if the transaction cannot be started.
     */
    fun beginTransaction() {
        try {
            internalDatabase.beginTransaction()
        } catch (e: Exception) {
            logger.e("Failed to begin transaction: ${e.message}", e)
            throw StorageException("Failed to begin transaction", e)
        }
    }
    
    /**
     * Mark the current transaction as successful.
     *
     * @throws StorageException if the transaction cannot be marked as successful.
     */
    fun setTransactionSuccessful() {
        try {
            internalDatabase.setTransactionSuccessful()
        } catch (e: Exception) {
            logger.e("Failed to set transaction successful: ${e.message}", e)
            throw StorageException("Failed to set transaction successful", e)
        }
    }
    
    /**
     * End the current transaction.
     *
     * @throws StorageException if the transaction cannot be ended.
     */
    fun endTransaction() {
        try {
            internalDatabase.endTransaction()
        } catch (e: Exception) {
            logger.e("Failed to end transaction: ${e.message}", e)
            throw StorageException("Failed to end transaction", e)
        }
    }
    
    /**
     * Execute a query with the given arguments and map the results.
     *
     * @param sql The SQL query to execute.
     * @param args The arguments for the SQL query.
     * @param mapper A function that maps a cursor to the desired result type.
     * @return A list of mapped results.
     * @throws StorageException if the query execution fails.
     */
    fun <T> query(sql: String, args: Array<String>?, mapper: (Cursor) -> T): List<T> {
        val results = mutableListOf<T>()
        
        try {
            internalDatabase.rawQuery(sql, args).use { cursor ->
                while (cursor.moveToNext()) {
                    results.add(mapper(cursor))
                }
            }
            return results
        } catch (e: Exception) {
            logger.e("Failed to execute query: ${e.message}", e)
            throw StorageException("Failed to execute query", e)
        }
    }

    /**
     * Execute a database operation on the IO dispatcher.
     * This is a helper method for subclasses to use when implementing their own database operations.
     *
     * @param operation The database operation to execute.
     * @return The result of the operation.
     */
    protected suspend fun <T> executeOnIO(operation: suspend () -> T): T {
        return withContext(Dispatchers.IO) {
            operation()
        }
    }

    /**
     * Helper class for creating and managing the SQLite database.
     * This class extends SQLiteOpenHelper which is required for proper SQLite database management in Android.
     * It provides hooks for database creation and version upgrades, but delegates the actual table creation
     * to the registered table creator functions.
     */
    protected open inner class DatabaseHelper(
        context: Context,
        databaseName: String,
        version: Int
    ) : SQLiteOpenHelper(context, databaseName, null, version) {

        override fun onCreate(db: SQLiteDatabase) {
            try {
                // Don't create a default table - the tableCreators will handle this
                logger.d("Database created successfully, waiting for table creators")
            } catch (e: Exception) {
                logger.e("Error during database creation: ${e.message}", e)
                throw e
            }
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            // Each table creator will be called again on upgrade
            logger.d("Database upgrading from version $oldVersion to $newVersion")
        }
    }
}
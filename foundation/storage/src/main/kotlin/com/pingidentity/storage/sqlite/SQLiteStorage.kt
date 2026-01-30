/*
 * Copyright (c) 2025-2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.storage.sqlite

import android.content.Context
import android.database.Cursor
import com.pingidentity.logger.Logger
import com.pingidentity.storage.exception.StorageException
import com.pingidentity.storage.sqlite.passphrase.KeyStorePassphraseProvider
import com.pingidentity.storage.sqlite.passphrase.PassphraseProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SQLiteOpenHelper

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
    protected val allowDestructiveRecovery: Boolean = false,
    protected val maxBackupCount: Int = 3,
    protected val backupOnError: Boolean = true,
    protected val onDatabaseError: (suspend (ErrorCode, Boolean, Exception) -> Unit)? = null,
    protected open val logger: Logger = Logger.logger
) {
    companion object {
        private const val DEFAULT_DATABASE_NAME = "pingidentity_storage.db"
        private const val DATABASE_VERSION = 1
    }

    /**
     * Error codes for database initialization failures.
     */
    enum class ErrorCode {
        /** Wrong passphrase (KeyStore issue) */
        PASSPHRASE_INVALID,
        
        /** File permissions issue (read-only) */
        PERMISSION_DENIED,
        
        /** Database file corrupted */
        CORRUPTION,
        
        /** Insufficient storage space */
        DISK_FULL,
        
        /** Unknown or general error */
        UNKNOWN
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
     * If the database cannot be opened, it will attempt to restore from backup if available.
     *
     * @throws StorageException if the database cannot be opened or the tables cannot be created.
     */
    suspend fun initializeDatabase() = executeOnIO {
        try {
            // Load SQLCipher libraries
            System.loadLibrary("sqlcipher")

            // Create database helper
            dbHelper = createDatabaseHelper(context, databaseName, databaseVersion)

            // Get the passphrase from the provider
            val passphrase = passphraseProvider.getPassphrase()
            logger.d("Using passphrase from provider for database initialization")

            var restorationAttempted = false

            try {
                // Open or create the database
                internalDatabase = SQLiteDatabase.openOrCreateDatabase(
                    context.getDatabasePath(databaseName).absolutePath,
                    passphrase,
                    null,
                    null,
                )
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                
                // Classify the error
                val errorCode = classifyError(e)
                logger.e("Database initialization failed with error code $errorCode: ${e.message}", e)

                // Attempt to restore from backup before proceeding with destructive recovery
                if (!restorationAttempted) {
                    restorationAttempted = true
                    logger.i("Attempting to restore from backup")
                    val restored = attemptBackupRestoration()

                    if (restored) {
                        logger.i("Backup restoration successful, retrying database initialization")
                        // Retry opening the restored database
                        try {
                            internalDatabase = SQLiteDatabase.openOrCreateDatabase(
                                context.getDatabasePath(databaseName).absolutePath,
                                passphrase,
                                null,
                                null,
                            )
                            logger.d("Database opened successfully after restoration")
                        } catch (retryException: Exception) {
                            currentCoroutineContext().ensureActive()
                            val retryErrorCode = classifyError(retryException)
                            logger.e("Failed to open database after restoration: ${retryException.message}", retryException)
                            closeAndCleanupDatabase(retryErrorCode, retryException)
                            
                            // Create a new helper and try again
                            dbHelper = createDatabaseHelper(context, databaseName, databaseVersion)
                            internalDatabase = dbHelper.writableDatabase
                        }
                    } else {
                        logger.w("Backup restoration failed or no backups available")
                        closeAndCleanupDatabase(errorCode, e)
                        
                        // Create a new helper and try again
                        dbHelper = createDatabaseHelper(context, databaseName, databaseVersion)
                        internalDatabase = dbHelper.writableDatabase
                    }
                } else {
                    // Already attempted restoration, proceed with cleanup
                    closeAndCleanupDatabase(errorCode, e)
                    
                    // Create a new helper and try again
                    dbHelper = createDatabaseHelper(context, databaseName, databaseVersion)
                    internalDatabase = dbHelper.writableDatabase
                }
            }

            logger.d("SQL storage initialized successfully")

            // Validate database is properly initialized by testing a simple query
            validateDatabaseInitialization()
            
            // Call all registered table creators
            tableCreators.forEach { creator ->
                try {
                    creator(internalDatabase)
                } catch (e: Exception) {
                    currentCoroutineContext().ensureActive()
                    logger.e("Failed to create table: ${e.message}", e)
                }
            }
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
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
                currentCoroutineContext().ensureActive()
                val errorCode = classifyError(e)
                logger.e("Database validation query failed with error code $errorCode: ${e.message}", e)
                closeAndCleanupDatabase(errorCode, e)
                throw e
            }
        }
    }

    /**
     * Close and cleanup database resources.
     * Respects the allowDestructiveRecovery flag and creates backups when enabled.
     *
     * @param errorCode The error code that triggered the cleanup.
     * @param exception The exception that caused the error.
     * @throws StorageException if destructive recovery is not allowed.
     */
    private suspend fun closeAndCleanupDatabase(errorCode: ErrorCode = ErrorCode.UNKNOWN, exception: Exception? = null) {
        executeOnIO {
            // Attempt to close internalDatabase
            try {
                if (::internalDatabase.isInitialized && internalDatabase.isOpen) {
                    internalDatabase.use { /* it.close() is called automatically */ }
                }
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                logger.e("Error during internalDatabase.close(): ${e.message}", e)
            }

            // Attempt to close dbHelper
            try {
                if (::dbHelper.isInitialized) {
                    dbHelper.close()
                }
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                logger.e("Error during dbHelper.close(): ${e.message}", e)
            }

            // Check if destructive recovery is allowed
            if (!allowDestructiveRecovery) {
                logger.e("Database initialization failed with error code: $errorCode. Destructive recovery is disabled.")
                if (exception != null) {
                    // Notify callback if provided
                    onDatabaseError?.invoke(errorCode, false, exception)
                }
                // Re-throw the exception to prevent database deletion
                throw StorageException("Database initialization failed and destructive recovery is disabled", exception)
            }

            logger.w("Destructive recovery enabled. Proceeding with database cleanup.")

            // Create backup before deletion if enabled
            if (backupOnError) {
                try {
                    val backupFile = createDatabaseBackup()
                    if (backupFile != null) {
                        logger.i("Created backup before database deletion: ${backupFile.name}")
                    } else {
                        logger.w("Failed to create backup before deletion")
                    }
                } catch (e: Exception) {
                    currentCoroutineContext().ensureActive()
                    logger.e("Error creating backup: ${e.message}", e)
                }
            }

            // Notify callback before deletion
            if (exception != null) {
                onDatabaseError?.invoke(errorCode, true, exception)
            }

            // Delete the database file
            try {
                context.deleteDatabase(databaseName)
                logger.d("Database file deleted successfully")
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
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
                internalDatabase = dbHelper.readableDatabase
                internalDatabase.close()
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                logger.e("Failed to create temporary database instance: ${e.message}", e)
            }
        }
    }

    /**
     * Attempt to restore the database from the most recent backup.
     *
     * @return true if restoration was successful, false otherwise.
     */
    open suspend fun attemptBackupRestoration(): Boolean = executeOnIO {
        try {
            val backups = listBackupFiles()
            if (backups.isEmpty()) {
                logger.w("No backup files found for restoration")
                return@executeOnIO false
            }

            val latestBackup = backups.first()
            logger.i("Attempting to restore from backup: ${latestBackup.name}")

            // Validate backup can be opened with current passphrase
            val dbFile = context.getDatabasePath(databaseName)
            val passphrase = passphraseProvider.getPassphrase()

            try {
                // Test opening the backup with current passphrase
                val testDb = SQLiteDatabase.openDatabase(
                    latestBackup.absolutePath,
                    passphrase,
                    null,
                    SQLiteDatabase.OPEN_READONLY,
                    null
                )
                testDb.close()
                logger.d("Backup validation successful")
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                logger.e("Backup validation failed: ${e.message}", e)
                return@executeOnIO false
            }

            // Copy backup to main database location
            try {
                // If database file exists and is read-only, make it writable or delete it
                if (dbFile.exists()) {
                    if (!dbFile.canWrite()) {
                        logger.w("Database file is read-only, making it writable for restoration")
                        dbFile.setWritable(true)
                    }
                    // Delete the existing (potentially corrupted/read-only) database
                    if (!dbFile.delete()) {
                        logger.w("Failed to delete existing database file, attempting to overwrite")
                    }
                }
                
                latestBackup.inputStream().use { input ->
                    dbFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                logger.i("Database restored successfully from backup: ${latestBackup.name}")
                return@executeOnIO true
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                logger.e("Failed to copy backup to database location: ${e.message}", e)
                return@executeOnIO false
            }
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            logger.e("Error during backup restoration: ${e.message}", e)
            return@executeOnIO false
        }
    }

    /**
     * Classify the error type based on the exception.
     *
     * @param exception The exception to classify.
     * @return The error code corresponding to the exception.
     */
    protected open fun classifyError(exception: Exception): ErrorCode {
        val message = exception.message?.lowercase() ?: ""
        
        return when {
            message.contains("passphrase") || message.contains("decrypt") || 
            message.contains("file is not a database") || message.contains("file is encrypted") -> {
                ErrorCode.PASSPHRASE_INVALID
            }
            message.contains("readonly") || message.contains("read-only") || 
            message.contains("permission denied") || message.contains("eacces") -> {
                ErrorCode.PERMISSION_DENIED
            }
            message.contains("corrupt") || message.contains("malformed") || 
            message.contains("damaged") || message.contains("database disk image is malformed") -> {
                ErrorCode.CORRUPTION
            }
            message.contains("disk") || message.contains("space") || 
            message.contains("enospc") || message.contains("full") -> {
                ErrorCode.DISK_FULL
            }
            else -> ErrorCode.UNKNOWN
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
     * Create a backup of the current database file.
     * The backup file will be encrypted with the same passphrase as the original database.
     *
     * @return The backup file if successfully created, null otherwise.
     */
    open suspend fun createDatabaseBackup(): java.io.File? = executeOnIO {
        try {
            val dbFile = context.getDatabasePath(databaseName)
            if (!dbFile.exists()) {
                logger.w("Database file does not exist, cannot create backup")
                return@executeOnIO null
            }

            val timestamp = System.currentTimeMillis()
            val backupFileName = getBackupFileName(timestamp)
            val backupFile = java.io.File(dbFile.parent, backupFileName)

            // Copy the database file to backup location
            dbFile.inputStream().use { input ->
                backupFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            logger.i("Database backup created: ${backupFile.name} (${backupFile.length()} bytes)")
            
            // Clean up old backups after creating new one
            cleanOldBackups()
            
            backupFile
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            logger.e("Failed to create database backup: ${e.message}", e)
            null
        }
    }

    /**
     * List all backup files for the current database, sorted by timestamp (newest first).
     *
     * @return List of backup files sorted by timestamp (newest first).
     */
    open suspend fun listBackupFiles(): List<java.io.File> = executeOnIO {
        try {
            val dbFile = context.getDatabasePath(databaseName)
            val dbDir = dbFile.parentFile ?: return@executeOnIO emptyList()

            val baseNameWithoutExtension = databaseName.substringBeforeLast('.')
            val backupPrefix = "${baseNameWithoutExtension}_backup_"

            dbDir.listFiles { file ->
                file.name.startsWith(backupPrefix) && file.name.endsWith(".db")
            }?.sortedByDescending { file ->
                parseBackupTimestamp(file.name) ?: 0L
            } ?: emptyList()
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            logger.e("Failed to list backup files: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Remove old backup files exceeding the configured maximum backup count.
     */
    protected open suspend fun cleanOldBackups() = executeOnIO {
        try {
            val backups = listBackupFiles()
            if (backups.size > maxBackupCount) {
                val backupsToDelete = backups.drop(maxBackupCount)
                backupsToDelete.forEach { backup ->
                    if (backup.delete()) {
                        logger.d("Deleted old backup: ${backup.name}")
                    } else {
                        logger.w("Failed to delete old backup: ${backup.name}")
                    }
                }
            }
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            logger.e("Failed to clean old backups: ${e.message}", e)
        }
    }

    /**
     * Generate a backup filename with timestamp.
     * Format: {databaseName}_backup_{timestamp}.db
     *
     * @param timestamp The timestamp to include in the filename.
     * @return The generated backup filename.
     */
    protected open fun getBackupFileName(timestamp: Long): String {
        val baseNameWithoutExtension = databaseName.substringBeforeLast('.')
        val extension = if (databaseName.contains('.')) ".${databaseName.substringAfterLast('.')}" else ""
        
        // Format: databaseName_backup_timestamp.db
        // Example: pingidentity_oath_backup_1738172425000.db
        return "${baseNameWithoutExtension}_backup_${timestamp}${extension}"
    }

    /**
     * Parse the timestamp from a backup filename.
     *
     * @param filename The backup filename to parse.
     * @return The timestamp if successfully parsed, null otherwise.
     */
    protected open fun parseBackupTimestamp(filename: String): Long? {
        return try {
            val baseNameWithoutExtension = databaseName.substringBeforeLast('.')
            val backupPrefix = "${baseNameWithoutExtension}_backup_"
            val extension = if (databaseName.contains('.')) ".${databaseName.substringAfterLast('.')}" else ""
            
            if (filename.startsWith(backupPrefix) && filename.endsWith(extension)) {
                val timestampStr = filename
                    .removePrefix(backupPrefix)
                    .removeSuffix(extension)
                timestampStr.toLongOrNull()
            } else {
                null
            }
        } catch (e: Exception) {
            logger.e("Failed to parse backup timestamp from filename: $filename", e)
            null
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
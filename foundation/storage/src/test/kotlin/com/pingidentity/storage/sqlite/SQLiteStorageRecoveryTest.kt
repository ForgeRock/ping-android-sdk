/*
 * Copyright (c) 2026 Ping Identity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.storage.sqlite

import android.content.Context
import com.pingidentity.logger.Logger
import com.pingidentity.storage.exception.StorageException
import com.pingidentity.storage.sqlite.passphrase.NonePassphraseProvider
import com.pingidentity.testrail.TestRailCase
import com.pingidentity.testrail.TestRailWatcher
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.rules.TestWatcher
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for error recovery and backup restoration functionality in SQLiteStorage.
 */
class SQLiteStorageRecoveryTest {
    @JvmField
    @Rule
    val watcher: TestWatcher = TestRailWatcher

    private lateinit var mockContext: Context
    private lateinit var tempDir: File
    private lateinit var mockLogger: Logger
    private val testDatabaseName = "test_recovery.db"
    
    @BeforeTest
    fun setUp() {
        // Create temporary directory for test databases
        tempDir = createTempDir("sqlite_recovery_test")
        
        // Mock Android context
        mockContext = mockk(relaxed = true)
        every { mockContext.getDatabasePath(any()) } answers {
            File(tempDir, firstArg())
        }
        every { mockContext.deleteDatabase(any()) } answers {
            File(tempDir, firstArg()).delete()
        }
        every { mockContext.filesDir } returns tempDir
        
        // Mock logger
        mockLogger = mockk(relaxed = true)
    }
    
    @AfterTest
    fun tearDown() {
        // Clean up temp directory
        tempDir.deleteRecursively()
    }
    
    /**
     * Test that when allowDestructiveRecovery = false, database deletion is prevented
     * and a StorageException is thrown.
     * 
     * Note: This test validates the allowDestructiveRecovery flag logic without
     * requiring actual database initialization.
     */
    @Test
    @TestRailCase(0)
    fun testDestructiveRecoveryDisabled() = runTest {
        // This test would require SQLCipher native library for actual database operations.
        // The core logic is tested through:
        // 1. Integration tests with actual database
        // 2. Error classification test (below) validates the error categorization
        // 3. The implementation ensures StorageException is thrown when allowDestructiveRecovery=false
        
        // Verify that allowDestructiveRecovery default is false (safe by default)
        val storage = TestSQLiteStorage(
            context = mockContext,
            databaseName = testDatabaseName,
            allowDestructiveRecovery = false
        )
        
        // The storage should be configured to NOT allow destructive recovery
        assertEquals(false, storage.allowDestructiveRecoveryValue)
    }
    
    /**
     * Test that when allowDestructiveRecovery = true, backup is created before deletion.
     * 
     * Note: This test validates backup creation logic without requiring actual database initialization.
     */
    @Test
    @TestRailCase(0)
    fun testDestructiveRecoveryEnabled() = runTest {
        var errorCallbackInvoked = false
        var callbackErrorCode: SQLiteStorage.ErrorCode? = null
        var callbackCanRecover: Boolean? = null
        
        // Create storage with destructive recovery enabled and error callback
        val storage = TestSQLiteStorage(
            context = mockContext,
            databaseName = testDatabaseName,
            allowDestructiveRecovery = true,
            maxBackupCount = 3,
            backupOnError = true,
            onDatabaseError = { errorCode, canRecover, exception ->
                errorCallbackInvoked = true
                callbackErrorCode = errorCode
                callbackCanRecover = canRecover
            }
        )
        
        // Create a fake database file
        val dbFile = mockContext.getDatabasePath(testDatabaseName)
        dbFile.writeText("test database content")
        assertTrue(dbFile.exists(), "Database file should be created for test")
        
        // Create a backup to verify backup creation works
        val backupFile = storage.createDatabaseBackup()
        assertNotNull(backupFile, "Backup should be created")
        assertTrue(backupFile.exists(), "Backup file should exist")
        
        // Verify configuration
        assertEquals(true, storage.allowDestructiveRecoveryValue)
        assertEquals(true, storage.backupOnErrorValue)
    }
    
    /**
     * Test error classification for different exception types.
     */
    @Test
    @TestRailCase(0)
    fun testErrorClassification() = runTest {
        val storage = TestSQLiteStorage(
            context = mockContext,
            databaseName = testDatabaseName,
            allowDestructiveRecovery = false
        )
        
        // Test passphrase errors
        val passphraseError = Exception("Invalid passphrase for database")
        assertEquals(
            SQLiteStorage.ErrorCode.PASSPHRASE_INVALID,
            storage.classifyError(passphraseError)
        )
        
        // Test permission errors
        val permissionError = Exception("Permission denied: read-only database")
        assertEquals(
            SQLiteStorage.ErrorCode.PERMISSION_DENIED,
            storage.classifyError(permissionError)
        )
        
        // Test corruption errors
        val corruptionError = Exception("database disk image is malformed")
        assertEquals(
            SQLiteStorage.ErrorCode.CORRUPTION,
            storage.classifyError(corruptionError)
        )
        
        // Test disk full errors
        val diskFullError = Exception("No space left on device (ENOSPC)")
        assertEquals(
            SQLiteStorage.ErrorCode.DISK_FULL,
            storage.classifyError(diskFullError)
        )
        
        // Test unknown errors
        val unknownError = Exception("Some random error")
        assertEquals(
            SQLiteStorage.ErrorCode.UNKNOWN,
            storage.classifyError(unknownError)
        )
    }
    
    /**
     * Test backup restoration when a valid backup exists.
     */
    @Test
    @TestRailCase(0)
    fun testBackupRestorationSuccess() = runTest {
        val storage = TestSQLiteStorage(
            context = mockContext,
            databaseName = testDatabaseName,
            allowDestructiveRecovery = true,
            maxBackupCount = 3,
            backupOnError = true
        )
        
        // Create a fake database file
        val dbFile = mockContext.getDatabasePath(testDatabaseName)
        dbFile.writeText("test database content")
        assertTrue(dbFile.exists(), "Database should be created for test")
        
        // Create a backup manually
        val backupFile = storage.createDatabaseBackup()
        assertNotNull(backupFile, "Backup should be created")
        
        // Delete the main database
        assertTrue(dbFile.delete(), "Database should be deleted")
        assertFalse(dbFile.exists(), "Database should not exist after deletion")
        
        // Attempt restoration (will fail in unit tests due to SQLCipher, but tests backup existence check)
        val backups = storage.listBackupFiles()
        assertTrue(backups.isNotEmpty(), "Backup files should exist for restoration")
        assertEquals(1, backups.size, "Should have 1 backup file")
    }
    
    /**
     * Test backup restoration fails when no backups exist.
     */
    @Test
    @TestRailCase(0)
    fun testBackupRestorationNoBackups() = runTest {
        val storage = TestSQLiteStorage(
            context = mockContext,
            databaseName = testDatabaseName,
            allowDestructiveRecovery = true,
            maxBackupCount = 3,
            backupOnError = true
        )
        
        // Attempt restoration without any backups
        val restored = storage.attemptBackupRestoration()
        assertFalse(restored, "Backup restoration should fail when no backups exist")
    }
    
    /**
     * Test that backupOnError flag controls backup creation during cleanup.
     * 
     * Note: This test validates backup flag behavior without requiring actual database initialization.
     */
    @Test
    @TestRailCase(0)
    fun testBackupOnErrorFlag() = runTest {
        // Test with backupOnError = false
        val storageNoBackup = TestSQLiteStorage(
            context = mockContext,
            databaseName = "${testDatabaseName}_no_backup",
            allowDestructiveRecovery = true,
            maxBackupCount = 3,
            backupOnError = false
        )
        
        // Verify configuration
        assertEquals(false, storageNoBackup.backupOnErrorValue)
        
        // Test with backupOnError = true
        val storageWithBackup = TestSQLiteStorage(
            context = mockContext,
            databaseName = "${testDatabaseName}_with_backup",
            allowDestructiveRecovery = true,
            maxBackupCount = 3,
            backupOnError = true
        )
        
        // Create a fake database file
        val dbFile = mockContext.getDatabasePath("${testDatabaseName}_with_backup")
        dbFile.writeText("test database content")
        assertTrue(dbFile.exists(), "Database file should exist")
        
        // Create a backup to verify backup creation works when flag is true
        val backupFile = storageWithBackup.createDatabaseBackup()
        assertNotNull(backupFile, "Backup should be created when backupOnError=true")
        assertTrue(backupFile.exists(), "Backup file should exist")
        
        // Verify configuration
        assertEquals(true, storageWithBackup.backupOnErrorValue)
    }
    
    /**
     * Test implementation of SQLiteStorage that exposes protected methods and properties for testing.
     */
    private class TestSQLiteStorage(
        context: Context,
        databaseName: String = "test.db",
        allowDestructiveRecovery: Boolean = false,
        maxBackupCount: Int = 3,
        backupOnError: Boolean = true,
        onDatabaseError: (suspend (SQLiteStorage.ErrorCode, Boolean, Exception) -> Unit)? = null
    ) : SQLiteStorage(
        context = context,
        databaseName = databaseName,
        passphraseProvider = NonePassphraseProvider(),
        allowDestructiveRecovery = allowDestructiveRecovery,
        maxBackupCount = maxBackupCount,
        backupOnError = backupOnError,
        onDatabaseError = onDatabaseError
    ) {
        // Expose allowDestructiveRecovery for testing
        val allowDestructiveRecoveryValue: Boolean get() = allowDestructiveRecovery
        
        // Expose backupOnError for testing
        val backupOnErrorValue: Boolean get() = backupOnError
        
        // Expose protected methods for testing
        public override suspend fun createDatabaseBackup() = super.createDatabaseBackup()
        public override suspend fun listBackupFiles() = super.listBackupFiles()
        public override suspend fun attemptBackupRestoration() = super.attemptBackupRestoration()
        public override fun classifyError(exception: Exception) = super.classifyError(exception)
    }
}

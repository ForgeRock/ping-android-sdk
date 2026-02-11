/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.storage.sqlite

import android.content.Context
import com.pingidentity.android.ContextProvider
import com.pingidentity.logger.Logger
import com.pingidentity.storage.sqlite.passphrase.NonePassphraseProvider
import com.pingidentity.testrail.TestRailCase
import com.pingidentity.testrail.TestRailWatcher
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.rules.TestWatcher
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for SQLiteStorage backup functionality.
 * Tests backup file creation, encryption, cleanup, naming, and failure handling.
 */
class SQLiteStorageBackupTest {
    @JvmField
    @Rule
    val watcher: TestWatcher = TestRailWatcher

    private lateinit var mockContext: Context
    private lateinit var tempDir: File
    private lateinit var mockLogger: Logger

    @BeforeTest
    fun setUp() {
        // Create temporary directory for test databases
        tempDir = createTempDir("sqlite_backup_test")
        
        // Mock Android context
        mockContext = mockk(relaxed = true)
        mockkObject(ContextProvider)
        every { ContextProvider.context } returns mockContext
        every { mockContext.getDatabasePath(any()) } answers {
            File(tempDir, firstArg())
        }
        every { mockContext.deleteDatabase(any()) } answers {
            File(tempDir, firstArg()).delete()
        }
        
        // Mock logger
        mockLogger = mockk(relaxed = true)
    }

    @AfterTest
    fun tearDown() {
        // Clean up temporary directory
        tempDir.deleteRecursively()
    }

    @TestRailCase(0) // TODO: Add TestRail case ID
    @Test
    fun testGetBackupFileName() {
        val storage = TestSQLiteStorage(
            context = mockContext,
            databaseName = "test.db",
            logger = mockLogger
        )

        val timestamp = 1738172425000L
        val backupFileName = storage.getBackupFileName(timestamp)

        assertEquals("test_backup_1738172425000.db", backupFileName)
    }

    @TestRailCase(0) // TODO: Add TestRail case ID
    @Test
    fun testGetBackupFileNameWithoutExtension() {
        val storage = TestSQLiteStorage(
            context = mockContext,
            databaseName = "testdb",
            logger = mockLogger
        )

        val timestamp = 1738172425000L
        val backupFileName = storage.getBackupFileName(timestamp)

        assertEquals("testdb_backup_1738172425000", backupFileName)
    }

    @TestRailCase(0) // TODO: Add TestRail case ID
    @Test
    fun testParseBackupTimestamp() {
        val storage = TestSQLiteStorage(
            context = mockContext,
            databaseName = "test.db",
            logger = mockLogger
        )

        val timestamp = storage.parseBackupTimestamp("test_backup_1738172425000.db")

        assertNotNull(timestamp)
        assertEquals(1738172425000L, timestamp)
    }

    @TestRailCase(0) // TODO: Add TestRail case ID
    @Test
    fun testParseBackupTimestampInvalidFormat() {
        val storage = TestSQLiteStorage(
            context = mockContext,
            databaseName = "test.db",
            logger = mockLogger
        )

        val timestamp = storage.parseBackupTimestamp("invalid_file.db")

        assertNull(timestamp)
    }

    @TestRailCase(0) // TODO: Add TestRail case ID
    @Test
    fun testParseBackupTimestampWrongPrefix() {
        val storage = TestSQLiteStorage(
            context = mockContext,
            databaseName = "test.db",
            logger = mockLogger
        )

        val timestamp = storage.parseBackupTimestamp("other_backup_1738172425000.db")

        assertNull(timestamp)
    }

    @TestRailCase(0) // TODO: Add TestRail case ID
    @Test
    fun testListBackupFilesEmpty() = runTest {
        val storage = TestSQLiteStorage(
            context = mockContext,
            databaseName = "test.db",
            logger = mockLogger
        )

        val backups = storage.listBackupFiles()

        assertTrue(backups.isEmpty())
    }

    @TestRailCase(0) // TODO: Add TestRail case ID
    @Test
    fun testListBackupFilesSortedByTimestamp() = runTest {
        val storage = TestSQLiteStorage(
            context = mockContext,
            databaseName = "test.db",
            logger = mockLogger
        )

        // Create fake backup files with different timestamps
        val backup1 = File(tempDir, "test_backup_1000000000.db").apply { createNewFile() }
        val backup2 = File(tempDir, "test_backup_3000000000.db").apply { createNewFile() }
        val backup3 = File(tempDir, "test_backup_2000000000.db").apply { createNewFile() }
        
        // Also create a non-backup file to ensure it's ignored
        File(tempDir, "test.db").createNewFile()
        File(tempDir, "other_file.db").createNewFile()

        val backups = storage.listBackupFiles()

        assertEquals(3, backups.size)
        // Should be sorted newest first
        assertEquals(backup2.name, backups[0].name)
        assertEquals(backup3.name, backups[1].name)
        assertEquals(backup1.name, backups[2].name)
    }

    @TestRailCase(0) // TODO: Add TestRail case ID
    @Test
    fun testCleanOldBackupsExceedsLimit() = runTest {
        val storage = TestSQLiteStorage(
            context = mockContext,
            databaseName = "test.db",
            maxBackupCount = 2,
            logger = mockLogger
        )

        // Create 5 backup files
        val backup1 = File(tempDir, "test_backup_1000000000.db").apply { createNewFile() }
        val backup2 = File(tempDir, "test_backup_2000000000.db").apply { createNewFile() }
        val backup3 = File(tempDir, "test_backup_3000000000.db").apply { createNewFile() }
        val backup4 = File(tempDir, "test_backup_4000000000.db").apply { createNewFile() }
        val backup5 = File(tempDir, "test_backup_5000000000.db").apply { createNewFile() }

        storage.cleanOldBackups()

        // Should keep only the 2 newest backups
        assertFalse(backup1.exists())
        assertFalse(backup2.exists())
        assertFalse(backup3.exists())
        assertTrue(backup4.exists())
        assertTrue(backup5.exists())
    }

    @TestRailCase(0) // TODO: Add TestRail case ID
    @Test
    fun testCleanOldBackupsWithinLimit() = runTest {
        val storage = TestSQLiteStorage(
            context = mockContext,
            databaseName = "test.db",
            maxBackupCount = 5,
            logger = mockLogger
        )

        // Create 3 backup files
        val backup1 = File(tempDir, "test_backup_1000000000.db").apply { createNewFile() }
        val backup2 = File(tempDir, "test_backup_2000000000.db").apply { createNewFile() }
        val backup3 = File(tempDir, "test_backup_3000000000.db").apply { createNewFile() }

        storage.cleanOldBackups()

        // Should keep all backups (within limit)
        assertTrue(backup1.exists())
        assertTrue(backup2.exists())
        assertTrue(backup3.exists())
    }

    @TestRailCase(0) // TODO: Add TestRail case ID
    @Test
    fun testCreateDatabaseBackupNoDatabase() = runTest {
        val storage = TestSQLiteStorage(
            context = mockContext,
            databaseName = "nonexistent.db",
            logger = mockLogger
        )

        val backupFile = storage.createDatabaseBackup()

        assertNull(backupFile)
    }

    @TestRailCase(0) // TODO: Add TestRail case ID
    @Test
    fun testCreateDatabaseBackupSuccess() = runTest {
        val dbName = "test.db"
        val storage = TestSQLiteStorage(
            context = mockContext,
            databaseName = dbName,
            maxBackupCount = 3,
            logger = mockLogger
        )

        // Create a fake database file with some content
        val dbFile = File(tempDir, dbName)
        dbFile.writeText("test database content")

        val backupFile = storage.createDatabaseBackup()

        assertNotNull(backupFile)
        assertTrue(backupFile.exists())
        assertTrue(backupFile.name.startsWith("test_backup_"))
        assertTrue(backupFile.name.endsWith(".db"))
        // Verify content was copied
        assertEquals("test database content", backupFile.readText())
    }

    @TestRailCase(0) // TODO: Add TestRail case ID
    @Test
    fun testCreateDatabaseBackupCleansOldBackups() = runTest {
        val dbName = "test.db"
        val storage = TestSQLiteStorage(
            context = mockContext,
            databaseName = dbName,
            maxBackupCount = 2,
            logger = mockLogger
        )

        // Create a fake database file
        val dbFile = File(tempDir, dbName)
        dbFile.writeText("test database content")

        // Create 2 existing old backups
        File(tempDir, "test_backup_1000000000.db").createNewFile()
        File(tempDir, "test_backup_2000000000.db").createNewFile()

        // Create new backup (should trigger cleanup)
        val backupFile = storage.createDatabaseBackup()

        assertNotNull(backupFile)
        
        // List all backups - should be only 2 (the 2 newest)
        val backups = storage.listBackupFiles()
        assertEquals(2, backups.size)
        // The oldest backup should be deleted
        assertFalse(File(tempDir, "test_backup_1000000000.db").exists())
    }

    /**
     * Test implementation of SQLiteStorage that exposes protected methods for testing.
     */
    private class TestSQLiteStorage(
        context: Context,
        databaseName: String = "test.db",
        maxBackupCount: Int = 3,
        logger: Logger = mockk(relaxed = true)
    ) : SQLiteStorage(
        SQLiteStorageConfig().apply {
            this.context = context
            this.databaseName = databaseName
            this.passphraseProvider = NonePassphraseProvider()
            this.allowDestructiveRecovery = false
            this.maxBackupCount = maxBackupCount
            this.backupOnError = true
            this.logger = logger
        }
    ) {
        // Expose protected methods for testing
        override suspend fun createDatabaseBackup() = super.createDatabaseBackup()
        override suspend fun listBackupFiles() = super.listBackupFiles()
        public override suspend fun cleanOldBackups() = super.cleanOldBackups()
        public override fun getBackupFileName(timestamp: Long) = super.getBackupFileName(timestamp)
        public override fun parseBackupTimestamp(filename: String) = super.parseBackupTimestamp(filename)
    }
}

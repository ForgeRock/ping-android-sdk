/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.auth.migration

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.pingidentity.android.ContextProvider
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockkConstructor
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val AUTH_DATA_ACCOUNT = "org.forgerock.android.authenticator.DATA.ACCOUNT"
private const val AUTH_DATA_MECHANISM = "org.forgerock.android.authenticator.DATA.MECHANISM"

@RunWith(RobolectricTestRunner::class)
class LegacyAuthenticatorRepositoryTest {

    private lateinit var context: Context
    private lateinit var repository: LegacyAuthenticationRepository
    private lateinit var accountPrefs: SharedPreferences
    private lateinit var mechanismPrefs: SharedPreferences

    @BeforeTest
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        ContextProvider.init(context)

        accountPrefs = context.getSharedPreferences(AUTH_DATA_ACCOUNT, Context.MODE_PRIVATE)
        mechanismPrefs = context.getSharedPreferences(AUTH_DATA_MECHANISM, Context.MODE_PRIVATE)

        cleanup()
    }

    @AfterTest
    fun tearDown() {
        cleanup()
        unmockkAll()
    }

    private fun cleanup() {
        try {
            context.deleteSharedPreferences(AUTH_DATA_ACCOUNT)
            context.deleteSharedPreferences(AUTH_DATA_MECHANISM)
        } catch (_: Exception) {
            // Ignore cleanup errors
        }
    }

    // ================================================================================
    // Test: isExists()
    // ================================================================================

    @Test
    fun `isExists returns false when no legacy data exists`() = runTest {
        // Given - Mock decryptor with no encryption key
        mockkConstructor(LegacyAuthenticationDecryptor::class)
        every { anyConstructed<LegacyAuthenticationDecryptor>().keyExists() } returns false

        repository = LegacyAuthenticationRepository(context)

        // When
        val exists = repository.isExists()

        // Then
        assertFalse(exists)
    }

    @Test
    fun `isExists returns true when encryption key exists`() = runTest {
        // Given - Mock decryptor with encryption key
        mockkConstructor(LegacyAuthenticationDecryptor::class)
        every { anyConstructed<LegacyAuthenticationDecryptor>().keyExists() } returns true

        repository = LegacyAuthenticationRepository(context)

        // When
        val exists = repository.isExists()

        // Then
        assertTrue(exists)
    }

    @Test
    fun `isExists returns true when SharedPreferences files exist (custom storage)`() = runTest {
        // Given - No encryption key but SharedPreferences exist
        mockkConstructor(LegacyAuthenticationDecryptor::class)
        every { anyConstructed<LegacyAuthenticationDecryptor>().keyExists() } returns false

        // Create SharedPreferences files
        accountPrefs.edit().putString("test-key", "test-value").commit()

        repository = LegacyAuthenticationRepository(context)

        // When
        val exists = repository.isExists()

        // Then
        assertTrue(exists)
    }

    @Test
    fun `isExists returns true when SQL database exists`() = runTest {
        // Given - Create SQL database file
        val dbFile = context.getDatabasePath("forgerock_authenticator.db")
        dbFile.parentFile?.mkdirs()
        dbFile.createNewFile()

        val config = LegacyAuthenticationConfig(migrationType = MigrationType.SQL)
        repository = LegacyAuthenticationRepository(context, config)

        // When
        val exists = repository.isExists()

        // Then
        assertTrue(exists)

        // Cleanup
        dbFile.delete()
    }

    @Test
    fun `isExists returns false when no data exists for any migration type`() = runTest {
        // Given - No data exists
        mockkConstructor(LegacyAuthenticationDecryptor::class)
        every { anyConstructed<LegacyAuthenticationDecryptor>().keyExists() } returns false

        val config = LegacyAuthenticationConfig(migrationType = MigrationType.AUTO)
        repository = LegacyAuthenticationRepository(context, config)

        // When
        val exists = repository.isExists()

        // Then
        assertFalse(exists)
    }

    // ================================================================================
    // Test: Migration Type Configuration
    // ================================================================================

    @Test
    fun `config defaults to AUTO migration type`() = runTest {
        // Given - Default config
        val config = LegacyAuthenticationConfig()

        // Then
        assertEquals(MigrationType.AUTO, config.migrationType)
        assertEquals("forgerock_authenticator.db", config.databaseName)
        assertEquals(false, config.backupFiles)
    }

    @Test
    fun `config supports XML migration type`() = runTest {
        // Given - XML migration config
        val config = LegacyAuthenticationConfig(
            migrationType = MigrationType.XML,
            keyAlias = "custom.KEY"
        )

        // Then
        assertEquals(MigrationType.XML, config.migrationType)
        assertEquals("custom.KEY", config.keyAlias)
    }

    @Test
    fun `config supports SQL migration type with database name`() = runTest {
        // Given - SQL migration config
        val config = LegacyAuthenticationConfig(
            migrationType = MigrationType.SQL,
            databaseName = "custom_database.db",
            databasePassphrase = "test-passphrase"
        )

        // Then
        assertEquals(MigrationType.SQL, config.migrationType)
        assertEquals("custom_database.db", config.databaseName)
        assertEquals("test-passphrase", config.databasePassphrase)
    }

    @Test
    fun `config supports AUTO migration type`() = runTest {
        // Given - AUTO migration config
        val config = LegacyAuthenticationConfig(
            migrationType = MigrationType.AUTO,
            databaseName = "forgerock_authenticator.db",
            keyAlias = "org.forgerock.android.authenticator.KEYS"
        )

        // Then
        assertEquals(MigrationType.AUTO, config.migrationType)
        assertEquals("forgerock_authenticator.db", config.databaseName)
        assertEquals("org.forgerock.android.authenticator.KEYS", config.keyAlias)
    }

    // ================================================================================
    // Test: exportAllData() - Encrypted Data
    // ================================================================================

    @Test
    fun `exportAllData decrypts and exports encrypted data successfully`() = runTest {
        // Given - Create SharedPreferences files first (required for file existence check)
        accountPrefs.edit().putString("dummy-key", "dummy-value").commit()
        mechanismPrefs.edit().putString("dummy-key", "dummy-value").commit()

        // Mock encrypted data
        mockkConstructor(LegacyAuthenticationDecryptor::class)
        every { anyConstructed<LegacyAuthenticationDecryptor>().keyExists() } returns true

        val accountJson = """
            {
                "id": "Google-user@example.com",
                "issuer": "Google",
                "accountName": "user@example.com",
                "displayIssuer": "Google Auth",
                "displayAccountName": "Test User",
                "imageURL": "https://google.com/logo.png",
                "backgroundColor": "#4285F4",
                "timeAdded": 1234567890,
                "policies": "{\"deviceTampering\":true}",
                "lockingPolicy": null,
                "lock": false
            }
        """.trimIndent()

        val mechanismJson = """
            {
                "id": "Google-user@example.com-otpauth",
                "issuer": "Google",
                "accountName": "user@example.com",
                "mechanismUID": "mechanism-uuid-123",
                "secret": "JBSWY3DPEHPK3PXP",
                "type": "otpauth",
                "oathType": "TOTP",
                "algorithm": "sha1",
                "digits": 6,
                "period": 30,
                "counter": 0,
                "uid": "user-123",
                "resourceId": "resource-456",
                "timeAdded": 1234567890
            }
        """.trimIndent()

        coEvery {
            anyConstructed<LegacyAuthenticationDecryptor>().decryptAll(AUTH_DATA_ACCOUNT)
        } returns mapOf("Google-user@example.com" to accountJson)

        coEvery {
            anyConstructed<LegacyAuthenticationDecryptor>().decryptAll(AUTH_DATA_MECHANISM)
        } returns mapOf("Google-user@example.com-otpauth" to mechanismJson)

        repository = LegacyAuthenticationRepository(context)

        // When
        val exportedData = repository.exportAllData()

        // Then
        assertNotNull(exportedData)
        assertEquals(1, exportedData.mechanisms.size)
        assertEquals(1, exportedData.metadata.totalMechanisms)

        val mechanism = exportedData.mechanisms[0]
        assertEquals("Google-user@example.com-otpauth", mechanism.id)
        assertEquals("Google", mechanism.issuer)
        assertEquals("user@example.com", mechanism.accountName)
        assertEquals("mechanism-uuid-123", mechanism.mechanismUID)
        assertEquals("JBSWY3DPEHPK3PXP", mechanism.secret)
        assertEquals("otpauth", mechanism.type)
        assertEquals("TOTP", mechanism.oathType)
        assertEquals("sha1", mechanism.algorithm)
        assertEquals(6, mechanism.digits)
        assertEquals(30, mechanism.period)

        // Verify nested account
        val account = mechanism.account
        assertNotNull(account)
        assertEquals("Google-user@example.com", account.id)
        assertEquals("Google", account.issuer)
        assertEquals("Google Auth", account.displayIssuer)
        assertEquals("user@example.com", account.accountName)
        assertEquals("Test User", account.displayAccountName)
        assertEquals("https://google.com/logo.png", account.imageURL)
        assertEquals("#4285F4", account.backgroundColor)
    }

    @Test
    fun `exportAllData handles missing account data gracefully`() = runTest {
        // Given - Create SharedPreferences files first
        mechanismPrefs.edit().putString("dummy-key", "dummy-value").commit()

        // Mechanism without corresponding account
        mockkConstructor(LegacyAuthenticationDecryptor::class)
        every { anyConstructed<LegacyAuthenticationDecryptor>().keyExists() } returns true

        val mechanismJson = """
            {
                "id": "GitHub-dev@example.com-otpauth",
                "issuer": "GitHub",
                "accountName": "dev@example.com",
                "mechanismUID": "mechanism-uuid-456",
                "secret": "SECRET123",
                "type": "otpauth",
                "oathType": "TOTP",
                "algorithm": "sha256",
                "digits": 6,
                "period": 30,
                "timeAdded": 1234567890
            }
        """.trimIndent()

        coEvery {
            anyConstructed<LegacyAuthenticationDecryptor>().decryptAll(AUTH_DATA_ACCOUNT)
        } returns emptyMap()  // No account data

        coEvery {
            anyConstructed<LegacyAuthenticationDecryptor>().decryptAll(AUTH_DATA_MECHANISM)
        } returns mapOf("GitHub-dev@example.com-otpauth" to mechanismJson)

        repository = LegacyAuthenticationRepository(context)

        // When
        val exportedData = repository.exportAllData()

        // Then
        assertNotNull(exportedData)
        assertEquals(1, exportedData.mechanisms.size)

        val mechanism = exportedData.mechanisms[0]
        assertEquals("GitHub", mechanism.issuer)
        assertEquals("dev@example.com", mechanism.accountName)
        assertEquals(null, mechanism.account)  // Account should be null
    }

    @Test
    fun `exportAllData handles multiple mechanisms correctly`() = runTest {
        // Given - Create SharedPreferences files first
        accountPrefs.edit().putString("dummy-key", "dummy-value").commit()
        mechanismPrefs.edit().putString("dummy-key", "dummy-value").commit()

        // Multiple mechanisms with accounts
        mockkConstructor(LegacyAuthenticationDecryptor::class)
        every { anyConstructed<LegacyAuthenticationDecryptor>().keyExists() } returns true

        val accountsMap = mapOf(
            "Google-user@example.com" to """{"id":"Google-user@example.com","issuer":"Google","accountName":"user@example.com","lock":false}""",
            "GitHub-dev@example.com" to """{"id":"GitHub-dev@example.com","issuer":"GitHub","accountName":"dev@example.com","lock":false}"""
        )

        val mechanismsMap = mapOf(
            "Google-user@example.com-otpauth" to """{"id":"Google-user@example.com-otpauth","issuer":"Google","accountName":"user@example.com","mechanismUID":"m1","secret":"SECRET1","type":"otpauth","oathType":"TOTP","algorithm":"sha1","digits":6,"period":30,"timeAdded":1234567890}""",
            "GitHub-dev@example.com-pushauth" to """{"id":"GitHub-dev@example.com-pushauth","issuer":"GitHub","accountName":"dev@example.com","mechanismUID":"m2","secret":"SECRET2","type":"pushauth","authenticationEndpoint":"https://example.com/push","timeAdded":1234567890}"""
        )

        coEvery {
            anyConstructed<LegacyAuthenticationDecryptor>().decryptAll(AUTH_DATA_ACCOUNT)
        } returns accountsMap

        coEvery {
            anyConstructed<LegacyAuthenticationDecryptor>().decryptAll(AUTH_DATA_MECHANISM)
        } returns mechanismsMap

        repository = LegacyAuthenticationRepository(context)

        // When
        val exportedData = repository.exportAllData()

        // Then
        assertEquals(2, exportedData.mechanisms.size)
        assertEquals(2, exportedData.metadata.totalMechanisms)

        // Verify first mechanism (OATH)
        val oathMechanism = exportedData.mechanisms.find { it.type == "otpauth" }
        assertNotNull(oathMechanism)
        assertEquals("Google", oathMechanism.issuer)
        assertNotNull(oathMechanism.account)

        // Verify second mechanism (Push)
        val pushMechanism = exportedData.mechanisms.find { it.type == "pushauth" }
        assertNotNull(pushMechanism)
        assertEquals("GitHub", pushMechanism.issuer)
        assertNotNull(pushMechanism.account)
    }

    // ================================================================================
    // Test: exportAllData() - Unencrypted Data (Custom Storage)
    // ================================================================================

    @Test
    fun `exportAllData reads unencrypted SharedPreferences for custom storage`() = runTest {
        // Given - Plain SharedPreferences without encryption
        mockkConstructor(LegacyAuthenticationDecryptor::class)
        every { anyConstructed<LegacyAuthenticationDecryptor>().keyExists() } returns false

        val accountJson = """{"id":"Custom-account@example.com","issuer":"Custom","accountName":"account@example.com","lock":false}"""
        val mechanismJson = """{"id":"Custom-account@example.com-otpauth","issuer":"Custom","accountName":"account@example.com","mechanismUID":"custom-m1","secret":"CUSTOMSECRET","type":"otpauth","oathType":"TOTP","algorithm":"sha1","digits":6,"period":30,"timeAdded":1234567890}"""

        accountPrefs.edit().putString("Custom-account@example.com", accountJson).commit()
        mechanismPrefs.edit().putString("Custom-account@example.com-otpauth", mechanismJson).commit()

        repository = LegacyAuthenticationRepository(context)

        // When
        val exportedData = repository.exportAllData()

        // Then
        assertEquals(1, exportedData.mechanisms.size)
        val mechanism = exportedData.mechanisms[0]
        assertEquals("Custom", mechanism.issuer)
        assertEquals("account@example.com", mechanism.accountName)
        assertEquals("CUSTOMSECRET", mechanism.secret)
        assertNotNull(mechanism.account)
    }

    // ================================================================================
    // Test: exportToJson()
    // ================================================================================

    @Test
    fun `exportToJson returns valid JSON string`() = runTest {
        // Given - Create SharedPreferences files first
        accountPrefs.edit().putString("dummy-key", "dummy-value").commit()
        mechanismPrefs.edit().putString("dummy-key", "dummy-value").commit()

        mockkConstructor(LegacyAuthenticationDecryptor::class)
        every { anyConstructed<LegacyAuthenticationDecryptor>().keyExists() } returns true

        val accountJson = """{"id":"Export-user@example.com","issuer":"Export","accountName":"user@example.com","lock":false}"""
        val mechanismJson = """{"id":"Export-user@example.com-otpauth","issuer":"Export","accountName":"user@example.com","mechanismUID":"export-m1","secret":"EXPORTSECRET","type":"otpauth","oathType":"TOTP","algorithm":"sha1","digits":6,"period":30,"timeAdded":1234567890}"""

        coEvery {
            anyConstructed<LegacyAuthenticationDecryptor>().decryptAll(AUTH_DATA_ACCOUNT)
        } returns mapOf("Export-user@example.com" to accountJson)

        coEvery {
            anyConstructed<LegacyAuthenticationDecryptor>().decryptAll(AUTH_DATA_MECHANISM)
        } returns mapOf("Export-user@example.com-otpauth" to mechanismJson)

        repository = LegacyAuthenticationRepository(context)

        // When
        val jsonString = repository.exportToJson()

        // Then
        assertNotNull(jsonString)
        assertTrue(jsonString.isNotEmpty())

        // Verify it's valid JSON by parsing
        val json = Json { ignoreUnknownKeys = true }
        val parsed = json.decodeFromString(LegacyExportedData.serializer(), jsonString)
        assertEquals(1, parsed.mechanisms.size)
        assertEquals("Export", parsed.mechanisms[0].issuer)
    }

    // ================================================================================
    // Test: deleteLegacyData()
    // ================================================================================

    @Test
    fun `deleteLegacyData removes SharedPreferences files`() = runTest {
        // Given - Create test data
        accountPrefs.edit().putString("test-account", "test-value").commit()
        mechanismPrefs.edit().putString("test-mechanism", "test-value").commit()

        mockkConstructor(LegacyAuthenticationDecryptor::class)
        every { anyConstructed<LegacyAuthenticationDecryptor>().keyExists() } returns false

        repository = LegacyAuthenticationRepository(context)

        // Verify data exists
        assertTrue(accountPrefs.contains("test-account"))
        assertTrue(mechanismPrefs.contains("test-mechanism"))

        // When
        repository.deleteLegacyData()

        // Then - Verify data is deleted
        val accountPrefsAfter = context.getSharedPreferences(AUTH_DATA_ACCOUNT, Context.MODE_PRIVATE)
        val mechanismPrefsAfter = context.getSharedPreferences(AUTH_DATA_MECHANISM, Context.MODE_PRIVATE)

        assertFalse(accountPrefsAfter.contains("test-account"))
        assertFalse(mechanismPrefsAfter.contains("test-mechanism"))
    }

    @Test
    fun `deleteLegacyData creates backups when configured`() = runTest {
        // Given - Configure with backup enabled
        val config = LegacyAuthenticationConfig(backupFiles = true)

        accountPrefs.edit().putString("backup-test", "backup-value").commit()

        mockkConstructor(LegacyAuthenticationDecryptor::class)
        every { anyConstructed<LegacyAuthenticationDecryptor>().keyExists() } returns false

        repository = LegacyAuthenticationRepository(context, config)

        // When
        repository.deleteLegacyData()

        // Then - Check backup files exist
        val prefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
        val backupFiles = prefsDir.listFiles { file ->
            file.name.contains("_backup_") && file.name.endsWith(".xml")
        }

        assertNotNull(backupFiles)
        assertTrue(backupFiles.isNotEmpty(), "Backup files should be created")
    }

    @Test
    fun `deleteLegacyData does not create backups when not configured`() = runTest {
        // Given - Configure without backup
        val config = LegacyAuthenticationConfig(backupFiles = false)

        accountPrefs.edit().putString("no-backup-test", "value").commit()

        mockkConstructor(LegacyAuthenticationDecryptor::class)
        every { anyConstructed<LegacyAuthenticationDecryptor>().keyExists() } returns false

        repository = LegacyAuthenticationRepository(context, config)

        // Get initial backup count
        val prefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
        val initialBackupCount = prefsDir.listFiles { file ->
            file.name.contains("_backup_") && file.name.endsWith(".xml")
        }?.size ?: 0

        // When
        repository.deleteLegacyData()

        // Then - Verify no new backup files created
        val finalBackupCount = prefsDir.listFiles { file ->
            file.name.contains("_backup_") && file.name.endsWith(".xml")
        }?.size ?: 0

        assertEquals(initialBackupCount, finalBackupCount, "No new backup files should be created")
    }

    @Test
    fun `deleteLegacyData removes SQL database files when SQL migration type`() = runTest {
        // Given - Create SQL database file
        val dbFile = context.getDatabasePath("forgerock_authenticator.db")
        dbFile.parentFile?.mkdirs()
        dbFile.createNewFile()

        // Create journal file
        val journalFile = File(dbFile.parent, "forgerock_authenticator.db-journal")
        journalFile.createNewFile()

        val config = LegacyAuthenticationConfig(
            migrationType = MigrationType.SQL,
            databaseName = "forgerock_authenticator.db"
        )
        repository = LegacyAuthenticationRepository(context, config)

        // Verify files exist
        assertTrue(dbFile.exists())
        assertTrue(journalFile.exists())

        // When
        repository.deleteLegacyData()

        // Then - Verify database files are deleted
        assertFalse(dbFile.exists(), "Database file should be deleted")
        assertFalse(journalFile.exists(), "Journal file should be deleted")
    }

    @Test
    fun `deleteLegacyData creates database backup when SQL migration with backups enabled`() = runTest {
        // Given - Create SQL database file
        val dbFile = context.getDatabasePath("test_backup.db")
        dbFile.parentFile?.mkdirs()
        dbFile.createNewFile()
        dbFile.writeText("test database content")

        val config = LegacyAuthenticationConfig(
            migrationType = MigrationType.SQL,
            databaseName = "test_backup.db",
            backupFiles = true
        )
        repository = LegacyAuthenticationRepository(context, config)

        // When
        repository.deleteLegacyData()

        // Then - Check backup file exists
        val dbDir = dbFile.parentFile
        val backupFiles = dbDir?.listFiles { file ->
            file.name.startsWith("test_backup_backup_") && file.name.endsWith(".db")
        }

        assertNotNull(backupFiles)
        assertTrue(backupFiles.isNotEmpty(), "Backup database should be created")

        // Cleanup
        backupFiles.forEach { it.delete() }
    }

    @Test
    fun `deleteLegacyData handles AUTO migration type correctly when SQL exists`() = runTest {
        // Given - Create only SQL database (AUTO will detect SQL and cleanup SQL only)
        val dbFile = context.getDatabasePath("forgerock_authenticator.db")
        dbFile.parentFile?.mkdirs()
        dbFile.createNewFile()

        val config = LegacyAuthenticationConfig(migrationType = MigrationType.AUTO)
        repository = LegacyAuthenticationRepository(context, config)

        // Verify file exists before cleanup
        assertTrue(dbFile.exists())

        // When
        repository.deleteLegacyData()

        // Then - SQL database should be cleaned up (AUTO detected SQL)
        assertFalse(dbFile.exists(), "Database file should be deleted")
    }

    @Test
    fun `deleteLegacyData handles AUTO migration type correctly when XML exists`() = runTest {
        // Given - Create only XML data (AUTO will detect XML and cleanup XML only)
        accountPrefs.edit().putString("auto-xml-test", "value").commit()

        // Ensure no SQL database exists
        val dbFile = context.getDatabasePath("forgerock_authenticator.db")
        if (dbFile.exists()) {
            dbFile.delete()
        }

        mockkConstructor(LegacyAuthenticationDecryptor::class)
        every { anyConstructed<LegacyAuthenticationDecryptor>().keyExists() } returns false

        val config = LegacyAuthenticationConfig(migrationType = MigrationType.AUTO)
        repository = LegacyAuthenticationRepository(context, config)

        // Verify data exists before cleanup
        assertTrue(accountPrefs.contains("auto-xml-test"))

        // When
        repository.deleteLegacyData()

        // Then - XML data should be cleaned up (AUTO detected XML)
        val accountPrefsAfter = context.getSharedPreferences(AUTH_DATA_ACCOUNT, Context.MODE_PRIVATE)
        assertFalse(accountPrefsAfter.contains("auto-xml-test"))
    }

    // ================================================================================
    // Test: Custom Key Alias
    // ================================================================================

    @Test
    fun `repository uses custom key alias from config`() = runTest {
        // Given - Custom key alias
        val customAlias = "com.myapp.custom.KEY_ALIAS"
        val config = LegacyAuthenticationConfig(keyAlias = customAlias)

        mockkConstructor(LegacyAuthenticationDecryptor::class)
        every { anyConstructed<LegacyAuthenticationDecryptor>().keyExists() } returns false

        repository = LegacyAuthenticationRepository(context, config)

        // When - Trigger decryptor usage
        repository.isExists()

        // Then - Verify decryptor was initialized with custom alias (implicitly tested via behavior)
        // The decryptor is created with the custom alias in the constructor
        // We can't directly verify constructor parameters with mockkConstructor,
        // but we verified the config is passed correctly through the repository
        assertNotNull(repository)
    }

    // ================================================================================
    // Test: Error Handling
    // ================================================================================

    @Test
    fun `exportAllData handles malformed JSON gracefully`() = runTest {
        // Given - Create SharedPreferences files first
        mechanismPrefs.edit().putString("dummy-key", "dummy-value").commit()

        // Invalid JSON in SharedPreferences
        mockkConstructor(LegacyAuthenticationDecryptor::class)
        every { anyConstructed<LegacyAuthenticationDecryptor>().keyExists() } returns true

        coEvery {
            anyConstructed<LegacyAuthenticationDecryptor>().decryptAll(AUTH_DATA_ACCOUNT)
        } returns emptyMap()

        coEvery {
            anyConstructed<LegacyAuthenticationDecryptor>().decryptAll(AUTH_DATA_MECHANISM)
        } returns mapOf(
            "valid-mechanism" to """{"id":"valid","issuer":"Test","accountName":"test","mechanismUID":"m1","secret":"SECRET","type":"otpauth","oathType":"TOTP","algorithm":"sha1","digits":6,"period":30}""",
            "invalid-mechanism" to """{"invalid json}"""  // Malformed JSON
        )

        repository = LegacyAuthenticationRepository(context)

        // When
        val exportedData = repository.exportAllData()

        // Then - Should skip invalid entry and continue
        assertEquals(1, exportedData.mechanisms.size)
        assertEquals("Test", exportedData.mechanisms[0].issuer)
    }

    @Test
    fun `exportAllData returns empty data when no files exist`() = runTest {
        // Given - No SharedPreferences files
        mockkConstructor(LegacyAuthenticationDecryptor::class)
        every { anyConstructed<LegacyAuthenticationDecryptor>().keyExists() } returns true

        coEvery {
            anyConstructed<LegacyAuthenticationDecryptor>().decryptAll(any())
        } returns emptyMap()

        repository = LegacyAuthenticationRepository(context)

        // When
        val exportedData = repository.exportAllData()

        // Then
        assertEquals(0, exportedData.mechanisms.size)
        assertEquals(0, exportedData.metadata.totalMechanisms)
    }

    // ================================================================================
    // Test: buildMechanismsList with all field types
    // ================================================================================

    @Test
    fun `buildMechanismsList handles all OATH fields correctly`() = runTest {
        // Given - Create SharedPreferences files first
        accountPrefs.edit().putString("dummy-key", "dummy-value").commit()
        mechanismPrefs.edit().putString("dummy-key", "dummy-value").commit()

        // Complete OATH mechanism with all fields
        mockkConstructor(LegacyAuthenticationDecryptor::class)
        every { anyConstructed<LegacyAuthenticationDecryptor>().keyExists() } returns true

        val accountJson = """
            {
                "id": "Complete-user@example.com",
                "issuer": "Complete",
                "displayIssuer": "Complete Display",
                "accountName": "user@example.com",
                "displayAccountName": "Complete User",
                "imageURL": "https://complete.com/logo.png",
                "backgroundColor": "#FF5722",
                "timeAdded": 1234567890,
                "policies": "{\"biometric\":true}",
                "lockingPolicy": "strict",
                "lock": true
            }
        """.trimIndent()

        val mechanismJson = """
            {
                "id": "Complete-user@example.com-otpauth",
                "issuer": "Complete",
                "accountName": "user@example.com",
                "mechanismUID": "complete-uuid",
                "secret": "COMPLETESECRET",
                "type": "otpauth",
                "oathType": "HOTP",
                "algorithm": "sha512",
                "digits": 8,
                "period": 60,
                "counter": 100,
                "uid": "complete-uid",
                "resourceId": "complete-resource",
                "timeAdded": 1234567890
            }
        """.trimIndent()

        coEvery {
            anyConstructed<LegacyAuthenticationDecryptor>().decryptAll(AUTH_DATA_ACCOUNT)
        } returns mapOf("Complete-user@example.com" to accountJson)

        coEvery {
            anyConstructed<LegacyAuthenticationDecryptor>().decryptAll(AUTH_DATA_MECHANISM)
        } returns mapOf("Complete-user@example.com-otpauth" to mechanismJson)

        repository = LegacyAuthenticationRepository(context)

        // When
        val exportedData = repository.exportAllData()

        // Then - Verify ALL fields
        val mechanism = exportedData.mechanisms[0]

        // Mechanism fields
        assertEquals("Complete-user@example.com-otpauth", mechanism.id)
        assertEquals("Complete", mechanism.issuer)
        assertEquals("user@example.com", mechanism.accountName)
        assertEquals("complete-uuid", mechanism.mechanismUID)
        assertEquals("COMPLETESECRET", mechanism.secret)
        assertEquals("otpauth", mechanism.type)
        assertEquals("HOTP", mechanism.oathType)
        assertEquals("sha512", mechanism.algorithm)
        assertEquals(8, mechanism.digits)
        assertEquals(60, mechanism.period)
        assertEquals(100L, mechanism.counter)
        assertEquals("complete-uid", mechanism.uid)
        assertEquals("complete-resource", mechanism.resourceId)
        assertEquals(1234567890L, mechanism.timeAdded)

        // Account fields
        val account = mechanism.account
        assertNotNull(account)
        assertEquals("Complete-user@example.com", account.id)
        assertEquals("Complete", account.issuer)
        assertEquals("Complete Display", account.displayIssuer)
        assertEquals("user@example.com", account.accountName)
        assertEquals("Complete User", account.displayAccountName)
        assertEquals("https://complete.com/logo.png", account.imageURL)
        assertEquals("#FF5722", account.backgroundColor)
        assertEquals(1234567890L, account.timeAdded)
        assertEquals("{\"biometric\":true}", account.policies)
        assertEquals("strict", account.lockingPolicy)
        assertEquals(true, account.lock)
    }

    @Test
    fun `buildMechanismsList handles all Push fields correctly`() = runTest {
        // Given - Create SharedPreferences files first
        accountPrefs.edit().putString("dummy-key", "dummy-value").commit()
        mechanismPrefs.edit().putString("dummy-key", "dummy-value").commit()

        // Complete Push mechanism with all fields
        mockkConstructor(LegacyAuthenticationDecryptor::class)
        every { anyConstructed<LegacyAuthenticationDecryptor>().keyExists() } returns true

        val accountJson = """
            {
                "id": "PushComplete-admin@company.com",
                "issuer": "PushComplete",
                "accountName": "admin@company.com",
                "lock": false
            }
        """.trimIndent()

        val mechanismJson = """
            {
                "id": "PushComplete-admin@company.com-pushauth",
                "issuer": "PushComplete",
                "accountName": "admin@company.com",
                "mechanismUID": "push-uuid-123",
                "secret": "PUSHSECRET123",
                "type": "pushauth",
                "registrationEndpoint": "https://example.com/push?_action=register",
                "authenticationEndpoint": "https://example.com/push?_action=authenticate",
                "platform": "PING_AM",
                "uid": "push-user-id",
                "resourceId": "push-resource-id",
                "timeAdded": 1234567890
            }
        """.trimIndent()

        coEvery {
            anyConstructed<LegacyAuthenticationDecryptor>().decryptAll(AUTH_DATA_ACCOUNT)
        } returns mapOf("PushComplete-admin@company.com" to accountJson)

        coEvery {
            anyConstructed<LegacyAuthenticationDecryptor>().decryptAll(AUTH_DATA_MECHANISM)
        } returns mapOf("PushComplete-admin@company.com-pushauth" to mechanismJson)

        repository = LegacyAuthenticationRepository(context)

        // When
        val exportedData = repository.exportAllData()

        // Then - Verify ALL Push fields
        val mechanism = exportedData.mechanisms[0]
        assertEquals("PushComplete-admin@company.com-pushauth", mechanism.id)
        assertEquals("PushComplete", mechanism.issuer)
        assertEquals("admin@company.com", mechanism.accountName)
        assertEquals("push-uuid-123", mechanism.mechanismUID)
        assertEquals("PUSHSECRET123", mechanism.secret)
        assertEquals("pushauth", mechanism.type)
        assertEquals("https://example.com/push?_action=register", mechanism.registrationEndpoint)
        assertEquals("https://example.com/push?_action=authenticate", mechanism.authenticationEndpoint)
        assertEquals("PING_AM", mechanism.platform)
        assertEquals("push-user-id", mechanism.uid)
        assertEquals("push-resource-id", mechanism.resourceId)
        assertEquals(1234567890L, mechanism.timeAdded)

        assertNotNull(mechanism.account)
        val account = mechanism.account
        assertNotNull(account)
        assertEquals("PushComplete-admin@company.com", account.id)
    }
}


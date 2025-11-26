/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.binding.migration

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedFile
import androidx.test.core.app.ApplicationProvider
import com.pingidentity.android.ContextProvider
import com.pingidentity.device.binding.UserKey
import com.pingidentity.device.binding.UserKeyStorageConfig
import com.pingidentity.device.binding.UserKeysStorage
import com.pingidentity.device.binding.authenticator.AppPinConfig
import com.pingidentity.device.binding.authenticator.DeviceBindingAuthenticationType
import com.pingidentity.migration.MigrationProgress
import com.pingidentity.storage.MemoryStorage
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class BindingMigrationTest {

    private lateinit var context: Context
    private lateinit var sharedPreferences: SharedPreferences

    @BeforeTest
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        ContextProvider.init(context)

        // Setup real SharedPreferences
        sharedPreferences = context.getSharedPreferences("org.forgerock.v1.DEVICE_REPO", Context.MODE_PRIVATE)

        // Mock LegacyEncryptedSharedPreferences.getInstance to return real SharedPreferences
        mockkObject(LegacyEncryptedSharedPreferences.Companion)
        every {
            LegacyEncryptedSharedPreferences.Companion.getInstance(any(), any(), any())
        } returns sharedPreferences

        // Mock LegacyEncryptedFile.getInstance to return mock EncryptedFile with real File operations
        mockkObject(LegacyEncryptedFile)
        every {
            LegacyEncryptedFile.getInstance(any(), any<File>(), any())
        } answers {
            val file = secondArg<File>()
            createMockEncryptedFileWithRealFile(file)
        }

        // Mock AppPinConfig to use MemoryStorage instead of real storage
        mockkConstructor(AppPinConfig::class)
        every { anyConstructed<AppPinConfig>().storage() } returns MemoryStorage()

        // Mock UserKeysStorage to use MemoryStorage instead of real storage
        mockkConstructor(UserKeyStorageConfig::class)
        every { anyConstructed<UserKeyStorageConfig>().storage() } returns MemoryStorage()

        cleanup()
    }

    private fun createMockEncryptedFileWithRealFile(file: File): EncryptedFile {
        val mockEncryptedFile = mockk<EncryptedFile>()

        every { mockEncryptedFile.openFileOutput() } answers {
            file.parentFile?.mkdirs()
            FileOutputStream(file)
        }

        every { mockEncryptedFile.openFileInput() } answers {
            if (file.exists()) {
                FileInputStream(file)
            } else {
                throw java.io.FileNotFoundException("File not found: ${file.absolutePath}")
            }
        }

        return mockEncryptedFile
    }

    @AfterTest
    fun tearDown() {
        cleanup()
        unmockkAll()
    }

    private fun cleanup() {
        // Clean up any existing files and preferences
        try {
            context.deleteSharedPreferences(ORG_FORGEROCK_V_1_DEVICE_REPO)
            val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            keyStore.deleteEntry(ORG_FORGEROCK_V_1_DEVICE_REPO)
        } catch (e: Exception) {
            // Ignore cleanup errors
        }
    }

    @Test
    fun `migration with no legacy repository should abort early`() = runTest {
        // Given - No legacy repository exists

        // When
        val progressList = BindingMigration.migration.migrate(context).toList()

        // Then
        assertTrue(progressList.any { it is MigrationProgress.Started })
        assertTrue(progressList.any { it is MigrationProgress.Success })

        // Should have StepCompleted event for step 1 that aborts
        val stepCompletedEvents = progressList.filterIsInstance<MigrationProgress.StepCompleted>()
        assertTrue(stepCompletedEvents.isNotEmpty())
        assertEquals("Check for legacy encrypted file keystore", stepCompletedEvents[0].step.description)

        // Should not have InProgress messages for step 2 and 3 since step 1 aborts
        val inProgressMessages = progressList.filterIsInstance<MigrationProgress.InProgress>()
        assertTrue(inProgressMessages.size <= 1) // Only step 1 should run
    }

    @Test
    fun `migration with empty legacy repository should abort after step 1`() = runTest {
        // Given - Legacy repository exists but is empty
        createEmptyLegacyRepository()

        // When
        val progressList = BindingMigration.migration.migrate(context).toList()

        // Then
        assertTrue(progressList.any { it is MigrationProgress.Started })
        assertTrue(progressList.any { it is MigrationProgress.Success })

        // Should have step 1 progress but abort before step 2
        val stepStartedEvents = progressList.filterIsInstance<MigrationProgress.StepCompleted>()
        assertTrue(stepStartedEvents.size >= 1)
        assertEquals("Check for legacy encrypted file keystore", stepStartedEvents[0].step.description)
    }

    @Test
    fun `migration with legacy data completes all steps successfully`() = runTest {
        // Given - Legacy repository with test data
        val testUserKeys = createTestLegacyData()

        // When
        val progressList = BindingMigration.migration.migrate(context).toList()

        // Then
        assertTrue(progressList.any { it is MigrationProgress.Started })
        assertTrue(progressList.any { it is MigrationProgress.Success })

        // Should have all three steps completed
        val stepCompletedEvents = progressList.filterIsInstance<MigrationProgress.StepCompleted>()
        assertTrue(stepCompletedEvents.size >= 3)

        assertEquals("Check for legacy encrypted file keystore", stepCompletedEvents[0].step.description)
        assertEquals("Migrate user keys to new user keys storage", stepCompletedEvents[1].step.description)
        assertEquals("Migrate App PIN keystore file to new encrypted keystore file", stepCompletedEvents[2].step.description)

        // Verify data was migrated to new storage
        val userKeysStorage = UserKeysStorage()
        val migratedKeys = userKeysStorage.findAll()
        assertEquals(testUserKeys.size, migratedKeys.size)
        assertTrue(migratedKeys.containsAll(testUserKeys))
    }

    @Test
    fun `migration handles APP_PIN keys correctly in step 3`() = runTest {
        // Given - Legacy repository with APP_PIN key
        val appPinKey = UserKey(
            id = "app_pin_key_id",
            userId = "test_user_app_pin",
            userName = "Test User APP PIN",
            kid = "app_pin_kid",
            authType = DeviceBindingAuthenticationType.APPLICATION_PIN,
            createdAt = System.currentTimeMillis()
        )

        val biometricKey = UserKey(
            id = "biometric_key_id",
            userId = "test_user_biometric",
            userName = "Test User Biometric",
            kid = "biometric_kid",
            authType = DeviceBindingAuthenticationType.BIOMETRIC_ONLY,
            createdAt = System.currentTimeMillis()
        )

        createLegacyRepositoryWithKeys(listOf(appPinKey, biometricKey))

        // Create encrypted file for APP_PIN key
        val keyAlias = com.pingidentity.device.binding.CryptoKey(appPinKey.userId).keyAlias
        val testKeyData = "encrypted_key_data_for_app_pin".toByteArray()
        val encryptedFile = LegacyEncryptedFile.getInstance(
            context,
            File(context.filesDir, keyAlias),
            "org.forgerock.v1.KEY_STORE_MASTER_KEY_ALIAS"
        )
        encryptedFile.openFileOutput().use { it.write(testKeyData) }

        // When
        val progressList = BindingMigration.migration.migrate(context).toList()

        // Then
        assertTrue(progressList.any { it is MigrationProgress.Success })

        // Verify that the legacy encrypted file was deleted (which indicates migration happened)
        val legacyKeyStore = LegacyEncryptedFileKeyStore(keyAlias)
        assertTrue(!legacyKeyStore.exist(context))

        // Verify APP_PIN data was migrated to MemoryStorage
        val appPinConfig = AppPinConfig()

        val migratedData = appPinConfig.storage().get()
        assertTrue(testKeyData.contentEquals(migratedData))
    }

    @Test
    fun `migration step 3 continues with other keys when one fails`() = runTest {
        // Given - Multiple APP_PIN keys, one will fail
        val appPinKey1 = UserKey(
            id = "app_pin_key_1",
            userId = "test_user_1",
            userName = "Test User 1",
            kid = "app_pin_kid_1",
            authType = DeviceBindingAuthenticationType.APPLICATION_PIN,
            createdAt = System.currentTimeMillis()
        )

        val appPinKey2 = UserKey(
            id = "app_pin_key_2",
            userId = "test_user_2",
            userName = "Test User 2",
            kid = "app_pin_kid_2",
            authType = DeviceBindingAuthenticationType.APPLICATION_PIN,
            createdAt = System.currentTimeMillis()
        )

        createLegacyRepositoryWithKeys(listOf(appPinKey1, appPinKey2))

        // Create encrypted file for only one key (the other will fail)
        val keyAlias2 = com.pingidentity.device.binding.CryptoKey(appPinKey2.userId).keyAlias
        val testKeyData = "encrypted_key_data_for_app_pin_2".toByteArray()
        val encryptedFile = LegacyEncryptedFile.getInstance(
            context,
            File(context.filesDir, keyAlias2),
            "org.forgerock.v1.KEY_STORE_MASTER_KEY_ALIAS"
        )
        encryptedFile.openFileOutput().use { it.write(testKeyData) }

        // When
        val progressList = BindingMigration.migration.migrate(context).toList()

        // Then - Migration should still complete successfully
        assertTrue(progressList.any { it is MigrationProgress.Success })

        // Verify the successful key was migrated to MemoryStorage
        val appPinConfig2 = AppPinConfig()

        val migratedData = appPinConfig2.storage().get()
        assertTrue(testKeyData.contentEquals(migratedData))

        // Verify the legacy file was deleted
        val legacyKeyStore = LegacyEncryptedFileKeyStore(keyAlias2)
        assertTrue(!legacyKeyStore.exist(context))
    }

    private fun createEmptyLegacyRepository() {
        // Create an empty SharedPreferences file using the mocked instance
        sharedPreferences.edit().clear().apply()
    }

    private fun createTestLegacyData(): List<UserKey> {
        val testKeys = listOf(
            UserKey(
                id = "key1",
                userId = "user1",
                userName = "Test User 1",
                kid = "kid1",
                authType = DeviceBindingAuthenticationType.BIOMETRIC_ONLY,
                createdAt = System.currentTimeMillis()
            ),
            UserKey(
                id = "key2",
                userId = "user2",
                userName = "Test User 2",
                kid = "kid2",
                authType = DeviceBindingAuthenticationType.APPLICATION_PIN,
                createdAt = System.currentTimeMillis()
            )
        )

        createLegacyRepositoryWithKeys(testKeys)
        return testKeys
    }

    private fun createLegacyRepositoryWithKeys(keys: List<UserKey>) {
        // Use the mocked SharedPreferences instance
        val editor = sharedPreferences.edit()

        keys.forEach { key ->
            val json = """
                {
                    "$idKey": "${key.id}",
                    "$userIdKey": "${key.userId}",
                    "$userNameKey": "${key.userName}",
                    "$kidKey": "${key.kid}",
                    "$authTypeKey": "${key.authType.name}",
                    "$createdAtKey": ${key.createdAt}
                }
            """.trimIndent()
            editor.putString(key.id, json)
        }

        editor.apply()
    }
}

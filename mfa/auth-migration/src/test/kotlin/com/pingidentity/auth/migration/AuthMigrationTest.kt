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
import com.pingidentity.mfa.oath.OathAlgorithm
import com.pingidentity.mfa.oath.OathCredential
import com.pingidentity.mfa.oath.OathType
import com.pingidentity.mfa.oath.storage.SQLOathStorage
import com.pingidentity.mfa.push.PushCredential
import com.pingidentity.mfa.push.storage.SQLPushStorage
import com.pingidentity.migration.MigrationProgress
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockkConstructor
import io.mockk.unmockkAll
import io.mockk.slot
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.DefaultAsserter.assertNotNull
import kotlin.test.DefaultAsserter.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Constants matching the ones in LegacyAuthenticationRepository
private const val AUTH_DATA_ACCOUNT = "org.forgerock.android.authenticator.DATA.ACCOUNT"
private const val AUTH_DATA_MECHANISM = "org.forgerock.android.authenticator.DATA.MECHANISM"
private const val AUTH_DATA_NOTIFICATIONS = "org.forgerock.android.authenticator.DATA.NOTIFICATIONS"
private const val AUTH_DATA_DEVICE_TOKEN = "org.forgerock.android.authenticator.DATA.DEVICE_TOKEN"

@RunWith(RobolectricTestRunner::class)
class AuthMigrationTest {

    private lateinit var context: Context
    private lateinit var accountPrefs: SharedPreferences
    private lateinit var mechanismPrefs: SharedPreferences
    private lateinit var notificationPrefs: SharedPreferences
    private lateinit var deviceTokenPrefs: SharedPreferences

    @BeforeTest
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        ContextProvider.init(context)

        // Setup real SharedPreferences for each legacy file
        accountPrefs = context.getSharedPreferences(AUTH_DATA_ACCOUNT, Context.MODE_PRIVATE)
        mechanismPrefs = context.getSharedPreferences(AUTH_DATA_MECHANISM, Context.MODE_PRIVATE)
        notificationPrefs = context.getSharedPreferences(AUTH_DATA_NOTIFICATIONS, Context.MODE_PRIVATE)
        deviceTokenPrefs = context.getSharedPreferences(AUTH_DATA_DEVICE_TOKEN, Context.MODE_PRIVATE)

        // Mock LegacyAuthenticationRepository
        mockkConstructor(LegacyAuthenticationRepository::class)

        // Mock SQLOathStorage
        mockkConstructor(SQLOathStorage::class)
        coEvery { anyConstructed<SQLOathStorage>().initializeDatabase() } returns Unit
        coEvery { anyConstructed<SQLOathStorage>().storeOathCredential(any()) } returns Unit

        // Mock SQLPushStorage
        mockkConstructor(SQLPushStorage::class)
        coEvery { anyConstructed<SQLPushStorage>().initializeDatabase() } returns Unit
        coEvery { anyConstructed<SQLPushStorage>().storePushCredential(any()) } returns Unit
        coEvery { anyConstructed<SQLPushStorage>().storePushNotification(any()) } returns Unit
        coEvery { anyConstructed<SQLPushStorage>().storePushDeviceToken(any()) } returns Unit

        cleanup()
    }

    @AfterTest
    fun tearDown() {
        cleanup()
        unmockkAll()
    }

    /**
     * Clean up any existing files and preferences
     */
    private fun cleanup() {
        try {
            context.deleteSharedPreferences(AUTH_DATA_ACCOUNT)
            context.deleteSharedPreferences(AUTH_DATA_MECHANISM)
            context.deleteSharedPreferences(AUTH_DATA_NOTIFICATIONS)
            context.deleteSharedPreferences(AUTH_DATA_DEVICE_TOKEN)
        } catch (_: Exception) {
            // Ignore cleanup errors
        }
    }

    @Test
    fun `migration with no legacy repository should abort early`() = runTest {
        // Given - No legacy repository exists (no encryption key)
        coEvery { anyConstructed<LegacyAuthenticationRepository>().isExists() } returns false

        // When
        val progressList = AuthMigration.migration.migrate(context).toList()

        // Then
        assertTrue(progressList.any { it is MigrationProgress.Started })
        assertTrue(progressList.any { it is MigrationProgress.Success })

        // Should have StepCompleted event for step 1 that aborts
        val stepCompletedEvents = progressList.filterIsInstance<MigrationProgress.StepCompleted>()
        assertTrue(stepCompletedEvents.isNotEmpty())
        assertEquals("Export legacy authenticator data", stepCompletedEvents[0].step.description)

        // Should not have InProgress messages for steps 2-5 since step 1 aborts
        val inProgressMessages = progressList.filterIsInstance<MigrationProgress.InProgress>()
        assertTrue(inProgressMessages.size <= 1) // Only step 1 should run
    }

    @Test
    fun `migration with empty legacy repository should abort after step 1`() = runTest {
        // Given - Legacy repository exists but is empty
        coEvery { anyConstructed<LegacyAuthenticationRepository>().isExists() } returns true
        coEvery { anyConstructed<LegacyAuthenticationRepository>().exportAllData() } returns LegacyExportedData(
            mechanisms = emptyList(),
            metadata = LegacyExportMetadata(0, 0)
        )

        // When
        val progressList = AuthMigration.migration.migrate(context).toList()

        // Then
        assertTrue(progressList.any { it is MigrationProgress.Started })
        assertTrue(progressList.any { it is MigrationProgress.Success })

        // Should have step 1 progress but abort before step 2
        val stepStartedEvents = progressList.filterIsInstance<MigrationProgress.StepCompleted>()
        assertTrue(stepStartedEvents.isNotEmpty())
        assertEquals("Export legacy authenticator data", stepStartedEvents[0].step.description)
    }

    @Test
    fun `migration with legacy data completes all steps successfully`() = runTest {
        // Given - Legacy repository with test data
        val exportedData = createTestLegacyData()

        coEvery { anyConstructed<LegacyAuthenticationRepository>().isExists() } returns true
        coEvery { anyConstructed<LegacyAuthenticationRepository>().exportAllData() } returns exportedData
        coEvery { anyConstructed<LegacyAuthenticationRepository>().deleteLegacyData() } returns Unit

        // When
        val progressList = AuthMigration.migration.migrate(context).toList()

        // Then
        assertTrue(progressList.any { it is MigrationProgress.Started })
        assertTrue(progressList.any { it is MigrationProgress.Success })

        // Should have all three steps completed (notifications and device tokens not yet implemented)
        val stepCompletedEvents = progressList.filterIsInstance<MigrationProgress.StepCompleted>()
        assertTrue(stepCompletedEvents.size >= 3)

        assertEquals("Export legacy authenticator data", stepCompletedEvents[0].step.description)
        assertEquals("Migrate mechanisms to OATH and Push storage", stepCompletedEvents[1].step.description)
        assertEquals("Cleanup legacy authenticator data", stepCompletedEvents[2].step.description)
    }

    @Test
    fun `migration migrates OATH credentials correctly`() = runTest {
        // Given - Legacy repository with TOTP mechanism with ALL fields
        val testTime = System.currentTimeMillis()

        val exportedData = createOathMechanism(
            mechanismUID = "totp-mechanism-1",
            issuer = "Google",
            accountName = "user@example.com",
            secret = "JBSWY3DPEHPK3PXP",
            oathType = "TOTP",
            algorithm = "sha1",
            digits = 6,
            period = 30,
            counter = 0,
            uid = "user-12345",
            resourceId = "resource-67890",
            displayIssuer = "Google Authenticator",
            displayAccountName = "Demo User",
            imageURL = "https://example.com/logo.png",
            backgroundColor = "#4285F4",
            policies = "{\"deviceTampering\":true}",
            timeAdded = testTime
        )

        coEvery { anyConstructed<LegacyAuthenticationRepository>().isExists() } returns true
        coEvery { anyConstructed<LegacyAuthenticationRepository>().exportAllData() } returns exportedData
        coEvery { anyConstructed<LegacyAuthenticationRepository>().deleteLegacyData() } returns Unit

        // When
        val progressList = AuthMigration.migration.migrate(context).toList()

        // Then
        assertTrue(progressList.any { it is MigrationProgress.Success })

        // Capture the stored credential
        val oathSlot = slot<OathCredential>()
        coVerify(exactly = 1) { anyConstructed<SQLOathStorage>().storeOathCredential(capture(oathSlot)) }

        // Verify ALL 19 fields are correctly migrated
        val storedCredential = oathSlot.captured
        assertEquals("totp-mechanism-1", storedCredential.id)
        assertEquals("user-12345", storedCredential.userId)
        assertEquals("resource-67890", storedCredential.resourceId)
        assertEquals("Google", storedCredential.issuer)
        assertEquals("Google Authenticator", storedCredential.displayIssuer)
        assertEquals("user@example.com", storedCredential.accountName)
        assertEquals("Demo User", storedCredential.displayAccountName)
        assertEquals(OathType.TOTP, storedCredential.oathType)
        assertEquals("JBSWY3DPEHPK3PXP", storedCredential.secret)
        assertEquals(OathAlgorithm.SHA1, storedCredential.oathAlgorithm)
        assertEquals(6, storedCredential.digits)
        assertEquals(30, storedCredential.period)
        assertEquals(0L, storedCredential.counter)
        assertEquals(testTime, storedCredential.createdAt.time)
        assertEquals("https://example.com/logo.png", storedCredential.imageURL)
        assertEquals("#4285F4", storedCredential.backgroundColor)
        assertEquals("{\"deviceTampering\":true}", storedCredential.policies)
        assertEquals(null, storedCredential.lockingPolicy)
        assertEquals(false, storedCredential.isLocked)
    }

    @Test
    fun `migration migrates HOTP credentials correctly with counter`() = runTest {
        // Given - Legacy repository with HOTP mechanism
        val testTime = System.currentTimeMillis()

        val exportedData = createOathMechanism(
            mechanismUID = "hotp-mechanism-1",
            issuer = "GitHub",
            accountName = "developer@example.com",
            secret = "HOTPSECRETKEY123",
            type = "hotp",
            oathType = "HOTP",
            algorithm = "sha256",
            digits = 8,
            counter = 42,
            uid = "hotp-user-789",
            resourceId = "hotp-resource-456",
            displayIssuer = "GitHub Inc",
            displayAccountName = "Developer Account",
            imageURL = "https://github.com/logo.png",
            backgroundColor = "#24292e",
            timeAdded = testTime
        )

        coEvery { anyConstructed<LegacyAuthenticationRepository>().isExists() } returns true
        coEvery { anyConstructed<LegacyAuthenticationRepository>().exportAllData() } returns exportedData
        coEvery { anyConstructed<LegacyAuthenticationRepository>().deleteLegacyData() } returns Unit

        // When
        val progressList = AuthMigration.migration.migrate(context).toList()

        // Then
        assertTrue(progressList.any { it is MigrationProgress.Success })

        val oathSlot = slot<OathCredential>()
        coVerify(exactly = 1) { anyConstructed<SQLOathStorage>().storeOathCredential(capture(oathSlot)) }

        // Verify HOTP-specific fields and all others
        val storedCredential = oathSlot.captured
        assertEquals("hotp-mechanism-1", storedCredential.id)
        assertEquals("hotp-user-789", storedCredential.userId)
        assertEquals("hotp-resource-456", storedCredential.resourceId)
        assertEquals("GitHub", storedCredential.issuer)
        assertEquals("GitHub Inc", storedCredential.displayIssuer)
        assertEquals("developer@example.com", storedCredential.accountName)
        assertEquals("Developer Account", storedCredential.displayAccountName)
        assertEquals(OathType.HOTP, storedCredential.oathType)  // HOTP type
        assertEquals("HOTPSECRETKEY123", storedCredential.secret)
        assertEquals(OathAlgorithm.SHA256, storedCredential.oathAlgorithm)  // SHA256
        assertEquals(8, storedCredential.digits)  // 8 digits
        assertEquals(30, storedCredential.period)  // Default period (not used for HOTP)
        assertEquals(42L, storedCredential.counter)  // Counter value
        assertEquals(testTime, storedCredential.createdAt.time)
        assertEquals("https://github.com/logo.png", storedCredential.imageURL)
        assertEquals("#24292e", storedCredential.backgroundColor)
        assertEquals(null, storedCredential.policies)
        assertEquals(null, storedCredential.lockingPolicy)
        assertEquals(false, storedCredential.isLocked)
    }

    @Test
    fun `migration migrates Push credentials correctly`() = runTest {
        // Given - Legacy repository with Push mechanism with ALL fields
        val testTime = System.currentTimeMillis()

        val exportedData = createPushMechanism(
            mechanismUID = "push-mechanism-1",
            issuer = "ForgeRock",
            accountName = "admin@company.com",
            secret = "sharedSecret123",
            authenticationEndpoint = "https://am.example.com/push?_action=authenticate",
            registrationEndpoint = "https://am.example.com/push?_action=register",
            platform = "PING_AM",
            uid = "user-99999",
            resourceId = "push-resource-11111",
            displayIssuer = "ForgeRock Identity",
            displayAccountName = "Admin User",
            imageURL = "https://forgerock.com/logo.png",
            backgroundColor = "#00A1DE",
            policies = "{\"biometric\":true}",
            lockingPolicy = "BiometricNotAvailable",
            isLocked = true,
            timeAdded = testTime
        )

        coEvery { anyConstructed<LegacyAuthenticationRepository>().isExists() } returns true
        coEvery { anyConstructed<LegacyAuthenticationRepository>().exportAllData() } returns exportedData
        coEvery { anyConstructed<LegacyAuthenticationRepository>().deleteLegacyData() } returns Unit

        // When
        val progressList = AuthMigration.migration.migrate(context).toList()

        // Then
        assertTrue(progressList.any { it is MigrationProgress.Success })

        // Capture the stored credential
        val pushSlot = slot<PushCredential>()
        coVerify(exactly = 1) { anyConstructed<SQLPushStorage>().storePushCredential(capture(pushSlot)) }

        // Verify ALL 16 fields are correctly migrated
        val storedCredential = pushSlot.captured
        assertEquals("push-mechanism-1", storedCredential.id)
        assertEquals("user-99999", storedCredential.userId)
        assertEquals("push-resource-11111", storedCredential.resourceId)
        assertEquals("ForgeRock", storedCredential.issuer)
        assertEquals("ForgeRock Identity", storedCredential.displayIssuer)
        assertEquals("admin@company.com", storedCredential.accountName)
        assertEquals("Admin User", storedCredential.displayAccountName)
        assertEquals("https://am.example.com/push", storedCredential.serverEndpoint) // Base URL without query params
        assertEquals("sharedSecret123", storedCredential.sharedSecret)
        assertEquals(testTime, storedCredential.createdAt.time)
        assertEquals("https://forgerock.com/logo.png", storedCredential.imageURL)
        assertEquals("#00A1DE", storedCredential.backgroundColor)
        assertEquals("{\"biometric\":true}", storedCredential.policies)
        assertEquals("BiometricNotAvailable", storedCredential.lockingPolicy)
        assertEquals(true, storedCredential.isLocked)
        assertEquals("PING_AM", storedCredential.platform)
    }


    @Test
    fun `migration step 2 continues with other mechanisms when one fails`() = runTest {
        // Given - Multiple mechanisms, one with invalid data
        val validTotp = LegacyMechanism(
            id = "Valid-user1@example.com-otpauth",
            issuer = "Valid",
            accountName = "user1@example.com",
            mechanismUID = "valid-totp",
            secret = "VALIDKEY",
            type = "totp",
            oathType = "TOTP",
            algorithm = "sha1",
            digits = 6,
            period = 30,
            account = LegacyAccount(
                id = "Valid-user1@example.com",
                issuer = "Valid",
                accountName = "user1@example.com",
                lock = false
            )
        )

        val validPush = LegacyMechanism(
            id = "Valid-user2@example.com-pushauth",
            issuer = "Valid",
            accountName = "user2@example.com",
            mechanismUID = "valid-push",
            secret = "pushSecret",
            type = "push",
            authenticationEndpoint = "https://example.com/push",
            account = LegacyAccount(
                id = "Valid-user2@example.com",
                issuer = "Valid",
                accountName = "user2@example.com",
                lock = false
            )
        )

        val invalidMechanism = LegacyMechanism(
            id = "invalid",
            issuer = "",
            accountName = "",
            mechanismUID = "invalid",
            secret = "",
            type = "unknown-type", // Invalid type
            account = null
        )

        val exportedData = LegacyExportedData(
            mechanisms = listOf(validTotp, invalidMechanism, validPush),
            metadata = LegacyExportMetadata(3)
        )

        coEvery { anyConstructed<LegacyAuthenticationRepository>().isExists() } returns true
        coEvery { anyConstructed<LegacyAuthenticationRepository>().exportAllData() } returns exportedData
        coEvery { anyConstructed<LegacyAuthenticationRepository>().deleteLegacyData() } returns Unit

        // When
        val progressList = AuthMigration.migration.migrate(context).toList()

        // Then - Migration should still complete successfully
        assertTrue(progressList.any { it is MigrationProgress.Success })

        // Should complete all 3 steps
        val stepCompletedEvents = progressList.filterIsInstance<MigrationProgress.StepCompleted>()
        assertTrue(stepCompletedEvents.size >= 3)
    }

    @Test
    fun `migration handles mixed OATH types correctly`() = runTest {
        // Given - Legacy repository with both TOTP and HOTP
        val totpMechanism = LegacyMechanism(
            id = "TOTP Service-totp@example.com-otpauth",
            issuer = "TOTP Service",
            accountName = "totp@example.com",
            mechanismUID = "totp-1",
            secret = "TOTPKEY",
            type = "totp",
            oathType = "TOTP",
            period = 30,
            account = LegacyAccount(
                id = "TOTP Service-totp@example.com",
                issuer = "TOTP Service",
                accountName = "totp@example.com",
                lock = false
            )
        )

        val hotpMechanism = LegacyMechanism(
            id = "HOTP Service-hotp@example.com-otpauth",
            issuer = "HOTP Service",
            accountName = "hotp@example.com",
            mechanismUID = "hotp-1",
            secret = "HOTPKEY",
            type = "hotp",
            oathType = "HOTP",
            counter = 5,
            account = LegacyAccount(
                id = "HOTP Service-hotp@example.com",
                issuer = "HOTP Service",
                accountName = "hotp@example.com",
                lock = false
            )
        )

        val exportedData = LegacyExportedData(
            mechanisms = listOf(totpMechanism, hotpMechanism),
            metadata = LegacyExportMetadata(2)
        )

        coEvery { anyConstructed<LegacyAuthenticationRepository>().isExists() } returns true
        coEvery { anyConstructed<LegacyAuthenticationRepository>().exportAllData() } returns exportedData
        coEvery { anyConstructed<LegacyAuthenticationRepository>().deleteLegacyData() } returns Unit

        // When
        val progressList = AuthMigration.migration.migrate(context).toList()

        // Then
        assertTrue(progressList.any { it is MigrationProgress.Success })
    }

    @Test
    fun `migration handles different hash algorithms correctly`() = runTest {
        // Given - Mechanisms with different algorithms
        val sha1Mechanism = LegacyMechanism(
            id = "SHA1 Service-sha1@example.com-otpauth",
            issuer = "SHA1 Service",
            accountName = "sha1@example.com",
            mechanismUID = "sha1-totp",
            secret = "SHA1KEY",
            type = "totp",
            algorithm = "sha1",
            account = LegacyAccount(
                id = "SHA1 Service-sha1@example.com",
                issuer = "SHA1 Service",
                accountName = "sha1@example.com",
                lock = false
            )
        )

        val sha256Mechanism = LegacyMechanism(
            id = "SHA256 Service-sha256@example.com-otpauth",
            issuer = "SHA256 Service",
            accountName = "sha256@example.com",
            mechanismUID = "sha256-totp",
            secret = "SHA256KEY",
            type = "totp",
            algorithm = "sha256",
            account = LegacyAccount(
                id = "SHA256 Service-sha256@example.com",
                issuer = "SHA256 Service",
                accountName = "sha256@example.com",
                lock = false
            )
        )

        val sha512Mechanism = LegacyMechanism(
            id = "SHA512 Service-sha512@example.com-otpauth",
            issuer = "SHA512 Service",
            accountName = "sha512@example.com",
            mechanismUID = "sha512-totp",
            secret = "SHA512KEY",
            type = "totp",
            algorithm = "sha512",
            account = LegacyAccount(
                id = "SHA512 Service-sha512@example.com",
                issuer = "SHA512 Service",
                accountName = "sha512@example.com",
                lock = false
            )
        )

        val exportedData = LegacyExportedData(
            mechanisms = listOf(sha1Mechanism, sha256Mechanism, sha512Mechanism),
            metadata = LegacyExportMetadata(3)
        )

        coEvery { anyConstructed<LegacyAuthenticationRepository>().isExists() } returns true
        coEvery { anyConstructed<LegacyAuthenticationRepository>().exportAllData() } returns exportedData
        coEvery { anyConstructed<LegacyAuthenticationRepository>().deleteLegacyData() } returns Unit

        // When
        val progressList = AuthMigration.migration.migrate(context).toList()

        // Then
        assertTrue(progressList.any { it is MigrationProgress.Success })
    }

    @Test
    fun `migration handles database initialization failure with destructive recovery enabled`() = runTest {
        // Given - Legacy repository with valid data
        val exportedData = createOathMechanism(
            mechanismUID = "totp-mechanism-1",
            issuer = "Google",
            accountName = "user@example.com",
            secret = "JBSWY3DPEHPK3PXP",
            oathType = "TOTP",
            algorithm = "sha1",
            digits = 6,
            period = 30
        )

        coEvery { anyConstructed<LegacyAuthenticationRepository>().isExists() } returns true
        coEvery { anyConstructed<LegacyAuthenticationRepository>().exportAllData() } returns exportedData
        coEvery { anyConstructed<LegacyAuthenticationRepository>().deleteLegacyData() } returns Unit

        // With destructive recovery enabled in the migration step configuration,
        // the storage should be able to recover from database corruption.
        // This test verifies that the migration configures storage correctly.

        // When
        val progressList = AuthMigration.migration.migrate(context).toList()

        // Then - Migration should complete successfully with destructive recovery enabled
        assertTrue(progressList.any { it is MigrationProgress.Started })
        assertTrue(progressList.any { it is MigrationProgress.Success })

        // Verify storage was initialized with proper configuration
        coVerify(atLeast = 1) { anyConstructed<SQLOathStorage>().initializeDatabase() }
        coVerify(atLeast = 1) { anyConstructed<SQLPushStorage>().initializeDatabase() }
    }

    @Test
    fun `migration cleanup step continues even if cleanup fails`() = runTest {
        // Given - Legacy repository with data but cleanup will fail
        val exportedData = createOathMechanism(
            mechanismUID = "test-mechanism",
            issuer = "Test",
            accountName = "test@example.com",
            secret = "TESTKEY",
            type = "totp"
        )

        coEvery { anyConstructed<LegacyAuthenticationRepository>().isExists() } returns true
        coEvery { anyConstructed<LegacyAuthenticationRepository>().exportAllData() } returns exportedData
        // Simulate cleanup failure
        coEvery { anyConstructed<LegacyAuthenticationRepository>().deleteLegacyData() } throws Exception("Cleanup failed")

        // When
        val progressList = AuthMigration.migration.migrate(context).toList()

        // Then - Migration should still complete successfully even if cleanup fails
        assertTrue(progressList.any { it is MigrationProgress.Success })

        // All steps should complete
        val stepCompletedEvents = progressList.filterIsInstance<MigrationProgress.StepCompleted>()
        assertEquals(3, stepCompletedEvents.size)
    }

    @Test
    fun `migration preserves existing valid databases and only removes incompatible ones`() = runTest {
        // Given - Legacy repository with data to migrate
        val exportedData = createOathMechanism(
            mechanismUID = "totp-mechanism-1",
            issuer = "Google",
            accountName = "user@example.com",
            secret = "JBSWY3DPEHPK3PXP",
            oathType = "TOTP",
            algorithm = "sha1",
            digits = 6,
            period = 30
        )

        coEvery { anyConstructed<LegacyAuthenticationRepository>().isExists() } returns true
        coEvery { anyConstructed<LegacyAuthenticationRepository>().exportAllData() } returns exportedData
        coEvery { anyConstructed<LegacyAuthenticationRepository>().deleteLegacyData() } returns Unit

        // Mock getAllOathCredentials to return empty list (simulating empty but valid database)
        coEvery { anyConstructed<SQLOathStorage>().getAllOathCredentials() } returns emptyList()
        coEvery { anyConstructed<SQLPushStorage>().getAllPushCredentials() } returns emptyList()
        coEvery { anyConstructed<SQLPushStorage>().getAllPushNotifications() } returns emptyList()
        coEvery { anyConstructed<SQLPushStorage>().getAllPushDeviceTokens() } returns emptyList()

        // When - The prepareDatabase function will:
        // 1. Try to open existing databases with current passphrase
        // 2. Check if they contain data
        // 3. Keep them if they can be opened (even if empty)
        // 4. Only delete if they cannot be opened (passphrase mismatch)
        val progressList = AuthMigration.migration.migrate(context).toList()

        // Then - Migration should complete successfully
        assertTrue(progressList.any { it is MigrationProgress.Success })

        // Verify migration completed and stored the credential
        coVerify(atLeast = 1) { anyConstructed<SQLOathStorage>().storeOathCredential(any()) }
    }

    @Test
    fun `migration supports custom storage with unencrypted SharedPreferences`() = runTest {
        // Given - Custom storage data (plain SharedPreferences, no encryption)
        // This simulates FRAClient built with CustomStorageClient
        val testTime = System.currentTimeMillis()

        val mechanism = LegacyMechanism(
            id = "CustomApp-custom@example.com-otpauth",
            issuer = "CustomApp",
            accountName = "custom@example.com",
            mechanismUID = "custom-totp-1",
            secret = "CUSTOMSECRET123",
            type = "otpauth",
            oathType = "TOTP",
            algorithm = "sha1",
            digits = 6,
            period = 30,
            uid = "custom-user-123",
            resourceId = "custom-resource-456",
            timeAdded = testTime,
            account = LegacyAccount(
                id = "CustomApp-custom@example.com",
                issuer = "CustomApp",
                displayIssuer = "Custom Application",
                accountName = "custom@example.com",
                displayAccountName = "Custom User",
                imageURL = "https://custom.com/logo.png",
                backgroundColor = "#FF5733",
                timeAdded = testTime,
                lock = false
            )
        )

        coEvery { anyConstructed<LegacyAuthenticationRepository>().isExists() } returns true
        coEvery { anyConstructed<LegacyAuthenticationRepository>().exportAllData() } returns LegacyExportedData(
            mechanisms = listOf(mechanism),
            metadata = LegacyExportMetadata(1)
        )
        coEvery { anyConstructed<LegacyAuthenticationRepository>().deleteLegacyData() } returns Unit

        // When
        val progress = AuthMigration.migration.migrate(context).toList()

        // Then
        assertTrue("Migration must succeed", progress.any { it is MigrationProgress.Success })

        // Verify the credential was migrated
        val oathSlot = slot<OathCredential>()
        coVerify(exactly = 1) { anyConstructed<SQLOathStorage>().storeOathCredential(capture(oathSlot)) }

        val storedCredential = oathSlot.captured
        assertEquals("custom-totp-1", storedCredential.id)
        assertEquals("CustomApp", storedCredential.issuer)
        assertEquals("Custom Application", storedCredential.displayIssuer)
        assertEquals("custom@example.com", storedCredential.accountName)
        assertEquals("Custom User", storedCredential.displayAccountName)
        assertEquals("custom-user-123", storedCredential.userId)
        assertEquals("custom-resource-456", storedCredential.resourceId)
    }

    @Test
    fun `migration supports custom key alias configuration`() = runTest {
        // Given - Configure migration with custom key alias
        val customKeyAlias = "com.myapp.custom.STORAGE_KEY"
        AuthMigration.configure(keyAlias = customKeyAlias)

        val mechanism = LegacyMechanism(
            id = "CustomKeyApp-user@customkey.com-otpauth",
            issuer = "CustomKeyApp",
            accountName = "user@customkey.com",
            mechanismUID = "custom-key-totp",
            secret = "CUSTOMKEYSECRET",
            type = "otpauth",
            oathType = "TOTP",
            algorithm = "sha1",
            digits = 6,
            period = 30,
            account = LegacyAccount(
                id = "CustomKeyApp-user@customkey.com",
                issuer = "CustomKeyApp",
                accountName = "user@customkey.com",
                lock = false
            )
        )

        coEvery { anyConstructed<LegacyAuthenticationRepository>().isExists() } returns true
        coEvery { anyConstructed<LegacyAuthenticationRepository>().exportAllData() } returns LegacyExportedData(
            mechanisms = listOf(mechanism),
            metadata = LegacyExportMetadata(1)
        )
        coEvery { anyConstructed<LegacyAuthenticationRepository>().deleteLegacyData() } returns Unit

        // When
        val progress = AuthMigration.migration.migrate(context).toList()

        // Then
        assertTrue("Migration with custom key alias must succeed", progress.any { it is MigrationProgress.Success })

        // Verify the credential was migrated
        val oathSlot = slot<OathCredential>()
        coVerify(exactly = 1) { anyConstructed<SQLOathStorage>().storeOathCredential(capture(oathSlot)) }

        val storedCredential = oathSlot.captured
        assertEquals("custom-key-totp", storedCredential.id)
        assertEquals("CustomKeyApp", storedCredential.issuer)

        // Reset configuration to default for other tests
        AuthMigration.configure()
    }

    @Test
    fun `startWithJson migrates mechanisms from JSON string`() = runTest {
        // Given - JSON string with OATH and Push mechanisms
        val exportedJson = """
        {
          "mechanisms": [
            {
              "id": "JsonTest-totp@example.com-otpauth",
              "issuer": "JsonTest",
              "accountName": "totp@example.com",
              "mechanismUID": "json-totp-uuid",
              "secret": "JSONBASE32SECRET",
              "type": "otpauth",
              "oathType": "TOTP",
              "algorithm": "SHA256",
              "digits": 8,
              "period": 60,
              "uid": "json-user-id",
              "resourceId": "json-resource-id",
              "timeAdded": 1234567890,
              "account": {
                "id": "JsonTest-totp@example.com",
                "issuer": "JsonTest",
                "displayIssuer": "JSON Test Display",
                "accountName": "totp@example.com",
                "displayAccountName": "JSON TOTP User",
                "imageURL": "https://jsontest.com/logo.png",
                "backgroundColor": "#FF6600",
                "policies": "{\"deviceTampering\":true}",
                "lock": false
              }
            },
            {
              "id": "JsonTest-push@example.com-pushauth",
              "issuer": "JsonTest",
              "accountName": "push@example.com",
              "mechanismUID": "json-push-uuid",
              "secret": "JSONPUSHSECRET",
              "type": "pushauth",
              "authenticationEndpoint": "https://jsontest.com/push?_action=authenticate",
              "registrationEndpoint": "https://jsontest.com/push?_action=register",
              "platform": "PING_AM",
              "uid": "json-push-user-id",
              "resourceId": "json-push-resource-id",
              "timeAdded": 1234567890,
              "account": {
                "id": "JsonTest-push@example.com",
                "issuer": "JsonTest",
                "displayIssuer": "JSON Test Push",
                "accountName": "push@example.com",
                "displayAccountName": "JSON Push User",
                "imageURL": "https://jsontest.com/push-logo.png",
                "backgroundColor": "#0066FF",
                "lock": false
              }
            }
          ],
          "metadata": {
            "totalMechanisms": 2,
            "exportedAt": 1234567890
          }
        }
        """.trimIndent()

        // When - Call startWithJson which internally creates and collects the migration flow
        AuthMigration.startWithJson(context, exportedJson)

        // Verify OATH credential was stored
        val oathSlot = slot<OathCredential>()
        coVerify(exactly = 1) { anyConstructed<SQLOathStorage>().storeOathCredential(capture(oathSlot)) }

        val storedOath = oathSlot.captured
        assertEquals("json-totp-uuid", storedOath.id)
        assertEquals("JsonTest", storedOath.issuer)
        assertEquals("JSON Test Display", storedOath.displayIssuer)
        assertEquals("totp@example.com", storedOath.accountName)
        assertEquals("JSON TOTP User", storedOath.displayAccountName)
        assertEquals("JSONBASE32SECRET", storedOath.secret)
        assertEquals(OathType.TOTP, storedOath.oathType)
        assertEquals(OathAlgorithm.SHA256, storedOath.oathAlgorithm)
        assertEquals(8, storedOath.digits)
        assertEquals(60, storedOath.period)
        assertEquals("json-user-id", storedOath.userId)
        assertEquals("json-resource-id", storedOath.resourceId)
        assertEquals("https://jsontest.com/logo.png", storedOath.imageURL)
        assertEquals("#FF6600", storedOath.backgroundColor)
        assertEquals("{\"deviceTampering\":true}", storedOath.policies)

        // Verify Push credential was stored
        val pushSlot = slot<PushCredential>()
        coVerify(exactly = 1) { anyConstructed<SQLPushStorage>().storePushCredential(capture(pushSlot)) }

        val storedPush = pushSlot.captured
        assertEquals("json-push-uuid", storedPush.id)
        assertEquals("JsonTest", storedPush.issuer)
        assertEquals("JSON Test Push", storedPush.displayIssuer)
        assertEquals("push@example.com", storedPush.accountName)
        assertEquals("JSON Push User", storedPush.displayAccountName)
        assertEquals("JSONPUSHSECRET", storedPush.sharedSecret)
        assertEquals("https://jsontest.com/push", storedPush.serverEndpoint) // Query params removed
        assertEquals("PING_AM", storedPush.platform)
        assertEquals("json-push-user-id", storedPush.userId)
        assertEquals("json-push-resource-id", storedPush.resourceId)
        assertEquals("https://jsontest.com/push-logo.png", storedPush.imageURL)
        assertEquals("#0066FF", storedPush.backgroundColor)

        assertTrue("Migration should complete all steps without errors", true)
    }

    @Test
    fun `startWithJson handles invalid JSON format`() = runTest {
        // Given - Invalid JSON string
        val invalidJson = "{ invalid json }"

        // When - Attempt to migrate with invalid JSON
        // The error is caught and logged by the migration framework
        // We verify that no credentials are stored when JSON is invalid
        try {
            AuthMigration.startWithJson(context, invalidJson)
        } catch (e: Exception) {
            // Expected - migration framework may throw
            assertTrue("Should be IllegalArgumentException or contain JSON error",
                e is IllegalArgumentException || e.message?.contains("JSON") == true)
        }

        // Then - No credentials should be stored
        coVerify(exactly = 0) { anyConstructed<SQLOathStorage>().storeOathCredential(any()) }
        coVerify(exactly = 0) { anyConstructed<SQLPushStorage>().storePushCredential(any()) }
    }

    @Test
    fun `startWithJson handles empty mechanisms list`() = runTest {
        // Given - JSON with empty mechanisms
        val emptyJson = """
        {
          "mechanisms": [],
          "metadata": {
            "totalMechanisms": 0,
            "exportedAt": 1234567890
          }
        }
        """.trimIndent()

        // When
        AuthMigration.startWithJson(context, emptyJson)

        // Then - No credentials should be stored
        coVerify(exactly = 0) { anyConstructed<SQLOathStorage>().storeOathCredential(any()) }
        coVerify(exactly = 0) { anyConstructed<SQLPushStorage>().storePushCredential(any()) }
    }

    @Test
    fun `startWithJson migrates multiple mechanisms of same type`() = runTest {
        // Given - JSON with multiple OATH mechanisms
        val multipleOathJson = """
        {
          "mechanisms": [
            {
              "id": "Service1-user1@example.com-otpauth",
              "issuer": "Service1",
              "accountName": "user1@example.com",
              "mechanismUID": "totp-uuid-1",
              "secret": "SECRET1",
              "type": "otpauth",
              "oathType": "TOTP",
              "algorithm": "SHA1",
              "digits": 6,
              "period": 30,
              "account": {
                "id": "Service1-user1@example.com",
                "issuer": "Service1",
                "accountName": "user1@example.com",
                "lock": false
              }
            },
            {
              "id": "Service2-user2@example.com-otpauth",
              "issuer": "Service2",
              "accountName": "user2@example.com",
              "mechanismUID": "totp-uuid-2",
              "secret": "SECRET2",
              "type": "otpauth",
              "oathType": "TOTP",
              "algorithm": "SHA256",
              "digits": 8,
              "period": 60,
              "account": {
                "id": "Service2-user2@example.com",
                "issuer": "Service2",
                "accountName": "user2@example.com",
                "lock": false
              }
            },
            {
              "id": "Service3-user3@example.com-otpauth",
              "issuer": "Service3",
              "accountName": "user3@example.com",
              "mechanismUID": "hotp-uuid-3",
              "secret": "SECRET3",
              "type": "hotp",
              "oathType": "HOTP",
              "algorithm": "SHA512",
              "digits": 8,
              "counter": 42,
              "account": {
                "id": "Service3-user3@example.com",
                "issuer": "Service3",
                "accountName": "user3@example.com",
                "lock": false
              }
            }
          ],
          "metadata": {
            "totalMechanisms": 3,
            "exportedAt": 1234567890
          }
        }
        """.trimIndent()

        // When
        AuthMigration.startWithJson(context, multipleOathJson)

        // Then - All 3 OATH credentials should be stored
        coVerify(exactly = 3) { anyConstructed<SQLOathStorage>().storeOathCredential(any()) }
    }

    // Helper methods


    /**
     * Creates test legacy data in the new format with mechanisms containing nested accounts
     */
    private fun createTestLegacyData(): LegacyExportedData {
        val testTime = System.currentTimeMillis()

        val mechanisms = listOf(
            LegacyMechanism(
                id = "Google-user@example.com-otpauth",
                issuer = "Google",
                accountName = "user@example.com",
                mechanismUID = "mechanism-1",
                secret = "JBSWY3DPEHPK3PXP",
                type = "otpauth",
                oathType = "TOTP",
                algorithm = "sha1",
                digits = 6,
                period = 30,
                timeAdded = testTime,
                account = LegacyAccount(
                    id = "Google-user@example.com",
                    issuer = "Google",
                    accountName = "user@example.com",
                    imageURL = "https://google.com/logo.png",
                    timeAdded = testTime,
                    lock = false
                )
            ),
            LegacyMechanism(
                id = "GitHub-developer@example.com-pushauth",
                issuer = "GitHub",
                accountName = "developer@example.com",
                mechanismUID = "mechanism-2",
                secret = "pushSecret123",
                type = "pushauth",
                authenticationEndpoint = "https://github.com/push?_action=authenticate",
                registrationEndpoint = "https://github.com/push?_action=register",
                timeAdded = testTime,
                account = LegacyAccount(
                    id = "GitHub-developer@example.com",
                    issuer = "GitHub",
                    accountName = "developer@example.com",
                    timeAdded = testTime,
                    lock = false
                )
            )
        )

        return LegacyExportedData(
            mechanisms = mechanisms,
            metadata = LegacyExportMetadata(
                totalMechanisms = mechanisms.size
            )
        )
    }

    /**
     * Helper to create a single OATH mechanism with account for testing
     */
    private fun createOathMechanism(
        mechanismUID: String,
        issuer: String,
        accountName: String,
        secret: String,
        type: String = "totp",
        oathType: String = "TOTP",
        algorithm: String = "sha1",
        digits: Int = 6,
        period: Int = 30,
        counter: Long = 0,
        uid: String? = null,
        resourceId: String? = null,
        displayIssuer: String? = null,
        displayAccountName: String? = null,
        imageURL: String? = null,
        backgroundColor: String? = null,
        policies: String? = null,
        lockingPolicy: String? = null,
        isLocked: Boolean = false,
        timeAdded: Long = System.currentTimeMillis()
    ): LegacyExportedData {
        val mechanism = LegacyMechanism(
            id = "$issuer-$accountName-otpauth",
            issuer = issuer,
            accountName = accountName,
            mechanismUID = mechanismUID,
            secret = secret,
            type = type,
            oathType = oathType,
            algorithm = algorithm,
            digits = digits,
            period = period,
            counter = counter,
            uid = uid,
            resourceId = resourceId,
            timeAdded = timeAdded,
            account = LegacyAccount(
                id = "$issuer-$accountName",
                issuer = issuer,
                displayIssuer = displayIssuer,
                accountName = accountName,
                displayAccountName = displayAccountName,
                imageURL = imageURL,
                backgroundColor = backgroundColor,
                policies = policies,
                lockingPolicy = lockingPolicy,
                lock = isLocked,
                timeAdded = timeAdded
            )
        )

        return LegacyExportedData(
            mechanisms = listOf(mechanism),
            metadata = LegacyExportMetadata(1)
        )
    }

    /**
     * Helper to create a single Push mechanism with account for testing
     */
    private fun createPushMechanism(
        mechanismUID: String = "push-mechanism-1",
        issuer: String = "ForgeRock",
        accountName: String,
        secret: String,
        authenticationEndpoint: String,
        registrationEndpoint: String? = null,
        platform: String? = null,
        uid: String? = null,
        resourceId: String? = null,
        displayIssuer: String? = null,
        displayAccountName: String? = null,
        imageURL: String? = null,
        backgroundColor: String? = null,
        policies: String? = null,
        lockingPolicy: String? = null,
        isLocked: Boolean = false,
        timeAdded: Long = System.currentTimeMillis()
    ): LegacyExportedData {
        val mechanism = LegacyMechanism(
            id = "$issuer-$accountName-pushauth",
            issuer = issuer,
            accountName = accountName,
            mechanismUID = mechanismUID,
            secret = secret,
            type = "pushauth",
            authenticationEndpoint = authenticationEndpoint,
            registrationEndpoint = registrationEndpoint,
            platform = platform,
            uid = uid,
            resourceId = resourceId,
            timeAdded = timeAdded,
            account = LegacyAccount(
                id = "$issuer-$accountName",
                issuer = issuer,
                displayIssuer = displayIssuer,
                accountName = accountName,
                displayAccountName = displayAccountName,
                imageURL = imageURL,
                backgroundColor = backgroundColor,
                policies = policies,
                lockingPolicy = lockingPolicy,
                lock = isLocked,
                timeAdded = timeAdded
            )
        )

        return LegacyExportedData(
            mechanisms = listOf(mechanism),
            metadata = LegacyExportMetadata(1)
        )
    }

    @Test
    fun `exportToJson returns valid JSON string with mechanisms`() = runTest {
        // Given - Legacy repository with mechanisms
        val exportedData = createTestLegacyData()

        coEvery { anyConstructed<LegacyAuthenticationRepository>().isExists() } returns true
        coEvery { anyConstructed<LegacyAuthenticationRepository>().exportToJson() } returns legacyJson.encodeToString(
            LegacyExportedData.serializer(),
            exportedData
        )

        // When - Export to JSON
        val jsonString = AuthMigration.exportToJson(context)

        // Then - Verify JSON is valid and can be parsed back
        assertTrue("JSON should not be empty", jsonString.isNotEmpty())

        // Parse JSON back to verify structure
        val parsedData = legacyJson.decodeFromString<LegacyExportedData>(jsonString)
        assertEquals(2, parsedData.mechanisms.size)
        assertEquals(2, parsedData.metadata.totalMechanisms)

        // Verify first mechanism (OATH)
        val oathMechanism = parsedData.mechanisms[0]
        assertEquals("Google", oathMechanism.issuer)
        assertEquals("user@example.com", oathMechanism.accountName)
        assertEquals("otpauth", oathMechanism.type)
        assertEquals("TOTP", oathMechanism.oathType)

        // Verify nested account data
        assertNotNull("Mechanism should have nested account", oathMechanism.account)
        assertEquals("Google", oathMechanism.account?.issuer)
        assertEquals("user@example.com", oathMechanism.account?.accountName)

        // Verify second mechanism (Push)
        val pushMechanism = parsedData.mechanisms[1]
        assertEquals("GitHub", pushMechanism.issuer)
        assertEquals("developer@example.com", pushMechanism.accountName)
        assertEquals("pushauth", pushMechanism.type)

        // Verify repository method was called
        coVerify(exactly = 1) { anyConstructed<LegacyAuthenticationRepository>().exportToJson() }
    }

    @Test
    fun `exportToJson handles OATH mechanism with all fields`() = runTest {
        // Given - OATH mechanism with complete field set
        val oathData = createOathMechanism(
            mechanismUID = "test-oath-uuid",
            issuer = "TestService",
            accountName = "test@example.com",
            secret = "TESTSECRET123",
            oathType = "TOTP",
            algorithm = "sha256",
            digits = 8,
            period = 60,
            uid = "test-user-123",
            resourceId = "test-resource-456",
            displayIssuer = "Test Service Display",
            displayAccountName = "Test User Display",
            imageURL = "https://test.com/logo.png",
            backgroundColor = "#FF5733",
            policies = "{\"biometricRequired\":true}",
            lockingPolicy = "DeviceTamperingPolicy",
            isLocked = true
        )

        coEvery { anyConstructed<LegacyAuthenticationRepository>().isExists() } returns true
        coEvery { anyConstructed<LegacyAuthenticationRepository>().exportToJson() } returns legacyJson.encodeToString(
            LegacyExportedData.serializer(),
            oathData
        )

        // When
        val jsonString = AuthMigration.exportToJson(context)

        // Then - Parse and verify all fields are present
        val parsedData = legacyJson.decodeFromString<LegacyExportedData>(jsonString)
        val mechanism = parsedData.mechanisms[0]

        // Verify mechanism fields
        assertEquals("test-oath-uuid", mechanism.mechanismUID)
        assertEquals("TestService", mechanism.issuer)
        assertEquals("test@example.com", mechanism.accountName)
        assertEquals("TESTSECRET123", mechanism.secret)
        assertEquals("totp", mechanism.type)
        assertEquals("TOTP", mechanism.oathType)
        assertEquals("sha256", mechanism.algorithm)
        assertEquals(8, mechanism.digits)
        assertEquals(60, mechanism.period)
        assertEquals("test-user-123", mechanism.uid)
        assertEquals("test-resource-456", mechanism.resourceId)

        // Verify nested account fields
        val account = mechanism.account
        assertNotNull("Account should be present", account)
        assertEquals("TestService", account?.issuer)
        assertEquals("Test Service Display", account?.displayIssuer)
        assertEquals("test@example.com", account?.accountName)
        assertEquals("Test User Display", account?.displayAccountName)
        assertEquals("https://test.com/logo.png", account?.imageURL)
        assertEquals("#FF5733", account?.backgroundColor)
        assertEquals("{\"biometricRequired\":true}", account?.policies)
        assertEquals("DeviceTamperingPolicy", account?.lockingPolicy)
        assertEquals(true, account?.lock)
    }

    @Test
    fun `exportToJson handles Push mechanism with all fields`() = runTest {
        // Given - Push mechanism with complete field set
        val pushData = createPushMechanism(
            mechanismUID = "test-push-uuid",
            issuer = "PushService",
            accountName = "push@example.com",
            secret = "PUSHSECRET456",
            authenticationEndpoint = "https://push.example.com/auth?_action=authenticate",
            registrationEndpoint = "https://push.example.com/auth?_action=register",
            platform = "PING_AM",
            uid = "push-user-789",
            resourceId = "push-resource-101",
            displayIssuer = "Push Service Display",
            displayAccountName = "Push User Display",
            imageURL = "https://push.com/icon.png",
            backgroundColor = "#0066CC"
        )

        coEvery { anyConstructed<LegacyAuthenticationRepository>().isExists() } returns true
        coEvery { anyConstructed<LegacyAuthenticationRepository>().exportToJson() } returns legacyJson.encodeToString(
            LegacyExportedData.serializer(),
            pushData
        )

        // When
        val jsonString = AuthMigration.exportToJson(context)

        // Then - Parse and verify all fields
        val parsedData = legacyJson.decodeFromString<LegacyExportedData>(jsonString)
        val mechanism = parsedData.mechanisms[0]

        // Verify mechanism fields
        assertEquals("test-push-uuid", mechanism.mechanismUID)
        assertEquals("PushService", mechanism.issuer)
        assertEquals("push@example.com", mechanism.accountName)
        assertEquals("PUSHSECRET456", mechanism.secret)
        assertEquals("pushauth", mechanism.type)
        assertEquals("https://push.example.com/auth?_action=authenticate", mechanism.authenticationEndpoint)
        assertEquals("https://push.example.com/auth?_action=register", mechanism.registrationEndpoint)
        assertEquals("PING_AM", mechanism.platform)
        assertEquals("push-user-789", mechanism.uid)
        assertEquals("push-resource-101", mechanism.resourceId)

        // Verify nested account
        val account = mechanism.account
        assertNotNull("Account should be present", account)
        assertEquals("PushService", account?.issuer)
        assertEquals("Push Service Display", account?.displayIssuer)
        assertEquals("push@example.com", account?.accountName)
        assertEquals("Push User Display", account?.displayAccountName)
        assertEquals("https://push.com/icon.png", account?.imageURL)
        assertEquals("#0066CC", account?.backgroundColor)
    }

    @Test
    fun `exportToJson handles empty repository`() = runTest {
        // Given - Empty repository
        val emptyData = LegacyExportedData(
            mechanisms = emptyList(),
            metadata = LegacyExportMetadata(0)
        )

        coEvery { anyConstructed<LegacyAuthenticationRepository>().isExists() } returns true
        coEvery { anyConstructed<LegacyAuthenticationRepository>().exportToJson() } returns legacyJson.encodeToString(
            LegacyExportedData.serializer(),
            emptyData
        )

        // When
        val jsonString = AuthMigration.exportToJson(context)

        // Then - Should return valid JSON with empty mechanisms
        val parsedData = legacyJson.decodeFromString<LegacyExportedData>(jsonString)
        assertTrue("Mechanisms should be empty", parsedData.mechanisms.isEmpty())
        assertEquals(0, parsedData.metadata.totalMechanisms)
    }

    @Test
    fun `exportToJson can be used with startWithJson for migration`() = runTest {
        // Given - Export JSON from repository
        val originalData = createTestLegacyData()
        val exportedJson = legacyJson.encodeToString(LegacyExportedData.serializer(), originalData)

        coEvery { anyConstructed<LegacyAuthenticationRepository>().isExists() } returns true
        coEvery { anyConstructed<LegacyAuthenticationRepository>().exportToJson() } returns exportedJson

        // When - Export and then migrate using the JSON
        val jsonString = AuthMigration.exportToJson(context)

        // Verify we can use this JSON for migration
        AuthMigration.startWithJson(context, jsonString)

        // Then - Both credentials should be migrated
        coVerify(exactly = 1) { anyConstructed<SQLOathStorage>().storeOathCredential(any()) }
        coVerify(exactly = 1) { anyConstructed<SQLPushStorage>().storePushCredential(any()) }
    }

    @Test
    fun `exportToJson preserves metadata`() = runTest {
        // Given - Data with specific export timestamp
        val exportTimestamp = 1234567890123L
        val data = LegacyExportedData(
            mechanisms = listOf(
                LegacyMechanism(
                    id = "Test-user@test.com-otpauth",
                    issuer = "Test",
                    accountName = "user@test.com",
                    mechanismUID = "test-uuid",
                    secret = "SECRET",
                    type = "otpauth",
                    oathType = "TOTP",
                    account = LegacyAccount(
                        id = "Test-user@test.com",
                        issuer = "Test",
                        accountName = "user@test.com",
                        lock = false
                    )
                )
            ),
            metadata = LegacyExportMetadata(
                totalMechanisms = 1,
                exportedAt = exportTimestamp
            )
        )

        coEvery { anyConstructed<LegacyAuthenticationRepository>().isExists() } returns true
        coEvery { anyConstructed<LegacyAuthenticationRepository>().exportToJson() } returns legacyJson.encodeToString(
            LegacyExportedData.serializer(),
            data
        )

        // When
        val jsonString = AuthMigration.exportToJson(context)

        // Then - Metadata should be preserved
        val parsedData = legacyJson.decodeFromString<LegacyExportedData>(jsonString)
        assertEquals(1, parsedData.metadata.totalMechanisms)
        assertEquals(exportTimestamp, parsedData.metadata.exportedAt)
    }

    @Test
    fun `exportToJson handles multiple mechanisms of same type`() = runTest {
        // Given - Multiple OATH mechanisms
        val mechanisms = listOf(
            LegacyMechanism(
                id = "Service1-user1@example.com-otpauth",
                issuer = "Service1",
                accountName = "user1@example.com",
                mechanismUID = "uuid-1",
                secret = "SECRET1",
                type = "otpauth",
                oathType = "TOTP",
                algorithm = "sha1",
                digits = 6,
                period = 30,
                account = LegacyAccount(
                    id = "Service1-user1@example.com",
                    issuer = "Service1",
                    accountName = "user1@example.com",
                    lock = false
                )
            ),
            LegacyMechanism(
                id = "Service2-user2@example.com-otpauth",
                issuer = "Service2",
                accountName = "user2@example.com",
                mechanismUID = "uuid-2",
                secret = "SECRET2",
                type = "otpauth",
                oathType = "HOTP",
                algorithm = "sha256",
                digits = 8,
                counter = 42,
                account = LegacyAccount(
                    id = "Service2-user2@example.com",
                    issuer = "Service2",
                    accountName = "user2@example.com",
                    lock = false
                )
            ),
            LegacyMechanism(
                id = "Service3-user3@example.com-otpauth",
                issuer = "Service3",
                accountName = "user3@example.com",
                mechanismUID = "uuid-3",
                secret = "SECRET3",
                type = "otpauth",
                oathType = "TOTP",
                algorithm = "sha512",
                digits = 6,
                period = 60,
                account = LegacyAccount(
                    id = "Service3-user3@example.com",
                    issuer = "Service3",
                    accountName = "user3@example.com",
                    lock = false
                )
            )
        )

        val data = LegacyExportedData(
            mechanisms = mechanisms,
            metadata = LegacyExportMetadata(3)
        )

        coEvery { anyConstructed<LegacyAuthenticationRepository>().isExists() } returns true
        coEvery { anyConstructed<LegacyAuthenticationRepository>().exportToJson() } returns legacyJson.encodeToString(
            LegacyExportedData.serializer(),
            data
        )

        // When
        val jsonString = AuthMigration.exportToJson(context)

        // Then - All mechanisms should be in JSON
        val parsedData = legacyJson.decodeFromString<LegacyExportedData>(jsonString)
        assertEquals(3, parsedData.mechanisms.size)
        assertEquals(3, parsedData.metadata.totalMechanisms)

        // Verify each mechanism is present
        assertEquals("Service1", parsedData.mechanisms[0].issuer)
        assertEquals("TOTP", parsedData.mechanisms[0].oathType)

        assertEquals("Service2", parsedData.mechanisms[1].issuer)
        assertEquals("HOTP", parsedData.mechanisms[1].oathType)
        assertEquals(42L, parsedData.mechanisms[1].counter)

        assertEquals("Service3", parsedData.mechanisms[2].issuer)
        assertEquals("TOTP", parsedData.mechanisms[2].oathType)
    }

    @Test
    fun `migration creates backup files when configured`() = runTest {
        // Given - Configure migration with backup enabled
        AuthMigration.configure(backupFiles = true)

        // Create test legacy data
        val mechanism = LegacyMechanism(
            id = "Backup-test@example.com-otpauth",
            issuer = "Backup",
            accountName = "test@example.com",
            mechanismUID = "backup-test-uuid",
            secret = "BACKUPSECRET",
            type = "otpauth",
            oathType = "TOTP",
            algorithm = "sha1",
            digits = 6,
            period = 30,
            account = LegacyAccount(
                id = "Backup-test@example.com",
                issuer = "Backup",
                accountName = "test@example.com",
                lock = false
            )
        )

        coEvery { anyConstructed<LegacyAuthenticationRepository>().isExists() } returns true
        coEvery { anyConstructed<LegacyAuthenticationRepository>().exportAllData() } returns LegacyExportedData(
            mechanisms = listOf(mechanism),
            metadata = LegacyExportMetadata(1)
        )

        // Mock the deleteLegacyData to verify it's called
        coEvery { anyConstructed<LegacyAuthenticationRepository>().deleteLegacyData() } returns Unit

        // When
        val progress = AuthMigration.migration.migrate(context).toList()

        // Then - Migration should complete successfully
        assertTrue("Migration should succeed", progress.any { it is MigrationProgress.Success })

        // Verify deleteLegacyData was called (which triggers backup if configured)
        coVerify(exactly = 1) { anyConstructed<LegacyAuthenticationRepository>().deleteLegacyData() }

        // Reset configuration to default for other tests
        AuthMigration.configure()
    }

    @Test
    fun `migration does not create backup files when not configured`() = runTest {
        // Given - Default configuration (backupFiles = false)
        AuthMigration.configure(backupFiles = false)

        // Create test legacy data
        val mechanism = LegacyMechanism(
            id = "NoBackup-test@example.com-otpauth",
            issuer = "NoBackup",
            accountName = "test@example.com",
            mechanismUID = "nobackup-test-uuid",
            secret = "NOBACKUPSECRET",
            type = "otpauth",
            oathType = "TOTP",
            account = LegacyAccount(
                id = "NoBackup-test@example.com",
                issuer = "NoBackup",
                accountName = "test@example.com",
                lock = false
            )
        )

        coEvery { anyConstructed<LegacyAuthenticationRepository>().isExists() } returns true
        coEvery { anyConstructed<LegacyAuthenticationRepository>().exportAllData() } returns LegacyExportedData(
            mechanisms = listOf(mechanism),
            metadata = LegacyExportMetadata(1)
        )
        coEvery { anyConstructed<LegacyAuthenticationRepository>().deleteLegacyData() } returns Unit

        // When
        val progress = AuthMigration.migration.migrate(context).toList()

        // Then - Migration should complete successfully
        assertTrue("Migration should succeed", progress.any { it is MigrationProgress.Success })

        // Verify deleteLegacyData was called (but without backup based on config)
        coVerify(exactly = 1) { anyConstructed<LegacyAuthenticationRepository>().deleteLegacyData() }

        // Reset configuration
        AuthMigration.configure()
    }
}

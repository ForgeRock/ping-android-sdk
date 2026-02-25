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
import com.pingidentity.mfa.push.PushDeviceToken
import com.pingidentity.mfa.push.PushNotification
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
        coEvery { anyConstructed<LegacyAuthenticationRepository>().exportAllData() } returns ExportedData(
            accounts = emptyMap(),
            mechanisms = emptyMap(),
            notifications = emptyMap(),
            deviceToken = emptyMap(),
            metadata = ExportMetadata(0, 0, 0, false)
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
        val (accounts, mechanisms, notifications, deviceTokens) = createTestLegacyData()

        coEvery { anyConstructed<LegacyAuthenticationRepository>().isExists() } returns true
        coEvery { anyConstructed<LegacyAuthenticationRepository>().exportAllData() } returns ExportedData(
            accounts = accounts,
            mechanisms = mechanisms,
            notifications = notifications,
            deviceToken = deviceTokens,
            metadata = ExportMetadata(
                totalAccounts = accounts.size,
                totalMechanisms = mechanisms.size,
                totalNotifications = notifications.size,
                hasDeviceToken = deviceTokens.isNotEmpty()
            )
        )
        coEvery { anyConstructed<LegacyAuthenticationRepository>().deleteLegacyData() } returns Unit

        // When
        val progressList = AuthMigration.migration.migrate(context).toList()

        // Then
        assertTrue(progressList.any { it is MigrationProgress.Started })
        assertTrue(progressList.any { it is MigrationProgress.Success })

        // Should have all five steps completed
        val stepCompletedEvents = progressList.filterIsInstance<MigrationProgress.StepCompleted>()
        assertTrue(stepCompletedEvents.size >= 5)

        assertEquals("Export legacy authenticator data", stepCompletedEvents[0].step.description)
        assertEquals("Migrate mechanisms to OATH and Push storage", stepCompletedEvents[1].step.description)
        assertEquals("Migrate push notifications to Push storage", stepCompletedEvents[2].step.description)
        assertEquals("Migrate device token to Push storage", stepCompletedEvents[3].step.description)
        assertEquals("Cleanup legacy authenticator data", stepCompletedEvents[4].step.description)
    }

    @Test
    fun `migration migrates OATH credentials correctly`() = runTest {
        // Given - Legacy repository with TOTP mechanism
        val mechanisms = mapOf(
            "totp-mechanism-1" to """
                {
                    "type": "totp",
                    "mechanismUID": "totp-mechanism-1",
                    "issuer": "Google",
                    "accountName": "user@example.com",
                    "secret": "JBSWY3DPEHPK3PXP",
                    "algorithm": "sha1",
                    "digits": 6,
                    "period": 30,
                    "oathType": "TOTP"
                }
            """.trimIndent()
        )
        val accounts = mapOf(
            "totp-mechanism-1" to """
                {
                    "id": "totp-mechanism-1",
                    "issuer": "Google",
                    "accountName": "user@example.com",
                    "imageURL": "https://example.com/logo.png",
                    "backgroundColor": "#4285F4"
                }
            """.trimIndent()
        )

        coEvery { anyConstructed<LegacyAuthenticationRepository>().isExists() } returns true
        coEvery { anyConstructed<LegacyAuthenticationRepository>().exportAllData() } returns ExportedData(
            accounts = accounts,
            mechanisms = mechanisms,
            notifications = emptyMap(),
            deviceToken = emptyMap(),
            metadata = ExportMetadata(
                totalAccounts = accounts.size,
                totalMechanisms = mechanisms.size,
                totalNotifications = 0,
                hasDeviceToken = false
            )
        )
        coEvery { anyConstructed<LegacyAuthenticationRepository>().deleteLegacyData() } returns Unit

        // When
        val progressList = AuthMigration.migration.migrate(context).toList()

        // Then
        assertTrue(progressList.any { it is MigrationProgress.Success })

        // Capture the stored credential
        val oathSlot = slot<OathCredential>()
        // Verify the storage function was called with the expected credential
        coVerify(exactly = 1) { anyConstructed<SQLOathStorage>().storeOathCredential(capture(oathSlot)) }

        // Verify the captured credential has the expected values
        val storedCredential = oathSlot.captured
        assertEquals("totp-mechanism-1", storedCredential.id)
        assertEquals("Google", storedCredential.issuer)
        assertEquals("user@example.com", storedCredential.accountName)
        assertEquals("JBSWY3DPEHPK3PXP", storedCredential.secret)
        assertEquals(OathType.TOTP, storedCredential.oathType)
        assertEquals(OathAlgorithm.SHA1, storedCredential.oathAlgorithm)
        assertEquals(6, storedCredential.digits)
        assertEquals(30, storedCredential.period)
        assertEquals("https://example.com/logo.png", storedCredential.imageURL)
        assertEquals("#4285F4", storedCredential.backgroundColor)
    }

    @Test
    fun `migration migrates Push credentials correctly`() = runTest {
        // Given - Legacy repository with Push mechanism
        val mechanisms = mapOf(
            "push-mechanism-1" to """
                {
                    "type": "push",
                    "mechanismUID": "push-mechanism-1",
                    "issuer": "ForgeRock",
                    "accountName": "admin@company.com",
                    "secret": "sharedSecret123",
                    "authenticationEndpoint": "https://am.example.com/push"
                }
            """.trimIndent()
        )
        val accounts = mapOf(
            "push-mechanism-1" to """
                {
                    "id": "push-mechanism-1",
                    "issuer": "ForgeRock",
                    "accountName": "admin@company.com"
                }
            """.trimIndent()
        )

        coEvery { anyConstructed<LegacyAuthenticationRepository>().isExists() } returns true
        coEvery { anyConstructed<LegacyAuthenticationRepository>().exportAllData() } returns ExportedData(
            accounts = accounts,
            mechanisms = mechanisms,
            notifications = emptyMap(),
            deviceToken = emptyMap(),
            metadata = ExportMetadata(
                totalAccounts = accounts.size,
                totalMechanisms = mechanisms.size,
                totalNotifications = 0,
                hasDeviceToken = false
            )
        )
        coEvery { anyConstructed<LegacyAuthenticationRepository>().deleteLegacyData() } returns Unit

        // When
        val progressList = AuthMigration.migration.migrate(context).toList()

        // Then
        assertTrue(progressList.any { it is MigrationProgress.Success })

        // Capture the stored credential
        val pushSlot = slot<PushCredential>()
        // Verify the storage function was called with the expected credential
        coVerify(exactly = 1) { anyConstructed<SQLPushStorage>().storePushCredential(capture(pushSlot)) }

        // Verify the captured credential has the expected values
        val storedCredential = pushSlot.captured
        assertEquals("push-mechanism-1", storedCredential.id)
        assertEquals("ForgeRock", storedCredential.issuer)
        assertEquals("admin@company.com", storedCredential.accountName)
        assertEquals("sharedSecret123", storedCredential.sharedSecret)
        assertEquals("https://am.example.com/push", storedCredential.serverEndpoint)
    }

    @Test
    fun `migration migrates push notifications correctly`() = runTest {
        // Given - Legacy repository with notification
        val notificationTime = System.currentTimeMillis()
        val notifications = mapOf(
            "notification-1" to """
                {
                    "id": "notification-1",
                    "credentialId": "push-credential-1",
                    "messageId": "msg-123",
                    "challenge": "challenge-data",
                    "ttl": 120,
                    "approved": false,
                    "pending": true,
                    "timeAdded": $notificationTime
                }
            """.trimIndent()
        )

        coEvery { anyConstructed<LegacyAuthenticationRepository>().isExists() } returns true
        coEvery { anyConstructed<LegacyAuthenticationRepository>().exportAllData() } returns ExportedData(
            accounts = emptyMap(),
            mechanisms = emptyMap(),
            notifications = notifications,
            deviceToken = emptyMap(),
            metadata = ExportMetadata(
                totalAccounts = 0,
                totalMechanisms = 0,
                totalNotifications = notifications.size,
                hasDeviceToken = false
            )
        )
        coEvery { anyConstructed<LegacyAuthenticationRepository>().deleteLegacyData() } returns Unit

        // When
        val progressList = AuthMigration.migration.migrate(context).toList()

        // Then
        assertTrue(progressList.any { it is MigrationProgress.Success })

        // Capture the stored notification
        val notificationSlot = slot<PushNotification>()
        // Verify the storage function was called with the expected notification
        coVerify(exactly = 1) { anyConstructed<SQLPushStorage>().storePushNotification(capture(notificationSlot)) }

        // Verify the captured notification has the expected values
        val storedNotification = notificationSlot.captured
        assertEquals("notification-1", storedNotification.id)
        assertEquals("push-credential-1", storedNotification.credentialId)
        assertEquals("msg-123", storedNotification.messageId)
        assertEquals("challenge-data", storedNotification.challenge)
        assertEquals(120, storedNotification.ttl)
        assertEquals(false, storedNotification.approved)
        assertEquals(true, storedNotification.pending)
        assertEquals(notificationTime, storedNotification.createdAt.time)
    }

    @Test
    fun `migration migrates device token correctly`() = runTest {
        // Given - Legacy repository with device token
        val tokenTime = System.currentTimeMillis()
        val deviceTokens = mapOf(
            "device-token-1" to """
                {
                    "id": "device-token-1",
                    "deviceToken": "fcm-token-xyz-123",
                    "token": "fcm-token-xyz-123",
                    "timeAdded": $tokenTime
                }
            """.trimIndent()
        )

        coEvery { anyConstructed<LegacyAuthenticationRepository>().isExists() } returns true
        coEvery { anyConstructed<LegacyAuthenticationRepository>().exportAllData() } returns ExportedData(
            accounts = emptyMap(),
            mechanisms = emptyMap(),
            notifications = emptyMap(),
            deviceToken = deviceTokens,
            metadata = ExportMetadata(
                totalAccounts = 0,
                totalMechanisms = 0,
                totalNotifications = 0,
                hasDeviceToken = true
            )
        )
        coEvery { anyConstructed<LegacyAuthenticationRepository>().deleteLegacyData() } returns Unit

        // When
        val progressList = AuthMigration.migration.migrate(context).toList()

        // Then
        assertTrue(progressList.any { it is MigrationProgress.Success })

        // Capture the stored device token
        val tokenSlot = slot<PushDeviceToken>()
        // Verify the storage function was called with the expected device token
        coVerify(exactly = 1) { anyConstructed<SQLPushStorage>().storePushDeviceToken(capture(tokenSlot)) }

        // Verify the captured device token has the expected values
        val storedToken = tokenSlot.captured
        assertEquals("device-token-1", storedToken.id)
        assertEquals("fcm-token-xyz-123", storedToken.tokenId)
        assertEquals(tokenTime, storedToken.createdAt.time)
    }

    @Test
    fun `migration step 2 continues with other mechanisms when one fails`() = runTest {
        // Given - Multiple mechanisms, one with invalid data
        val mechanisms = mapOf(
            "valid-totp" to """
                {
                    "type": "totp",
                    "mechanismUID": "valid-totp",
                    "issuer": "Valid",
                    "accountName": "user1@example.com",
                    "secret": "VALIDKEY",
                    "algorithm": "sha1",
                    "digits": 6,
                    "period": 30
                }
            """.trimIndent(),
            "invalid-mechanism" to """
                {
                    "type": "unknown-type",
                    "invalid": "data"
                }
            """.trimIndent(),
            "valid-push" to """
                {
                    "type": "push",
                    "mechanismUID": "valid-push",
                    "issuer": "Valid",
                    "accountName": "user2@example.com",
                    "secret": "pushSecret",
                    "authenticationEndpoint": "https://example.com/push"
                }
            """.trimIndent()
        )

        coEvery { anyConstructed<LegacyAuthenticationRepository>().isExists() } returns true
        coEvery { anyConstructed<LegacyAuthenticationRepository>().exportAllData() } returns ExportedData(
            accounts = emptyMap(),
            mechanisms = mechanisms,
            notifications = emptyMap(),
            deviceToken = emptyMap(),
            metadata = ExportMetadata(
                totalAccounts = 0,
                totalMechanisms = mechanisms.size,
                totalNotifications = 0,
                hasDeviceToken = false
            )
        )
        coEvery { anyConstructed<LegacyAuthenticationRepository>().deleteLegacyData() } returns Unit

        // When
        val progressList = AuthMigration.migration.migrate(context).toList()

        // Then - Migration should still complete successfully
        assertTrue(progressList.any { it is MigrationProgress.Success })

        // Should complete all steps even though one mechanism failed
        val stepCompletedEvents = progressList.filterIsInstance<MigrationProgress.StepCompleted>()
        assertTrue(stepCompletedEvents.size >= 5)
    }

    @Test
    fun `migration handles mixed OATH types correctly`() = runTest {
        // Given - Legacy repository with both TOTP and HOTP
        val mechanisms = mapOf(
            "totp-1" to """
                {
                    "type": "totp",
                    "mechanismUID": "totp-1",
                    "issuer": "TOTP Service",
                    "accountName": "totp@example.com",
                    "secret": "TOTPKEY",
                    "oathType": "TOTP",
                    "period": 30
                }
            """.trimIndent(),
            "hotp-1" to """
                {
                    "type": "hotp",
                    "mechanismUID": "hotp-1",
                    "issuer": "HOTP Service",
                    "accountName": "hotp@example.com",
                    "secret": "HOTPKEY",
                    "oathType": "HOTP",
                    "counter": 5
                }
            """.trimIndent()
        )

        coEvery { anyConstructed<LegacyAuthenticationRepository>().isExists() } returns true
        coEvery { anyConstructed<LegacyAuthenticationRepository>().exportAllData() } returns ExportedData(
            accounts = emptyMap(),
            mechanisms = mechanisms,
            notifications = emptyMap(),
            deviceToken = emptyMap(),
            metadata = ExportMetadata(
                totalAccounts = 0,
                totalMechanisms = mechanisms.size,
                totalNotifications = 0,
                hasDeviceToken = false
            )
        )
        coEvery { anyConstructed<LegacyAuthenticationRepository>().deleteLegacyData() } returns Unit

        // When
        val progressList = AuthMigration.migration.migrate(context).toList()

        // Then
        assertTrue(progressList.any { it is MigrationProgress.Success })
    }

    @Test
    fun `migration handles different hash algorithms correctly`() = runTest {
        // Given - Mechanisms with different algorithms
        val mechanisms = mapOf(
            "sha1-totp" to """
                {
                    "type": "totp",
                    "mechanismUID": "sha1-totp",
                    "issuer": "SHA1 Service",
                    "accountName": "sha1@example.com",
                    "secret": "SHA1KEY",
                    "algorithm": "sha1"
                }
            """.trimIndent(),
            "sha256-totp" to """
                {
                    "type": "totp",
                    "mechanismUID": "sha256-totp",
                    "issuer": "SHA256 Service",
                    "accountName": "sha256@example.com",
                    "secret": "SHA256KEY",
                    "algorithm": "sha256"
                }
            """.trimIndent(),
            "sha512-totp" to """
                {
                    "type": "totp",
                    "mechanismUID": "sha512-totp",
                    "issuer": "SHA512 Service",
                    "accountName": "sha512@example.com",
                    "secret": "SHA512KEY",
                    "algorithm": "sha512"
                }
            """.trimIndent()
        )

        coEvery { anyConstructed<LegacyAuthenticationRepository>().isExists() } returns true
        coEvery { anyConstructed<LegacyAuthenticationRepository>().exportAllData() } returns ExportedData(
            accounts = emptyMap(),
            mechanisms = mechanisms,
            notifications = emptyMap(),
            deviceToken = emptyMap(),
            metadata = ExportMetadata(
                totalAccounts = 0,
                totalMechanisms = mechanisms.size,
                totalNotifications = 0,
                hasDeviceToken = false
            )
        )
        coEvery { anyConstructed<LegacyAuthenticationRepository>().deleteLegacyData() } returns Unit

        // When
        val progressList = AuthMigration.migration.migrate(context).toList()

        // Then
        assertTrue(progressList.any { it is MigrationProgress.Success })
    }

    @Test
    fun `migration cleanup step continues even if cleanup fails`() = runTest {
        // Given - Legacy repository with data but cleanup will fail
        val mechanisms = mapOf(
            "test-mechanism" to """
                {
                    "type": "totp",
                    "mechanismUID": "test-mechanism",
                    "issuer": "Test",
                    "accountName": "test@example.com",
                    "secret": "TESTKEY"
                }
            """.trimIndent()
        )

        coEvery { anyConstructed<LegacyAuthenticationRepository>().isExists() } returns true
        coEvery { anyConstructed<LegacyAuthenticationRepository>().exportAllData() } returns ExportedData(
            accounts = emptyMap(),
            mechanisms = mechanisms,
            notifications = emptyMap(),
            deviceToken = emptyMap(),
            metadata = ExportMetadata(
                totalAccounts = 0,
                totalMechanisms = mechanisms.size,
                totalNotifications = 0,
                hasDeviceToken = false
            )
        )
        // Simulate cleanup failure
        coEvery { anyConstructed<LegacyAuthenticationRepository>().deleteLegacyData() } throws Exception("Cleanup failed")

        // When
        val progressList = AuthMigration.migration.migrate(context).toList()

        // Then - Migration should still complete successfully even if cleanup fails
        assertTrue(progressList.any { it is MigrationProgress.Success })

        // All steps should complete
        val stepCompletedEvents = progressList.filterIsInstance<MigrationProgress.StepCompleted>()
        assertEquals(5, stepCompletedEvents.size)
    }

    // Helper methods


    private fun createTestLegacyData(): LegacyTestData {
        val accounts = mapOf(
            "account-1" to """
                {
                    "id": "account-1",
                    "issuer": "Google",
                    "accountName": "user@example.com",
                    "imageURL": "https://google.com/logo.png"
                }
            """.trimIndent(),
            "account-2" to """
                {
                    "id": "account-2",
                    "issuer": "GitHub",
                    "accountName": "developer@example.com"
                }
            """.trimIndent()
        )

        val mechanisms = mapOf(
            "mechanism-1" to """
                {
                    "type": "totp",
                    "mechanismUID": "mechanism-1",
                    "issuer": "Google",
                    "accountName": "user@example.com",
                    "secret": "JBSWY3DPEHPK3PXP",
                    "algorithm": "sha1",
                    "digits": 6,
                    "period": 30
                }
            """.trimIndent(),
            "mechanism-2" to """
                {
                    "type": "push",
                    "mechanismUID": "mechanism-2",
                    "issuer": "GitHub",
                    "accountName": "developer@example.com",
                    "secret": "pushSecret123",
                    "authenticationEndpoint": "https://github.com/push"
                }
            """.trimIndent()
        )

        val notifications = mapOf(
            "notification-1" to """
                {
                    "id": "notification-1",
                    "credentialId": "mechanism-2",
                    "messageId": "msg-456",
                    "challenge": "challenge-xyz",
                    "ttl": 120,
                    "approved": false,
                    "pending": true,
                    "timeAdded": ${System.currentTimeMillis()}
                }
            """.trimIndent()
        )

        val deviceTokens = mapOf(
            "token-1" to """
                {
                    "id": "token-1",
                    "deviceToken": "fcm-token-abc-123",
                    "timeAdded": ${System.currentTimeMillis()}
                }
            """.trimIndent()
        )

        return LegacyTestData(accounts, mechanisms, notifications, deviceTokens)
    }

    data class LegacyTestData(
        val accounts: Map<String, String>,
        val mechanisms: Map<String, String>,
        val notifications: Map<String, String>,
        val deviceTokens: Map<String, String>
    )
}



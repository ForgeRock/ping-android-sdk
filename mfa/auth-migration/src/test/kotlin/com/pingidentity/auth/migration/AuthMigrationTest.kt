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
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockkConstructor
import io.mockk.unmockkAll
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

// Constants matching the ones in LegacyAuthenticationRepository
private const val AUTH_DATA_ACCOUNT = "org.forgerock.android.authenticator.DATA.ACCOUNT"
private const val AUTH_DATA_MECHANISM = "org.forgerock.android.authenticator.DATA.MECHANISM"

@RunWith(RobolectricTestRunner::class)
class AuthMigrationTest {

    private lateinit var context: Context
    private lateinit var accountPrefs: SharedPreferences
    private lateinit var mechanismPrefs: SharedPreferences

    @BeforeTest
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        ContextProvider.init(context)

        accountPrefs = context.getSharedPreferences(AUTH_DATA_ACCOUNT, Context.MODE_PRIVATE)
        mechanismPrefs = context.getSharedPreferences(AUTH_DATA_MECHANISM, Context.MODE_PRIVATE)

        // Mock LegacyAuthenticationRepository
        mockkConstructor(LegacyAuthenticationRepository::class)
        coEvery { anyConstructed<LegacyAuthenticationRepository>().backupSharedPreferences() } returns emptyList()
        coEvery { anyConstructed<LegacyAuthenticationRepository>().deleteLegacyData() } returns Unit

        // Mock SQLOathStorage
        mockkConstructor(SQLOathStorage::class)
        coEvery { anyConstructed<SQLOathStorage>().initializeDatabase() } returns Unit
        coEvery { anyConstructed<SQLOathStorage>().storeOathCredential(any()) } returns Unit
        coEvery { anyConstructed<SQLOathStorage>().closeDatabase() } returns Unit

        // Mock SQLPushStorage
        mockkConstructor(SQLPushStorage::class)
        coEvery { anyConstructed<SQLPushStorage>().initializeDatabase() } returns Unit
        coEvery { anyConstructed<SQLPushStorage>().storePushCredential(any()) } returns Unit
        coEvery { anyConstructed<SQLPushStorage>().closeDatabase() } returns Unit

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

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun mockNoLegacyData() {
        coEvery { anyConstructed<LegacyAuthenticationRepository>().isExists() } returns false
    }

    private fun mockLegacyData(mechanisms: List<LegacyMechanism>) {
        coEvery { anyConstructed<LegacyAuthenticationRepository>().isExists() } returns true
        coEvery { anyConstructed<LegacyAuthenticationRepository>().exportAllData() } returns LegacyExportedData(
            mechanisms = mechanisms,
            metadata = LegacyExportMetadata(totalMechanisms = mechanisms.size)
        )
    }

    // ── step 1 ───────────────────────────────────────────────────────────────

    @Test
    fun `migration with no legacy repository should abort early`() = runTest {
        mockNoLegacyData()

        AuthMigration.start(context)

        coVerify(exactly = 0) { anyConstructed<SQLOathStorage>().storeOathCredential(any()) }
        coVerify(exactly = 0) { anyConstructed<SQLPushStorage>().storePushCredential(any()) }
    }

    @Test
    fun `migration with empty legacy repository should abort after step 1`() = runTest {
        mockLegacyData(emptyList())

        AuthMigration.start(context)

        coVerify(exactly = 0) { anyConstructed<SQLOathStorage>().storeOathCredential(any()) }
        coVerify(exactly = 0) { anyConstructed<SQLPushStorage>().storePushCredential(any()) }
    }

    // ── steps 1→2→3 ──────────────────────────────────────────────────────────

    @Test
    fun `migration with legacy data completes all three steps successfully`() = runTest {
        mockLegacyData(createTestLegacyData().mechanisms)

        AuthMigration.start(context)

        coVerify(atLeast = 1) { anyConstructed<SQLOathStorage>().storeOathCredential(any()) }
        coVerify(atLeast = 1) { anyConstructed<SQLPushStorage>().storePushCredential(any()) }
        coVerify(atLeast = 1) { anyConstructed<LegacyAuthenticationRepository>().deleteLegacyData() }
    }

    // ── OATH migration ────────────────────────────────────────────────────────

    @Test
    fun `migration migrates OATH TOTP credentials correctly`() = runTest {
        val testTime = System.currentTimeMillis()

        val mechanism = LegacyMechanism(
            id = "totp-mechanism-1",
            issuer = "Google",
            accountName = "user@example.com",
            mechanismUID = "totp-mechanism-1",
            secret = "JBSWY3DPEHPK3PXP",
            type = "totp",
            oathType = "TOTP",
            algorithm = "sha1",
            digits = 6,
            period = 30,
            counter = 0,
            uid = "user-12345",
            resourceId = "resource-67890",
            timeAdded = testTime,
            account = LegacyAccount(
                id = "Google-user@example.com",
                issuer = "Google",
                displayIssuer = "Google Authenticator",
                accountName = "user@example.com",
                displayAccountName = "Demo User",
                imageURL = "https://example.com/logo.png",
                backgroundColor = "#4285F4",
                policies = "{\"deviceTampering\":true}",
                lock = false
            )
        )
        mockLegacyData(listOf(mechanism))

        AuthMigration.start(context)

        // Then - verify all mapped fields
        val slot = slot<OathCredential>()
        coVerify(exactly = 1) { anyConstructed<SQLOathStorage>().storeOathCredential(capture(slot)) }

        val c = slot.captured
        assertNotNull(c.id)
        assertEquals("user-12345", c.userId)
        assertEquals("resource-67890", c.resourceId)
        assertEquals("Google", c.issuer)
        assertEquals("Google Authenticator", c.displayIssuer)
        assertEquals("user@example.com", c.accountName)
        assertEquals("Demo User", c.displayAccountName)
        assertEquals(OathType.TOTP, c.oathType)
        assertEquals("JBSWY3DPEHPK3PXP", c.secret)
        assertEquals(OathAlgorithm.SHA1, c.oathAlgorithm)
        assertEquals(6, c.digits)
        assertEquals(30, c.period)
        assertEquals(0L, c.counter)
        assertEquals(testTime, c.createdAt.time)
        assertEquals("https://example.com/logo.png", c.imageURL)
        assertEquals("#4285F4", c.backgroundColor)
        assertEquals("{\"deviceTampering\":true}", c.policies)
        assertEquals(null, c.lockingPolicy)
        assertEquals(false, c.isLocked)
    }

    @Test
    fun `migration migrates HOTP credentials correctly with counter`() = runTest {
        val testTime = System.currentTimeMillis()

        val mechanism = LegacyMechanism(
            id = "hotp-mechanism-1",
            issuer = "GitHub",
            accountName = "developer@example.com",
            mechanismUID = "hotp-mechanism-1",
            secret = "HOTPSECRETKEY123",
            type = "hotp",
            oathType = "HOTP",
            algorithm = "sha256",
            digits = 8,
            counter = 42,
            uid = "hotp-user-789",
            resourceId = "hotp-resource-456",
            timeAdded = testTime,
            account = LegacyAccount(
                id = "GitHub-developer@example.com",
                issuer = "GitHub",
                displayIssuer = "GitHub Inc",
                accountName = "developer@example.com",
                displayAccountName = "Developer Account",
                imageURL = "https://github.com/logo.png",
                backgroundColor = "#24292e",
                lock = false
            )
        )
        mockLegacyData(listOf(mechanism))

        AuthMigration.start(context)

        val slot = slot<OathCredential>()
        coVerify(exactly = 1) { anyConstructed<SQLOathStorage>().storeOathCredential(capture(slot)) }

        val c = slot.captured
        assertNotNull(c.id)
        assertEquals("hotp-user-789", c.userId)
        assertEquals("hotp-resource-456", c.resourceId)
        assertEquals("GitHub", c.issuer)
        assertEquals("GitHub Inc", c.displayIssuer)
        assertEquals("developer@example.com", c.accountName)
        assertEquals("Developer Account", c.displayAccountName)
        assertEquals(OathType.HOTP, c.oathType)
        assertEquals("HOTPSECRETKEY123", c.secret)
        assertEquals(OathAlgorithm.SHA256, c.oathAlgorithm)
        assertEquals(8, c.digits)
        assertEquals(42L, c.counter)
        assertEquals(testTime, c.createdAt.time)
        assertEquals("https://github.com/logo.png", c.imageURL)
        assertEquals("#24292e", c.backgroundColor)
        assertEquals(null, c.policies)
        assertEquals(null, c.lockingPolicy)
        assertEquals(false, c.isLocked)
    }

    // ── Push migration ────────────────────────────────────────────────────────

    @Test
    fun `migration migrates Push credentials correctly`() = runTest {
        val testTime = System.currentTimeMillis()

        val mechanism = LegacyMechanism(
            id = "push-mechanism-1",
            issuer = "ForgeRock",
            accountName = "admin@company.com",
            mechanismUID = "push-mechanism-1",
            secret = "sharedSecret123",
            type = "push",
            uid = "user-99999",
            resourceId = "push-resource-11111",
            authenticationEndpoint = "https://am.example.com/push?_action=authenticate",
            registrationEndpoint = "https://am.example.com/push?_action=register",
            platform = "PING_AM",
            timeAdded = testTime,
            account = LegacyAccount(
                id = "ForgeRock-admin@company.com",
                issuer = "ForgeRock",
                displayIssuer = "ForgeRock Identity",
                accountName = "admin@company.com",
                displayAccountName = "Admin User",
                imageURL = "https://forgerock.com/logo.png",
                backgroundColor = "#00A1DE",
                policies = "{\"biometric\":true}",
                lockingPolicy = "BiometricNotAvailable",
                lock = true
            )
        )
        mockLegacyData(listOf(mechanism))

        AuthMigration.start(context)

        val slot = slot<PushCredential>()
        coVerify(exactly = 1) { anyConstructed<SQLPushStorage>().storePushCredential(capture(slot)) }

        val c = slot.captured
        assertNotNull(c.id)
        assertEquals("user-99999", c.userId)
        assertEquals("push-resource-11111", c.resourceId)
        assertEquals("ForgeRock", c.issuer)
        assertEquals("ForgeRock Identity", c.displayIssuer)
        assertEquals("admin@company.com", c.accountName)
        assertEquals("Admin User", c.displayAccountName)
        assertEquals("https://am.example.com/push", c.serverEndpoint)
        assertEquals("sharedSecret123", c.sharedSecret)
        assertEquals(testTime, c.createdAt.time)
        assertEquals("https://forgerock.com/logo.png", c.imageURL)
        assertEquals("#00A1DE", c.backgroundColor)
        assertEquals("{\"biometric\":true}", c.policies)
        assertEquals("BiometricNotAvailable", c.lockingPolicy)
        assertEquals(true, c.isLocked)
        assertEquals("PING_AM", c.platform)
    }

    // ── resilience ────────────────────────────────────────────────────────────

    @Test
    fun `migration step 2 continues with other mechanisms when one fails`() = runTest {
        mockLegacyData(listOf(
            LegacyMechanism(
                id = "valid-totp", issuer = "Valid", accountName = "user1@example.com",
                mechanismUID = "valid-totp", secret = "VALIDKEY", type = "totp",
                algorithm = "sha1", digits = 6, period = 30
            ),
            LegacyMechanism(
                id = "unknown", issuer = "Unknown", accountName = "u@example.com",
                mechanismUID = "unknown", secret = "s", type = "unknown-type"
            ),
            LegacyMechanism(
                id = "valid-push", issuer = "Valid", accountName = "user2@example.com",
                mechanismUID = "valid-push", secret = "pushSecret", type = "push",
                authenticationEndpoint = "https://example.com/push"
            )
        ))

        AuthMigration.start(context)

        coVerify(exactly = 1) { anyConstructed<SQLOathStorage>().storeOathCredential(any()) }
        coVerify(exactly = 1) { anyConstructed<SQLPushStorage>().storePushCredential(any()) }
    }

    @Test
    fun `migration handles mixed OATH types correctly`() = runTest {
        mockLegacyData(listOf(
            LegacyMechanism(
                id = "totp-1", issuer = "TOTP Service", accountName = "totp@example.com",
                mechanismUID = "totp-1", secret = "TOTPKEY", type = "totp", oathType = "TOTP", period = 30
            ),
            LegacyMechanism(
                id = "hotp-1", issuer = "HOTP Service", accountName = "hotp@example.com",
                mechanismUID = "hotp-1", secret = "HOTPKEY", type = "hotp", oathType = "HOTP", counter = 5
            )
        ))

        AuthMigration.start(context)

        coVerify(exactly = 2) { anyConstructed<SQLOathStorage>().storeOathCredential(any()) }
    }

    @Test
    fun `migration handles different hash algorithms correctly`() = runTest {
        mockLegacyData(listOf(
            LegacyMechanism(
                id = "sha1-totp", issuer = "SHA1 Service", accountName = "sha1@example.com",
                mechanismUID = "sha1-totp", secret = "SHA1KEY", type = "totp", algorithm = "sha1"
            ),
            LegacyMechanism(
                id = "sha256-totp", issuer = "SHA256 Service", accountName = "sha256@example.com",
                mechanismUID = "sha256-totp", secret = "SHA256KEY", type = "totp", algorithm = "sha256"
            ),
            LegacyMechanism(
                id = "sha512-totp", issuer = "SHA512 Service", accountName = "sha512@example.com",
                mechanismUID = "sha512-totp", secret = "SHA512KEY", type = "totp", algorithm = "sha512"
            )
        ))

        AuthMigration.start(context)

        coVerify(exactly = 3) { anyConstructed<SQLOathStorage>().storeOathCredential(any()) }
    }

    @Test
    fun `migration handles database initialization failure with destructive recovery enabled`() = runTest {
        mockLegacyData(listOf(
            LegacyMechanism(
                id = "totp-mechanism-1", issuer = "Google", accountName = "user@example.com",
                mechanismUID = "totp-mechanism-1", secret = "JBSWY3DPEHPK3PXP",
                type = "totp", oathType = "TOTP", algorithm = "sha1", digits = 6, period = 30
            )
        ))

        AuthMigration.start(context)

        coVerify(atLeast = 1) { anyConstructed<SQLOathStorage>().initializeDatabase() }
        coVerify(atLeast = 1) { anyConstructed<SQLPushStorage>().initializeDatabase() }
        coVerify(exactly = 1) { anyConstructed<SQLOathStorage>().storeOathCredential(any()) }
    }

    @Test
    fun `migration cleanup step continues even if cleanup fails`() = runTest {
        mockLegacyData(listOf(
            LegacyMechanism(
                id = "test-mechanism", issuer = "Test", accountName = "test@example.com",
                mechanismUID = "test-mechanism", secret = "TESTKEY", type = "totp"
            )
        ))
        coEvery { anyConstructed<LegacyAuthenticationRepository>().deleteLegacyData() } throws Exception("Cleanup failed")

        AuthMigration.start(context)

        coVerify(exactly = 1) { anyConstructed<SQLOathStorage>().storeOathCredential(any()) }
    }

    @Test
    fun `migration preserves existing valid databases and only removes incompatible ones`() = runTest {
        mockLegacyData(listOf(
            LegacyMechanism(
                id = "totp-mechanism-1", issuer = "Google", accountName = "user@example.com",
                mechanismUID = "totp-mechanism-1", secret = "JBSWY3DPEHPK3PXP",
                type = "totp", oathType = "TOTP", algorithm = "sha1", digits = 6, period = 30
            )
        ))
        coEvery { anyConstructed<SQLOathStorage>().getAllOathCredentials() } returns emptyList()
        coEvery { anyConstructed<SQLPushStorage>().getAllPushCredentials() } returns emptyList()

        AuthMigration.start(context)

        coVerify(atLeast = 1) { anyConstructed<SQLOathStorage>().storeOathCredential(any()) }
    }

    // ── custom storage ────────────────────────────────────────────────────────

    @Test
    fun `migration supports custom storage via LegacyStorageProvider`() = runTest {
        val testTime = System.currentTimeMillis()

        val customMechanism = LegacyMechanism(
            id = "custom-totp-1",
            issuer = "CustomApp",
            accountName = "custom@example.com",
            mechanismUID = "custom-totp-1",
            secret = "CUSTOMSECRET123",
            type = "totp",
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
                backgroundColor = "#FF5733"
            )
        )

        // Supply data via DSL — bypasses LegacyAuthenticationRepository entirely
        AuthMigration.start(context) {
            legacyStorageProvider = object : LegacyStorageProvider {
                override suspend fun isMigrationRequired(context: Context) = true
                override suspend fun getMigrationData(context: Context) = LegacyExportedData(
                    mechanisms = listOf(customMechanism),
                    metadata = LegacyExportMetadata(totalMechanisms = 1)
                )
                override suspend fun cleanUp(context: Context) = Unit
            }
        }

        val slot = slot<OathCredential>()
        coVerify(exactly = 1) { anyConstructed<SQLOathStorage>().storeOathCredential(capture(slot)) }

        val c = slot.captured
        assertNotNull(c.id)
        assertEquals("CustomApp", c.issuer)
        assertEquals("Custom Application", c.displayIssuer)
        assertEquals("custom@example.com", c.accountName)
        assertEquals("Custom User", c.displayAccountName)
        assertEquals("custom-user-123", c.userId)
        assertEquals("custom-resource-456", c.resourceId)
    }

    @Test
    fun `migration supports custom key alias configuration`() = runTest {
        val customKeyAlias = "com.myapp.custom.STORAGE_KEY"

        mockLegacyData(listOf(
            LegacyMechanism(
                id = "custom-key-totp",
                issuer = "CustomKeyApp",
                accountName = "user@customkey.com",
                mechanismUID = "custom-key-totp",
                secret = "CUSTOMKEYSECRET",
                type = "totp",
                oathType = "TOTP",
                algorithm = "sha1",
                digits = 6,
                period = 30,
                account = LegacyAccount(
                    id = "CustomKeyApp-user@customkey.com",
                    issuer = "CustomKeyApp",
                    accountName = "user@customkey.com"
                )
            )
        ))

        // Set the key alias via the DSL block — no LegacyAuthenticationConfig constructor needed
        AuthMigration.start(context) {
            keyAlias = customKeyAlias
        }

        val slot = slot<OathCredential>()
        coVerify(exactly = 1) { anyConstructed<SQLOathStorage>().storeOathCredential(capture(slot)) }
        assertNotNull(slot.captured.id)
        assertEquals("CustomKeyApp", slot.captured.issuer)
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun createTestLegacyData(): LegacyTestData = LegacyTestData(
        mechanisms = listOf(
            LegacyMechanism(
                id = "mechanism-1",
                issuer = "Google",
                accountName = "user@example.com",
                mechanismUID = "mechanism-1",
                secret = "JBSWY3DPEHPK3PXP",
                type = "totp",
                algorithm = "sha1",
                digits = 6,
                period = 30,
                account = LegacyAccount(
                    id = "Google-user@example.com",
                    issuer = "Google",
                    accountName = "user@example.com",
                    imageURL = "https://google.com/logo.png"
                )
            ),
            LegacyMechanism(
                id = "mechanism-2",
                issuer = "GitHub",
                accountName = "developer@example.com",
                mechanismUID = "mechanism-2",
                secret = "pushSecret123",
                type = "push",
                authenticationEndpoint = "https://github.com/push",
                account = LegacyAccount(
                    id = "GitHub-developer@example.com",
                    issuer = "GitHub",
                    accountName = "developer@example.com"
                )
            )
        )
    )

    data class LegacyTestData(val mechanisms: List<LegacyMechanism>)
}

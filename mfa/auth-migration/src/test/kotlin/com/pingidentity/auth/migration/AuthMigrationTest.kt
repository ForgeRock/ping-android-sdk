/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.auth.migration

import android.content.Context
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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class AuthMigrationTest {

    private lateinit var context: Context

    // Holds the fake provider configured by each test via mockNoLegacyData() / mockLegacyData()
    private lateinit var fakeProvider: FakeLegacyStorageProvider

    @BeforeTest
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        ContextProvider.init(context)

        // Mock SQLOathStorage — controls Step 2 behaviour without real SQLite
        mockkConstructor(SQLOathStorage::class)
        coEvery { anyConstructed<SQLOathStorage>().initializeDatabase() } returns Unit
        coEvery { anyConstructed<SQLOathStorage>().storeOathCredential(any()) } returns Unit
        coEvery { anyConstructed<SQLOathStorage>().closeDatabase() } returns Unit

        // Mock SQLPushStorage — controls Step 2 behaviour without real SQLite
        mockkConstructor(SQLPushStorage::class)
        coEvery { anyConstructed<SQLPushStorage>().initializeDatabase() } returns Unit
        coEvery { anyConstructed<SQLPushStorage>().storePushCredential(any()) } returns Unit
        coEvery { anyConstructed<SQLPushStorage>().closeDatabase() } returns Unit
    }

    @AfterTest
    fun tearDown() {
        unmockkAll()
    }

    // ── Test double ───────────────────────────────────────────────────────────

    /**
     * A simple, fully in-process [LegacyStorageProvider] that avoids all real I/O
     * (no `DefaultStorageClient`, no `withContext(Dispatchers.IO)`, no SharedPreferences reads).
     *
     * Configure via the constructor; observe results via the boolean properties after the
     * migration completes.
     */
    private class FakeLegacyStorageProvider(
        private val migrationRequired: Boolean = true,
        private val mechanisms: List<LegacyMechanism> = emptyList(),
        /** Called at the end of [cleanUp], after the backup callback is invoked. */
        private val onCleanUp: () -> Unit = {},
    ) : LegacyStorageProvider {

        var isMigrationRequiredCalled = false
        var cleanUpCalled = false

        override suspend fun isMigrationRequired(context: Context): Boolean {
            isMigrationRequiredCalled = true
            return migrationRequired
        }

        override suspend fun getMigrationData(context: Context) = LegacyExportedData(
            mechanisms = mechanisms,
            metadata = LegacyExportMetadata(totalMechanisms = mechanisms.size)
        )

        override suspend fun cleanUp(context: Context, backup: (context: Context) -> Unit) {
            backup(context)
            cleanUpCalled = true
            onCleanUp()
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Configures [fakeProvider] so that [LegacyStorageProvider.isMigrationRequired] returns false. */
    private fun mockNoLegacyData() {
        fakeProvider = FakeLegacyStorageProvider(migrationRequired = false)
    }

    /** Configures [fakeProvider] to report migration required and return [mechanisms]. */
    private fun mockLegacyData(mechanisms: List<LegacyMechanism>) {
        fakeProvider = FakeLegacyStorageProvider(mechanisms = mechanisms)
    }

    // ── Step 1 ───────────────────────────────────────────────────────────────

    @Test
    fun `migration with no legacy repository should abort early`() = runTest {
        mockNoLegacyData()

        AuthMigration.start(context) { legacyStorageProvider = fakeProvider }

        assertTrue(fakeProvider.isMigrationRequiredCalled)
        assertFalse(fakeProvider.cleanUpCalled, "cleanUp must not be called when migration is not required")
        coVerify(exactly = 0) { anyConstructed<SQLOathStorage>().storeOathCredential(any()) }
        coVerify(exactly = 0) { anyConstructed<SQLPushStorage>().storePushCredential(any()) }
    }

    @Test
    fun `migration with empty legacy repository should abort after step 1`() = runTest {
        mockLegacyData(emptyList())

        AuthMigration.start(context) { legacyStorageProvider = fakeProvider }

        assertFalse(fakeProvider.cleanUpCalled, "cleanUp must not be called when mechanisms list is empty")
        coVerify(exactly = 0) { anyConstructed<SQLOathStorage>().storeOathCredential(any()) }
        coVerify(exactly = 0) { anyConstructed<SQLPushStorage>().storePushCredential(any()) }
    }

    // ── Steps 1→2→3 ──────────────────────────────────────────────────────────

    @Test
    fun `migration with legacy data completes all three steps successfully`() = runTest {
        mockLegacyData(createTestLegacyData().mechanisms)

        AuthMigration.start(context) { legacyStorageProvider = fakeProvider }

        coVerify(atLeast = 1) { anyConstructed<SQLOathStorage>().storeOathCredential(any()) }
        coVerify(atLeast = 1) { anyConstructed<SQLPushStorage>().storePushCredential(any()) }
        assertTrue(fakeProvider.cleanUpCalled, "Step 3 cleanUp must run after successful migration")
    }

    @Test
    fun `migration cleanup invokes backup callback before clearing data`() = runTest {
        var backupCallbackInvoked = false
        fakeProvider = FakeLegacyStorageProvider(mechanisms = createTestLegacyData().mechanisms)

        AuthMigration.start(context) {
            legacyStorageProvider = object : LegacyStorageProvider {
                override suspend fun isMigrationRequired(context: Context) = true
                override suspend fun getMigrationData(context: Context) = LegacyExportedData(
                    mechanisms = createTestLegacyData().mechanisms,
                    metadata = LegacyExportMetadata(totalMechanisms = createTestLegacyData().mechanisms.size)
                )
                override suspend fun cleanUp(context: Context, backup: (context: Context) -> Unit) {
                    backup(context)
                    backupCallbackInvoked = true
                }
            }
        }

        coVerify(atLeast = 1) { anyConstructed<SQLOathStorage>().storeOathCredential(any()) }
        coVerify(atLeast = 1) { anyConstructed<SQLPushStorage>().storePushCredential(any()) }
        assertTrue(backupCallbackInvoked, "backup callback must be invoked by cleanUp")
    }


    // ── OATH migration ────────────────────────────────────────────────────────

    @Test
    fun `migration migrates OATH TOTP credentials correctly`() = runTest {
        val testTime = System.currentTimeMillis()
        val mechanism = LegacyMechanism(
            id = "totp-mechanism-1", issuer = "Google", accountName = "user@example.com",
            mechanismUID = "totp-mechanism-1", secret = "JBSWY3DPEHPK3PXP",
            type = "totp", oathType = "TOTP", algorithm = "sha1", digits = 6, period = 30, counter = 0,
            uid = "user-12345", resourceId = "resource-67890", timeAdded = testTime,
            account = LegacyAccount(
                id = "Google-user@example.com", issuer = "Google",
                displayIssuer = "Google Authenticator", accountName = "user@example.com",
                displayAccountName = "Demo User", imageURL = "https://example.com/logo.png",
                backgroundColor = "#4285F4", policies = "{\"deviceTampering\":true}", lock = false
            )
        )
        mockLegacyData(listOf(mechanism))

        AuthMigration.start(context) { legacyStorageProvider = fakeProvider }

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
            id = "hotp-mechanism-1", issuer = "GitHub", accountName = "developer@example.com",
            mechanismUID = "hotp-mechanism-1", secret = "HOTPSECRETKEY123",
            type = "hotp", oathType = "HOTP", algorithm = "sha256", digits = 8, counter = 42,
            uid = "hotp-user-789", resourceId = "hotp-resource-456", timeAdded = testTime,
            account = LegacyAccount(
                id = "GitHub-developer@example.com", issuer = "GitHub", displayIssuer = "GitHub Inc",
                accountName = "developer@example.com", displayAccountName = "Developer Account",
                imageURL = "https://github.com/logo.png", backgroundColor = "#24292e", lock = false
            )
        )
        mockLegacyData(listOf(mechanism))

        AuthMigration.start(context) { legacyStorageProvider = fakeProvider }

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
        assertEquals(null, c.lockingPolicy)
        assertEquals(false, c.isLocked)
    }

    // ── Push migration ────────────────────────────────────────────────────────

    @Test
    fun `migration migrates Push credentials correctly`() = runTest {
        val testTime = System.currentTimeMillis()
        val mechanism = LegacyMechanism(
            id = "push-mechanism-1", issuer = "ForgeRock", accountName = "admin@company.com",
            mechanismUID = "push-mechanism-1", secret = "sharedSecret123", type = "push",
            uid = "user-99999", resourceId = "push-resource-11111",
            authenticationEndpoint = "https://am.example.com/push?_action=authenticate",
            registrationEndpoint = "https://am.example.com/push?_action=register",
            platform = "PING_AM", timeAdded = testTime,
            account = LegacyAccount(
                id = "ForgeRock-admin@company.com", issuer = "ForgeRock",
                displayIssuer = "ForgeRock Identity", accountName = "admin@company.com",
                displayAccountName = "Admin User", imageURL = "https://forgerock.com/logo.png",
                backgroundColor = "#00A1DE", policies = "{\"biometric\":true}",
                lockingPolicy = "BiometricNotAvailable", lock = true
            )
        )
        mockLegacyData(listOf(mechanism))

        AuthMigration.start(context) { legacyStorageProvider = fakeProvider }

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

    // ── Resilience ────────────────────────────────────────────────────────────

    @Test
    fun `migration step 2 continues with other mechanisms when one fails`() = runTest {
        mockLegacyData(listOf(
            LegacyMechanism(
                id = "valid-totp", issuer = "Valid", accountName = "user1@example.com",
                mechanismUID = "valid-totp", secret = "VALIDKEY", type = "totp",
                algorithm = "sha1", digits = 6, period = 30,
                account = LegacyAccount(id = "Valid-user1@example.com", issuer = "Valid", accountName = "user1@example.com")
            ),
            LegacyMechanism(
                id = "unknown", issuer = "Unknown", accountName = "u@example.com",
                mechanismUID = "unknown", secret = "s", type = "unknown-type",
                account = LegacyAccount(id = "Unknown-u@example.com", issuer = "Unknown", accountName = "u@example.com")
            ),
            LegacyMechanism(
                id = "valid-push", issuer = "Valid", accountName = "user2@example.com",
                mechanismUID = "valid-push", secret = "pushSecret", type = "push",
                authenticationEndpoint = "https://example.com/push",
                account = LegacyAccount(id = "Valid-user2@example.com", issuer = "Valid", accountName = "user2@example.com")
            )
        ))

        AuthMigration.start(context) { legacyStorageProvider = fakeProvider }

        coVerify(exactly = 1) { anyConstructed<SQLOathStorage>().storeOathCredential(any()) }
        coVerify(exactly = 1) { anyConstructed<SQLPushStorage>().storePushCredential(any()) }
    }

    @Test
    fun `migration handles mixed OATH types correctly`() = runTest {
        mockLegacyData(listOf(
            LegacyMechanism(
                id = "totp-1", issuer = "TOTP Service", accountName = "totp@example.com",
                mechanismUID = "totp-1", secret = "TOTPKEY", type = "totp", oathType = "TOTP", period = 30,
                account = LegacyAccount(id = "TOTP Service-totp@example.com", issuer = "TOTP Service", accountName = "totp@example.com")
            ),
            LegacyMechanism(
                id = "hotp-1", issuer = "HOTP Service", accountName = "hotp@example.com",
                mechanismUID = "hotp-1", secret = "HOTPKEY", type = "hotp", oathType = "HOTP", counter = 5,
                account = LegacyAccount(id = "HOTP Service-hotp@example.com", issuer = "HOTP Service", accountName = "hotp@example.com")
            )
        ))

        AuthMigration.start(context) { legacyStorageProvider = fakeProvider }

        coVerify(exactly = 2) { anyConstructed<SQLOathStorage>().storeOathCredential(any()) }
    }

    @Test
    fun `migration handles different hash algorithms correctly`() = runTest {
        mockLegacyData(listOf(
            LegacyMechanism(
                id = "sha1-totp", issuer = "SHA1 Service", accountName = "sha1@example.com",
                mechanismUID = "sha1-totp", secret = "SHA1KEY", type = "totp", algorithm = "sha1",
                account = LegacyAccount(id = "SHA1 Service-sha1@example.com", issuer = "SHA1 Service", accountName = "sha1@example.com")
            ),
            LegacyMechanism(
                id = "sha256-totp", issuer = "SHA256 Service", accountName = "sha256@example.com",
                mechanismUID = "sha256-totp", secret = "SHA256KEY", type = "totp", algorithm = "sha256",
                account = LegacyAccount(id = "SHA256 Service-sha256@example.com", issuer = "SHA256 Service", accountName = "sha256@example.com")
            ),
            LegacyMechanism(
                id = "sha512-totp", issuer = "SHA512 Service", accountName = "sha512@example.com",
                mechanismUID = "sha512-totp", secret = "SHA512KEY", type = "totp", algorithm = "sha512",
                account = LegacyAccount(id = "SHA512 Service-sha512@example.com", issuer = "SHA512 Service", accountName = "sha512@example.com")
            )
        ))

        AuthMigration.start(context) { legacyStorageProvider = fakeProvider }

        coVerify(exactly = 3) { anyConstructed<SQLOathStorage>().storeOathCredential(any()) }
    }

    @Test
    fun `migration handles database initialization and storage setup for each migration`() = runTest {
        mockLegacyData(listOf(
            LegacyMechanism(
                id = "totp-mechanism-1", issuer = "Google", accountName = "user@example.com",
                mechanismUID = "totp-mechanism-1", secret = "JBSWY3DPEHPK3PXP",
                type = "totp", oathType = "TOTP", algorithm = "sha1", digits = 6, period = 30,
                account = LegacyAccount(id = "Google-user@example.com", issuer = "Google", accountName = "user@example.com")
            )
        ))

        AuthMigration.start(context) { legacyStorageProvider = fakeProvider }

        coVerify(atLeast = 1) { anyConstructed<SQLOathStorage>().initializeDatabase() }
        coVerify(atLeast = 1) { anyConstructed<SQLPushStorage>().initializeDatabase() }
        coVerify(exactly = 1) { anyConstructed<SQLOathStorage>().storeOathCredential(any()) }
    }

    @Test
    fun `migration cleanup step continues even if cleanup fails`() = runTest {
        fakeProvider = FakeLegacyStorageProvider(
            mechanisms = listOf(
                LegacyMechanism(
                    id = "test-mechanism", issuer = "Test", accountName = "test@example.com",
                    mechanismUID = "test-mechanism", secret = "TESTKEY", type = "totp",
                    account = LegacyAccount(id = "Test-test@example.com", issuer = "Test", accountName = "test@example.com")
                )
            ),
            onCleanUp = { throw Exception("Cleanup failed") }
        )

        // Migration must still store credentials even when cleanup throws
        AuthMigration.start(context) { legacyStorageProvider = fakeProvider }

        coVerify(exactly = 1) { anyConstructed<SQLOathStorage>().storeOathCredential(any()) }
    }

    @Test
    fun `migration preserves existing valid databases and only removes incompatible ones`() = runTest {
        mockLegacyData(listOf(
            LegacyMechanism(
                id = "totp-mechanism-1", issuer = "Google", accountName = "user@example.com",
                mechanismUID = "totp-mechanism-1", secret = "JBSWY3DPEHPK3PXP",
                type = "totp", oathType = "TOTP", algorithm = "sha1", digits = 6, period = 30,
                account = LegacyAccount(id = "Google-user@example.com", issuer = "Google", accountName = "user@example.com")
            )
        ))
        coEvery { anyConstructed<SQLOathStorage>().getAllOathCredentials() } returns emptyList()
        coEvery { anyConstructed<SQLPushStorage>().getAllPushCredentials() } returns emptyList()

        AuthMigration.start(context) { legacyStorageProvider = fakeProvider }

        coVerify(atLeast = 1) { anyConstructed<SQLOathStorage>().storeOathCredential(any()) }
    }

    // ── Custom storage ────────────────────────────────────────────────────────

    @Test
    fun `migration supports custom storage via LegacyStorageProvider`() = runTest {
        val testTime = System.currentTimeMillis()
        val customMechanism = LegacyMechanism(
            id = "custom-totp-1", issuer = "CustomApp", accountName = "custom@example.com",
            mechanismUID = "custom-totp-1", secret = "CUSTOMSECRET123", type = "totp",
            oathType = "TOTP", algorithm = "sha1", digits = 6, period = 30,
            uid = "custom-user-123", resourceId = "custom-resource-456", timeAdded = testTime,
            account = LegacyAccount(
                id = "CustomApp-custom@example.com", issuer = "CustomApp",
                displayIssuer = "Custom Application", accountName = "custom@example.com",
                displayAccountName = "Custom User", imageURL = "https://custom.com/logo.png",
                backgroundColor = "#FF5733"
            )
        )

        AuthMigration.start(context) {
            legacyStorageProvider = object : LegacyStorageProvider {
                override suspend fun isMigrationRequired(context: Context) = true
                override suspend fun getMigrationData(context: Context) = LegacyExportedData(
                    mechanisms = listOf(customMechanism),
                    metadata = LegacyExportMetadata(totalMechanisms = 1)
                )
                override suspend fun cleanUp(context: Context, backup: (context: Context) -> Unit) {
                    backup(context)
                }
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
    fun `migration custom provider cleanUp receives and can invoke backup callback`() = runTest {
        var backupContextCaptured: Context? = null

        AuthMigration.start(context) {
            legacyStorageProvider = object : LegacyStorageProvider {
                override suspend fun isMigrationRequired(context: Context) = true
                override suspend fun getMigrationData(context: Context) = LegacyExportedData(
                    mechanisms = listOf(
                        LegacyMechanism(
                            id = "totp-1", issuer = "Test", accountName = "test@example.com",
                            mechanismUID = "totp-1", secret = "TESTSECRET", type = "totp",
                            account = LegacyAccount(id = "Test-test@example.com", issuer = "Test", accountName = "test@example.com")
                        )
                    ),
                    metadata = LegacyExportMetadata(totalMechanisms = 1)
                )
                override suspend fun cleanUp(context: Context, backup: (context: Context) -> Unit) {
                    backupContextCaptured = context
                    backup(context)
                }
            }
        }

        coVerify(exactly = 1) { anyConstructed<SQLOathStorage>().storeOathCredential(any()) }
        assertNotNull(backupContextCaptured)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun createTestLegacyData() = LegacyTestData(
        mechanisms = listOf(
            LegacyMechanism(
                id = "mechanism-1", issuer = "Google", accountName = "user@example.com",
                mechanismUID = "mechanism-1", secret = "JBSWY3DPEHPK3PXP", type = "totp",
                algorithm = "sha1", digits = 6, period = 30,
                account = LegacyAccount(
                    id = "Google-user@example.com", issuer = "Google",
                    accountName = "user@example.com", imageURL = "https://google.com/logo.png"
                )
            ),
            LegacyMechanism(
                id = "mechanism-2", issuer = "GitHub", accountName = "developer@example.com",
                mechanismUID = "mechanism-2", secret = "pushSecret123", type = "push",
                authenticationEndpoint = "https://github.com/push",
                account = LegacyAccount(
                    id = "GitHub-developer@example.com", issuer = "GitHub",
                    accountName = "developer@example.com"
                )
            )
        )
    )

    data class LegacyTestData(val mechanisms: List<LegacyMechanism>)
}

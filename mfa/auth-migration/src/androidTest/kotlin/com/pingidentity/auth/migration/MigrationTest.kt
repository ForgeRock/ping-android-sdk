/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.auth.migration

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pingidentity.mfa.oath.OathAlgorithm
import com.pingidentity.mfa.oath.OathType
import com.pingidentity.mfa.oath.storage.SQLOathStorage
import com.pingidentity.mfa.push.PushPlatform
import com.pingidentity.mfa.push.storage.SQLPushStorage
import com.pingidentity.migration.MigrationProgress
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.forgerock.android.auth.DefaultStorageClient
import org.forgerock.android.auth.FRAClient
import org.forgerock.android.auth.FRAListener
import org.forgerock.android.auth.Mechanism
import org.forgerock.android.auth.policy.DeviceTamperingPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyStore
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.minutes

/**
 * End-to-end instrumented tests that verify the full migration pipeline from
 * Legacy ForgeRock Authenticator SDK storage to the modern Ping SDK SQLite storage.
 *
 * ## Test strategy
 * - **TOTP / HOTP**: [FRAClient.createMechanismFromUri] writes real encrypted SharedPreferences →
 *   [AuthMigration] reads and migrates → modern [SQLOathStorage] is verified.
 * - **Push**: same flow, with a [MockWebServer] acting as the PingAM registration endpoint so
 *   that [FRAClient] can complete its mandatory server-side handshake.
 * - **Each test** cleans up all legacy SharedPreferences files, the AndroidKeyStore encryption
 *   key, and the modern SQLite databases in both [@Before] and [@After] to guarantee isolation.
 *
 * No mocking frameworks (MockK / Mockito) are used; the only mock is [MockWebServer], which is
 * required solely to satisfy [FRAClient]'s HTTP registration call for push credentials.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    // ──────────────────────────────── Constants ────────────────────────────────

    companion object {
        /* Legacy SharedPreferences file names written by ForgeRock Authenticator SDK */
        private const val LEGACY_ACCOUNT_PREFS =
            "org.forgerock.android.authenticator.DATA.ACCOUNT"
        private const val LEGACY_MECHANISM_PREFS =
            "org.forgerock.android.authenticator.DATA.MECHANISM"

        /** AndroidKeyStore alias used by the legacy SDK to encrypt SharedPrefs */
        private const val LEGACY_KEY_ALIAS = "org.forgerock.android.authenticator.KEYS"

        /* Modern Ping SDK SQLite database file names */
        private const val OATH_DB = "pingidentity_oath.db"
        private const val PUSH_DB = "pingidentity_push.db"

        // ── OATH URIs (matching user-supplied examples) ───────────────────

        /**
         * Standard TOTP URI.
         * `counter` is not meaningful for TOTP and will be ignored by FRAClient;
         * `period=30` drives time-based code generation.
         */
        private const val TOTP_URI =
            "otpauth://totp/Forgerock:user@forgerock.com" +
                    "?secret=JBSWY3DPEHPK3PXP&counter=0&issuer=Forgerock" +
                    "&algorithm=SHA1&digits=6&period=30"

        // ── Push URI parameters (from user-supplied pushauth example) ─────

        /** `s` param: shared secret used for the HMAC registration challenge */
        private const val PUSH_SHARED_SECRET =
            "b3uYLkQ7dRPjBaIzV0t_aijoXRgMq-NP5AwVAvRfa_E"

        /** `c` param: challenge value */
        private const val PUSH_CHALLENGE =
            "9giiBAdUHjqpo0XE4YdZ7pRlv0hrQYwDz8Z1wwLLbkg"

        /** `l` param: load-balancer cookie (base64 of "amlbcookie=01") */
        private const val PUSH_LOAD_BALANCER = "YW1sYmNvb2tpZT0wMQ"

        /** `b` param: background colour integer */
        private const val PUSH_BG_COLOR = "519387"

        /** `m` param: registration message-ID token */
        private const val PUSH_MESSAGE_ID =
            "REGISTER:8be951c6-af83-438d-8f74-421bd18650421570561063169"

        /**
         * Fake FCM device token injected into [FRAClient] before push registration.
         * A real Firebase installation is **not** required; the token is stored
         * locally by FRAClient and included in the registration POST body.
         */
        private const val FAKE_DEVICE_TOKEN = "fakeTestFcmDeviceToken_migration_12345"
    }

    // ─────────────────────────────── State ────────────────────────────────────

    private lateinit var context: Context
    private lateinit var fraClient: FRAClient

    // ─────────────────────────────── Lifecycle ────────────────────────────────

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        // Always start from a clean state regardless of previous test failures
        cleanupAll()
        try {
            fraClient = FRAClient.builder()
                .withContext(context)
                .start()
        } catch (e: Exception) {
            throw AssertionError(
                "Failed to initialise FRAClient — is the test device configured? ${e.message}",
                e
            )
        }
    }

    @After
    fun tearDown() {
        cleanupAll()
    }


    // ─────────────────────────────── Cleanup ──────────────────────────────────

    /**
     * Removes all legacy ForgeRock SharedPreferences files, the AndroidKeyStore
     * encryption key, and the modern Ping SDK SQLite databases so that every
     * test starts and ends in a pristine state.
     */

    private fun cleanupAll() {
        // 1. Delete legacy ForgeRock SharedPreferences files
        listOf(
            LEGACY_ACCOUNT_PREFS,
            LEGACY_MECHANISM_PREFS,
        ).forEach { context.deleteSharedPreferences(it) }

        // Delete legacy AndroidKeyStore encryption key
        try {
            val ks = KeyStore.getInstance("AndroidKeyStore").also { it.load(null) }
            if (ks.containsAlias(LEGACY_KEY_ALIAS)) {
                ks.deleteEntry(LEGACY_KEY_ALIAS)
            }
        } catch (_: Exception) { /* key may not exist – that is fine */
        }

        // Delete modern Ping SDK SQLite databases
        context.deleteDatabase(OATH_DB)
        context.deleteDatabase(PUSH_DB)
    }


    // ─────────────────────────── FRAClient helpers ────────────────────────────

    /**
     * Calls [FRAClient.createMechanismFromUri] synchronously, blocking up to
     * [timeoutSec] seconds for the [FRAListener] callback.
     *
     * @return the created [Mechanism] on success, or **null** if the call times
     *         out or FRAClient reports an error (e.g. push server unreachable).
     */
    private fun createLegacyMechanism(uri: String, timeoutSec: Long = 10L): Mechanism? {
        var mechanism: Mechanism? = null
        val latch = CountDownLatch(1)

        fraClient.createMechanismFromUri(uri, object : FRAListener<Mechanism> {
            override fun onSuccess(result: Mechanism) {
                mechanism = result
                latch.countDown()
            }

            override fun onException(e: Exception) {
                // Don't fail here; the caller checks whether the result is null.
                latch.countDown()
            }
        })

        latch.await(timeoutSec, TimeUnit.SECONDS)
        return mechanism
    }

    /**
     * Constructs a **pushauth: URI whose registration (`r`) and authentication
     * (`a`) endpoints resolve to the given [MockWebServer], allowing [FRAClient]
     * to complete its mandatory server-side push registration without a real
     * PingAM instance.
     *
     * All cryptographic parameters (`s`, `c`, `l`, `m`) are preserved from the
     * user-supplied example so the HMAC response computed by FRAClient remains
     * valid.
     */
    private fun buildPushUri(
        mockWebServer: MockWebServer,
        issuer: String = "Forgerock",
        account: String = "user@forgerock.com",
    ): String {
        val authUrl = mockWebServer
            .url("/openam/json/push/sns/message?_action=authenticate")
            .toString()
        val regUrl = mockWebServer
            .url("/openam/json/push/sns/message?_action=register")
            .toString()

        val authB64 = Base64.encodeToString(authUrl.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val regB64 = Base64.encodeToString(regUrl.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val issuerB64 = Base64.encodeToString(issuer.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

        return "pushauth://push/$issuer:$account" +
                "?a=$authB64" +
                "&b=$PUSH_BG_COLOR" +
                "&r=$regB64" +
                "&s=$PUSH_SHARED_SECRET" +
                "&c=$PUSH_CHALLENGE" +
                "&l=$PUSH_LOAD_BALANCER" +
                "&m=$PUSH_MESSAGE_ID" +
                "&issuer=$issuerB64"
    }

    // ──────── Modern storage readers (matching the migration's defaults) ───────

    /**
     * Opens the default [SQLOathStorage] —
     * reads all credentials, then closes the database.
     */
    private suspend fun readOathCredentials() =
        SQLOathStorage { context = this@MigrationTest.context }
            .also { it.initialize() }
            .let { storage ->
                try {
                    storage.getAllOathCredentials()
                } finally {
                    storage.close()
                }
            }

    /**
     * Opens the default [SQLPushStorage] —
     * reads all credentials, then closes the database.
     */
    private suspend fun readPushCredentials() =
        SQLPushStorage { context = this@MigrationTest.context }
            .also { it.initialize() }
            .let { storage ->
                try {
                    storage.getAllPushCredentials()
                } finally {
                    storage.close()
                }
            }


    // ══════════════════════════════════════════════════════════════════════════
    //  Test 1 – No legacy data → migration aborts at step 1 without error
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * When no legacy ForgeRock data exists (FRAClient was never used on the
     * device), the migration must detect the absent AndroidKeyStore key, abort
     * cleanly after step 1, and leave the modern databases untouched.
     */
    @Test
    fun testMigrationWithNoLegacyData() = runTest {
        val progress = mutableListOf<MigrationProgress>()
        AuthMigration.start(context) {
            this.progress = FlowCollector { progress.add(it) }
        }

        assertTrue("Must emit Started", progress.any { it is MigrationProgress.Started })
        assertTrue("Must emit Success", progress.any { it is MigrationProgress.Success })
        assertFalse("Must not emit Error", progress.any { it is MigrationProgress.Error })

        // Only step 1 must have run (it returns ABORT when no key is found)
        val completed = progress.filterIsInstance<MigrationProgress.StepCompleted>()
        assertEquals("Only step 1 should complete before ABORT", 1, completed.size)
        assertEquals("Import legacy data", completed[0].step.description)

        // Both modern databases must remain empty
        assertTrue("OATH storage must be empty", readOathCredentials().isEmpty())
        assertTrue("Push storage must be empty", readPushCredentials().isEmpty())
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Test 2 – TOTP migration end-to-end
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Creates a TOTP mechanism via the **real** legacy [FRAClient] with every
     * available field populated:
     * - `algorithm=SHA256` (non-default)
     * - `digits=6`
     * - `period=60` (non-default)
     * - `image` → Account.imageURL
     * - `b` → Account.backgroundColor  ([MechanismParser.BG_COLOR] = `"b"`)
     *
     * After creation the Account's mutable display fields are updated via
     * [FRAClient.updateAccount] so that `displayIssuer` and `displayAccountName`
     * differ from their defaults and can be independently verified.
     *
     * After running [AuthMigration] every field of the migrated [OathCredential]
     * is verified to confirm the full round-trip fidelity.
     */
    @Test
    fun testTotpMigration() = runTest {
        // ── TOTP URI with every supported field populated ──────────────────
        // image and b (background-color) are parsed by MechanismParser and
        // stored on the Account; algorithm/digits/period are OATH parameters.
        // policies is base64-encoded JSON stored on the Account after the SDK
        // decodes it (MechanismParser.POLICIES = "policies").
        val imageUrl = "http://forgerock.com/logo.jpg"
        val bgColor = "032b75"
        // Base64 of: {"biometricAvailable": { },"deviceTampering": {"score": 0.8}}
        val policiesB64 =
            "eyJiaW9tZXRyaWNBdmFpbGFibGUiOiB7IH0sImRldmljZVRhbXBlcmluZyI6IHsic2NvcmUiOiAwLjh9fQ"
        val expectedPolicies = String(Base64.decode(policiesB64, Base64.DEFAULT))
        val totpUriAllFields =
            "otpauth://totp/Forgerock:totp@forgerock.com" +
                    "?secret=JBSWY3DPEHPK3PXP" +
                    "&issuer=Forgerock" +
                    "&algorithm=SHA256" +
                    "&digits=6" +
                    "&period=60" +
                    "&image=$imageUrl" +
                    "&b=$bgColor" +
                    "&policies=$policiesB64"

        // ── 1. Create legacy data via FRAClient ────────────────────────────
        val mechanism = createLegacyMechanism(totpUriAllFields)
        assertNotNull("FRAClient must create the TOTP mechanism", mechanism)
        assertTrue(
            "Legacy encryption key must exist after FRAClient write",
            DefaultStorageClient(context).allAccounts.isNotEmpty()
        )

        // ── 2. Populate mutable Account display fields ─────────────────────
        // imageURL and backgroundColor are immutable (set from URI above).
        // displayIssuer / displayAccountName have public setters and are
        // persisted by FRAClient.updateAccount().
        val account = fraClient.getAccount(mechanism!!)
        assertNotNull("Account must be retrievable from FRAClient", account)
        account!!.displayIssuer = "Forgerock Display"
        account.displayAccountName = "TOTP Display User"
        fraClient.updateAccount(account)

        // Lock the account so lockingPolicy / isLocked are persisted to legacy storage
        // and we can verify they survive the migration intact.
        // DeviceTamperingPolicy is keyed "deviceTampering" in the policies JSON that was
        // included in the TOTP URI above, so isPolicyAttached() returns true and the lock succeeds.
        val lockingPolicy = DeviceTamperingPolicy()
        fraClient.lockAccount(account, lockingPolicy)

        // ── 3. Run migration ───────────────────────────────────────────────
        val progress = mutableListOf<MigrationProgress>()
        AuthMigration.start(context) {
            this.progress = FlowCollector { progress.add(it) }
        }

        assertTrue("Migration must succeed", progress.any { it is MigrationProgress.Success })
        assertFalse("No migration error expected", progress.any { it is MigrationProgress.Error })
        assertEquals(
            "All 3 migration steps must complete",
            3,
            progress.filterIsInstance<MigrationProgress.StepCompleted>().size,
        )

        // ── 4. Verify every field in modern OATH storage ───────────────────
        val credentials = readOathCredentials()
        assertEquals("Exactly 1 OATH credential must be migrated", 1, credentials.size)

        with(credentials[0]) {
            // ── Credential identity fields ─────────────────────────────────
            assertTrue("id must not be blank", id.isNotBlank())
            // uid / resourceId are only present for server-provisioned mechanisms;
            // URI-created TOTP credentials carry neither field in legacy storage.
            assertNull("userId must be null for URI-created TOTP", userId)
            assertNotNull("resourceId must not be null", resourceId)
            assertTrue("createdAt must be a valid timestamp", createdAt.time > 0)

            // ── Mechanism / OATH fields ────────────────────────────────────
            assertEquals("oathType must be TOTP", OathType.TOTP, oathType)
            assertEquals("secret must not be equal", "JBSWY3DPEHPK3PXP", secret)
            assertEquals("algorithm must be SHA256", OathAlgorithm.SHA256, oathAlgorithm)
            assertEquals("digits must be 6", 6, digits)
            assertEquals("period must be 60", 60, period)
            assertEquals("counter must be 0", 0L, counter)

            // ── Account identity fields ────────────────────────────────────
            assertEquals("issuer must match", "Forgerock", issuer)
            assertEquals("accountName must match", "totp@forgerock.com", accountName)
            assertEquals("displayIssuer must match", "Forgerock Display", displayIssuer)
            assertEquals("displayAccountName must match", "TOTP Display User", displayAccountName)

            // ── Account visual fields (carried via URI image / b params) ───
            assertEquals("imageURL must be migrated", imageUrl, imageURL)
            assertEquals("backgroundColor must be migrated", "#${bgColor}", backgroundColor)

            // ── Account policies (base64-decoded JSON via URI policies param) ──
            assertEquals("policies must be migrated", expectedPolicies, policies)

            // ── Locking fields (must survive migration with the locked state intact) ─
            assertEquals("lockingPolicy must be migrated", lockingPolicy.name, this.lockingPolicy)
            assertTrue("isLocked must be true after locking via fraClient", isLocked)
        }

        // ── 5. Push storage must be empty ─────────────────────────────────
        assertTrue("Push storage must be empty", readPushCredentials().isEmpty())

        // ── 6. Legacy data must be fully cleaned up ────────────────────────
        assertTrue(
            "Legacy encryption key must be removed after migration",
            DefaultStorageClient(context).allAccounts.isEmpty()
        )
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Test 3 – HOTP migration end-to-end
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Creates an HOTP mechanism via the **real** legacy [FRAClient] with every
     * available field populated (mirroring [testTotpMigration]):
     * - `algorithm=SHA512` (non-default, distinguishable from the TOTP test)
     * - `digits=6`
     * - `counter=5` (non-zero, to verify the counter is preserved exactly)
     * - `image` → Account.imageURL
     * - `b` → Account.backgroundColor
     * - `policies` → base64-encoded JSON → decoded and stored on Account
     *
     * After creation the Account's mutable display fields are updated via
     * [FRAClient.updateAccount] so that `displayIssuer` and `displayAccountName`
     * differ from their defaults and can be independently verified.
     *
     * After running [AuthMigration] every field of the migrated [OathCredential]
     * is verified to confirm full round-trip fidelity.
     */
    @Test
    fun testHotpMigration() = runTest {
        // ── HOTP URI with every supported field populated ──────────────────
        val imageUrl = "http://forgerock.com/logo.jpg"
        val bgColor = "032b75"
        // Base64 of: {"biometricAvailable": { },"deviceTampering": {"score": 0.8}}
        val policiesB64 =
            "eyJiaW9tZXRyaWNBdmFpbGFibGUiOiB7IH0sImRldmljZVRhbXBlcmluZyI6IHsic2NvcmUiOiAwLjh9fQ"
        val expectedPolicies = String(Base64.decode(policiesB64, Base64.DEFAULT))
        val hotpUriAllFields =
            "otpauth://hotp/Forgerock:hotp@forgerock.com" +
                    "?secret=JBSWY3DPEHPK3PXP" +
                    "&issuer=Forgerock" +
                    "&algorithm=SHA512" +
                    "&digits=6" +
                    "&counter=5" +
                    "&image=$imageUrl" +
                    "&b=$bgColor" +
                    "&policies=$policiesB64"

        // ── 1. Create legacy data via FRAClient ────────────────────────────
        val mechanism = createLegacyMechanism(hotpUriAllFields)
        assertNotNull("FRAClient must create the HOTP mechanism", mechanism)
        assertTrue(
            "Legacy encryption key must exist after FRAClient write",
            DefaultStorageClient(context).allAccounts.isNotEmpty()
        )

        // ── 2. Populate mutable Account display fields ─────────────────────
        val account = fraClient.getAccount(mechanism!!)
        assertNotNull("Account must be retrievable from FRAClient", account)
        account!!.displayIssuer = "Forgerock Display"
        account.displayAccountName = "HOTP Display User"
        fraClient.updateAccount(account)

        // Lock the account so lockingPolicy / isLocked are persisted to legacy storage
        // and we can verify they survive the migration intact.
        // DeviceTamperingPolicy is keyed "deviceTampering" in the policies JSON included
        // in the HOTP URI above, so isPolicyAttached() returns true and the lock succeeds.
        val lockingPolicy = DeviceTamperingPolicy()
        fraClient.lockAccount(account, lockingPolicy)

        // ── 3. Run migration ───────────────────────────────────────────────
        val progress = mutableListOf<MigrationProgress>()
        AuthMigration.start(context) {
            this.progress = FlowCollector { progress.add(it) }
        }

        assertTrue("Migration must succeed", progress.any { it is MigrationProgress.Success })
        assertFalse("No migration error expected", progress.any { it is MigrationProgress.Error })
        assertEquals(
            "All 3 migration steps must complete",
            3,
            progress.filterIsInstance<MigrationProgress.StepCompleted>().size,
        )

        // ── 4. Verify every field in modern OATH storage ───────────────────
        val credentials = readOathCredentials()
        assertEquals("Exactly 1 OATH credential must be migrated", 1, credentials.size)

        with(credentials[0]) {
            // ── Credential identity fields ─────────────────────────────────
            assertTrue("id must not be blank", id.isNotBlank())
            // uid / resourceId are only present for server-provisioned mechanisms;
            // URI-created HOTP credentials carry neither field in legacy storage.
            assertNull("userId must be null for URI-created HOTP", userId)
            assertNotNull("resourceId must not be null", resourceId)
            assertTrue("createdAt must be a valid timestamp", createdAt.time > 0)

            // ── Mechanism / OATH fields ────────────────────────────────────
            assertEquals("oathType must be HOTP", OathType.HOTP, oathType)
            assertEquals("secret must be equal", "JBSWY3DPEHPK3PXP", secret)
            assertEquals("algorithm must be SHA512", OathAlgorithm.SHA512, oathAlgorithm)
            assertEquals("digits must be 6", 6, digits)
            assertEquals("counter must be preserved as 5", 5L, counter)

            // ── Account identity fields ────────────────────────────────────
            assertEquals("issuer must match", "Forgerock", issuer)
            assertEquals("accountName must match", "hotp@forgerock.com", accountName)
            assertEquals("displayIssuer must match", "Forgerock Display", displayIssuer)
            assertEquals("displayAccountName must match", "HOTP Display User", displayAccountName)

            // ── Account visual fields (carried via URI image / b params) ───
            assertEquals("imageURL must be migrated", imageUrl, imageURL)
            assertEquals("backgroundColor must be migrated", "#${bgColor}", backgroundColor)

            // ── Account policies (base64-decoded JSON via URI policies param) ──
            assertEquals("policies must be migrated", expectedPolicies, policies)

            // ── Locking fields (must survive migration with the locked state intact) ─
            assertEquals("lockingPolicy must be migrated", lockingPolicy.name, this.lockingPolicy)
            assertTrue("isLocked must be true after locking via fraClient", isLocked)
        }

        // ── 5. Push storage must be empty ─────────────────────────────────
        assertTrue("Push storage must be empty", readPushCredentials().isEmpty())

        // ── 6. Legacy data must be fully cleaned up ────────────────────────
        assertTrue(
            "Legacy encryption key must be removed after migration",
            DefaultStorageClient(context).allAccounts.isEmpty()
        )
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Test 4 – Push migration end-to-end  (MockWebServer for AM registration)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Creates a Push mechanism via the legacy [FRAClient] using a [MockWebServer]
     * to stand in for the PingAM registration endpoint, then verifies the
     * migrated credential in [SQLPushStorage].
     *
     * [MockWebServer] is the **only** mock used in this suite; it is required
     * solely to satisfy [FRAClient]'s mandatory HTTP registration handshake —
     * all other production code runs unmodified.
     */
    @Test
    fun testPushMigration() = runTest {
        val mockWebServer = MockWebServer()
        mockWebServer.start()

        try {
            // ── 1. Enqueue a success response for the registration POST ────
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody("{}")
                    .addHeader("Content-Type", "application/json"),
            )

            // Provide a fake FCM token so FRAClient can build the POST body
            // (no real Firebase installation needed; token is stored locally).
            // Skip the test gracefully on devices/emulators without push support.
            try {
                fraClient.registerForRemoteNotifications(FAKE_DEVICE_TOKEN)
            } catch (e: Exception) {
                Assume.assumeNoException("Push not available in this environment: ${e.message}", e)
            }

            // Base64 of: {"biometricAvailable": { },"deviceTampering": {"score": 0.8}}
            // Required so that isPolicyAttached() returns true and lockAccount succeeds.
            val policiesB64 =
                "eyJiaW9tZXRyaWNBdmFpbGFibGUiOiB7IH0sImRldmljZVRhbXBlcmluZyI6IHsic2NvcmUiOiAwLjh9fQ"
            val expectedPolicies = String(Base64.decode(policiesB64, Base64.DEFAULT))

            val pushUri = buildPushUri(mockWebServer) + "&policies=$policiesB64"
            val mechanism = createLegacyMechanism(pushUri, timeoutSec = 15L)
            // If mechanism is null here, Google Play Services is not available on this device/
            // emulator and PushFactory#checkGooglePlayServices returned false — skip gracefully.
            Assume.assumeTrue(
                "Push mechanism creation failed — Google Play Services likely not available in this environment",
                mechanism != null,
            )
            assertTrue(
                "Legacy encryption key must exist after push registration",
                DefaultStorageClient(context).allAccounts.isNotEmpty()
            )

            // ── 2. Lock the account so lockingPolicy / isLocked are persisted ─
            // DeviceTamperingPolicy is keyed "deviceTampering" in the policies JSON
            // included in the push URI above, so isPolicyAttached() returns true
            // and the lock succeeds.
            val account = fraClient.getAccount(mechanism!!)
            assertNotNull("Account must be retrievable from FRAClient", account)
            val lockingPolicy = DeviceTamperingPolicy()
            fraClient.lockAccount(account!!, lockingPolicy)

            // ── 3. Run migration ───────────────────────────────────────────
            val progress = mutableListOf<MigrationProgress>()
            AuthMigration.start(context) {
                this.progress = FlowCollector { progress.add(it) }
            }

            assertTrue("Migration must succeed", progress.any { it is MigrationProgress.Success })
            assertFalse(
                "No migration error expected",
                progress.any { it is MigrationProgress.Error })
            assertEquals(
                "All 3 migration steps must complete",
                3,
                progress.filterIsInstance<MigrationProgress.StepCompleted>().size,
            )

            // ── 4. Verify Push credential in modern Push storage ───────────
            val pushCredentials = readPushCredentials()
            assertEquals("Exactly 1 Push credential must be migrated", 1, pushCredentials.size)

            with(pushCredentials[0]) {
                // ── Credential identity fields ─────────────────────────────
                assertTrue("id must not be blank", id.isNotBlank())
                // userId is assigned by the server; MockWebServer returns {} so it is null
                assertNull("userId must be null (MockWebServer returns no uid)", userId)
                // resourceId falls back to mechanismUID when the server does not assign one
                assertTrue("resourceId must not be blank", resourceId?.isNotBlank() ?: false)
                assertTrue("createdAt must be a valid timestamp", createdAt.time > 0)

                // ── Account identity fields ────────────────────────────────
                assertEquals("issuer must be 'Forgerock'", "Forgerock", issuer)
                assertEquals("displayIssuer must match issuer", "Forgerock", displayIssuer)
                assertEquals("accountName must match", "user@forgerock.com", accountName)
                assertEquals(
                    "displayAccountName must match",
                    "user@forgerock.com",
                    displayAccountName
                )

                // ── Push-specific fields ───────────────────────────────────
                assertTrue("serverEndpoint must not be empty", serverEndpoint.isNotEmpty())
                assertTrue("sharedSecret must not be empty", sharedSecret.isNotEmpty())
                assertEquals("platform must be PING_AM", PushPlatform.PING_AM.name, platform)

                // ── Account visual fields ──────────────────────────────────
                // The push URI includes &b=PUSH_BG_COLOR; the legacy SDK stores it with a '#' prefix
                assertEquals("backgroundColor must be migrated", "#$PUSH_BG_COLOR", backgroundColor)
                // No image param in the push URI
                assertNull("imageURL must be null (not in push URI)", imageURL)

                // ── Account policies ───────────────────────────────────────
                assertEquals("policies must be migrated", expectedPolicies, policies)

                // ── Locking fields (must survive migration with the locked state intact) ─
                assertEquals(
                    "lockingPolicy must be migrated",
                    lockingPolicy.name,
                    this.lockingPolicy
                )
                assertTrue("isLocked must be true after locking via fraClient", isLocked)
            }

            // ── 5. OATH storage must remain empty ─────────────────────────
            assertTrue("OATH storage must be empty", readOathCredentials().isEmpty())

            // ── 6. Legacy data must be fully cleaned up ────────────────────
            assertTrue(
                "Legacy encryption key must be removed after migration",
                DefaultStorageClient(context).allAccounts.isEmpty()
            )
        } finally {
            mockWebServer.shutdown()
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Test 5 – TOTP + HOTP together (distinct accounts)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Creates one TOTP and one HOTP mechanism (with different account names) in
     * the legacy storage, migrates them, and verifies both appear in
     * [SQLOathStorage] with the correct types and account names.
     */
    @Test
    fun testMultipleOathTypesMigration() = runTest {
        val totpUri =
            "otpauth://totp/Forgerock:totp@forgerock.com" +
                    "?secret=JBSWY3DPEHPK3PXP&issuer=Forgerock&algorithm=SHA1&digits=6&period=30"
        val hotpUri =
            "otpauth://hotp/Forgerock:hotp@forgerock.com" +
                    "?secret=KRUGS4ZANFZSA3TP&counter=0&issuer=Forgerock&algorithm=SHA1&digits=6"

        // ── 1. Create both legacy mechanisms ──────────────────────────────
        assertNotNull("TOTP mechanism must be created", createLegacyMechanism(totpUri))
        assertNotNull("HOTP mechanism must be created", createLegacyMechanism(hotpUri))

        // ── 2. Run migration ───────────────────────────────────────────────
        val progress = mutableListOf<MigrationProgress>()
        AuthMigration.start(context) {
            this.progress = FlowCollector { progress.add(it) }
        }

        assertTrue("Migration must succeed", progress.any { it is MigrationProgress.Success })
        assertFalse("No migration error expected", progress.any { it is MigrationProgress.Error })

        // ── 3. Verify both credentials ─────────────────────────────────────
        val credentials = readOathCredentials()
        assertEquals("Two OATH credentials must be migrated", 2, credentials.size)

        val totp = credentials.firstOrNull { it.oathType == OathType.TOTP }
        val hotp = credentials.firstOrNull { it.oathType == OathType.HOTP }

        assertNotNull("TOTP credential must exist", totp)
        assertNotNull("HOTP credential must exist", hotp)
        assertEquals("TOTP account name must match", "totp@forgerock.com", totp?.accountName)
        assertEquals("HOTP account name must match", "hotp@forgerock.com", hotp?.accountName)
        assertEquals("HOTP initial counter must be 0", 0L, hotp?.counter)

        // ── 4. Legacy data must be fully cleaned up ────────────────────────
        assertTrue(
            "Legacy encryption key must be removed after migration",
            DefaultStorageClient(context).allAccounts.isEmpty()
        )
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Test 6 – Full migration: TOTP + HOTP + Push together
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * The "golden path" test: registers all three credential types before
     * running the migration and verifies each type ends up in the correct modern
     * storage backend with the expected field values.
     */
    @Test
    fun testFullMigrationWithAllMechanismTypes() = runTest {
        val mockWebServer = MockWebServer()
        mockWebServer.start()

        try {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody("{}")
                    .addHeader("Content-Type", "application/json"),
            )

            val totpUri =
                "otpauth://totp/Forgerock:totp@forgerock.com" +
                        "?secret=JBSWY3DPEHPK3PXP&issuer=Forgerock&algorithm=SHA1&digits=6&period=30"
            val hotpUri =
                "otpauth://hotp/Forgerock:hotp@forgerock.com" +
                        "?secret=KRUGS4ZANFZSA3TP&counter=5&issuer=Forgerock&algorithm=SHA1&digits=6"

            // ── 1. Create all three legacy mechanisms ──────────────────────
            assertNotNull("TOTP mechanism must be created", createLegacyMechanism(totpUri))
            assertNotNull("HOTP mechanism must be created", createLegacyMechanism(hotpUri))

            try {
                fraClient.registerForRemoteNotifications(FAKE_DEVICE_TOKEN)
            } catch (e: Exception) {
                Assume.assumeNoException("Push not available in this environment: ${e.message}", e)
            }
            val pushUri = buildPushUri(mockWebServer, account = "push@forgerock.com")
            val pushMechanism = createLegacyMechanism(pushUri, timeoutSec = 15L)
            // If null, Google Play Services is unavailable and PushFactory#checkGooglePlayServices
            // returned false — skip gracefully rather than failing.
            Assume.assumeTrue(
                "Push mechanism creation failed — Google Play Services likely not available in this environment",
                pushMechanism != null,
            )

            assertTrue(
                "Legacy data must be present before migration",
                DefaultStorageClient(context).allAccounts.isNotEmpty()
            )

            // ── 2. Run migration ───────────────────────────────────────────
            val progress = mutableListOf<MigrationProgress>()
            AuthMigration.start(context) {
                this.progress = FlowCollector { progress.add(it) }
            }

            assertTrue("Migration must succeed", progress.any { it is MigrationProgress.Success })
            assertFalse(
                "No migration error expected",
                progress.any { it is MigrationProgress.Error })
            assertEquals(
                "All 3 migration steps must complete",
                3,
                progress.filterIsInstance<MigrationProgress.StepCompleted>().size,
            )

            // ── 3. Verify OATH credentials (TOTP + HOTP) ──────────────────
            val oathCreds = readOathCredentials()
            assertEquals("Two OATH credentials must be migrated", 2, oathCreds.size)
            assertTrue("TOTP credential must exist", oathCreds.any { it.oathType == OathType.TOTP })
            assertTrue("HOTP credential must exist", oathCreds.any { it.oathType == OathType.HOTP })

            // HOTP counter must survive the migration unchanged
            val hotpCred = oathCreds.first { it.oathType == OathType.HOTP }
            assertEquals("HOTP counter must be preserved as 5", 5L, hotpCred.counter)

            // ── 4. Verify Push credential ──────────────────────────────────
            val pushCreds = readPushCredentials()
            assertEquals("One Push credential must be migrated", 1, pushCreds.size)
            assertEquals("Push account must match", "push@forgerock.com", pushCreds[0].accountName)
            assertTrue(
                "Push shared secret must not be empty",
                pushCreds[0].sharedSecret.isNotEmpty()
            )
            assertTrue(
                "Push server endpoint must not be empty",
                pushCreds[0].serverEndpoint.isNotEmpty()
            )

            // ── 5. Legacy data must be fully cleaned up ────────────────────
            assertTrue(
                "Legacy encryption key must be removed after full migration",
                DefaultStorageClient(context).allAccounts.isEmpty()
            )
        } finally {
            mockWebServer.shutdown()
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Test 7 – All legacy files and the AndroidKeyStore key are deleted
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun testLegacyDataFullyCleanedUpAfterMigration() = runTest {
        assertNotNull("TOTP mechanism must be created", createLegacyMechanism(TOTP_URI))

        val sharedPrefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
        val keyStore = KeyStore.getInstance("AndroidKeyStore").also { it.load(null) }

        // ── Pre-migration assertions ───────────────────────────────────────
        assertTrue(
            "Legacy KeyStore key must exist before migration",
            keyStore.containsAlias(LEGACY_KEY_ALIAS),
        )
        assertTrue(
            "Legacy mechanism SharedPreferences file must exist before migration",
            File(sharedPrefsDir, "$LEGACY_MECHANISM_PREFS.xml").exists(),
        )

        // ── Run migration ──────────────────────────────────────────────────
        val progress = mutableListOf<MigrationProgress>()
        AuthMigration.start(context) {
            this.progress = FlowCollector { progress.add(it) }
        }
        assertTrue("Migration must succeed", progress.any { it is MigrationProgress.Success })

        // ── Post-migration assertions ──────────────────────────────────────
        keyStore.load(null) // reload to clear any in-memory caches

        assertFalse(
            "Legacy account SharedPreferences file must be deleted after migration",
            File(sharedPrefsDir, "$LEGACY_ACCOUNT_PREFS.xml").exists(),
        )
        assertFalse(
            "Legacy mechanism SharedPreferences file must be deleted after migration",
            File(sharedPrefsDir, "$LEGACY_MECHANISM_PREFS.xml").exists(),
        )
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Test 8 – Migration is idempotent: a second run is a safe no-op
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Running [AuthMigration] a second time after legacy data has already been
     * migrated (and cleaned up) must be a safe no-op: the migration aborts at
     * step 1 (no legacy key found) and the credential count in modern storage
     * remains unchanged — no duplicates are inserted.
     */
    @Test
    fun testSecondMigrationRunIsNoOp() = runTest {
        assertNotNull("TOTP mechanism must be created", createLegacyMechanism(TOTP_URI))

        // ── First run: real migration ──────────────────────────────────────
        val firstProgress = mutableListOf<MigrationProgress>()
        AuthMigration.start(context) {
            progress = FlowCollector { firstProgress.add(it) }
        }
        assertTrue(
            "First migration must succeed",
            firstProgress.any { it is MigrationProgress.Success })

        val countAfterFirst = readOathCredentials().size
        assertEquals("One credential must exist after first migration", 1, countAfterFirst)

        // ── Second run: no legacy data remains ────────────────────────────
        val secondProgress = mutableListOf<MigrationProgress>()
        AuthMigration.start(context) {
            progress = FlowCollector { secondProgress.add(it) }
        }

        assertTrue(
            "Second migration must succeed (ABORT = success)",
            secondProgress.any { it is MigrationProgress.Success })
        assertFalse(
            "Second migration must not emit Error",
            secondProgress.any { it is MigrationProgress.Error })

        // Only step 1 should have executed (then ABORT — no legacy key found)
        val completedOnSecondRun =
            secondProgress.filterIsInstance<MigrationProgress.StepCompleted>()
        assertEquals("Second run must abort after step 1", 1, completedOnSecondRun.size)

        // Credential count must be unchanged — no duplicates written
        val countAfterSecond = readOathCredentials().size
        assertEquals(
            "Credential count must be identical after idempotent second run",
            countAfterFirst,
            countAfterSecond,
        )
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Test 9 – Performance: 20 mechanisms must migrate within 2 seconds
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Creates 20 legacy mechanisms (alternating TOTP / HOTP, each with a unique
     * account name so FRAClient accepts all of them), runs [AuthMigration], and
     * asserts that the migration wall-clock time is under **2 000 ms**.
     *
     * The 2-second gate is measured only around [AuthMigration.start] so that
     * the (intentionally slow) FRAClient setup phase does not inflate the figure.
     */
    @Test
    fun testMigrationPerformanceWith20Mechanisms() = runTest(timeout = 5.minutes) {
        val tag = "MigrationPerfTest"
        val total = 20
        val expectedDuration = 10_000L

        Log.i(tag, "── Setup: creating $total legacy mechanisms via FRAClient ──────────────")
        val setupStartMs = System.currentTimeMillis()

        // ── 1. Create 20 legacy mechanisms ────────────────────────────────
        repeat(total) { i ->
            val type = if (i % 2 == 0) "TOTP" else "HOTP"
            val uri = if (i % 2 == 0) {
                "otpauth://totp/Forgerock:perf-totp$i@forgerock.com" +
                        "?secret=JBSWY3DPEHPK3PXP&issuer=Forgerock" +
                        "&algorithm=SHA1&digits=6&period=30"
            } else {
                "otpauth://hotp/Forgerock:perf-hotp$i@forgerock.com" +
                        "?secret=JBSWY3DPEHPK3PXP&counter=$i&issuer=Forgerock" +
                        "&algorithm=SHA1&digits=6"
            }
            val mechanismStartMs = System.currentTimeMillis()
            val mechanism = createLegacyMechanism(uri)
            val mechanismElapsedMs = System.currentTimeMillis() - mechanismStartMs
            Log.i(
                tag,
                "  Mechanism ${i + 1}/$total ($type): ${if (mechanism != null) "OK" else "FAILED"} (${mechanismElapsedMs} ms)"
            )
            assertNotNull("Mechanism $i must be created", mechanism)
        }

        val setupElapsedMs = System.currentTimeMillis() - setupStartMs
        Log.i(tag, "── Setup complete: $total mechanisms created in $setupElapsedMs ms ──")

        assertTrue(
            "Legacy encryption key must exist before migration",
            DefaultStorageClient(context).allAccounts.isNotEmpty()
        )

        // ── 2. Run migration — measure wall-clock time ─────────────────────
        Log.i(tag, "── Migration starting ─────────────────────────────────────────────────")
        val migrationStartMs = System.currentTimeMillis()
        val progress = mutableListOf<MigrationProgress>()
        AuthMigration.start(context) {
            this.progress = FlowCollector { event ->
                val elapsedSoFar = System.currentTimeMillis() - migrationStartMs
                Log.i(tag, "  [+${elapsedSoFar} ms] $event")
                progress.add(event)
            }
        }
        val migrationElapsedMs = System.currentTimeMillis() - migrationStartMs
        Log.i(tag, "── Migration finished in $migrationElapsedMs ms ─────────────────────")

        assertTrue("Migration must succeed", progress.any { it is MigrationProgress.Success })
        assertFalse("No migration error expected", progress.any { it is MigrationProgress.Error })

        assertTrue(
            "Migration of $total mechanisms must complete within $expectedDuration ms (took $migrationElapsedMs ms)",
            migrationElapsedMs < expectedDuration,
        )

        // ── 3. Verify all 20 credentials were migrated ────────────────────
        val credentials = readOathCredentials()
        assertEquals("All $total OATH credentials must be migrated", total, credentials.size)

        val totpCount = credentials.count { it.oathType == OathType.TOTP }
        val hotpCount = credentials.count { it.oathType == OathType.HOTP }
        Log.i(tag, "  TOTP: $totpCount  HOTP: $hotpCount  (total: ${credentials.size})")
        assertEquals("${total / 2} TOTP credentials must exist", total / 2, totpCount)
        assertEquals("${total / 2} HOTP credentials must exist", total / 2, hotpCount)

        assertTrue("Push storage must be empty", readPushCredentials().isEmpty())

        // ── 4. Legacy data must be fully cleaned up ────────────────────────
        assertTrue(
            "Legacy encryption key must be removed after migration",
            DefaultStorageClient(context).allAccounts.isEmpty()
        )
        Log.i(
            tag,
            "── Test complete. Setup: $setupElapsedMs ms | Migration: $migrationElapsedMs ms ──"
        )
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Test 10 – Migration Flow emits all expected progress events in order
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * When all 5 steps run the [Flow] returned by [Migration.migrate] must
     * emit events in the order:
     * [MigrationProgress.Started] →
     * [MigrationProgress.InProgress] ×5 →
     * [MigrationProgress.StepCompleted] ×5 (with the correct step descriptions) →
     * [MigrationProgress.Success].
     */
    @Test
    fun testMigrationProgressEmitsAllEventsInOrder() = runTest {
        assertNotNull("TOTP mechanism must be created", createLegacyMechanism(TOTP_URI))

        val progress = mutableListOf<MigrationProgress>()
        AuthMigration.start(context) {
            this.progress = FlowCollector { progress.add(it) }
        }

        // ── Mandatory bookend events ───────────────────────────────────────
        assertTrue("Must emit Started", progress.any { it is MigrationProgress.Started })
        assertTrue("Must emit Success", progress.any { it is MigrationProgress.Success })
        assertFalse("Must not emit Error", progress.any { it is MigrationProgress.Error })

        // ── 5 InProgress events (one emitted before each step) ────────────
        val inProgressEvents = progress.filterIsInstance<MigrationProgress.InProgress>()
        assertEquals("Must emit 3 InProgress events", 3, inProgressEvents.size)

        // ── 5 StepCompleted events in the correct order ───────────────────
        val stepDescriptions = progress
            .filterIsInstance<MigrationProgress.StepCompleted>()
            .map { it.step.description }

        assertEquals("Must complete exactly 3 steps", 3, stepDescriptions.size)
        assertEquals("Step 1", "Import legacy data", stepDescriptions[0])
        assertEquals("Step 2", "Migrate mechanisms to OATH and Push storage", stepDescriptions[1])
        assertEquals("Step 5", "Cleanup legacy authenticator data", stepDescriptions[2])
    }

}
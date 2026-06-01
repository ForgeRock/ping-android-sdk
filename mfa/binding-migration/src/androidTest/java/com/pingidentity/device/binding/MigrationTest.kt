/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.binding

import android.content.Context
import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pingidentity.android.ContextProvider
import com.pingidentity.device.binding.authenticator.exception.DeviceNotRegisteredException
import com.pingidentity.device.binding.journey.DeviceSigningVerifierCallback
import com.pingidentity.device.binding.migration.BindingMigration
import com.pingidentity.device.binding.migration.LegacyLocalDeviceBindingRepository
import com.pingidentity.device.binding.migration.ORG_FORGEROCK_V_1_DEVICE_REPO
import com.pingidentity.journey.plugin.Journey
import com.pingidentity.logger.Logger
import com.pingidentity.orchestrate.WorkflowConfig
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.forgerock.android.auth.callback.DeviceBindingCallback
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore
import java.security.MessageDigest


@RunWith(AndroidJUnit4::class)
class MigrationTest {

    companion object {
        /**
         * userId from the DeviceBindingCallback JSON — used as the AndroidKeyStore alias
         * for the RSA key pair created by [DeviceBindingCallback.bind].
         */
        private const val USER_ID = "id=demo,ou=user,dc=openam,dc=forgerock,dc=org"

        /**
         * Replicates [org.forgerock.android.auth.CryptoKey.getKeyAlias]:
         * SHA-256 hash of [userId], Base64-encoded with NO_WRAP | NO_PADDING | URL_SAFE.
         *
         * This is the actual AndroidKeyStore alias used by the legacy SDK —
         * the raw [userId] is **never** used as the alias directly.
         */
        private fun keyAliasFor(userId: String): String {
            val hash = MessageDigest.getInstance("SHA-256").digest(userId.toByteArray())
            return Base64.encodeToString(
                hash,
                Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE
            )
        }
    }

    private lateinit var context: Context
    private lateinit var mockJourneyConfig: WorkflowConfig
    private lateinit var mockLogger: Logger
    private lateinit var mockJourney: Journey


    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        ContextProvider.init(context)
        mockJourney = mockk(relaxed = true)
        mockLogger = mockk(relaxed = true)
        mockJourneyConfig = mockk(relaxed = true)
        every { mockJourney.config } returns mockJourneyConfig
        every { mockJourneyConfig.logger } returns mockLogger
    }

    @After
    fun tearDown() {
        // Remove all data written by DeviceBindingCallback.bind():
        //   1. RSA key pair in AndroidKeyStore  (alias = userId)
        //   2. EncryptedSharedPreferences master key  (alias = ORG_FORGEROCK_V_1_DEVICE_REPO)
        //   3. EncryptedSharedPreferences file that holds the user-key metadata
        try {
            val ks = KeyStore.getInstance("AndroidKeyStore").also { it.load(null) }
            if (ks.containsAlias(keyAliasFor(USER_ID))) ks.deleteEntry(keyAliasFor(USER_ID))
            if (ks.containsAlias(ORG_FORGEROCK_V_1_DEVICE_REPO)) ks.deleteEntry(
                ORG_FORGEROCK_V_1_DEVICE_REPO
            )
        } catch (_: Exception) { /* key may not exist — that is fine */
        }

        context.deleteSharedPreferences(ORG_FORGEROCK_V_1_DEVICE_REPO)

        // 4. Remove the user key migrated to the new UserKeysStorage
        runBlocking { UserKeysStorage().deleteByUserId(USER_ID) }
    }

    /**
     * Verifies that the **legacy** [DeviceBindingCallback] (ForgeRock SDK) can successfully
     * bind a device using [authenticationType] = NONE.
     *
     * Source:
     * https://github.com/ForgeRock/forgerock-android-sdk/tree/develop/forgerock-auth/src/main/java/org/forgerock/android/auth/callback
     */
    @Test
    fun testMigration() = runTest {
        val rawContent = """
            {
              "type": "DeviceBindingCallback",
              "output": [
                {"name": "userId",             "value": "id=demo,ou=user,dc=openam,dc=forgerock,dc=org"},
                {"name": "username",           "value": "demo"},
                {"name": "authenticationType", "value": "NONE"},
                {"name": "challenge",          "value": "CS3+g40VkHXx+dN7rpnJKhrEAvwZaYgbaXoEcpO5twM="},
                {"name": "title",              "value": "Authentication required"},
                {"name": "subtitle",           "value": "Cryptography device binding"},
                {"name": "description",        "value": "Please complete with biometric to proceed"},
                {"name": "timeout",            "value": 60},
                {"name": "attestation",        "value": false}
              ],
              "input": [
                {"name": "IDToken1jws",         "value": ""},
                {"name": "IDToken1deviceName",  "value": ""},
                {"name": "IDToken1deviceId",    "value": ""},
                {"name": "IDToken1clientError", "value": ""}
              ]
            }
        """.trimIndent()

        val legacyDeviceBindingCallback = DeviceBindingCallback(JSONObject(rawContent), 0)
        legacyDeviceBindingCallback.bind(context)

        // ── Assert successful bind ─────────────────────────────────────────

        // userId / userName must be read back correctly
        assertEquals("userId must match", USER_ID, legacyDeviceBindingCallback.userId)
        assertEquals("userName must match", "demo", legacyDeviceBindingCallback.userName)

        // JWS (index 0) must be a non-empty JWT string
        val jws = legacyDeviceBindingCallback.getInputValue(0) as? String
        assertNotNull("JWS must not be null after a successful bind", jws)
        assertTrue("JWS must not be blank after a successful bind", jws!!.isNotBlank())

        // Device ID (index 2) must be populated
        val deviceId = legacyDeviceBindingCallback.getInputValue(2) as? String
        assertNotNull("Device ID must be set after bind", deviceId)
        assertTrue("Device ID must not be blank", deviceId!!.isNotBlank())

        // Client error (index 3) must be absent on success
        val clientError = legacyDeviceBindingCallback.getInputValue(3) as? String
        assertTrue(
            "Client error must be black after a successful bind",
            clientError.isNullOrBlank()
        )

        // The RSA key pair must exist in the AndroidKeyStore under the hashed userId alias.
        // CryptoKey.getKeyAlias() = Base64(SHA-256(userId), NO_WRAP | NO_PADDING | URL_SAFE)
        val ks = KeyStore.getInstance("AndroidKeyStore").also { it.load(null) }
        assertTrue(
            "RSA key must be present in AndroidKeyStore",
            ks.containsAlias(keyAliasFor(USER_ID))
        )

        // ── Assert sign fails BEFORE migration ────────────────────────────
        // The legacy key exists in AndroidKeyStore but has not yet been migrated
        // to the new UserKeysStorage. DeviceSigningVerifierCallback cannot find
        // a UserKey record → must fail with DeviceNotRegisteredException.
        val preMigrationInput = Json.parseToJsonElement(
            """
            {
              "type": "DeviceSigningVerifierCallback",
              "output": [
                {"name": "userId",       "value": "id=demo,ou=user,dc=openam,dc=forgerock,dc=org"},
                {"name": "challenge",    "value": "CS3+g40VkHXx+dN7rpnJKhrEAvwZaYgbaXoEcpO5twM="},
                {"name": "title",        "value": "Authentication required"},
                {"name": "subtitle",     "value": "Cryptography device binding"},
                {"name": "description",  "value": "Please complete with biometric to proceed"},
                {"name": "timeout",      "value": 60}
              ],
              "input": [
                {"name": "IDToken1jws",         "value": ""},
                {"name": "IDToken1clientError",  "value": ""}
              ]
            }
            """.trimIndent()
        ) as JsonObject

        val preMigrationCallback = DeviceSigningVerifierCallback().apply {
            journey = mockJourney
        }
        preMigrationCallback.init(preMigrationInput)

        val preMigrationResult = preMigrationCallback.sign()

        assertFalse(
            "sign() must fail before migration",
            preMigrationResult.isSuccess,
        )
        assertTrue(
            "sign() must throw DeviceNotRegisteredException before migration",
            preMigrationResult.exceptionOrNull() is DeviceNotRegisteredException,
        )

        // ── Run migration ──────────────────────────────────────────────────
        BindingMigration.start(context)


        // Legacy repository must be fully cleaned up by step 2
        assertFalse(
            "Legacy repository must not exist after migration",
            LegacyLocalDeviceBindingRepository(context).exists(context),
        )

        // UserKey must have been migrated to the new UserKeysStorage
        val migratedKey = UserKeysStorage().findByUserId(USER_ID)
        assertNotNull("Migrated key must be present in UserKeysStorage", migratedKey)
        assertEquals("Migrated userId must match", USER_ID, migratedKey!!.userId)


        val input = Json.parseToJsonElement(
            """
            {
              "type": "DeviceSigningVerifierCallback",
              "output": [
                {"name": "userId",       "value": "id=demo,ou=user,dc=openam,dc=forgerock,dc=org"},
                {"name": "challenge",    "value": "CS3+g40VkHXx+dN7rpnJKhrEAvwZaYgbaXoEcpO5twM="},
                {"name": "title",        "value": "Authentication required"},
                {"name": "subtitle",     "value": "Cryptography device binding"},
                {"name": "description",  "value": "Please complete with biometric to proceed"},
                {"name": "timeout",      "value": 60}
              ],
              "input": [
                {"name": "IDToken1jws",         "value": ""},
                {"name": "IDToken1clientError",  "value": ""}
              ]
            }
            """.trimIndent()
        ) as JsonObject

        val deviceSigningVerifierCallback = DeviceSigningVerifierCallback().apply {
            journey = mockJourney
        }
        deviceSigningVerifierCallback.init(input)

        val signResult = deviceSigningVerifierCallback.sign()

        assertTrue("After migrate sign must be success", signResult.isSuccess)

        // JWS must be a non-blank, well-formed JWT (header.payload.signature)
        val signedJws = signResult.getOrNull()
        assertNotNull("Signed JWS must not be null", signedJws)
        assertTrue("Signed JWS must not be blank", signedJws!!.isNotBlank())
        assertEquals(
            "Signed JWS must have 3 JWT parts separated by '.'",
            3,
            signedJws.split(".").size
        )
    }

}
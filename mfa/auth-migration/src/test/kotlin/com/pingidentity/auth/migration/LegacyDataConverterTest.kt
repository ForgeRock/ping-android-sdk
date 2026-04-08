/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.auth.migration

import com.pingidentity.logger.Logger
import com.pingidentity.logger.STANDARD
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.forgerock.android.auth.Account
import org.forgerock.android.auth.Mechanism
import org.forgerock.android.auth.StorageClient
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LegacyDataConverterTest {

    @BeforeTest
    fun setUp() {
        // Reset logger to a no-op so warning/error logs don't interfere
        AuthMigration.logger = Logger.STANDARD
    }

    // ── buildMechanismsList — empty / short-circuit ───────────────────────────

    @Test
    fun `returns empty list when both maps are empty`() {
        val result = LegacyDataConverter.buildMechanismsList(emptyMap(), emptyMap())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `returns empty list when mechanisms map is empty`() {
        val accounts = mapOf("Google-user@example.com" to accountJson("Google-user@example.com", "Google", accountName = "user@example.com"))
        val result = LegacyDataConverter.buildMechanismsList(emptyMap(), accounts)
        assertTrue(result.isEmpty())
    }

    // ── buildMechanismsList — mandatory field validation ──────────────────────

    @Test
    fun `skips mechanism missing issuer field`() {
        val json = """{"accountName":"user@example.com","mechanismUID":"uid-001","type":"totp","secret":"S"}"""
        val result = LegacyDataConverter.buildMechanismsList(mapOf("mech-1" to json), emptyMap())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `skips mechanism missing accountName field`() {
        val json = """{"issuer":"Google","mechanismUID":"uid-001","type":"totp","secret":"S"}"""
        val result = LegacyDataConverter.buildMechanismsList(mapOf("mech-1" to json), emptyMap())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `skips mechanism missing mechanismUID field`() {
        val json = """{"issuer":"Google","accountName":"user@example.com","type":"totp","secret":"S"}"""
        val result = LegacyDataConverter.buildMechanismsList(mapOf("mech-1" to json), emptyMap())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `skips mechanism when no associated account is found`() {
        val mechanisms = mapOf("mech-1" to totpMechanismJson())
        // accounts map is empty — no "Google-user@example.com" key
        val result = LegacyDataConverter.buildMechanismsList(mechanisms, emptyMap())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `skips mechanism when account key does not match issuer-accountName format`() {
        val mechanisms = mapOf("mech-1" to totpMechanismJson(issuer = "Google", accountName = "user@example.com"))
        // Wrong key — uses "google-user@example.com" (lower-case), should not match "Google-user@example.com"
        val accounts = mapOf("google-user@example.com" to accountJson("google-user@example.com", "google", accountName = "user@example.com"))
        val result = LegacyDataConverter.buildMechanismsList(mechanisms, accounts)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `skips mechanism with invalid JSON and continues with others`() {
        val mechanisms = mapOf(
            "bad-mech" to "NOT_VALID_JSON{{{",
            "good-mech" to totpMechanismJson(issuer = "Google", accountName = "user@example.com", mechanismUID = "totp-uid-001")
        )
        val accounts = mapOf(
            "Google-user@example.com" to accountJson("Google-user@example.com", "Google", accountName = "user@example.com")
        )
        val result = LegacyDataConverter.buildMechanismsList(mechanisms, accounts)
        // Only the valid mechanism should be returned
        assertEquals(1, result.size)
        assertEquals("Google", result[0].issuer)
    }

    // ── buildMechanismsList — TOTP mechanism

    @Test
    fun `correctly maps all TOTP mechanism fields`() {
        val testTime = 1_700_000_000_000L
        val mechanisms = mapOf(
            "totp-uid-001" to totpMechanismJson(
                id = "Google-user@example.com-totp",
                issuer = "Google",
                accountName = "user@example.com",
                mechanismUID = "totp-uid-001",
                secret = "JBSWY3DPEHPK3PXP",
                oathType = "TOTP",
                algorithm = "sha1",
                digits = 6,
                period = 30,
                counter = 0L,
                uid = "user-12345",
                resourceId = "resource-67890",
                timeAdded = testTime
            )
        )
        val accounts = mapOf(
            "Google-user@example.com" to accountJson(
                id = "Google-user@example.com",
                issuer = "Google",
                displayIssuer = "Google Authenticator",
                accountName = "user@example.com",
                displayAccountName = "Demo User",
                imageURL = "https://example.com/logo.png",
                backgroundColor = "#4285F4",
                timeAdded = testTime,
                policies = null,
                lockingPolicy = null,
                lock = false
            )
        )

        val result = LegacyDataConverter.buildMechanismsList(mechanisms, accounts)

        assertEquals(1, result.size)
        val m = result[0]
        assertEquals("Google-user@example.com-totp", m.id)
        assertEquals("Google", m.issuer)
        assertEquals("user@example.com", m.accountName)
        assertEquals("totp-uid-001", m.mechanismUID)
        assertEquals("JBSWY3DPEHPK3PXP", m.secret)
        assertEquals("totp", m.type)
        assertEquals("TOTP", m.oathType)
        assertEquals("sha1", m.algorithm)
        assertEquals(6, m.digits)
        assertEquals(30, m.period)
        assertEquals(0L, m.counter)
        assertEquals("user-12345", m.uid)
        assertEquals("resource-67890", m.resourceId)
        assertEquals(testTime, m.timeAdded)

        // account fields
        val a = m.account
        assertEquals("Google-user@example.com", a.id)
        assertEquals("Google", a.issuer)
        assertEquals("Google Authenticator", a.displayIssuer)
        assertEquals("user@example.com", a.accountName)
        assertEquals("Demo User", a.displayAccountName)
        assertEquals("https://example.com/logo.png", a.imageURL)
        assertEquals("#4285F4", a.backgroundColor)
        assertEquals(testTime, a.timeAdded)
        assertNull(a.policies)
        assertNull(a.lockingPolicy)
        assertEquals(false, a.lock)
    }

    // ── buildMechanismsList — HOTP mechanism

    @Test
    fun `correctly maps HOTP mechanism fields including counter`() {
        val mechanisms = mapOf(
            "hotp-uid-002" to hotpMechanismJson(
                id = "GitHub-dev@example.com-hotp",
                issuer = "GitHub",
                accountName = "dev@example.com",
                mechanismUID = "hotp-uid-002",
                secret = "HOTPSECRETKEY",
                algorithm = "sha256",
                digits = 8,
                counter = 42L
            )
        )
        val accounts = mapOf(
            "GitHub-dev@example.com" to accountJson(
                id = "GitHub-dev@example.com",
                issuer = "GitHub",
                displayIssuer = "GitHub Inc",
                accountName = "dev@example.com",
                displayAccountName = "Developer",
                backgroundColor = "#24292e",
                lock = false
            )
        )

        val result = LegacyDataConverter.buildMechanismsList(mechanisms, accounts)

        assertEquals(1, result.size)
        val m = result[0]
        assertEquals("HOTP", m.oathType)
        assertEquals("sha256", m.algorithm)
        assertEquals(8, m.digits)
        assertEquals(42L, m.counter)
        assertEquals("hotp", m.type)
        assertNull(m.period)
    }

    @Test
    fun `maps SHA-512 algorithm correctly`() {
        val json = """{"id":"svc-u-totp","issuer":"svc","accountName":"u","mechanismUID":"uid-512",""" +
                   """"secret":"K","type":"totp","oathType":"TOTP","algorithm":"sha512","digits":6,"period":30}"""
        val mechanisms = mapOf("uid-512" to json)
        val accounts = mapOf("svc-u" to accountJson("svc-u", "svc", accountName = "u"))

        val result = LegacyDataConverter.buildMechanismsList(mechanisms, accounts)

        assertEquals(1, result.size)
        assertEquals("sha512", result[0].algorithm)
    }

    // ── buildMechanismsList — Push mechanism

    @Test
    fun `correctly maps all Push mechanism fields`() {
        val testTime = 1_700_000_001_000L
        val mechanisms = mapOf(
            "push-uid-003" to pushMechanismJson(
                id = "FR-admin@company.com-push",
                issuer = "FR",
                accountName = "admin@company.com",
                mechanismUID = "push-uid-003",
                secret = "sharedSecret123",
                authEndpoint = "https://am.example.com/push?_action=authenticate",
                regEndpoint = "https://am.example.com/push?_action=register",
                platform = "PING_AM",
                uid = "user-99999",
                resourceId = "push-resource-11111",
                timeAdded = testTime
            )
        )
        val accounts = mapOf(
            "FR-admin@company.com" to accountJson(
                id = "FR-admin@company.com",
                issuer = "FR",
                displayIssuer = "ForgeRock Identity",
                accountName = "admin@company.com",
                displayAccountName = "Admin User",
                imageURL = "https://forgerock.com/logo.png",
                backgroundColor = "#00A1DE",
                policies = "{\"deviceTampering\":true}",
                lockingPolicy = "BiometricNotAvailable",
                lock = true
            )
        )

        val result = LegacyDataConverter.buildMechanismsList(mechanisms, accounts)

        assertEquals(1, result.size)
        val m = result[0]
        assertEquals("push", m.type)
        assertEquals("sharedSecret123", m.secret)
        assertEquals("https://am.example.com/push?_action=authenticate", m.authenticationEndpoint)
        assertEquals("https://am.example.com/push?_action=register", m.registrationEndpoint)
        assertEquals("PING_AM", m.platform)
        assertEquals("user-99999", m.uid)
        assertEquals("push-resource-11111", m.resourceId)
        assertEquals(testTime, m.timeAdded)

        // account fields
        val a = m.account
        assertEquals("ForgeRock Identity", a.displayIssuer)
        assertEquals("Admin User", a.displayAccountName)
        assertEquals("https://forgerock.com/logo.png", a.imageURL)
        assertEquals("#00A1DE", a.backgroundColor)
        assertEquals("{\"deviceTampering\":true}", a.policies)
        assertEquals("BiometricNotAvailable", a.lockingPolicy)
        assertEquals(true, a.lock)
    }

    // ── buildMechanismsList — fallback / defaults ─────────────────────────────

    @Test
    fun `falls back to map key when id field is absent in mechanism JSON`() {
        val json = """{"issuer":"Svc","accountName":"u@test.com","mechanismUID":"uid-fallback","type":"totp","secret":"S"}"""
        val mechanisms = mapOf("map-key-id" to json)
        val accounts = mapOf("Svc-u@test.com" to accountJson("Svc-u@test.com", "Svc", accountName = "u@test.com"))

        val result = LegacyDataConverter.buildMechanismsList(mechanisms, accounts)

        assertEquals(1, result.size)
        assertEquals("map-key-id", result[0].id)
    }

    @Test
    fun `falls back to mechanismUID for resourceId when resourceId is absent`() {
        val json = """{"id":"svc-u-totp","issuer":"Svc","accountName":"u@test.com","mechanismUID":"uid-res-fallback","type":"totp","secret":"S"}"""
        val mechanisms = mapOf("svc-u-totp" to json)
        val accounts = mapOf("Svc-u@test.com" to accountJson("Svc-u@test.com", "Svc", accountName = "u@test.com"))

        val result = LegacyDataConverter.buildMechanismsList(mechanisms, accounts)

        assertEquals(1, result.size)
        assertEquals("uid-res-fallback", result[0].resourceId)
    }

    @Test
    fun `falls back to computed accountId when account id field is absent`() {
        val json = """{"id":"svc-u-totp","issuer":"Svc","accountName":"u@test.com","mechanismUID":"uid-001","type":"totp","secret":"S"}"""
        val mechanisms = mapOf("svc-u-totp" to json)
        // Account JSON without an "id" field
        val accountJsonNoId = """{"issuer":"Svc","accountName":"u@test.com","lock":false}"""
        val accounts = mapOf("Svc-u@test.com" to accountJsonNoId)

        val result = LegacyDataConverter.buildMechanismsList(mechanisms, accounts)

        assertEquals(1, result.size)
        assertEquals("Svc-u@test.com", result[0].account.id)
    }

    @Test
    fun `falls back to mechanismIssuer for account issuer when absent from account JSON`() {
        val json = """{"id":"svc-u-totp","issuer":"FallbackIssuer","accountName":"u@test.com","mechanismUID":"uid-001","type":"totp","secret":"S"}"""
        val mechanisms = mapOf("svc-u-totp" to json)
        val accountJsonNoIssuer = """{"id":"FallbackIssuer-u@test.com","accountName":"u@test.com","lock":false}"""
        val accounts = mapOf("FallbackIssuer-u@test.com" to accountJsonNoIssuer)

        val result = LegacyDataConverter.buildMechanismsList(mechanisms, accounts)

        assertEquals(1, result.size)
        assertEquals("FallbackIssuer", result[0].account.issuer)
    }

    @Test
    fun `falls back to mechanismAccountName for account accountName when absent from account JSON`() {
        val json = """{"id":"svc-u-totp","issuer":"Svc","accountName":"u@test.com","mechanismUID":"uid-001","type":"totp","secret":"S"}"""
        val mechanisms = mapOf("svc-u-totp" to json)
        val accountJsonNoAccountName = """{"id":"Svc-u@test.com","issuer":"Svc","lock":false}"""
        val accounts = mapOf("Svc-u@test.com" to accountJsonNoAccountName)

        val result = LegacyDataConverter.buildMechanismsList(mechanisms, accounts)

        assertEquals(1, result.size)
        assertEquals("u@test.com", result[0].account.accountName)
    }

    @Test
    fun `empty secret defaults to empty string when absent`() {
        val json = """{"id":"svc-u-totp","issuer":"Svc","accountName":"u@test.com","mechanismUID":"uid-001","type":"totp"}"""
        val mechanisms = mapOf("svc-u-totp" to json)
        val accounts = mapOf("Svc-u@test.com" to accountJson("Svc-u@test.com", "Svc", accountName = "u@test.com"))

        val result = LegacyDataConverter.buildMechanismsList(mechanisms, accounts)

        assertEquals(1, result.size)
        assertEquals("", result[0].secret)
    }

    @Test
    fun `empty type defaults to empty string when absent`() {
        val json = """{"id":"svc-u-totp","issuer":"Svc","accountName":"u@test.com","mechanismUID":"uid-001","secret":"S"}"""
        val mechanisms = mapOf("svc-u-totp" to json)
        val accounts = mapOf("Svc-u@test.com" to accountJson("Svc-u@test.com", "Svc", accountName = "u@test.com"))

        val result = LegacyDataConverter.buildMechanismsList(mechanisms, accounts)

        assertEquals(1, result.size)
        assertEquals("", result[0].type)
    }

    @Test
    fun `optional OATH fields are null when absent from JSON`() {
        val json = """{"id":"svc-u-totp","issuer":"Svc","accountName":"u@test.com","mechanismUID":"uid-001","type":"totp","secret":"S"}"""
        val mechanisms = mapOf("svc-u-totp" to json)
        val accounts = mapOf("Svc-u@test.com" to accountJson("Svc-u@test.com", "Svc", accountName = "u@test.com"))

        val result = LegacyDataConverter.buildMechanismsList(mechanisms, accounts)

        assertEquals(1, result.size)
        val m = result[0]
        assertNull(m.oathType)
        assertNull(m.algorithm)
        assertNull(m.digits)
        assertNull(m.period)
        assertNull(m.counter)
    }

    @Test
    fun `optional Push fields are null when absent from JSON`() {
        val json = """{"id":"svc-u-push","issuer":"Svc","accountName":"u@test.com","mechanismUID":"uid-001","type":"push","secret":"S"}"""
        val mechanisms = mapOf("svc-u-push" to json)
        val accounts = mapOf("Svc-u@test.com" to accountJson("Svc-u@test.com", "Svc", accountName = "u@test.com"))

        val result = LegacyDataConverter.buildMechanismsList(mechanisms, accounts)

        assertEquals(1, result.size)
        val m = result[0]
        assertNull(m.authenticationEndpoint)
        assertNull(m.registrationEndpoint)
        assertNull(m.platform)
    }

    @Test
    fun `optional account fields are null when absent`() {
        val json = """{"id":"svc-u-totp","issuer":"Svc","accountName":"u@test.com","mechanismUID":"uid-001","type":"totp","secret":"S"}"""
        val mechanisms = mapOf("svc-u-totp" to json)
        val minimalAccount = """{"id":"Svc-u@test.com","issuer":"Svc","accountName":"u@test.com","lock":false}"""
        val accounts = mapOf("Svc-u@test.com" to minimalAccount)

        val result = LegacyDataConverter.buildMechanismsList(mechanisms, accounts)

        assertEquals(1, result.size)
        val a = result[0].account
        assertNull(a.displayIssuer)
        assertNull(a.displayAccountName)
        assertNull(a.imageURL)
        assertNull(a.backgroundColor)
        assertNull(a.timeAdded)
        assertNull(a.policies)
        assertNull(a.lockingPolicy)
        assertEquals(false, a.lock)
    }

    @Test
    fun `lock defaults to false when absent from account JSON`() {
        val json = """{"id":"svc-u-totp","issuer":"Svc","accountName":"u@test.com","mechanismUID":"uid-001","type":"totp","secret":"S"}"""
        val mechanisms = mapOf("svc-u-totp" to json)
        val accountJsonNoLock = """{"id":"Svc-u@test.com","issuer":"Svc","accountName":"u@test.com"}"""
        val accounts = mapOf("Svc-u@test.com" to accountJsonNoLock)

        val result = LegacyDataConverter.buildMechanismsList(mechanisms, accounts)

        assertEquals(1, result.size)
        assertEquals(false, result[0].account.lock)
    }

    @Test
    fun `lock is parsed as true when set`() {
        val json = """{"id":"svc-u-totp","issuer":"Svc","accountName":"u@test.com","mechanismUID":"uid-001","type":"totp","secret":"S"}"""
        val mechanisms = mapOf("svc-u-totp" to json)
        val accounts = mapOf("Svc-u@test.com" to accountJson("Svc-u@test.com", "Svc", accountName = "u@test.com", lock = true))

        val result = LegacyDataConverter.buildMechanismsList(mechanisms, accounts)

        assertEquals(1, result.size)
        assertEquals(true, result[0].account.lock)
    }

    // ── buildMechanismsList — multiple mechanisms ─────────────────────────────

    @Test
    fun `processes multiple mechanisms of different types`() {
        val mechanisms = mapOf(
            "totp-uid-001" to totpMechanismJson(issuer = "Google", accountName = "user@example.com", mechanismUID = "totp-uid-001"),
            "hotp-uid-002" to hotpMechanismJson(issuer = "GitHub", accountName = "dev@example.com", mechanismUID = "hotp-uid-002"),
            "push-uid-003" to pushMechanismJson(issuer = "FR", accountName = "admin@company.com", mechanismUID = "push-uid-003")
        )
        val accounts = mapOf(
            "Google-user@example.com" to accountJson("Google-user@example.com", "Google", accountName = "user@example.com"),
            "GitHub-dev@example.com" to accountJson("GitHub-dev@example.com", "GitHub", accountName = "dev@example.com"),
            "FR-admin@company.com" to accountJson("FR-admin@company.com", "FR", accountName = "admin@company.com")
        )

        val result = LegacyDataConverter.buildMechanismsList(mechanisms, accounts)

        assertEquals(3, result.size)
        val types = result.map { it.type }.toSet()
        assertTrue(types.contains("totp"))
        assertTrue(types.contains("hotp"))
        assertTrue(types.contains("push"))
    }

    @Test
    fun `skips invalid mechanisms but still returns valid ones`() {
        val mechanisms = mapOf(
            "totp-uid-001" to totpMechanismJson(issuer = "Google", accountName = "user@example.com", mechanismUID = "totp-uid-001"),
            "no-issuer" to """{"accountName":"u@x.com","mechanismUID":"uid-x","type":"totp","secret":"S"}""",
            "no-account" to totpMechanismJson(issuer = "Orphan", accountName = "orphan@x.com", mechanismUID = "uid-orphan"),
            "bad-json" to "INVALID{JSON"
        )
        val accounts = mapOf(
            "Google-user@example.com" to accountJson("Google-user@example.com", "Google", accountName = "user@example.com")
        )

        val result = LegacyDataConverter.buildMechanismsList(mechanisms, accounts)

        assertEquals(1, result.size)
        assertEquals("Google", result[0].issuer)
    }

    @Test
    fun `two mechanisms from same account are each linked to the same account data`() {
        val mechanisms = mapOf(
            "totp-uid-001" to totpMechanismJson(issuer = "Google", accountName = "user@example.com", mechanismUID = "totp-uid-001"),
            "hotp-uid-002" to hotpMechanismJson(issuer = "Google", accountName = "user@example.com", mechanismUID = "hotp-uid-002")
        )
        val accounts = mapOf(
            "Google-user@example.com" to accountJson(
                "Google-user@example.com", "Google",
                displayIssuer = "Google Inc",
                accountName = "user@example.com"
            )
        )

        val result = LegacyDataConverter.buildMechanismsList(mechanisms, accounts)

        assertEquals(2, result.size)
        result.forEach { m ->
            assertEquals("Google Inc", m.account.displayIssuer)
            assertEquals("Google-user@example.com", m.account.id)
        }
    }

    // ── convertToLegacyExportedData

    @Test
    fun `convertToLegacyExportedData returns empty export when storageClient has no accounts`() {
        val storageClient = mockk<StorageClient>()
        every { storageClient.allAccounts } returns emptyList()

        val result = LegacyDataConverter.convertToLegacyExportedData(storageClient)

        assertTrue(result.mechanisms.isEmpty())
        assertEquals(0, result.metadata.totalMechanisms)
    }

    @Test
    fun `convertToLegacyExportedData calls getMechanismsForAccount for each account`() {
        val storageClient = mockk<StorageClient>()
        val account1 = mockk<Account>()
        val account2 = mockk<Account>()

        every { account1.id } returns "Google-user1@example.com"
        every { account1.toJson() } returns accountJson("Google-user1@example.com", "Google", accountName = "user1@example.com")
        every { account2.id } returns "GitHub-user2@example.com"
        every { account2.toJson() } returns accountJson("GitHub-user2@example.com", "GitHub", accountName = "user2@example.com")
        every { storageClient.allAccounts } returns listOf(account1, account2)
        every { storageClient.getMechanismsForAccount(account1) } returns emptyList()
        every { storageClient.getMechanismsForAccount(account2) } returns emptyList()

        LegacyDataConverter.convertToLegacyExportedData(storageClient)

        verify(exactly = 1) { storageClient.getMechanismsForAccount(account1) }
        verify(exactly = 1) { storageClient.getMechanismsForAccount(account2) }
    }

    @Test
    fun `convertToLegacyExportedData correctly converts account and mechanism via toJson`() {
        val storageClient = mockk<StorageClient>()
        val account = mockk<Account>()
        val mechanism = mockk<Mechanism>()

        val accountId = "Google-user@example.com"
        val mechanismId = "Google-user@example.com-totp"
        val testTime = 1_700_000_000_000L

        every { account.id } returns accountId
        every { account.toJson() } returns accountJson(
            id = accountId,
            issuer = "Google",
            displayIssuer = "Google Authenticator",
            accountName = "user@example.com",
            displayAccountName = "Demo User"
        )
        every { mechanism.id } returns mechanismId
        every { mechanism.toJson() } returns totpMechanismJson(
            id = mechanismId,
            issuer = "Google",
            accountName = "user@example.com",
            mechanismUID = "totp-uid-001",
            secret = "JBSWY3DPEHPK3PXP",
            timeAdded = testTime
        )
        every { storageClient.allAccounts } returns listOf(account)
        every { storageClient.getMechanismsForAccount(account) } returns listOf(mechanism)

        val result = LegacyDataConverter.convertToLegacyExportedData(storageClient)

        assertEquals(1, result.mechanisms.size)
        assertEquals(1, result.metadata.totalMechanisms)
        val m = result.mechanisms[0]
        assertEquals("Google", m.issuer)
        assertEquals("user@example.com", m.accountName)
        assertEquals("JBSWY3DPEHPK3PXP", m.secret)
        assertEquals("Google Authenticator", m.account.displayIssuer)
        assertEquals("Demo User", m.account.displayAccountName)
    }

    @Test
    fun `convertToLegacyExportedData metadata totalMechanisms reflects actual migrated count`() {
        val storageClient = mockk<StorageClient>()
        val account = mockk<Account>()
        val mechValid = mockk<Mechanism>()
        val mechInvalid = mockk<Mechanism>() // will produce JSON missing required fields

        val accountId = "Google-user@example.com"
        every { account.id } returns accountId
        every { account.toJson() } returns accountJson(accountId, "Google", accountName = "user@example.com")

        every { mechValid.id } returns "valid-mech"
        every { mechValid.toJson() } returns totpMechanismJson(
            issuer = "Google", accountName = "user@example.com", mechanismUID = "uid-valid"
        )
        // Missing mechanismUID → will be skipped by buildMechanismsList
        every { mechInvalid.id } returns "invalid-mech"
        every { mechInvalid.toJson() } returns """{"issuer":"Google","accountName":"user@example.com","type":"totp","secret":"S"}"""

        every { storageClient.allAccounts } returns listOf(account)
        every { storageClient.getMechanismsForAccount(account) } returns listOf(mechValid, mechInvalid)

        val result = LegacyDataConverter.convertToLegacyExportedData(storageClient)

        // Only mechValid should survive; mechInvalid is skipped due to missing mechanismUID
        assertEquals(1, result.mechanisms.size)
        assertEquals(1, result.metadata.totalMechanisms)
    }

    @Test
    fun `convertToLegacyExportedData handles account with multiple mechanisms`() {
        val storageClient = mockk<StorageClient>()
        val account = mockk<Account>()
        val totp = mockk<Mechanism>()
        val push = mockk<Mechanism>()

        val accountId = "Google-user@example.com"
        every { account.id } returns accountId
        every { account.toJson() } returns accountJson(accountId, "Google", accountName = "user@example.com")

        every { totp.id } returns "totp-mech"
        every { totp.toJson() } returns totpMechanismJson(
            id = "totp-mech", issuer = "Google", accountName = "user@example.com", mechanismUID = "uid-totp"
        )
        every { push.id } returns "push-mech"
        every { push.toJson() } returns pushMechanismJson(
            id = "push-mech", issuer = "Google", accountName = "user@example.com", mechanismUID = "uid-push"
        )
        every { storageClient.allAccounts } returns listOf(account)
        every { storageClient.getMechanismsForAccount(account) } returns listOf(totp, push)

        val result = LegacyDataConverter.convertToLegacyExportedData(storageClient)

        assertEquals(2, result.mechanisms.size)
        assertEquals(2, result.metadata.totalMechanisms)
        val types = result.mechanisms.map { it.type }.toSet()
        assertTrue(types.contains("totp"))
        assertTrue(types.contains("push"))
    }


    /** Minimal valid TOTP mechanism JSON keyed by mechanismUID. */
    private fun totpMechanismJson(
        id: String = "Google-user@example.com-totp",
        issuer: String = "Google",
        accountName: String = "user@example.com",
        mechanismUID: String = "totp-uid-001",
        secret: String = "JBSWY3DPEHPK3PXP",
        oathType: String = "TOTP",
        algorithm: String = "sha1",
        digits: Int = 6,
        period: Int = 30,
        counter: Long = 0L,
        uid: String? = null,
        resourceId: String? = null,
        timeAdded: Long? = null,
    ): String = buildJsonObject {
        put("id", id)
        put("issuer", issuer)
        put("accountName", accountName)
        put("mechanismUID", mechanismUID)
        put("secret", secret)
        put("type", "totp")
        put("oathType", oathType)
        put("algorithm", algorithm)
        put("digits", digits)
        put("period", period)
        put("counter", counter)
        uid?.let { put("uid", it) }
        resourceId?.let { put("resourceId", it) }
        timeAdded?.let { put("timeAdded", it) }
    }.toString()

    /** Minimal valid HOTP mechanism JSON. */
    private fun hotpMechanismJson(
        id: String = "GitHub-dev@example.com-hotp",
        issuer: String = "GitHub",
        accountName: String = "dev@example.com",
        mechanismUID: String = "hotp-uid-002",
        secret: String = "HOTPSECRETKEY",
        algorithm: String = "sha256",
        digits: Int = 8,
        counter: Long = 42L,
    ): String = buildJsonObject {
        put("id", id)
        put("issuer", issuer)
        put("accountName", accountName)
        put("mechanismUID", mechanismUID)
        put("secret", secret)
        put("type", "hotp")
        put("oathType", "HOTP")
        put("algorithm", algorithm)
        put("digits", digits)
        put("counter", counter)
    }.toString()

    /** Minimal valid Push mechanism JSON. */
    private fun pushMechanismJson(
        id: String = "FR-admin@company.com-push",
        issuer: String = "FR",
        accountName: String = "admin@company.com",
        mechanismUID: String = "push-uid-003",
        secret: String = "sharedSecret123",
        authEndpoint: String = "https://am.example.com/push?_action=authenticate",
        regEndpoint: String = "https://am.example.com/push?_action=register",
        platform: String = "PING_AM",
        uid: String? = null,
        resourceId: String? = null,
        timeAdded: Long? = null,
    ): String = buildJsonObject {
        put("id", id)
        put("issuer", issuer)
        put("accountName", accountName)
        put("mechanismUID", mechanismUID)
        put("secret", secret)
        put("type", "push")
        put("authenticationEndpoint", authEndpoint)
        put("registrationEndpoint", regEndpoint)
        put("platform", platform)
        uid?.let { put("uid", it) }
        resourceId?.let { put("resourceId", it) }
        timeAdded?.let { put("timeAdded", it) }
    }.toString()

    /** Full account JSON. */
    private fun accountJson(
        id: String,
        issuer: String,
        displayIssuer: String? = null,
        accountName: String,
        displayAccountName: String? = null,
        imageURL: String? = null,
        backgroundColor: String? = null,
        timeAdded: Long? = null,
        policies: String? = null,
        lockingPolicy: String? = null,
        lock: Boolean = false,
    ): String = buildJsonObject {
        put("id", id)
        put("issuer", issuer)
        put("accountName", accountName)
        put("lock", lock)
        displayIssuer?.let { put("displayIssuer", it) }
        displayAccountName?.let { put("displayAccountName", it) }
        imageURL?.let { put("imageURL", it) }
        backgroundColor?.let { put("backgroundColor", it) }
        timeAdded?.let { put("timeAdded", it) }
        policies?.let { put("policies", it) }
        lockingPolicy?.let { put("lockingPolicy", it) }
    }.toString()
}


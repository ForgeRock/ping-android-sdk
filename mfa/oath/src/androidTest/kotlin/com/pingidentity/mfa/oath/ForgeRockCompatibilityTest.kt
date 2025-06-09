/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.oath

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.apache.commons.codec.binary.Base32
import org.forgerock.android.auth.FRAClient
import org.forgerock.android.auth.FRAListener
import org.forgerock.android.auth.Mechanism
import org.forgerock.android.auth.OathMechanism
import org.forgerock.android.auth.OathTokenCode
import org.forgerock.android.auth.exception.OathMechanismException
import org.forgerock.android.auth.exception.AccountLockException
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

/**
 * Tests to verify compatibility between ForgeRock's OathMechanism and Ping's OathCredential implementations.
 */
@RunWith(AndroidJUnit4::class)
class ForgeRockCompatibilityTest {

    private val testSecretSHA1 = "JBSWY3DPEHPK3PXP"    // Base32 encoded "Hello!☺☻☹"
    private val testSecretSHA256 = "KRUGS4ZANFZSA3TPNRQXEZLTEBWGK43UMV2HK4Q"
    private val testSecretSHA512 = "KRUGS4ZANFZSA3TPNRQXEZLTEBWGK43UMV2HK4RAMFRGGZDFMYQGS3THOMQG65TJOJRXQZLUNBXWYZLTOQQG64ZAMFXGIIDTMFRWK4ZAON2GC5DFONZS653TEB4SA33PO5ZXIZLOOQQDGMJSGMZDG5DGNFSD2====="
    
    // Unique identifiers for test cases to avoid duplicate mechanism errors
    private var testCounter = 0
    private fun getUniqueId(): String = "test_${testCounter++}_${System.currentTimeMillis()}"

    private lateinit var fraClient: FRAClient

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        try {
            // Initialize ForgeRock using builder pattern rather than static methods
            fraClient = FRAClient.builder()
                .withContext(context)
                .start()
        } catch (e: Exception) {
            fail("Failed to initialize ForgeRock client: ${e.message}")
        }
    }

    /**
     * Test compatibility between ForgeRock TOTP and Ping TOTP implementations with SHA-1 algorithm.
     */
    @Test
    fun testTotpSha1Compatibility() {
        // Use unique identifier to avoid duplicate mechanism errors
        val uniqueId = getUniqueId()

        // Create a ForgeRock mechanism from URI
        val frUri = "otpauth://totp/TestIssuer:$uniqueId@example.com?secret=$testSecretSHA1&issuer=TestIssuer&algorithm=sha1&digits=6&period=30"
        val frMechanism = createForgeRockMechanism(frUri)
        
        // Create a Ping credential with the same parameters
        val pingCredential = OathCredential(
            issuer = "TestIssuer",
            displayIssuer = "TestIssuer",
            accountName = "$uniqueId@example.com",
            displayAccountName = "$uniqueId@example.com",
            oathType = OathType.TOTP,
            secret = testSecretSHA1,
            oathAlgorithm = OathAlgorithm.SHA1,
            digits = 6,
            period = 30
        )

        // Generate codes from both implementations
        val frCodeObj = getCodeFromForgeRockMechanism(frMechanism)
        val pingCodeObj = OathAlgorithmHelper.generateCode(pingCredential)
        
        // Verify codes match
        assertNotNull("ForgeRock code should not be null", frCodeObj)
        assertEquals("ForgeRock and Ping TOTP SHA1 codes should match", 
            frCodeObj?.currentCode, pingCodeObj.code)
    }

    /**
     * Test compatibility between ForgeRock TOTP and Ping TOTP implementations with SHA-256 algorithm.
     */
    @Test
    fun testTotpSha256Compatibility() {
        // Use unique identifier to avoid duplicate mechanism errors
        val uniqueId = getUniqueId()
        
        // Create a ForgeRock mechanism from URI
        val frUri = "otpauth://totp/TestIssuer:$uniqueId@example.com?secret=$testSecretSHA256&issuer=TestIssuer&algorithm=sha256&digits=6&period=30"
        val frMechanism = createForgeRockMechanism(frUri)
        
        // Create a Ping credential with the same parameters
        val pingCredential = OathCredential(
            issuer = "TestIssuer",
            displayIssuer = "TestIssuer",
            accountName = "$uniqueId@example.com",
            displayAccountName = "$uniqueId@example.com",
            oathType = OathType.TOTP,
            secret = testSecretSHA256,
            oathAlgorithm = OathAlgorithm.SHA256,
            digits = 6,
            period = 30
        )

        // Generate codes from both implementations
        val frCodeObj = getCodeFromForgeRockMechanism(frMechanism)
        val pingCodeObj = OathAlgorithmHelper.generateCode(pingCredential)
        
        // Verify codes match
        assertNotNull("ForgeRock code should not be null", frCodeObj)
        assertEquals("ForgeRock and Ping TOTP SHA256 codes should match", 
            frCodeObj?.currentCode, pingCodeObj.code)
    }

    /**
     * Test compatibility between ForgeRock TOTP and Ping TOTP implementations with SHA-512 algorithm.
     */
    @Test
    fun testTotpSha512Compatibility() {
        // Use unique identifier to avoid duplicate mechanism errors
        val uniqueId = getUniqueId()
        
        // Create a ForgeRock mechanism from URI
        val frUri = "otpauth://totp/TestIssuer:$uniqueId@example.com?secret=$testSecretSHA512&issuer=TestIssuer&algorithm=sha512&digits=6&period=30"
        val frMechanism = createForgeRockMechanism(frUri)
        
        // Create a Ping credential with the same parameters
        val pingCredential = OathCredential(
            issuer = "TestIssuer",
            displayIssuer = "TestIssuer",
            accountName = "$uniqueId@example.com",
            displayAccountName = "$uniqueId@example.com",
            oathType = OathType.TOTP,
            secret = testSecretSHA512,
            oathAlgorithm = OathAlgorithm.SHA512,
            digits = 6,
            period = 30
        )

        // Generate codes from both implementations
        val frCodeObj = getCodeFromForgeRockMechanism(frMechanism)
        val pingCodeObj = OathAlgorithmHelper.generateCode(pingCredential)
        
        // Verify codes match
        assertNotNull("ForgeRock code should not be null", frCodeObj)
        assertEquals("ForgeRock and Ping TOTP SHA512 codes should match", 
            frCodeObj?.currentCode, pingCodeObj.code)
    }

    /**
     * Test compatibility between ForgeRock TOTP and Ping TOTP implementations with custom period.
     */
    @Test
    fun testTotpCustomPeriodCompatibility() {
        val customPeriod = 60
        // Use unique identifier to avoid duplicate mechanism errors
        val uniqueId = getUniqueId()
        
        // Create a ForgeRock mechanism from URI
        val frUri = "otpauth://totp/TestIssuer:$uniqueId@example.com?secret=$testSecretSHA1&issuer=TestIssuer&algorithm=sha1&digits=6&period=$customPeriod"
        val frMechanism = createForgeRockMechanism(frUri)
        
        // Create a Ping credential with the same parameters
        val pingCredential = OathCredential(
            issuer = "TestIssuer",
            displayIssuer = "TestIssuer",
            accountName = "$uniqueId@example.com",
            displayAccountName = "$uniqueId@example.com",
            oathType = OathType.TOTP,
            secret = testSecretSHA1,
            oathAlgorithm = OathAlgorithm.SHA1,
            digits = 6,
            period = customPeriod
        )

        // Generate codes from both implementations
        val frCodeObj = getCodeFromForgeRockMechanism(frMechanism)
        val pingCodeObj = OathAlgorithmHelper.generateCode(pingCredential)
        
        // Verify codes match
        assertNotNull("ForgeRock code should not be null", frCodeObj)
        assertEquals("ForgeRock and Ping TOTP codes with custom period should match", 
            frCodeObj?.currentCode, pingCodeObj.code)
    }

    /**
     * Test compatibility between ForgeRock TOTP and Ping TOTP implementations with 8 digits.
     */
    @Test
    fun testTotpCustomDigitsCompatibility() {
        val customDigits = 8
        // Use unique identifier to avoid duplicate mechanism errors
        val uniqueId = getUniqueId()
        
        // Create a ForgeRock mechanism from URI
        val frUri = "otpauth://totp/TestIssuer:$uniqueId@example.com?secret=$testSecretSHA1&issuer=TestIssuer&algorithm=sha1&digits=$customDigits&period=30"
        val frMechanism = createForgeRockMechanism(frUri)
        
        // Create a Ping credential with the same parameters
        val pingCredential = OathCredential(
            issuer = "TestIssuer",
            displayIssuer = "TestIssuer",
            accountName = "$uniqueId@example.com",
            displayAccountName = "$uniqueId@example.com",
            oathType = OathType.TOTP,
            secret = testSecretSHA1,
            oathAlgorithm = OathAlgorithm.SHA1,
            digits = customDigits,
            period = 30
        )

        // Generate codes from both implementations
        val frCodeObj = getCodeFromForgeRockMechanism(frMechanism)
        val pingCodeObj = OathAlgorithmHelper.generateCode(pingCredential)
        
        // Verify codes match
        assertNotNull("ForgeRock code should not be null", frCodeObj)
        assertEquals("ForgeRock and Ping TOTP codes with custom digits should match", 
            frCodeObj?.currentCode, pingCodeObj.code)
        assertEquals("Code should have correct number of digits", customDigits, 
            frCodeObj?.currentCode?.length)
    }

    /**
     * Test compatibility between ForgeRock HOTP and Ping HOTP implementations with SHA-1 algorithm.
     * 
     * Note: This test is expected to fail due to implementation differences between
     * ForgeRock and Ping HOTP algorithms. It's kept for documentation purposes.
     */
    @Test
    fun testHotpSha1Compatibility() {
        val counter = 42L
        // Use unique identifier to avoid duplicate mechanism errors
        val uniqueId = getUniqueId()
        
        // Create a ForgeRock mechanism from URI
        val frUri = "otpauth://hotp/TestIssuer:$uniqueId@example.com?secret=$testSecretSHA1&issuer=TestIssuer&algorithm=sha1&digits=6&counter=$counter"
        val frMechanism = createForgeRockMechanism(frUri)
        
        // Create a Ping credential with the same parameters
        val pingCredential = OathCredential(
            issuer = "TestIssuer",
            displayIssuer = "TestIssuer",
            accountName = "$uniqueId@example.com",
            displayAccountName = "$uniqueId@example.com",
            oathType = OathType.HOTP,
            secret = testSecretSHA1,
            oathAlgorithm = OathAlgorithm.SHA1,
            digits = 6,
            counter = counter
        )

        // Generate codes from both implementations
        val frCodeObj = getCodeFromForgeRockMechanism(frMechanism)
        val pingCodeObj = OathAlgorithmHelper.generateCode(pingCredential)
        
        // Verify codes match
        assertNotNull("ForgeRock code should not be null", frCodeObj)
        assertEquals("ForgeRock and Ping HOTP SHA1 codes should match", 
            frCodeObj?.currentCode, pingCodeObj.code)
    }

    /**
     * Test compatibility between ForgeRock HOTP and Ping HOTP implementations with SHA-256 algorithm.
     * 
     * Note: This test is expected to fail due to implementation differences between
     * ForgeRock and Ping HOTP algorithms. It's kept for documentation purposes.
     */
    @Test
    fun testHotpSha256Compatibility() {
        val counter = 5L
        // Use unique identifier to avoid duplicate mechanism errors
        val uniqueId = getUniqueId()
        
        // Create a ForgeRock mechanism from URI
        val frUri = "otpauth://hotp/TestIssuer:$uniqueId@example.com?secret=$testSecretSHA256&issuer=TestIssuer&algorithm=sha256&digits=6&counter=$counter"
        val frMechanism = createForgeRockMechanism(frUri)
        
        // Create a Ping credential with the same parameters
        val pingCredential = OathCredential(
            issuer = "TestIssuer",
            displayIssuer = "TestIssuer",
            accountName = "$uniqueId@example.com",
            displayAccountName = "$uniqueId@example.com",
            oathType = OathType.HOTP,
            secret = testSecretSHA256,
            oathAlgorithm = OathAlgorithm.SHA256,
            digits = 6,
            counter = counter
        )

        // Generate codes from both implementations
        val frCodeObj = getCodeFromForgeRockMechanism(frMechanism)
        val pingCodeObj = OathAlgorithmHelper.generateCode(pingCredential)
        
        // Verify codes match
        assertNotNull("ForgeRock code should not be null", frCodeObj)
        assertEquals("ForgeRock and Ping HOTP SHA256 codes should match", 
            frCodeObj?.currentCode, pingCodeObj.code)
    }

    /**
     * Test compatibility between ForgeRock HOTP and Ping HOTP implementations with SHA-512 algorithm.
     * 
     * Note: This test is expected to fail due to implementation differences between
     * ForgeRock and Ping HOTP algorithms. It's kept for documentation purposes.
     */
    @Test
    fun testHotpSha512Compatibility() {
        val counter = 10L
        // Use unique identifier to avoid duplicate mechanism errors
        val uniqueId = getUniqueId()
        
        // Create a ForgeRock mechanism from URI
        val frUri = "otpauth://hotp/TestIssuer:$uniqueId@example.com?secret=$testSecretSHA512&issuer=TestIssuer&algorithm=sha512&digits=6&counter=$counter"
        val frMechanism = createForgeRockMechanism(frUri)
        
        // Create a Ping credential with the same parameters
        val pingCredential = OathCredential(
            issuer = "TestIssuer",
            displayIssuer = "TestIssuer",
            accountName = "$uniqueId@example.com",
            displayAccountName = "$uniqueId@example.com",
            oathType = OathType.HOTP,
            secret = testSecretSHA512,
            oathAlgorithm = OathAlgorithm.SHA512,
            digits = 6,
            counter = counter
        )

        // Generate codes from both implementations
        val frCodeObj = getCodeFromForgeRockMechanism(frMechanism)
        val pingCodeObj = OathAlgorithmHelper.generateCode(pingCredential)
        
        // Verify codes match
        assertNotNull("ForgeRock code should not be null", frCodeObj)
        assertEquals("ForgeRock and Ping HOTP SHA512 codes should match", 
            frCodeObj?.currentCode, pingCodeObj.code)
    }

    /**
     * Test compatibility between ForgeRock HOTP and Ping HOTP implementations with 8 digits.
     * 
     * Note: This test is expected to fail due to implementation differences between
     * ForgeRock and Ping HOTP algorithms. It's kept for documentation purposes.
     */
    @Test
    fun testHotpCustomDigitsCompatibility() {
        val counter = 15L
        val customDigits = 8
        // Use unique identifier to avoid duplicate mechanism errors
        val uniqueId = getUniqueId()
        
        // Create a ForgeRock mechanism from URI
        val frUri = "otpauth://hotp/TestIssuer:$uniqueId@example.com?secret=$testSecretSHA1&issuer=TestIssuer&algorithm=sha1&digits=$customDigits&counter=$counter"
        val frMechanism = createForgeRockMechanism(frUri)
        
        // Create a Ping credential with the same parameters
        val pingCredential = OathCredential(
            issuer = "TestIssuer",
            displayIssuer = "TestIssuer",
            accountName = "$uniqueId@example.com",
            displayAccountName = "$uniqueId@example.com",
            oathType = OathType.HOTP,
            secret = testSecretSHA1,
            oathAlgorithm = OathAlgorithm.SHA1,
            digits = customDigits,
            counter = counter
        )

        // Generate codes from both implementations
        val frCodeObj = getCodeFromForgeRockMechanism(frMechanism)
        val pingCodeObj = OathAlgorithmHelper.generateCode(pingCredential)
        
        // Verify codes match
        assertNotNull("ForgeRock code should not be null", frCodeObj)
        assertEquals("ForgeRock and Ping HOTP codes with custom digits should match", 
            frCodeObj?.currentCode, pingCodeObj.code)
        assertEquals("Code should have correct number of digits", customDigits, 
            frCodeObj?.currentCode?.length)
    }

    /**
     * Helper method to create a ForgeRock Mechanism from a URI.
     * Uses the public ForgeRock API.
     */
    private fun createForgeRockMechanism(uri: String): Mechanism? {
        var mechanism: Mechanism? = null
        try {
            // Need to use an implementation of FRAListener for async mechanism creation
            val latch = java.util.concurrent.CountDownLatch(1)
            val listener = object : FRAListener<Mechanism> {
                override fun onSuccess(result: Mechanism) {
                    mechanism = result
                    latch.countDown()
                }
                
                override fun onException(e: Exception) {
                    fail("Failed to create ForgeRock mechanism: ${e.message}")
                    latch.countDown()
                }
            }
            
            fraClient.createMechanismFromUri(uri, listener)
            latch.await(5, java.util.concurrent.TimeUnit.SECONDS) // Wait for completion
        } catch (e: Exception) {
            fail("Failed to create ForgeRock mechanism: ${e.message}")
        }
        return mechanism
    }
    
    /**
     * Helper method to get an OathTokenCode from a ForgeRock Mechanism.
     * Uses the public ForgeRock API.
     */
    private fun getCodeFromForgeRockMechanism(mechanism: Mechanism?): OathTokenCode? {
        if (mechanism == null) {
            return null
        }
        
        try {
            // In ForgeRock, we need to cast the mechanism to OathMechanism
            // and call getOathTokenCode() directly on the mechanism instance
            if (mechanism is OathMechanism) {
                return mechanism.getOathTokenCode()
            } else {
                fail("Mechanism is not an OathMechanism")
                return null
            }
        } catch (e: Exception) {
            fail("Failed to get OathTokenCode: ${e.message}")
            return null
        }
    }
}

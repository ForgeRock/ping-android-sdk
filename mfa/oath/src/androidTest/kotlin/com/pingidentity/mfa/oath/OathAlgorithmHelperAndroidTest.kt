/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.oath

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.apache.commons.codec.binary.Base32
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.pow

/**
 * Tests that focus on the actual OATH algorithm implementation.
 * 
 * These tests verify the algorithm implementation details, particularly the HMAC calculations
 * and truncation methods, to ensure they match the HOTP/TOTP specifications (RFC 4226/6238).
 * 
 * This is run as an instrumented test to verify the cryptographic operations work correctly
 * on actual Android devices.
 */
@RunWith(AndroidJUnit4::class)
class OathAlgorithmImplementationTest {
    
    /**
     * Test vectors from RFC 4226 for HOTP
     */
    @Test
    fun testRFC4226TestVectors() {
        // Test vectors from RFC 4226 (Appendix D)
        val testSecret = "12345678901234567890"  // ASCII encoding of the secret
        val hotpTestVectors = mapOf(
            0L to "755224",
            1L to "287082",
            2L to "359152",
            3L to "969429",
            4L to "338314",
            5L to "254676",
            6L to "287922",
            7L to "162583",
            8L to "399871",
            9L to "520489"
        )
        
        // Convert ASCII secret to bytes
        val secretBytes = testSecret.toByteArray(Charsets.US_ASCII)
        
        // Calculate HOTP values manually
        for ((counter, expectedOtp) in hotpTestVectors) {
            val calculatedOtp = calculateHOTP(secretBytes, counter, 6, "HmacSHA1")
            assertEquals("RFC 4226 test vector should match for counter=$counter", 
                expectedOtp, calculatedOtp)
        }
    }
    
    /**
     * Test vectors from RFC 6238 for TOTP (SHA1 only)
     */
    @Test
    fun testRFC6238TestVectors_SHA1() {
        // Test vectors from RFC 6238 (Appendix B)
        val testSecret = "12345678901234567890"  // ASCII encoding of the secret
        val totpTestVectors = mapOf(
            59L to "94287082",       // 1970-01-01 00:00:59
            1111111109L to "07081804", // 2005-03-18 01:58:29
            1111111111L to "14050471", // 2005-03-18 01:58:31
            1234567890L to "89005924", // 2009-02-13 23:31:30
            2000000000L to "69279037", // 2033-05-18 03:33:20
            20000000000L to "65353130"  // 2603-10-11 11:33:20
        )
        
        // Convert ASCII secret to bytes
        val secretBytes = testSecret.toByteArray(Charsets.US_ASCII)
        
        // Calculate TOTP values manually
        for ((timeInSeconds, expectedOtp) in totpTestVectors) {
            // RFC 6238 uses 30-second time step
            val counter = timeInSeconds / 30
            val calculatedOtp = calculateHOTP(secretBytes, counter, 8, "HmacSHA1")
            assertEquals("RFC 6238 SHA1 test vector should match for time=$timeInSeconds", 
                expectedOtp, calculatedOtp)
        }
    }
    
    /**
     * Test vectors from RFC 6238 for TOTP (SHA256)
     */
    @Test
    fun testRFC6238TestVectors_SHA256() {
        // Test vectors from RFC 6238 (Appendix B) - different secret for SHA256
        val testSecret = "12345678901234567890123456789012"  // 32 bytes
        val totpTestVectors = mapOf(
            59L to "46119246",       // 1970-01-01 00:00:59
            1111111109L to "68084774", // 2005-03-18 01:58:29
            1111111111L to "67062674", // 2005-03-18 01:58:31
            1234567890L to "91819424", // 2009-02-13 23:31:30
            2000000000L to "90698825", // 2033-05-18 03:33:20
            20000000000L to "77737706"  // 2603-10-11 11:33:20
        )
        
        // Convert ASCII secret to bytes
        val secretBytes = testSecret.toByteArray(Charsets.US_ASCII)
        
        // Calculate TOTP values manually
        for ((timeInSeconds, expectedOtp) in totpTestVectors) {
            // RFC 6238 uses 30-second time step
            val counter = timeInSeconds / 30
            val calculatedOtp = calculateHOTP(secretBytes, counter, 8, "HmacSHA256")
            assertEquals("RFC 6238 SHA256 test vector should match for time=$timeInSeconds", 
                expectedOtp, calculatedOtp)
        }
    }
    
    /**
     * Test vectors from RFC 6238 for TOTP (SHA512)
     */
    @Test
    fun testRFC6238TestVectors_SHA512() {
        // Test vectors from RFC 6238 (Appendix B) - different secret for SHA512
        val testSecret = "1234567890123456789012345678901234567890123456789012345678901234"  // 64 bytes
        val totpTestVectors = mapOf(
            59L to "90693936",       // 1970-01-01 00:00:59
            1111111109L to "25091201", // 2005-03-18 01:58:29
            1111111111L to "99943326", // 2005-03-18 01:58:31
            1234567890L to "93441116", // 2009-02-13 23:31:30
            2000000000L to "38618901", // 2033-05-18 03:33:20
            20000000000L to "47863826"  // 2603-10-11 11:33:20
        )
        
        // Convert ASCII secret to bytes
        val secretBytes = testSecret.toByteArray(Charsets.US_ASCII)
        
        // Calculate TOTP values manually
        for ((timeInSeconds, expectedOtp) in totpTestVectors) {
            // RFC 6238 uses 30-second time step
            val counter = timeInSeconds / 30
            val calculatedOtp = calculateHOTP(secretBytes, counter, 8, "HmacSHA512")
            assertEquals("RFC 6238 SHA512 test vector should match for time=$timeInSeconds", 
                expectedOtp, calculatedOtp)
        }
    }
    
    /**
     * Test that our Base32 decoding matches the ForgeRock implementation
     */
    @Test
    fun testBase32Decoding() {
        val testSecret = "JBSWY3DPEHPK3PXP"
        val expectedBytes = byteArrayOf(72, 101, 108, 108, 111, 33, -34, -83, -66, -17)
        
        // Decode using Apache Commons Base32
        val base32 = Base32()
        val decodedBytes = base32.decode(testSecret.uppercase())
        
        // Check byte array length
        assertEquals("Decoded byte array length should match", 
            expectedBytes.size, decodedBytes.size)
        
        // Check each byte
        for (i in expectedBytes.indices) {
            assertEquals("Decoded byte at index $i should match", 
                expectedBytes[i], decodedBytes[i])
        }
    }
    
    /**
     * Test against the OathAlgorithmHelper implementation
     */
    @Test
    fun testAgainstOathAlgorithmHelper() {
        // Create a credential
        val credential = OathCredential(
            issuer = "Test Issuer",
            displayIssuer = "Test Issuer",
            accountName = "test@example.com",
            displayAccountName = "test@example.com",
            oathType = OathType.HOTP,
            secret = "JBSWY3DPEHPK3PXP",
            oathAlgorithm = OathAlgorithm.SHA1,
            digits = 6,
            counter = 42L
        )
        
        // Generate code using OathAlgorithmHelper
        val generatedCode = OathAlgorithmHelper.generateCode(credential).code
        
        // Generate code manually
        val base32 = Base32()
        val secretBytes = base32.decode("JBSWY3DPEHPK3PXP".uppercase())
        val manualCode = calculateHOTP(secretBytes, 43L, 6, "HmacSHA1")
        
        // Verify they match
        assertEquals("OathAlgorithmHelper output should match manual calculation", 
            manualCode, generatedCode)
    }
    
    /**
     * Manual implementation of the HOTP algorithm for verification.
     * This mimics what should be happening inside OathAlgorithmHelper.
     */
    private fun calculateHOTP(secret: ByteArray, counter: Long, digits: Int, algorithm: String): String {
        // Convert counter to bytes (8 bytes, big-endian)
        val counterBytes = ByteBuffer.allocate(8)
            .putLong(counter)
            .array()
        
        // Calculate HMAC hash
        val mac = Mac.getInstance(algorithm)
        mac.init(SecretKeySpec(secret, algorithm))
        val hash = mac.doFinal(counterBytes)
        
        // Get offset from last 4 bits of the hash
        val offset = hash.last().toInt() and 0x0f
        
        // Get 4 bytes from the hash starting at offset
        val truncatedHash = ByteBuffer.wrap(hash, offset, 4).int and 0x7fffffff
        
        // Calculate modulus to get the code with the desired number of digits
        val modulus = 10.0.pow(digits.toDouble()).toInt()
        val code = truncatedHash % modulus
        
        // Format the code with leading zeros if necessary
        return String.format("%0${digits}d", code)
    }
}

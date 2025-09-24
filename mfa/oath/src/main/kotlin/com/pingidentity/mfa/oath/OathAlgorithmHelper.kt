/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.oath

import com.pingidentity.logger.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.codec.binary.Base32
import java.nio.ByteBuffer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.floor
import kotlin.math.pow

/**
 * Helper class for OATH algorithm operations, including code generation.
 * 
 * Used by OathService to perform the cryptographic operations needed for OATH.
 */
internal object OathAlgorithmHelper {
    
    /**
     * Generate an OTP code for an OATH credential.
     *
     * @param credential The OathCredential to generate code for.
     * @param logger Optional logger for logging events during code generation
     * @return OathCodeInfo containing the code and validity information.
     */
    suspend fun generateCode(credential: OathCredential, logger: Logger? = null): OathCodeInfo = withContext(Dispatchers.Default) {
        return@withContext when (credential.oathType) {
            OathType.TOTP -> generateTotpCode(credential, logger)
            OathType.HOTP -> generateHotpCode(credential, logger)
        }
    }
    
    /**
     * Generate a TOTP code for a credential.
     *
     * @param credential The TOTP credential.
     * @param logger Optional logger for logging events during code generation
     * @return OathCodeInfo for TOTP.
     */
    private fun generateTotpCode(credential: OathCredential, logger: Logger? = null): OathCodeInfo {
        val period = credential.period
        val now = System.currentTimeMillis() / 1000
        val counter = floor(now.toDouble() / period).toLong()
        
        val code = generateOtpCode(credential, counter, logger)
        
        // Calculate time remaining in the current period
        val nextPeriodStart = (counter + 1) * period
        val timeRemaining = (nextPeriodStart - now).toInt()
        
        return OathCodeInfo.forTotp(
            code = code,
            timeRemaining = timeRemaining,
            totalPeriod = period
        )
    }
    
    /**
     * Generate a HOTP code for a credential.
     *
     * @param credential The HOTP credential.
     * @param logger Optional logger for logging events during code generation
     * @return OathCodeInfo for HOTP.
     */
    private fun generateHotpCode(credential: OathCredential, logger: Logger? = null): OathCodeInfo {
        val counter = credential.counter + 1 
        val code = generateOtpCode(credential, counter, logger) // Use incremented counter for code generation
        
        return OathCodeInfo.forHotp(
            code = code,
            counter = counter // Return the incremented counter
        )
    }
    
    /**
     * Generate an OTP code using the specified algorithm and counter.
     *
     * @param credential The OathCredential containing the algorithm settings.
     * @param counter The counter value to use.
     * @param logger Optional logger for logging events during code generation
     * @return The generated code.
     */
    private fun generateOtpCode(credential: OathCredential, counter: Long, logger: Logger? = null): String {
        try {
            // Decode the base32-encoded secret
            val base32 = Base32()
            val key = base32.decode(credential.secret.uppercase())
            
            // Get the appropriate MAC algorithm
            val algorithm = getMacAlgorithm(credential.oathAlgorithm)
            val mac = Mac.getInstance(algorithm)
            mac.init(SecretKeySpec(key, algorithm))
            
            // Convert counter to bytes (8 bytes, big-endian)
            val counterBytes = ByteBuffer.allocate(8)
                .putLong(counter)
                .array()
            
            // Calculate HMAC hash
            val hash = mac.doFinal(counterBytes)
            
            // Get offset from last 4 bits of the hash
            val offset = hash.last().toInt() and 0x0f
            
            // Get 4 bytes from the hash starting at offset
            val truncatedHash = ByteBuffer.wrap(hash, offset, 4).int and 0x7fffffff
            
            // Calculate modulus to get the code with the desired number of digits
            val modulus = 10.0.pow(credential.digits).toInt()
            val code = truncatedHash % modulus
            
            // Format the code with leading zeros if necessary
            return String.format("%0${credential.digits}d", code)
        } catch (exception: Exception) {
            logger?.e("Error generating OTP code: ${exception.message}", exception)
            throw IllegalStateException("Failed to generate OTP code", exception)
        }
    }
    
    /**
     * Get the MAC algorithm name from the credential's algorithm.
     *
     * @param algorithm The OathAlgorithm enum value.
     * @return The MAC algorithm name.
     */
    private fun getMacAlgorithm(algorithm: OathAlgorithm): String {
        return when (algorithm) {
            OathAlgorithm.SHA1 -> "HmacSHA1"
            OathAlgorithm.SHA256 -> "HmacSHA256"
            OathAlgorithm.SHA512 -> "HmacSHA512"
        }
    }
}

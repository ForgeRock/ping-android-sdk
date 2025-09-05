package com.pingidentity.mfa.commons.policy

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for MfaPolicy implementations.
 * Tests individual policy behavior and configuration.
 */
@RunWith(RobolectricTestRunner::class)
class MfaPolicyTest {
    
    private lateinit var context: Context
    
    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }
    
    @Test
    fun `test biometric available policy name`() {
        val policy = BiometricAvailablePolicy()
        assertEquals("biometricAvailable", policy.getName())
    }
    
    @Test
    fun `test biometric available policy evaluate`() {
        val policy = BiometricAvailablePolicy()
        val result = policy.evaluate(context)
        
        // Result should be boolean (depends on test environment capabilities)
        assertTrue(result)
    }
    
    @Test
    fun `test biometric available policy with data`() {
        val policy = BiometricAvailablePolicy()
        policy.data = buildJsonObject { }
        
        val result = policy.evaluate(context)
        assertTrue(result)
    }
    
    @Test
    fun `test biometric available policy to string`() {
        val policy = BiometricAvailablePolicy()
        val description = policy.toString()
        assertTrue(description.contains("BiometricAvailablePolicy"))
        assertTrue(description.contains("biometricAvailable"))
    }
    
    @Test
    fun `test device tampering policy name`() {
        val policy = DeviceTamperingPolicy()
        assertEquals("deviceTampering", policy.getName())
    }
    
    @Test
    fun `test device tampering policy without data`() {
        val policy = DeviceTamperingPolicy()
        val result = policy.evaluate(context)
        
        // Default implementation should return true (not tampered)
        assertTrue(result)
    }
    
    @Test
    fun `test device tampering policy with data`() {
        val policy = DeviceTamperingPolicy()
        policy.data = buildJsonObject { 
            put("score", JsonPrimitive(0.8))
        }
        
        val result = policy.evaluate(context)
        
        // Should still return true since it's a placeholder
        assertTrue(result)
    }
    
    @Test
    fun `test device tampering policy to string`() {
        val policy = DeviceTamperingPolicy()
        val description = policy.toString()
        assertTrue(description.contains("DeviceTamperingPolicy"))
        assertTrue(description.contains("deviceTampering"))
    }
    
    @Test
    fun `test device tampering policy get score`() {
        val policy = DeviceTamperingPolicy()
        policy.data = buildJsonObject { 
            put("score", JsonPrimitive(0.75))
            put("enabled", JsonPrimitive(true))
        }
        
        assertNotNull(policy.data)
        val scoreElement = policy.data!!["score"]
        val enabledElement = policy.data!!["enabled"]
        
        assertNotNull(scoreElement)
        assertNotNull(enabledElement)
    }
    
    @Test
    fun `test device tampering policy default score`() {
        val policy = DeviceTamperingPolicy()
        
        val result = policy.evaluate(context)
        assertTrue(result)
    }
    
    @Test
    fun `test policy equality`() {
        val policy1 = BiometricAvailablePolicy()
        val policy2 = BiometricAvailablePolicy()
        
        // Policies with same name should be considered equal
        assertEquals(policy1.getName(), policy2.getName())
    }
    
    @Test
    fun `test policy difference`() {
        val biometricPolicy = BiometricAvailablePolicy()
        val tamperingPolicy = DeviceTamperingPolicy()
        
        // Different policies should have different names
        assertNotEquals(biometricPolicy.getName(), tamperingPolicy.getName())
    }
    
    @Test
    fun `test device tampering policy with complex data`() {
        val policy = DeviceTamperingPolicy()
        policy.data = buildJsonObject {
            put("score", JsonPrimitive(0.85))
            put("enabled", JsonPrimitive(true))
            put("metadata", buildJsonObject {
                put("version", JsonPrimitive("1.0"))
                put("lastUpdated", JsonPrimitive("2023-01-01"))
            })
        }
        
        val result = policy.evaluate(context)
        assertTrue(result)
        
        assertNotNull(policy.data)
        val metadata = policy.data!!["metadata"]
        
        assertNotNull(metadata)
        assertTrue(result)
    }
}

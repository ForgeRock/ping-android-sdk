package com.pingidentity.mfa.commons.policy

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
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
        val policy = BiometricAvailablePolicy
        assertEquals("biometricAvailable", policy.getName())
    }
    
    @Test
    fun `test biometric available policy evaluate`() = runTest {
        val policy = BiometricAvailablePolicy
        val result = policy.evaluate(context, null)
        
        // Result should be boolean (depends on test environment capabilities)
        assertTrue(result)
    }
    
    @Test
    fun `test biometric available policy with data`() = runTest {
        val policy = BiometricAvailablePolicy
        val data = buildJsonObject { }
        
        val result = policy.evaluate(context, data)
        assertTrue(result)
    }
    
    @Test
    fun `test biometric available policy to string`() {
        val policy = BiometricAvailablePolicy
        val description = policy.toString()
        assertTrue(description.contains("BiometricAvailablePolicy"))
        assertTrue(description.contains("biometricAvailable"))
    }
    
    @Test
    fun `test device tampering policy name`() {
        val policy = DeviceTamperingPolicy
        assertEquals("deviceTampering", policy.getName())
    }
    
    @Test
    fun `test device tampering policy without data`() = runTest {
        val policy = DeviceTamperingPolicy
        val result = policy.evaluate(context, null)
        
        // Default implementation should return true (not tampered)
        assertTrue(result)
    }
    
    @Test
    fun `test device tampering policy with data`() = runTest {
        val policy = DeviceTamperingPolicy
        val data = buildJsonObject { 
            put("score", JsonPrimitive(0.8))
        }
        
        val result = policy.evaluate(context, data)
        
        // Should still return true since it's a placeholder
        assertTrue(result)
    }
    
    @Test
    fun `test device tampering policy to string`() {
        val policy = DeviceTamperingPolicy
        val description = policy.toString()
        assertTrue(description.contains("DeviceTamperingPolicy"))
        assertTrue(description.contains("deviceTampering"))
    }
    
    @Test
    fun `test device tampering policy data handling`() = runTest {
        val policy = DeviceTamperingPolicy
        val data = buildJsonObject { 
            put("score", JsonPrimitive(0.75))
            put("enabled", JsonPrimitive(true))
        }
        
        // Test that the policy can handle the data correctly
        val result = policy.evaluate(context, data)
        assertTrue(result)
    }
    
    @Test
    fun `test device tampering policy default score`() = runTest {
        val policy = DeviceTamperingPolicy
        
        val result = policy.evaluate(context, null)
        assertTrue(result)
    }
    
    @Test
    fun `test policy equality`() {
        val policy1 = BiometricAvailablePolicy
        val policy2 = BiometricAvailablePolicy
        
        // Objects should be the same reference
        assertEquals(policy1, policy2)
        assertEquals(policy1.getName(), policy2.getName())
    }
    
    @Test
    fun `test policy difference`() {
        val biometricPolicy = BiometricAvailablePolicy
        val tamperingPolicy = DeviceTamperingPolicy
        
        // Different policies should have different names
        assertNotEquals(biometricPolicy.getName(), tamperingPolicy.getName())
    }
    
    @Test
    fun `test device tampering policy with complex data`() = runTest {
        val policy = DeviceTamperingPolicy
        val data = buildJsonObject {
            put("score", JsonPrimitive(0.85))
            put("enabled", JsonPrimitive(true))
            put("metadata", buildJsonObject {
                put("version", JsonPrimitive("1.0"))
                put("lastUpdated", JsonPrimitive("2023-01-01"))
            })
        }
        
        val result = policy.evaluate(context, data)
        assertTrue(result)
        
        // Test that complex data structure doesn't break evaluation
        val metadata = data["metadata"]
        assertNotNull(metadata)
    }
}

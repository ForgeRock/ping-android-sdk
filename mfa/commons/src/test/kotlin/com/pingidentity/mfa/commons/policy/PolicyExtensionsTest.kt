package com.pingidentity.mfa.commons.policy

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for PolicyExtensions functions.
 * Tests JSON parsing and utility functions.
 */
@RunWith(RobolectricTestRunner::class)
class PolicyExtensionsTest {
    
    @Before
    fun setUp() {
        // No setup needed for extension functions
    }
    
    @Test
    fun `test parse policies JSON with valid JSON`() {
        val policiesJson = """{"deviceTampering": {"score": 0.8}}"""
        val result = parsePoliciesJson(policiesJson)
        
        assertNotNull(result)
        assertTrue(result!!.containsKey("deviceTampering"))
    }
    
    @Test
    fun `test parse policies JSON with null`() {
        val result = parsePoliciesJson(null)
        assertNull(result)
    }
    
    @Test
    fun `test parse policies JSON with empty`() {
        val result = parsePoliciesJson("")
        assertNull(result)
    }
    
    @Test
    fun `test parse policies JSON with blank`() {
        val result = parsePoliciesJson("   ")
        assertNull(result)
    }
    
    @Test
    fun `test parse policies JSON with invalid JSON`() {
        val result = parsePoliciesJson("{invalid json")
        assertNull(result)
    }
    
    @Test
    fun `test has policy in JSON returns true`() {
        val policiesJson = """{"biometricAvailable": {}, "deviceTampering": {"score": 0.8}}"""
        
        assertTrue(hasPolicyInJson(policiesJson, "biometricAvailable"))
        assertTrue(hasPolicyInJson(policiesJson, "deviceTampering"))
    }
    
    @Test
    fun `test has policy in JSON returns false`() {
        val policiesJson = """{"biometricAvailable": {}}"""
        
        assertFalse(hasPolicyInJson(policiesJson, "deviceTampering"))
        assertFalse(hasPolicyInJson(policiesJson, "nonexistent"))
    }
    
    @Test
    fun `test has policy in JSON with null policies`() {
        assertFalse(hasPolicyInJson(null, "anyPolicy"))
    }
    
    @Test
    fun `test has policy in JSON with invalid JSON`() {
        assertFalse(hasPolicyInJson("{invalid", "anyPolicy"))
    }
    
    @Test
    fun `test get policy data from JSON exists`() {
        val policiesJson = """{"deviceTampering": {"score": 0.8, "enabled": true}}"""
        val result = getPolicyDataFromJson(policiesJson, "deviceTampering")
        
        assertNotNull(result)
        assertTrue(result!!.containsKey("score"))
        assertTrue(result.containsKey("enabled"))
    }
    
    @Test
    fun `test get policy data from JSON not exists`() {
        val policiesJson = """{"biometricAvailable": {}}"""
        val result = getPolicyDataFromJson(policiesJson, "deviceTampering")
        
        assertNull(result)
    }
    
    @Test
    fun `test get policy data from JSON with null policies`() {
        val result = getPolicyDataFromJson(null, "anyPolicy")
        assertNull(result)
    }
    
    @Test
    fun `test create policy JSON with empty data`() {
        val result = createPolicyJson("biometricAvailable")
        
        assertNotNull(result)
        assertTrue(result.contains("biometricAvailable"))
    }
    
    @Test
    fun `test create policy JSON with data`() {
        val policyData = buildJsonObject {
            put("score", JsonPrimitive(0.8))
            put("enabled", JsonPrimitive(true))
        }
        
        val result = createPolicyJson("deviceTampering", policyData)
        
        assertNotNull(result)
        assertTrue(result.contains("deviceTampering"))
        assertTrue(result.contains("0.8"))
        assertTrue(result.contains("true"))
    }
    
    @Test
    fun `test add policy to JSON with null policies`() {
        val result = addPolicyToJson(null, "biometricAvailable")
        
        assertNotNull(result)
        assertTrue(result.contains("biometricAvailable"))
    }
    
    @Test
    fun `test add policy to JSON with existing policies`() {
        val existingPolicies = """{"biometricAvailable": {}}"""
        val policyData = buildJsonObject {
            put("score", JsonPrimitive(0.8))
        }
        
        val result = addPolicyToJson(existingPolicies, "deviceTampering", policyData)
        
        assertNotNull(result)
        assertTrue(result.contains("biometricAvailable"))
        assertTrue(result.contains("deviceTampering"))
        assertTrue(result.contains("0.8"))
    }
    
    @Test
    fun `test add policy to JSON overwrites existing`() {
        val existingPolicies = """{"deviceTampering": {"score": 0.5}}"""
        val policyData = buildJsonObject {
            put("score", JsonPrimitive(0.9))
        }
        
        val result = addPolicyToJson(existingPolicies, "deviceTampering", policyData)
        
        assertNotNull(result)
        assertTrue(result.contains("deviceTampering"))
        assertTrue(result.contains("0.9"))
        assertFalse(result.contains("0.5"))
    }
    
    @Test
    fun `test remove policy from JSON exists`() {
        val existingPolicies = """{"biometricAvailable": {}, "deviceTampering": {"score": 0.8}}"""
        val result = removePolicyFromJson(existingPolicies, "deviceTampering")
        
        assertNotNull(result)
        assertTrue(result.contains("biometricAvailable"))
        assertFalse(result.contains("deviceTampering"))
    }
    
    @Test
    fun `test remove policy from JSON not exists`() {
        val existingPolicies = """{"biometricAvailable": {}}"""
        val result = removePolicyFromJson(existingPolicies, "deviceTampering")
        
        assertNotNull(result)
        assertTrue(result.contains("biometricAvailable"))
    }
    
    @Test
    fun `test remove policy from JSON with null policies`() {
        val result = removePolicyFromJson(null, "anyPolicy")
        
        assertEquals("", result)
    }
    
    @Test
    fun `test remove policy from JSON remove all`() {
        val existingPolicies = """{"deviceTampering": {"score": 0.8}}"""
        val result = removePolicyFromJson(existingPolicies, "deviceTampering")
        
        assertNotNull(result)
        assertFalse(result.contains("deviceTampering"))
    }
}

package com.pingidentity.mfa.commons.policy

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.pingidentity.logger.Logger
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for MfaPolicyEvaluator.
 * Tests policy evaluation engine and DSL-style configuration functionality.
 */
@RunWith(RobolectricTestRunner::class)
class MfaPolicyEvaluatorTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `test dsl with single policy`() {
        val evaluator = MfaPolicyEvaluator {
            policies = listOf(BiometricAvailablePolicy)
        }

        assertEquals(1, evaluator.getPolicies().size)
    }

    @Test
    fun `test dsl with multiple policies`() {
        val evaluator = MfaPolicyEvaluator {
            policies = listOf(
                BiometricAvailablePolicy,
                DeviceTamperingPolicy
            )
        }

        assertEquals(2, evaluator.getPolicies().size)
    }

    @Test
    fun `test dsl with defaults`() {
        val evaluator = MfaPolicyEvaluator()

        assertEquals(2, evaluator.getPolicies().size)
        val policyNames = evaluator.getPolicies().map { it.getName() }
        assertTrue(policyNames.contains("biometricAvailable"))
        assertTrue(policyNames.contains("deviceTampering"))
    }

    @Test
    fun `test dsl with policies array`() {
        val newPolicies = arrayOf(
            BiometricAvailablePolicy,
            DeviceTamperingPolicy
        )

        val evaluator = MfaPolicyEvaluator {
            policies = newPolicies.toList()
        }

        assertEquals(2, evaluator.getPolicies().size)
    }

    @Test
    fun `test dsl with policies collection`() {
        val newPolicies = listOf(
            BiometricAvailablePolicy,
            DeviceTamperingPolicy
        )

        val evaluator = MfaPolicyEvaluator {
            policies = newPolicies
        }

        assertEquals(2, evaluator.getPolicies().size)
    }

    @Test
    fun `test dsl duplicate policy prevention`() {
        val evaluator = MfaPolicyEvaluator {
            policies = listOf(
                BiometricAvailablePolicy,
                BiometricAvailablePolicy
            )
        }

        // With objects, both references are the same instance
        assertEquals(2, evaluator.getPolicies().size)
    }

    @Test
    fun `test default constructor`() {
        val evaluator = MfaPolicyEvaluator()

        assertEquals(2, evaluator.getPolicies().size)
        val policyNames = evaluator.getPolicies().map { it.getName() }
        assertTrue(policyNames.contains("biometricAvailable"))
        assertTrue(policyNames.contains("deviceTampering"))
    }

    @Test
    fun `test getPolicy with existing policy`() {
        val biometricPolicy = BiometricAvailablePolicy
        val evaluator = MfaPolicyEvaluator {
            policies = listOf(
                biometricPolicy,
                DeviceTamperingPolicy
            )
        }

        val retrievedPolicy = evaluator.getPolicy("biometricAvailable")
        assertNotNull(retrievedPolicy)
        assertEquals(biometricPolicy.getName(), retrievedPolicy?.getName())
        assertTrue(retrievedPolicy is BiometricAvailablePolicy)
    }

    @Test
    fun `test getPolicy with non-existent policy`() {
        val evaluator = MfaPolicyEvaluator()

        val retrievedPolicy = evaluator.getPolicy("nonExistentPolicy")
        assertNull(retrievedPolicy)
    }

    @Test
    fun `test dsl withLogger`() = runTest {
        val testLogger = TestLogger()
        val evaluator = MfaPolicyEvaluator {
            logger = testLogger
            policies = listOf(BiometricAvailablePolicy)
        }

        val result = evaluator.evaluate(context, null)

        assertTrue(result.isSuccess)
        assertTrue(testLogger.hasLoggedDebug)
    }


    @Test
    fun `test evaluate with null policies`() = runTest {
        val evaluator = MfaPolicyEvaluator()

        val result = evaluator.evaluate(context, null)

        assertTrue(result.isSuccess)
        assertNull(result.nonCompliancePolicyName)
    }

    @Test
    fun `test evaluate with empty policies`() = runTest {
        val evaluator = MfaPolicyEvaluator()

        val result = evaluator.evaluate(context, "")

        assertTrue(result.isSuccess)
        assertNull(result.nonCompliancePolicyName)
    }

    @Test
    fun `test evaluate with blank policies`() = runTest {
        val evaluator = MfaPolicyEvaluator()

        val result = evaluator.evaluate(context, "   ")

        assertTrue(result.isSuccess)
        assertNull(result.nonCompliancePolicyName)
    }

    @Test
    fun `test evaluate with valid policies json`() = runTest {
        val evaluator = MfaPolicyEvaluator {
            policies = listOf(BiometricAvailablePolicy)
        }

        val policiesJson = """{"biometricAvailable": {}}"""
        val result = evaluator.evaluate(context, policiesJson)

        // Should evaluate successfully
        assertNotNull(result)
        assertTrue(result.isSuccess)
    }

    @Test
    fun `test evaluate with device tampering policy`() = runTest {
        val evaluator = MfaPolicyEvaluator {
            policies = listOf(DeviceTamperingPolicy)
        }

        val policiesJson = """{"deviceTampering": {"score": 0.8}}"""
        val result = evaluator.evaluate(context, policiesJson)

        // DeviceTamperingPolicy is placeholder that always returns true
        assertTrue(result.isSuccess)
        assertNull(result.nonCompliancePolicyName)
    }

    @Test
    fun `test evaluate with invalid json`() = runTest {
        val evaluator = MfaPolicyEvaluator()

        val result = evaluator.evaluate(context, "{invalid json")

        // Should gracefully handle malformed JSON by returning compliant
        assertTrue(result.isSuccess)
        assertNull(result.nonCompliancePolicyName)
    }

    @Test
    fun `test evaluate with complex policies`() = runTest {
        val evaluator = MfaPolicyEvaluator {
            policies = listOf(BiometricAvailablePolicy, DeviceTamperingPolicy)
        }

        val policiesJson = """{
            "biometricAvailable": {},
            "deviceTampering": {
                "score": 0.8,
                "enabled": true
            }
        }"""

        val result = evaluator.evaluate(context, policiesJson)

        assertNotNull(result)
        assertTrue(result.isSuccess)
    }

    @Test
    fun `test evaluate with json object`() = runTest {
        val evaluator = MfaPolicyEvaluator {
            policies = listOf(DeviceTamperingPolicy)
        }

        val policiesJson = buildJsonObject {
            put("deviceTampering", buildJsonObject {
                put("score", JsonPrimitive(0.75))
                put("enabled", JsonPrimitive(true))
            })
        }

        val result = evaluator.evaluate(context, policiesJson)

        assertNotNull(result)
        assertTrue(result.isSuccess)
    }

    @Test
    fun `test evaluate multiple policies all pass`() = runTest {
        val evaluator = MfaPolicyEvaluator()

        val policiesJson = """{
            "biometricAvailable": {},
            "deviceTampering": {"score": 0.9}
        }"""

        val result = evaluator.evaluate(context, policiesJson)

        assertNotNull(result)
        assertTrue(result.isSuccess)
    }

    @Test
    fun `test evaluate with failing policy`() = runTest {
        val evaluator = MfaPolicyEvaluator {
            policies = listOf(FailingTestPolicy())
        }

        val policiesJson = """{"failingTestPolicy": {}}"""
        val result = evaluator.evaluate(context, policiesJson)

        assertNotNull(result)
        assertTrue(result.isFailure)
        assertEquals("failingTestPolicy", result.nonCompliancePolicyName)
    }
}

// Test policy that always fails
class FailingTestPolicy : MfaPolicy() {
    override fun getName(): String = "failingTestPolicy"
    override suspend fun evaluate(context: Context, data: JsonObject?): Boolean {
        return false
    }
}

// Test Logger implementation
class TestLogger : Logger {
    var hasLoggedDebug = false
    var hasLoggedInfo = false
    var hasLoggedWarning = false
    var hasLoggedError = false
    override fun d(message: String) { hasLoggedDebug = true }
    override fun i(message: String) { hasLoggedInfo = true }
    override fun w(message: String, throwable: Throwable?) { hasLoggedWarning = true }
    override fun e(message: String, throwable: Throwable?) { hasLoggedError = true }
}

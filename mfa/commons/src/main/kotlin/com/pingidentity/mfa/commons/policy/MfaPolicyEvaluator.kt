/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.commons.policy

import android.content.Context
import com.pingidentity.logger.Logger
import com.pingidentity.mfa.commons.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * The MFA Policy Evaluator is used by the SDK to enforce Policy rules, such as Device Tampering
 * Policy. It consist of one or more [MfaPolicy] objects. Each Policy contain instructions that
 * determine whether it comply to a particular condition at a particular time.
 *
 * This class provides a DSL-style configuration and can evaluate policies against credentials to
 * determine compliance.
 */
class MfaPolicyEvaluator private constructor(
    private val policies: List<MfaPolicy>,
    private var logger: Logger
) {

    /**
     * Evaluate policies for a credential with embedded policy configuration.
     *
     * @param context The Android context for policy evaluation.
     * @param credentialPolicies JSON string containing policy configurations from credential.
     * @return MfaPolicyResult indicating compliance status.
     */
    suspend fun evaluate(context: Context, credentialPolicies: String?): MfaPolicyResult = withContext(Dispatchers.Default) {
        if (credentialPolicies.isNullOrBlank()) {
            logger.d("No policies configured, considering compliant by default.")
            return@withContext MfaPolicyResult.success()
        }

        return@withContext try {
            val policiesJson = json.parseToJsonElement(credentialPolicies).jsonObject
            evaluate(context, policiesJson)
        } catch (e: Exception) {
            logger.w("Malformed policies JSON: ${e.message}")
            // If policies JSON is malformed, consider as compliant to avoid blocking
            MfaPolicyResult.success()
        }
    }

    /**
     * Evaluate policies against a JSON configuration.
     *
     * @param context The Android context for policy evaluation.
     * @param policiesJson JSON object containing policy configurations.
     * @return MfaPolicyResult indicating compliance status.
     */
    suspend fun evaluate(context: Context, policiesJson: JsonObject): MfaPolicyResult = withContext(Dispatchers.Default) {
        for (policy in policies) {
            val policyName = policy.getName()

            // Check if this policy is configured in the JSON
            if (policiesJson.containsKey(policyName)) {
                try {
                    // Get the policy data from JSON configuration
                    val policyData = policiesJson[policyName]?.jsonObject

                    // Evaluate the policy with data as parameter
                    if (!policy.evaluate(context, policyData)) {
                        logger.d("Policy '$policyName' evaluation failed.")
                        return@withContext MfaPolicyResult.failure(policy)
                    }
                } catch (e: Exception) {
                    logger.w("Error evaluating policy '$policyName': ${e.message}")
                    // If policy configuration is malformed, skip it
                    continue
                }
            }
        }

        logger.d("All policies evaluated successfully.")
        return@withContext MfaPolicyResult.success()
    }

    /**
     * Get all available policies in this evaluator.
     *
     * @return List of all configured policies.
     */
    fun getPolicies(): List<MfaPolicy> {
        return policies.toList()
    }

    /**
     * Get a specific policy by name.
     *
     * @param policyName The name of the policy to retrieve.
     * @return The policy with the given name, or null if not found.
     */
    fun getPolicy(policyName: String): MfaPolicy? {
        return policies.find { it.getName() == policyName }
    }

    /**
     * Config class for constructing MfaPolicyEvaluator instances.
     */
    class Config {
        var policies: List<MfaPolicy> = listOf(BiometricAvailablePolicy, DeviceTamperingPolicy)
        var logger: Logger = Logger.logger
    }

    companion object {
        /**
         * Create a new MfaPolicyEvaluator using a DSL-style configuration.
         *
         * @return A new MfaPolicyEvaluator instance.
         */
        operator fun invoke(block: Config.() -> Unit = {}): MfaPolicyEvaluator {
            val config = Config().apply(block)
            return MfaPolicyEvaluator(config.policies, config.logger)
        }
    }
}
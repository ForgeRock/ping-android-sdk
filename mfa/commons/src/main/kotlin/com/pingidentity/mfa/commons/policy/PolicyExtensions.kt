/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.commons.policy

import com.pingidentity.mfa.commons.json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Extension functions for working with policy-related JSON strings.
 * These utilities help parse and manipulate policy configurations.
 */

/**
 * Parse a policies JSON string into a JsonObject.
 * 
 * @param policies JSON string containing policy configurations.
 * @return JsonObject parsed from the string, or null if invalid/empty.
 */
fun parsePoliciesJson(policies: String?): JsonObject? {
    if (policies.isNullOrBlank()) {
        return null
    }
    
    return try {
        json.parseToJsonElement(policies).jsonObject
    } catch (e: Exception) {
        null
    }
}

/**
 * Check if a specific policy exists in a policies JSON string.
 * 
 * @param policies JSON string containing policy configurations.
 * @param policyName Name of the policy to check for.
 * @return true if the policy exists in the configuration.
 */
fun hasPolicyInJson(policies: String?, policyName: String): Boolean {
    val policiesJson = parsePoliciesJson(policies) ?: return false
    return policiesJson.containsKey(policyName)
}

/**
 * Get policy configuration data from a policies JSON string.
 * 
 * @param policies JSON string containing policy configurations.
 * @param policyName Name of the policy to retrieve configuration for.
 * @return JsonObject containing policy configuration, or null if not found.
 */
fun getPolicyDataFromJson(policies: String?, policyName: String): JsonObject? {
    val policiesJson = parsePoliciesJson(policies) ?: return null
    
    return try {
        if (policiesJson.containsKey(policyName)) {
            policiesJson[policyName]?.jsonObject
        } else {
            null
        }
    } catch (e: Exception) {
        null
    }
}

/**
 * Create a JSON string with a single policy configuration.
 * 
 * @param policyName Name of the policy.
 * @param policyData Configuration data for the policy.
 * @return JSON string representation.
 */
fun createPolicyJson(policyName: String, policyData: JsonObject = buildJsonObject {}): String {
    val policies = buildJsonObject {
        put(policyName, policyData)
    }
    return json.encodeToString(JsonObject.serializer(), policies)
}

/**
 * Add or update a policy in a policies JSON string.
 * 
 * @param policies Existing policies JSON string.
 * @param policyName Name of the policy to add/update.
 * @param policyData Configuration data for the policy.
 * @return Updated policies JSON string.
 */
fun addPolicyToJson(policies: String?, policyName: String, policyData: JsonObject = buildJsonObject {}): String {
    val existingPolicies = parsePoliciesJson(policies) ?: buildJsonObject {}
    val updatedPolicies = buildJsonObject {
        // Copy existing policies
        for ((key, value) in existingPolicies) {
            put(key, value)
        }
        // Add or update the new policy
        put(policyName, policyData)
    }
    return json.encodeToString(JsonObject.serializer(), updatedPolicies)
}

/**
 * Remove a policy from a policies JSON string.
 * 
 * @param policies Existing policies JSON string.
 * @param policyName Name of the policy to remove.
 * @return Updated policies JSON string.
 */
fun removePolicyFromJson(policies: String?, policyName: String): String {
    val existingPolicies = parsePoliciesJson(policies) ?: return ""
    val updatedPolicies = buildJsonObject {
        for ((key, value) in existingPolicies) {
            if (key != policyName) {
                put(key, value)
            }
        }
    }
    return json.encodeToString(JsonObject.serializer(), updatedPolicies)
}
/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.commons.policy

/**
 * Represents the result of policy evaluation for a credential.
 * 
 * This class encapsulates both the compliance status and identifies
 * which policy (if any) caused non-compliance.
 * 
 * @property isCompliant true if all policies are satisfied, false otherwise.
 * @property nonComplianceMfaPolicy The policy that failed evaluation, null if all policies passed.
 */
data class MfaPolicyResult(
    private val isCompliant: Boolean,
    private val nonComplianceMfaPolicy: MfaPolicy? = null
) {

    /**
     * Returns true if the credential is compliant with all policies.
     */
    val isSuccess: Boolean
        get() = isCompliant

    /**
     * Returns true if the credential failed at least one policy.
     */
    val isFailure: Boolean
        get() = !isCompliant

    /**
     * Returns the name of the non-compliant policy, or null if compliant.
     */
    val nonCompliancePolicyName: String?
        get() = nonComplianceMfaPolicy?.getName()


    /**
     * Returns the non-compliant policy instance, or null if compliant.
     */
    val nonCompliancePolicy: MfaPolicy?
        get() = nonComplianceMfaPolicy
    
    companion object {
        /**
         * Create a successful policy evaluation result.
         * 
         * @return A compliant MfaPolicyResult.
         */
        fun success(): MfaPolicyResult {
            return MfaPolicyResult(isCompliant = true)
        }
        
        /**
         * Create a failed policy evaluation result.
         * 
         * @param policy The policy that failed evaluation.
         * @return A non-compliant MfaPolicyResult.
         */
        fun failure(policy: MfaPolicy): MfaPolicyResult {
            return MfaPolicyResult(isCompliant = false, nonComplianceMfaPolicy = policy)
        }
    }
}
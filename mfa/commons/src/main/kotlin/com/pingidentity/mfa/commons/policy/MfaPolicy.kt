/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.commons.policy

import android.content.Context
import kotlinx.serialization.json.JsonObject

/**
 * Base abstract class for all MFA policies.
 * 
 * MFA policies provide a way to enforce security requirements before allowing
 * credential operations. Each policy can evaluate device state and return
 * whether the current conditions meet security requirements.
 */
abstract class MfaPolicy {
    
    /**
     * Get the unique name identifier for this policy.
     * 
     * @return The policy name as a string.
     */
    abstract fun getName(): String
    
    /**
     * Evaluate this policy against the current device context.
     * 
     * @param context The Android context for accessing system services.
     * @param data Configuration data for this policy, parsed from JSON policies string.
     * @return true if the policy requirements are met, false otherwise.
     */
    abstract suspend fun evaluate(context: Context, data: JsonObject?): Boolean
    
    /**
     * Returns a string representation of this policy.
     */
    override fun toString(): String {
        return "${this::class.simpleName}(name='${getName()}')"
    }
}
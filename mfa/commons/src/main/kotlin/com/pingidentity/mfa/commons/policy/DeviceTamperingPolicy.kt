/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.commons.policy

import android.content.Context
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import com.pingidentity.device.analyze

/**
 * Policy that checks for device tampering indicators.
 *
 * Uses the TamperDetector module to analyze the device for signs of rooting or tampering
 * and compares the result against a configurable threshold.
 * 
 * JSON format: {"deviceTampering": {"score": 0.8}}
 * 
 * The score threshold can be configured in the policy data. If the device
 * tampering score exceeds the threshold, the policy will fail.
 */
object DeviceTamperingPolicy : MfaPolicy() {
    
    const val POLICY_NAME = "deviceTampering"
    private const val DEFAULT_THRESHOLD = 0.8
    
    override fun getName(): String {
        return POLICY_NAME
    }
    
    override suspend fun evaluate(context: Context, data: JsonObject?): Boolean {
        // Get the threshold from policy configuration data
        val threshold = data?.get("score")?.jsonPrimitive?.doubleOrNull ?: DEFAULT_THRESHOLD
        
        // Use a device tampering detection library to get the score
        val deviceTamperingScore = analyze()
        
        // Return true if device tampering score is below threshold
        return deviceTamperingScore < threshold
    }
}
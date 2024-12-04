/*
 * Copyright (c) 2024 Ping Identity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci

import com.pingidentity.davinci.collector.PasswordPolicy
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class PasswordPolicyJsonTest {

    @Test
    fun convertsJsonStringToPasswordPolicy() {
        val jsonString = """
            {
                "name": "Test Policy",
                "description": "A test password policy",
                "excludesProfileData": true,
                "notSimilarToCurrent": true,
                "excludesCommonlyUsed": true,
                "maxAgeDays": 90,
                "minAgeDays": 1,
                "maxRepeatedCharacters": 2,
                "minUniqueCharacters": 3,
                "history": {
                    "count": 5,
                    "retentionDays": 365
                },
                "lockout": {
                    "failureCount": 3,
                    "durationSeconds": 600
                },
                "length": {
                    "min": 8,
                    "max": 20
                },
                "minCharacters": {
                    "digits": 1,
                    "special": 1
                },
                "populationCount": 1000,
                "createdAt": "2024-01-01T00:00:00Z",
                "updatedAt": "2024-01-02T00:00:00Z",
                "default": true
            }
        """
        val policy = Json.decodeFromString<PasswordPolicy>(jsonString)

        assertEquals("Test Policy", policy.name)
        assertEquals("A test password policy", policy.description)
        assertEquals(true, policy.excludesProfileData)
        assertEquals(true, policy.notSimilarToCurrent)
        assertEquals(true, policy.excludesCommonlyUsed)
        assertEquals(90, policy.maxAgeDays)
        assertEquals(1, policy.minAgeDays)
        assertEquals(2, policy.maxRepeatedCharacters)
        assertEquals(3, policy.minUniqueCharacters)
        assertEquals(5, policy.history?.count)
        assertEquals(365, policy.history?.retentionDays)
        assertEquals(3, policy.lockout?.failureCount)
        assertEquals(600, policy.lockout?.durationSeconds)
        assertEquals(8, policy.length.min)
        assertEquals(20, policy.length.max)
        assertEquals(1, policy.minCharacters["digits"])
        assertEquals(1, policy.minCharacters["special"])
        assertEquals(1000, policy.populationCount)
        assertEquals("2024-01-01T00:00:00Z", policy.createdAt)
        assertEquals("2024-01-02T00:00:00Z", policy.updatedAt)
        assertEquals(true, policy.default)
    }

    @Test
    fun convertsEmptyJsonStringToPasswordPolicyWithDefaultValues() {
        val jsonString = "{}"
        val policy = Json.decodeFromString<PasswordPolicy>(jsonString)

        assertEquals("", policy.name)
        assertEquals("", policy.description)
        assertEquals(false, policy.excludesProfileData)
        assertEquals(false, policy.notSimilarToCurrent)
        assertEquals(false, policy.excludesCommonlyUsed)
        assertEquals(0, policy.maxAgeDays)
        assertEquals(0, policy.minAgeDays)
        assertEquals(Int.MAX_VALUE, policy.maxRepeatedCharacters)
        assertEquals(0, policy.minUniqueCharacters)
        assertEquals(null, policy.history)
        assertEquals(null, policy.lockout)
        assertEquals(0, policy.length.min)
        assertEquals(Int.MAX_VALUE, policy.length.max)
        assertEquals(emptyMap(), policy.minCharacters)
        assertEquals(0, policy.populationCount)
        assertEquals("", policy.createdAt)
        assertEquals("", policy.updatedAt)
        assertEquals(false, policy.default)
    }
}
/*
 * Copyright (c) 2025 Ping Identity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci.collector

import kotlinx.serialization.Serializable

/**
 * Data class representing a password policy.
 */
@Serializable
data class PasswordPolicy(
    val name: String = "",
    val description: String = "",
    val excludesProfileData: Boolean = false,
    val notSimilarToCurrent: Boolean = false,
    val excludesCommonlyUsed: Boolean = false,
    val maxAgeDays: Int = 0,
    val minAgeDays: Int = 0,
    val maxRepeatedCharacters: Int = Int.MAX_VALUE,
    val minUniqueCharacters: Int = 0,
    val history: History? = null,
    val lockout: Lockout? = null,
    val length: Length = Length(0, Int.MAX_VALUE),
    val minCharacters: Map<String, Int> = emptyMap(),
    val populationCount: Int = 0,
    val createdAt: String = "",
    val updatedAt: String = "",
    val default: Boolean = false
)

/**
 * Data class representing a password policy history.
 */
@Serializable
data class History(
    val count: Int,
    val retentionDays: Int
)

/**
 * Data class representing a password policy lockout.
 */
@Serializable
data class Lockout(
    val failureCount: Int,
    val durationSeconds: Int
)

/**
 * Data class representing a password policy length.
 */
@Serializable
data class Length(
    val min: Int,
    val max: Int
)

/**
 * Sealed class representing Invalid length error.
 */
data class InvalidLength(val min: Int, val max: Int) : ValidationError()

/**
 * Sealed class representing Unique character error.
 */
data class UniqueCharacter(val min: Int) : ValidationError()

/**
 * Sealed class representing max repeat error.
 */
data class MaxRepeat(val max: Int) : ValidationError()

/**
 * Sealed class representing min character error.
 */
data class MinCharacters(val character: String, val min: Int) : ValidationError()

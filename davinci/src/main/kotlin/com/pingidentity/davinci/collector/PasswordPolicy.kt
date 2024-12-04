/*
 * Copyright (c) 2024 PingIdentity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci.collector

import kotlinx.serialization.Serializable

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

@Serializable
data class History(
    val count: Int,
    val retentionDays: Int
)

@Serializable
data class Lockout(
    val failureCount: Int,
    val durationSeconds: Int
)

@Serializable
data class Length(
    val min: Int,
    val max: Int
)

data class InvalidLength(val min: Int, val max: Int): ValidationError()
data class UniqueCharacter(val min: Int): ValidationError()
data class MaxRepeat(val max: Int): ValidationError()
data class MinCharacters(val character: String, val min: Int): ValidationError()

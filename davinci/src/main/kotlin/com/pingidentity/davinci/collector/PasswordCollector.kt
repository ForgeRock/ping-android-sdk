/*
 * Copyright (c) 2024 Ping Identity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci.collector

import com.pingidentity.davinci.json
import com.pingidentity.davinci.plugin.ContinueNodeAware
import com.pingidentity.orchestrate.Closeable
import com.pingidentity.orchestrate.ContinueNode
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject

/**
 * Class representing a PASSWORD Type.
 *
 * This class inherits from the FieldCollector class and implements the Closeable and Collector interfaces.
 * It is used to collect password data.
 *
 * @constructor Creates a new PasswordCollector with the given input.
 */
class PasswordCollector : ValidatedCollector(), ContinueNodeAware, Closeable {

    /**
     * The continue node for the DaVinci flow.
     */
    override lateinit var continueNode: ContinueNode
    private var cachedPasswordPolicy: PasswordPolicy? = null

    // A flag to determine whether to clear the password or not after submission.
    var clearPassword = true

    /**
     * Overrides the close function from the Closeable interface.
     * It is used to clear the value of the password field when the collector is closed.
     * If the clearPassword flag is set to true, the value of the password field will be cleared.
     */
    override fun close() {
        if (clearPassword) value = ""
    }

    /**
     * Function to retrieve the password policy, if available.
     */
    fun passwordPolicy(): PasswordPolicy? {
        if (cachedPasswordPolicy == null) {
            cachedPasswordPolicy = continueNode.input["passwordPolicy"]?.jsonObject?.let {
                json.decodeFromJsonElement(it)
            }
        }
        return cachedPasswordPolicy

    }

    /**
     * Function to validate the password field.
     * @return A list of validation errors.
     */
    override fun validate(): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()
        val result = super.validate()
        errors.addAll(result)

        passwordPolicy()?.let { policy ->
            if (value.length !in policy.length.min..policy.length.max) {
                errors.add(InvalidLength(policy.length.min, policy.length.max))
            }
            if (value.toSet().size < policy.minUniqueCharacters) {
                errors.add(UniqueCharacter(policy.minUniqueCharacters))
            }
            val maxRepeated =
                value.groupingBy { it }.eachCount().values.maxOrNull()
                    ?: 0
            if (maxRepeated > policy.maxRepeatedCharacters) {
                errors.add(MaxRepeat(policy.maxRepeatedCharacters))
            }
            for ((chars, minCount) in policy.minCharacters) {
                if (value.count { it in chars } < minCount) {
                    errors.add(MinCharacters(chars, minCount))
                }
            }
        }
        return errors
    }
}
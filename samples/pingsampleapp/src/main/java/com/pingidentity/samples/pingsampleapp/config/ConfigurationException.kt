/*
 * Copyright (c) 2024 - 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.samples.pingsampleapp.config

/**
 * Exception hierarchy for configuration load and validation failures.
 *
 * - [Malformed] — JSON could not be deserialized at all.
 * - [MissingField] — a required field was absent from a configuration entry.
 * - [InvalidValue] — a field was present but did not pass semantic validation.
 * - [DuplicateName] — two entries share the same [Configuration.name].
 */
sealed class ConfigurationException(message: String, cause: Throwable? = null) :
    Exception(message, cause) {

    /**
     * The JSON input could not be parsed.
     *
     * @param cause The underlying [kotlinx.serialization.SerializationException].
     */
    class Malformed(cause: Throwable) :
        ConfigurationException("Configuration JSON could not be parsed", cause)

    /**
     * A required field was absent from a configuration entry.
     *
     * @param configName The [Configuration.name] of the offending entry, or `"unknown"` when the
     *   name itself could not be decoded.
     * @param fieldName The name of the missing field.
     */
    class MissingField(val configName: String, val fieldName: String) :
        ConfigurationException("Configuration '$configName' is missing required field '$fieldName'")

    /**
     * A field value did not pass semantic validation.
     *
     * @param configName The [Configuration.name] of the offending entry.
     * @param fieldName The name of the invalid field.
     * @param value The invalid value as a string.
     * @param reason Human-readable explanation of why the value is invalid.
     */
    class InvalidValue(
        val configName: String,
        val fieldName: String,
        val value: String,
        val reason: String,
    ) : ConfigurationException(
        "Configuration '$configName' has invalid value '$value' for '$fieldName': $reason"
    )

    /**
     * Two configuration entries share the same [Configuration.name].
     *
     * @param name The duplicated name.
     */
    class DuplicateName(val name: String) :
        ConfigurationException("Duplicate configuration name '$name'")
}

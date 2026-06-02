/*
 * Copyright (c) 2024 - 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.samples.pingsampleapp.config

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.URISyntaxException
import kotlin.coroutines.cancellation.CancellationException

/**
 * Parses and validates a JSON array of [Configuration] entries.
 *
 * Throw hierarchy:
 * - [ConfigurationException.Malformed] — JSON is syntactically broken.
 * - [ConfigurationException.MissingField] — a required field is absent.
 * - [ConfigurationException.InvalidValue] — a field value fails semantic validation.
 * - [ConfigurationException.DuplicateName] — two entries share the same [Configuration.name].
 */
internal object ConfigurationParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = false
    }

    /**
     * Parses [jsonString] as a JSON array of [Configuration] objects, then validates each entry.
     *
     * @param jsonString Raw JSON text representing a `List<Configuration>`.
     * @return The validated list of configurations.
     * @throws ConfigurationException if the input is malformed, has missing fields, contains
     *   invalid values, or contains duplicate names.
     */
    fun parse(jsonString: String): List<Configuration> {
        val configs = decode(jsonString)
        configs.forEach { validate(it) }
        checkUniqueness(configs)
        return configs
    }

    // --- private helpers ---

    @OptIn(ExperimentalSerializationApi::class)
    private fun decode(jsonString: String): List<Configuration> {
        try {
            return json.decodeFromString<List<Configuration>>(jsonString)
        } catch (e: CancellationException) {
            throw e
        } catch (e: MissingFieldException) {
            // MissingFieldException is a subtype of SerializationException — catch it first.
            val fieldName = e.missingFields.firstOrNull() ?: "unknown"
            throw ConfigurationException.MissingField(configName = "unknown", fieldName = fieldName)
        } catch (e: SerializationException) {
            throw ConfigurationException.Malformed(e)
        }
    }

    private fun validate(config: Configuration) {
        // discoveryEndpoint must parse as a URI with a non-null scheme.
        validateUri(
            config = config,
            fieldName = "discoveryEndpoint",
            value = config.discoveryEndpoint,
            requireScheme = true,
        )

        // redirectUri must have a non-empty scheme.
        validateUri(
            config = config,
            fieldName = "redirectUri",
            value = config.redirectUri,
            requireScheme = true,
        )

        // scopes must be non-empty.
        if (config.scopes.isEmpty()) {
            throw ConfigurationException.InvalidValue(
                configName = config.name,
                fieldName = "scopes",
                value = "[]",
                reason = "at least one scope is required",
            )
        }

        // Journey-specific required fields.
        if (config.type == ConfigType.JOURNEY) {
            if (config.serverUrl.isNullOrBlank()) {
                throw ConfigurationException.InvalidValue(
                    configName = config.name,
                    fieldName = "serverUrl",
                    value = config.serverUrl ?: "null",
                    reason = "serverUrl is required for Journey configurations",
                )
            }
            if (config.cookieName.isNullOrBlank()) {
                throw ConfigurationException.InvalidValue(
                    configName = config.name,
                    fieldName = "cookieName",
                    value = config.cookieName ?: "null",
                    reason = "cookieName is required for Journey configurations",
                )
            }
        }
    }

    private fun validateUri(
        config: Configuration,
        fieldName: String,
        value: String,
        requireScheme: Boolean,
    ) {
        val uri = try {
            URI(value)
        } catch (e: URISyntaxException) {
            throw ConfigurationException.InvalidValue(
                configName = config.name,
                fieldName = fieldName,
                value = value,
                reason = "not a valid URI: ${e.reason}",
            )
        }

        if (requireScheme && uri.scheme.isNullOrEmpty()) {
            throw ConfigurationException.InvalidValue(
                configName = config.name,
                fieldName = fieldName,
                value = value,
                reason = "URI must have a scheme",
            )
        }
    }

    private fun checkUniqueness(configs: List<Configuration>) {
        val seen = mutableSetOf<String>()
        for (config in configs) {
            if (!seen.add(config.name)) {
                throw ConfigurationException.DuplicateName(config.name)
            }
        }
    }
}

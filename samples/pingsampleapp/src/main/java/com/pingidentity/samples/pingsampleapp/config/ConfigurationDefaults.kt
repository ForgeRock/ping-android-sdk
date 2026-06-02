/*
 * Copyright (c) 2024 - 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.samples.pingsampleapp.config

import android.content.Context

private const val ASSET_FILE_NAME = "ping_sdk_config.json"

/**
 * Loads and caches the bundled default [Configuration] entries from
 * `assets/ping_sdk_config.json`.
 *
 * Call [load] once at application startup (e.g. from `Application.onCreate`). After that,
 * [entries] is safe to read from any thread, and [isDefault] can be used to distinguish
 * bundled entries from user-added ones.
 */
internal object ConfigurationDefaults {

    /**
     * The configurations parsed from `assets/ping_sdk_config.json`.
     *
     * Initialized by [load]. Accessing this before [load] has been called throws
     * [UninitializedPropertyAccessException].
     */
    lateinit var entries: List<Configuration>
        private set

    /**
     * Reads `assets/ping_sdk_config.json`, parses it via [ConfigurationParser], and caches the
     * result in [entries].
     *
     * @param context Any [Context]; the assets are opened from the application context.
     * @return The loaded list of configurations (same reference stored in [entries]).
     * @throws ConfigurationException.Malformed if the asset cannot be read or is not valid JSON.
     * @throws ConfigurationException.MissingField if a required field is absent.
     * @throws ConfigurationException.InvalidValue if a field value fails validation.
     * @throws ConfigurationException.DuplicateName if two entries share the same name.
     */
    fun load(context: Context): List<Configuration> {
        val jsonString = try {
            context.applicationContext.assets.open(ASSET_FILE_NAME).use { stream ->
                stream.bufferedReader().readText()
            }
        } catch (e: Exception) {
            throw ConfigurationException.Malformed(e)
        }

        val loaded = ConfigurationParser.parse(jsonString)
        entries = loaded
        return loaded
    }

    /**
     * Returns `true` when [config] originated from the bundled defaults asset.
     *
     * Matching is by [Configuration.name] — the same key used for per-type selection persistence.
     *
     * @param config The configuration to check.
     */
    fun isDefault(config: Configuration): Boolean = entries.any { it.name == config.name }
}

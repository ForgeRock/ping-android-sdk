/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.pingonemfapp.config

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import com.pingidentity.davinci.DaVinci
import com.pingidentity.davinci.module.Oidc
import com.pingidentity.davinci.plugin.DaVinci
import com.pingidentity.logger.Logger
import com.pingidentity.logger.STANDARD
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds

private const val AUTHORIZATION_CONFIG_ID = "authorization"
private const val PAIRING_CONFIG_ID = "pairing"
private const val PREFS_NAME = "davinci_environment"
private const val KEY_CONFIGS_JSON = "configs_json"
private const val CUSTOM_CONFIG_ID_PREFIX = "custom"

private val defaultConfigs =
    listOf(
        EditableDaVinciConfig(
            id = AUTHORIZATION_CONFIG_ID,
            timeoutSeconds = "30",
            clientId = "5e145e2a-5b84-4abe-b905-d38bee13fca8",
            discoveryEndpoint = "https://auth.pingone.com/846d642c-f42c-4f82-ad08-3268e4e159db/as/.well-known/openid-configuration",
            scopes = "openid,email,address,phone,profile",
            redirectUri = "app://oauth2redirect",
            display = "DaVinci Payload Flow Config",
            storageFileName = "daVinci",
        ),
        EditableDaVinciConfig(
            id = PAIRING_CONFIG_ID,
            timeoutSeconds = "30",
            clientId = "353bf2bb-ad13-4bbb-bd53-59dcb5ad4cde",
            discoveryEndpoint = "https://auth.pingone.com/846d642c-f42c-4f82-ad08-3268e4e159db/as/.well-known/openid-configuration",
            scopes = "openid,email,address,phone,profile",
            redirectUri = "app://oauth2redirect",
            display = "DaVinci Pairing Flow Config",
            storageFileName = "daVinci",
        ),
    )

private val json = Json {
    ignoreUnknownKeys = true
}

var daVinci: DaVinci? = null
lateinit var redirectUri: Uri

class EnvViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences =
        application.getSharedPreferences(PREFS_NAME, Application.MODE_PRIVATE)

    var configs by mutableStateOf(readConfigs())
        private set

    /**
     * Updates one editable DaVinci configuration in memory.
     *
     * Changes are intentionally not persisted until [apply] is called, so users can edit fields
     * and either apply the configuration or reset it without writing every keystroke.
     */
    fun updateConfig(configId: String, transform: EditableDaVinciConfig.() -> EditableDaVinciConfig) {
        configs = configs.map { config ->
            if (config.id == configId) {
                config.transform()
            } else {
                config
            }
        }
    }

    /**
     * Adds a new editable configuration based on the default authorization settings.
     *
     * The generated id is returned so the UI can immediately expand the new configuration.
     */
    fun addConfig(): String {
        val newId = nextCustomConfigId()
        val newConfig =
            defaultConfigs
                .first { it.id == AUTHORIZATION_CONFIG_ID }
                .copy(
                    id = newId,
                    display = "Custom DaVinci Config",
                )

        configs = configs + newConfig
        persistConfigs()
        return newId
    }

    /**
     * Removes the selected configuration and persists the updated list.
     */
    fun removeConfig(configId: String): Boolean {
        val updatedConfigs = configs.filterNot { it.id == configId }
        if (updatedConfigs.size == configs.size) {
            return false
        }

        configs = updatedConfigs
        persistConfigs()
        return true
    }

    /**
     * Makes the selected configuration active for the next DaVinci flow and saves all configs.
     */
    fun apply(configId: String) {
        val config = configFor(configId)
        applyConfig(config)
        persistConfigs()
    }

    /**
     * Restores the selected configuration values to defaults, then applies and persists them.
     */
    fun reset(configId: String) {
        val defaultConfig = defaultConfigFor(configId)
        configs = configs.map { config ->
            if (config.id == configId) {
                defaultConfig.copy(display = config.display)
            } else {
                config
            }
        }
        apply(configId)
    }

    /**
     * Creates the next available custom configuration id.
     */
    private fun nextCustomConfigId(): String {
        val existingIds = configs.map { it.id }.toSet()
        var index = 1
        var candidate = "${CUSTOM_CONFIG_ID_PREFIX}_$index"
        while (candidate in existingIds) {
            index += 1
            candidate = "${CUSTOM_CONFIG_ID_PREFIX}_$index"
        }
        return candidate
    }

    /**
     * Rebuilds the global DaVinci client from the selected editable configuration.
     */
    private fun applyConfig(config: EditableDaVinciConfig) {
        daVinci = buildDaVinci(config)
        redirectUri = config.redirectUri.toUri()
    }

    /**
     * Finds an editable config by id, falling back to authorization so callers never get null.
     */
    private fun configFor(configId: String): EditableDaVinciConfig {
        return configs.firstOrNull { it.id == configId }
            ?: defaultConfigs.first { it.id == AUTHORIZATION_CONFIG_ID }
    }

    /**
     * Finds a reset template for a config. Custom configs reset to the authorization template.
     */
    private fun defaultConfigFor(configId: String): EditableDaVinciConfig {
        return defaultConfigs.firstOrNull { it.id == configId }
            ?: defaultConfigs.first { it.id == AUTHORIZATION_CONFIG_ID }.copy(id = configId)
    }

    /**
     * Loads saved configurations, using defaults for configs that have not been persisted yet.
     */
    private fun readConfigs(): List<EditableDaVinciConfig> {
        return preferences.readConfigs()
    }

    /**
     * Stores each editable configuration as a single JSON blob keyed by config id.
     */
    private fun persistConfigs() {
        preferences.edit {
            putString(KEY_CONFIGS_JSON, json.encodeToString(configs))
        }
    }
}

@Serializable
data class EditableDaVinciConfig(
    val id: String,
    val timeoutSeconds: String,
    val clientId: String,
    val discoveryEndpoint: String,
    val scopes: String,
    val redirectUri: String,
    val display: String,
    val storageFileName: String
)

/**
 * Builds a DaVinci client from the currently selected editable configuration.
 */
private fun buildDaVinci(config: EditableDaVinciConfig): DaVinci {
    return DaVinci {
        timeout = config.timeoutSeconds.toLongOrNull()?.seconds?.inWholeMilliseconds
            ?: defaultConfigs.first { it.id == config.id }.timeoutSeconds.toLong().seconds.inWholeMilliseconds
        logger = Logger.STANDARD
        module(Oidc) {
            clientId = config.clientId
            discoveryEndpoint = config.discoveryEndpoint
            scopes = config.scopes.asScopeSet()
            redirectUri = config.redirectUri
            display = config.display
            storage {
                fileName = config.storageFileName
            }
        }
    }
}

/**
 * Converts the comma-separated scopes entered in the UI into the set expected by OIDC.
 */
private fun String.asScopeSet(): MutableSet<String> {
    return split(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .toMutableSet()
}

/**
 * Reads all known DaVinci configurations from SharedPreferences.
 */
private fun android.content.SharedPreferences.readConfigs(): List<EditableDaVinciConfig> {
    val savedConfig = getString(KEY_CONFIGS_JSON, null)
    if (savedConfig != null) {
        try {
            return json.decodeFromString<List<EditableDaVinciConfig>>(savedConfig)
        } catch (_: SerializationException) {
            // Fall through to defaults if the saved JSON is invalid.
        } catch (_: IllegalArgumentException) {
            // Fall through to defaults if the saved JSON has unexpected values.
        }
    }

    return defaultConfigs
}

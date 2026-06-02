/*
 * Copyright (c) 2024 - 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.samples.pingsampleapp.config

import android.content.Context
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent
import java.util.logging.Level
import java.util.logging.Logger as JvmLogger
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.pingidentity.davinci.DaVinci
import com.pingidentity.davinci.plugin.DaVinci
import com.pingidentity.journey.Journey
import com.pingidentity.logger.Logger
import com.pingidentity.logger.STANDARD
import com.pingidentity.oidc.OidcDeviceClient
import com.pingidentity.oidc.OidcWebClient
import com.pingidentity.oidc.module.Web
import com.pingidentity.samples.pingsampleapp.settingDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.coroutines.cancellation.CancellationException
import com.pingidentity.davinci.module.Oidc as DaVinciOidc
import com.pingidentity.journey.module.Oidc as JourneyOidc
import com.pingidentity.oidc.module.Oidc as OidcModule

private const val TAG = "ConfigurationManager"
private val jvmLog: JvmLogger = JvmLogger.getLogger(TAG)
private val USER_CONFIGS_KEY = stringPreferencesKey("user_configurations")
private fun selectionKey(type: ConfigType) = stringPreferencesKey("selected_${type.name}")

/**
 * Serializes [configs] to a JSON string using [kotlinx.serialization].
 *
 * Exposed as `internal` so pure-JVM unit tests can exercise the round-trip logic
 * without touching DataStore or Android Context.
 */
internal fun serializeUserConfigs(configs: List<Configuration>): String =
    Json.encodeToString(configs)

/**
 * Deserializes a JSON string produced by [serializeUserConfigs] back into a list of
 * [Configuration] objects.
 *
 * Returns an empty list on any parse or validation error (the recoverable path for
 * user-persisted data).
 */
internal fun deserializeUserConfigs(json: String): List<Configuration> = try {
    ConfigurationParser.parse(json)
} catch (e: CancellationException) {
    throw e
} catch (t: Throwable) {
    jvmLog.log(Level.SEVERE, "Failed to deserialize user configurations — returning empty list", t)
    emptyList()
}

/**
 * Singleton configuration manager for the Ping sample app.
 *
 * Maintains the full list of available [Configuration] entries (bundled defaults merged with
 * user-added entries), tracks the per-type selection, and owns the live SDK instances
 * ([journey], [daVinci], [oidcWebClient], [deviceClient]) that are rebuilt whenever the
 * active configuration changes.
 *
 * Call [initialize] once from `Application.onCreate` (inside a coroutine scope / `withContext`)
 * before observing any flows or calling mutation operations.
 */
internal object ConfigurationManager {

    private val mutex = Mutex()

    private val _configurations = MutableStateFlow<List<Configuration>>(emptyList())
    private val _selections = MutableStateFlow<Map<ConfigType, Configuration>>(emptyMap())

    private var appContext: Context? = null

    /** All available configurations: bundled defaults followed by user-added entries. */
    val configurations: StateFlow<List<Configuration>> = _configurations.asStateFlow()

    /** Per-[ConfigType] selected [Configuration]. */
    val selections: StateFlow<Map<ConfigType, Configuration>> = _selections.asStateFlow()

    /** Currently active [Journey] instance, or `null` if no Journey configuration is selected. */
    var journey: Journey? = null
        private set

    /** Currently active [DaVinci] instance, or `null` if no DaVinci configuration is selected. */
    var daVinci: DaVinci? = null
        private set

    /** Currently active [OidcWebClient] instance, or `null` if no OIDC Web configuration is selected. */
    var oidcWebClient: OidcWebClient? = null
        private set

    /** Currently active [OidcDeviceClient] instance, or `null` if no Device configuration is selected. */
    var deviceClient: OidcDeviceClient? = null
        private set

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Initialises the manager by loading defaults and persisted user configs, resolving per-type
     * selections, and building initial SDK instances.
     *
     * Must be called once before any other operation. Safe to call on `Dispatchers.IO`.
     *
     * @param context Any [Context]; the application context is captured internally.
     */
    suspend fun initialize(context: Context) {
        val ctx = context.applicationContext
        appContext = ctx

        val defaults: List<Configuration> = ConfigurationDefaults.load(ctx)

        val userConfigs: List<Configuration> = readUserConfigs(ctx)

        _configurations.value = defaults + userConfigs

        val resolvedSelections = mutableMapOf<ConfigType, Configuration>()
        for (type in ConfigType.entries) {
            val selection = readSelection(ctx, type, _configurations.value)
            if (selection != null) {
                resolvedSelections[type] = selection
            }
        }
        _selections.value = resolvedSelections

        for ((_, config) in resolvedSelections) {
            rebuildInstance(config)
        }
    }

    // -------------------------------------------------------------------------
    // Query
    // -------------------------------------------------------------------------

    /**
     * Returns the currently selected [Configuration] for [type], or `null` if none is selected.
     */
    fun selectedConfig(type: ConfigType): Configuration? = _selections.value[type]

    /**
     * Returns `true` when at least one [Configuration] with the given [type] exists in the list.
     */
    fun hasConfiguration(type: ConfigType): Boolean =
        _configurations.value.any { it.type == type }

    // -------------------------------------------------------------------------
    // Mutations
    // -------------------------------------------------------------------------

    /**
     * Selects [config] as the active configuration for its [ConfigType], rebuilds the
     * corresponding SDK instance, and persists the selection.
     */
    suspend fun select(config: Configuration) {
        mutex.withLock {
            _selections.value = _selections.value + (config.type to config)
            appContext?.let { writeSelection(it, config.type, config) }
            rebuildInstance(config)
        }
    }

    /**
     * Adds [config] to the user-defined configuration list, updates the combined list, and
     * persists the user configs to DataStore.
     *
     * Default configurations (from `assets/ping_sdk_config.json`) are never written via this
     * method.
     */
    suspend fun add(config: Configuration) {
        mutex.withLock {
            val current = _configurations.value
            val userConfigs = current.filter { !ConfigurationDefaults.isDefault(it) } + config
            _configurations.value = current.filter { ConfigurationDefaults.isDefault(it) } + userConfigs
            appContext?.let { writeUserConfigs(it, userConfigs) }
        }
    }

    /**
     * Replaces the user-added configuration whose [Configuration.name] equals [oldName] with
     * [config]. If the replaced entry was the active selection for its type, the SDK instance
     * is rebuilt with the new config.
     */
    suspend fun update(oldName: String, config: Configuration) {
        mutex.withLock {
            val defaults = _configurations.value.filter { ConfigurationDefaults.isDefault(it) }
            val userConfigs = _configurations.value
                .filter { !ConfigurationDefaults.isDefault(it) }
                .map { if (it.name == oldName) config else it }
            _configurations.value = defaults + userConfigs

            appContext?.let { writeUserConfigs(it, userConfigs) }

            // If this config was selected, update the selection and rebuild.
            val currentSelection = _selections.value[config.type]
            if (currentSelection?.name == oldName) {
                _selections.value = _selections.value + (config.type to config)
                appContext?.let { writeSelection(it, config.type, config) }
                rebuildInstance(config)
            }
        }
    }

    /**
     * Removes [config] from the user-defined list. Default configurations cannot be deleted.
     *
     * If [config] was the active selection for its type, the selection falls back to the first
     * default of that type (or is cleared if no default exists).
     */
    suspend fun delete(config: Configuration) {
        if (ConfigurationDefaults.isDefault(config)) return

        mutex.withLock {
            val defaults = _configurations.value.filter { ConfigurationDefaults.isDefault(it) }
            val userConfigs = _configurations.value
                .filter { !ConfigurationDefaults.isDefault(it) && it.name != config.name }
            _configurations.value = defaults + userConfigs

            appContext?.let { writeUserConfigs(it, userConfigs) }

            val currentSelection = _selections.value[config.type]
            if (currentSelection?.name == config.name) {
                val fallback = defaults.firstOrNull { it.type == config.type }
                val newSelections = _selections.value.toMutableMap()
                if (fallback != null) {
                    newSelections[config.type] = fallback
                    _selections.value = newSelections
                    appContext?.let { writeSelection(it, config.type, fallback) }
                    rebuildInstance(fallback)
                } else {
                    newSelections.remove(config.type)
                    _selections.value = newSelections
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // SDK instance builders
    // -------------------------------------------------------------------------

    private fun rebuildInstance(config: Configuration) {
        when (config.type) {
            ConfigType.JOURNEY -> journey = buildJourney(config)
            ConfigType.DAVINCI -> daVinci = buildDaVinci(config)
            ConfigType.OIDC_WEB -> oidcWebClient = buildOidcWebClient(config)
            ConfigType.DEVICE -> deviceClient = buildDeviceClient(config)
        }
    }

    /**
     * Builds a [Journey] instance from [config].
     *
     * Preserves the same module registrations and storage fileName as the legacy
     * `buildJourney(JourneyConfigState)` package-level function in `EnvViewModel.kt`.
     */
    private fun buildJourney(config: Configuration): Journey = Journey {
        logger = Logger.STANDARD
        serverUrl = config.serverUrl ?: ""
        realm = config.realm ?: "root"
        if (!config.cookieName.isNullOrBlank()) cookie = config.cookieName
        module(JourneyOidc) {
            clientId = config.clientId
            discoveryEndpoint = config.discoveryEndpoint
            scopes = config.scopes.toMutableSet()
            redirectUri = config.redirectUri
            display = config.name
            config.signOutUri?.let { signOutRedirectUri = it }
            config.par?.let { par = it }
            storage { fileName = "journey" }
        }
    }

    /**
     * Builds a [DaVinci] instance from [config].
     *
     * Preserves the same module registrations and storage fileName as the legacy
     * `buildDaVinci(OidcConfigState)` package-level function in `EnvViewModel.kt`.
     */
    private fun buildDaVinci(config: Configuration): DaVinci = DaVinci {
        logger = Logger.STANDARD
        module(DaVinciOidc) {
            clientId = config.clientId
            discoveryEndpoint = config.discoveryEndpoint
            scopes = config.scopes.toMutableSet()
            redirectUri = config.redirectUri
            display = config.name
            config.acrValues?.let { acrValues = it }
            config.signOutUri?.let { signOutRedirectUri = it }
            config.par?.let { par = it }
            storage { fileName = "daVinci" }
        }
    }

    /**
     * Builds an [OidcWebClient] instance from [config].
     *
     * Preserves the same module registrations as the legacy `buildWeb(OidcConfigState)`
     * package-level function in `EnvViewModel.kt`, including the Custom Tabs color-scheme
     * customizer.
     */
    private fun buildOidcWebClient(config: Configuration): OidcWebClient = OidcWebClient {
        logger = Logger.STANDARD
        module(OidcModule) {
            clientId = config.clientId
            discoveryEndpoint = config.discoveryEndpoint
            scopes = config.scopes.toMutableSet()
            redirectUri = config.redirectUri
            display = config.name
            config.acrValues?.let { acrValues = it }
            config.signOutUri?.let { signOutRedirectUri = it }
            config.par?.let { par = it }
        }
        module(Web) {
            customTabsCustomizer = { setColorScheme(CustomTabsIntent.COLOR_SCHEME_DARK) }
            authTabCustomizer = { setColorScheme(CustomTabsIntent.COLOR_SCHEME_DARK) }
        }
    }

    /**
     * Builds an [OidcDeviceClient] instance from [config].
     *
     * Applies [Configuration.deviceAuthorizationEndpoint] via `openIdOverride` when non-null,
     * as documented in `OidcClientConfig`. Storage fileName is "device" to keep token storage
     * segregated from other flow types.
     */
    private fun buildDeviceClient(config: Configuration): OidcDeviceClient = OidcDeviceClient {
        clientId = config.clientId
        discoveryEndpoint = config.discoveryEndpoint
        scopes = config.scopes.toMutableSet()
        redirectUri = config.redirectUri
        display = config.name
        config.signOutUri?.let { signOutRedirectUri = it }
        config.par?.let { par = it }
        config.deviceAuthorizationEndpoint?.let { deviceEndpoint ->
            openIdOverride = { deviceAuthorizationEndpoint = deviceEndpoint }
        }
        storage { fileName = "device" }
    }

    // -------------------------------------------------------------------------
    // DataStore helpers
    // -------------------------------------------------------------------------

    private suspend fun readUserConfigs(context: Context): List<Configuration> {
        return try {
            val prefs = context.settingDataStore.data.first()
            val json = prefs[USER_CONFIGS_KEY] ?: return emptyList()
            deserializeUserConfigs(json)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to read user configurations from DataStore — using empty list", t)
            emptyList()
        }
    }

    private suspend fun writeUserConfigs(context: Context, configs: List<Configuration>) {
        context.settingDataStore.edit { prefs ->
            prefs[USER_CONFIGS_KEY] = serializeUserConfigs(configs)
        }
    }

    private suspend fun readSelection(
        context: Context,
        type: ConfigType,
        all: List<Configuration>,
    ): Configuration? {
        return try {
            val prefs = context.settingDataStore.data.first()
            val name = prefs[selectionKey(type)]
            if (name != null) {
                all.firstOrNull { it.name == name && it.type == type }
                    ?: all.firstOrNull { it.type == type }
            } else {
                all.firstOrNull { it.type == type }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to read selection for $type — falling back to first available", t)
            all.firstOrNull { it.type == type }
        }
    }

    private suspend fun writeSelection(
        context: Context,
        type: ConfigType,
        config: Configuration,
    ) {
        context.settingDataStore.edit { prefs ->
            prefs[selectionKey(type)] = config.name
        }
    }
}

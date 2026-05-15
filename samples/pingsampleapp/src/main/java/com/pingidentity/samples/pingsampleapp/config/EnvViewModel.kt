/*
 * Copyright (c) 2026 - 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.samples.pingsampleapp.config

import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pingidentity.android.ContextProvider
import com.pingidentity.davinci.DaVinci
import com.pingidentity.davinci.plugin.DaVinci
import com.pingidentity.journey.Journey
import com.pingidentity.logger.Logger
import com.pingidentity.logger.STANDARD
import com.pingidentity.oidc.OidcClient
import com.pingidentity.oidc.OidcWebClient
import com.pingidentity.oidc.module.Web
import com.pingidentity.oidc.OidcDeviceClient
import com.pingidentity.samples.pingsampleapp.settingDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import com.pingidentity.davinci.module.Oidc as DaVinciOidc
import com.pingidentity.journey.module.Oidc as JourneyOidc

// ---------------------------------------------------------------------------
// Config state data classes
// ---------------------------------------------------------------------------

data class JourneyConfigState(
    val serverUrl: String = "https://www.example.com/am",
    val realm: String = "alpha",
    val cookie: String = "",
    val clientId: String = "dummy",
    val discoveryEndpoint: String = "https://www.example.com/am/oauth2/alpha/.well-known/openid-configuration",
    val scopes: String = "openid,email,address,profile,phone",
    val redirectUri: String = "org.forgerock.demo:/oauth2redirect",
    val display: String = "Journey Test Config",
)

data class OidcConfigState(
    val clientId: String = "",
    val discoveryEndpoint: String = "",
    val scopes: String = "",
    val redirectUri: String = "",
    val display: String = "",
    val arcValue: String = ""
)

data class DeviceAuthConfigState(
    val clientId: String = "",
    val discoveryEndpoint: String = "",
    val scopes: String = "",
    val display: String = "Device Authorization",
)

// ---------------------------------------------------------------------------
// Default preset configs (used as fallbacks when no saved config exists)
// ---------------------------------------------------------------------------

internal val defaultJourneyConfig = JourneyConfigState()

internal val defaultDaVinciConfig = OidcConfigState(
    clientId = "dummy",
    discoveryEndpoint = "https://auth.pingone.ca/dummy/as/.well-known/openid-configuration",
    scopes = "openid,email,address,phone,profile",
    redirectUri = "org.forgerock.demo://oauth2redirect",
    arcValue = "123",
    display = "DaVinci Test Config",
)

internal val defaultDeviceAuthConfig = DeviceAuthConfigState(
    clientId = "dummy",
    discoveryEndpoint = "https://auth.pingone.ca/dummy/as/.well-known/openid-configuration",
    scopes = "openid,email,address,phone,profile",
    display = "Test Config",
)

internal val defaultWebConfig = OidcConfigState(
    clientId = "dummy",
    discoveryEndpoint = "https://www.example.com/am/oauth2/alpha/.well-known/openid-configuration",
    scopes = "openid,email,address,profile,phone",
    redirectUri = "org.forgerock.demo:/oauth2redirect",
    display = "OIDC Forgeblock",
)

// ---------------------------------------------------------------------------
// Global SDK instance holders (consumed by other ViewModels)
// ---------------------------------------------------------------------------

var journey: Journey = Journey {
    logger = Logger.STANDARD
    serverUrl = defaultJourneyConfig.serverUrl
    realm = defaultJourneyConfig.realm
    cookie = defaultJourneyConfig.cookie
    module(JourneyOidc) {
        clientId = defaultJourneyConfig.clientId
        discoveryEndpoint = defaultJourneyConfig.discoveryEndpoint
        scopes = defaultJourneyConfig.scopes.toScopeSet()
        redirectUri = defaultJourneyConfig.redirectUri
        display = defaultJourneyConfig.display
        storage { fileName = "journey" }
    }
}
var oidcClient: OidcClient? = null
var daVinci: DaVinci? = null
var web: OidcWebClient? = null
var oidcDeviceClient: OidcDeviceClient? = null
/** Used by Journey's IdP (social identity provider) callback. Set only by [buildJourney]. */
lateinit var redirectUri: Uri
/** Used by DaVinci's Social Login button. Set only by [buildDaVinci]. Never overwritten by Journey. */
lateinit var daVinciRedirectUri: Uri

// ---------------------------------------------------------------------------
// Package-level SDK builders (called both at app startup and from ViewModel)
// ---------------------------------------------------------------------------

internal fun buildJourney(config: JourneyConfigState) {
    journey = Journey {
        logger = Logger.STANDARD
        serverUrl = config.serverUrl
        realm = config.realm
        if (config.cookie.isNotBlank()) cookie = config.cookie
        module(JourneyOidc) {
            clientId = config.clientId
            discoveryEndpoint = config.discoveryEndpoint
            scopes = config.scopes.toScopeSet()
            redirectUri = config.redirectUri
            display = config.display
            storage { fileName = "journey" }
        }
    }
    oidcClient = OidcClient {
        clientId = config.clientId
        discoveryEndpoint = config.discoveryEndpoint
        scopes = config.scopes.toScopeSet()
        redirectUri = config.redirectUri
        display = config.display
    }
    redirectUri = config.redirectUri.toUri()
}

internal fun buildDaVinci(config: OidcConfigState) {
    daVinci = DaVinci {
        logger = Logger.STANDARD
        module(DaVinciOidc) {
            clientId = config.clientId
            discoveryEndpoint = config.discoveryEndpoint
            scopes = config.scopes.toScopeSet()
            redirectUri = config.redirectUri
            display = config.display
            if (config.arcValue.isNotBlank()) acrValues = config.arcValue
            storage { fileName = "daVinci" }
        }
    }
    // Store in the DaVinci-specific global so it never overwrites Journey's redirectUri
    daVinciRedirectUri = config.redirectUri.toUri()
}

internal fun buildWeb(config: OidcConfigState) {
    web = OidcWebClient {
        logger = Logger.STANDARD
        module(com.pingidentity.oidc.module.Oidc) {
            clientId = config.clientId
            discoveryEndpoint = config.discoveryEndpoint
            scopes = config.scopes.toScopeSet()
            redirectUri = config.redirectUri
            display = config.display
        }
        module(Web) {
            customTabsCustomizer = { setColorScheme(CustomTabsIntent.COLOR_SCHEME_DARK) }
            authTabCustomizer = { setColorScheme(CustomTabsIntent.COLOR_SCHEME_DARK) }
        }
    }
}

internal fun buildDeviceAuthClient(config: DeviceAuthConfigState) {
    oidcDeviceClient = OidcDeviceClient {
        clientId = config.clientId
        discoveryEndpoint = config.discoveryEndpoint
        scopes = config.scopes.toScopeSet()
    }
}

// ---------------------------------------------------------------------------
// App-startup initializer — call from Application.onCreate()
// ---------------------------------------------------------------------------

/**
 * Loads the last-saved configs from DataStore and applies them to the global
 * SDK instances so all flows are ready immediately when the app starts,
 * before the user ever visits the Configuration screen.
 */
suspend fun initConfigs() {
    val prefs = ContextProvider.context.settingDataStore.data.first()

    val jConfig = prefs[stringPreferencesKey("j_clientId")]?.let { clientId ->
        JourneyConfigState(
            serverUrl = prefs[stringPreferencesKey("j_serverUrl")] ?: defaultJourneyConfig.serverUrl,
            realm = prefs[stringPreferencesKey("j_realm")] ?: defaultJourneyConfig.realm,
            cookie = prefs[stringPreferencesKey("j_cookie")] ?: defaultJourneyConfig.cookie,
            clientId = clientId,
            discoveryEndpoint = prefs[stringPreferencesKey("j_discoveryEndpoint")] ?: "",
            scopes = prefs[stringPreferencesKey("j_scopes")] ?: "",
            redirectUri = prefs[stringPreferencesKey("j_redirectUri")] ?: "",
            display = prefs[stringPreferencesKey("j_display")] ?: "",
        )
    } ?: defaultJourneyConfig

    val dvConfig = prefs[stringPreferencesKey("dv_clientId")]?.let { clientId ->
        OidcConfigState(
            clientId = clientId,
            discoveryEndpoint = prefs[stringPreferencesKey("dv_discoveryEndpoint")] ?: "",
            scopes = prefs[stringPreferencesKey("dv_scopes")] ?: "",
            redirectUri = prefs[stringPreferencesKey("dv_redirectUri")] ?: "",
            display = prefs[stringPreferencesKey("dv_display")] ?: "",
            arcValue = prefs[stringPreferencesKey("dv_arcValue")] ?: "",
        )
    } ?: defaultDaVinciConfig

    val wConfig = prefs[stringPreferencesKey("w_clientId")]?.let { clientId ->
        OidcConfigState(
            clientId = clientId,
            discoveryEndpoint = prefs[stringPreferencesKey("w_discoveryEndpoint")] ?: "",
            scopes = prefs[stringPreferencesKey("w_scopes")] ?: "",
            redirectUri = prefs[stringPreferencesKey("w_redirectUri")] ?: "",
            display = prefs[stringPreferencesKey("w_display")] ?: "",
        )
    } ?: defaultWebConfig

    val daConfig = prefs[stringPreferencesKey("da_clientId")]?.let { clientId ->
        DeviceAuthConfigState(
            clientId = clientId,
            discoveryEndpoint = prefs[stringPreferencesKey("da_discoveryEndpoint")] ?: "",
            scopes = prefs[stringPreferencesKey("da_scopes")] ?: "",
            display = prefs[stringPreferencesKey("da_display")] ?: "",
        )
    } ?: defaultDeviceAuthConfig

    // Each builder writes to its own global; no ordering dependency.
    buildJourney(jConfig)
    buildWeb(wConfig)
    buildDaVinci(dvConfig)
    buildDeviceAuthClient(daConfig)
}

// ---------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------

class EnvViewModel : ViewModel() {

    // -- Preset lists (read-only, built-in) ----------------------------------

    val journeyPresets = listOf(
        defaultJourneyConfig,
        JourneyConfigState(
            serverUrl = "http://192.168.86.32:8080/openam",
            realm = "root",
            cookie = "",
            clientId = "AndroidTest2",
            discoveryEndpoint = "http://192.168.86.32:8080/openam/oauth2/.well-known/openid-configuration",
            scopes = "openid,email,address,profile,phone",
            redirectUri = "org.forgerock.demo:/oauth2redirect",
            display = "Localhost",
        ),
    )

    val daVinciPresets = listOf(
        defaultDaVinciConfig,
        OidcConfigState(
            clientId = "dummy",
            discoveryEndpoint = "https://auth.pingone.ca/dummy/as/.well-known/openid-configuration",
            scopes = "openid,email,address,phone,profile",
            redirectUri = "org.forgerock.demo://oauth2redirect",
            display = "DaVinci Prod Config",
        ),
        OidcConfigState(
            clientId = "dummy",
            discoveryEndpoint = "https://auth.pingone.com/dummy/as/.well-known/openid-configuration",
            scopes = "openid,email,address",
            redirectUri = "com.pingidentity.demo://oauth2redirect",
            display = "Social Config",
        ),
    )

    val webPresets = listOf(
        defaultWebConfig,
        OidcConfigState(
            clientId = "dummy",
            discoveryEndpoint = "https://auth.test-one-pingone.com/dummy/as/.well-known/openid-configuration",
            scopes = "openid,email,address",
            redirectUri = "org.forgerock.demo://oauth2redirect",
            display = "OIDC PingOne",
        ),
    )

    val deviceAuthPresets = listOf(
        defaultDeviceAuthConfig,
        DeviceAuthConfigState(
            clientId = "dummy",
            discoveryEndpoint = "https://auth.pingone.ca/dummy/as/.well-known/openid-configuration",
            scopes = "openid",
            display = "Device Auth PingOne",
        ),
    )

    // -- Currently applied configs -------------------------------------------

    var appliedJourneyConfig by mutableStateOf<JourneyConfigState?>(null)
        private set

    var appliedDaVinciConfig by mutableStateOf<OidcConfigState?>(null)
        private set

    var appliedWebConfig by mutableStateOf<OidcConfigState?>(null)
        private set

    var appliedDeviceAuthConfig by mutableStateOf<DeviceAuthConfigState?>(null)
        private set

    // -- User-defined custom configs -----------------------------------------

    var customJourneyConfigs by mutableStateOf<List<JourneyConfigState>>(emptyList())
        private set

    var customDaVinciConfigs by mutableStateOf<List<OidcConfigState>>(emptyList())
        private set

    var customWebConfigs by mutableStateOf<List<OidcConfigState>>(emptyList())
        private set

    var customDeviceAuthConfigs by mutableStateOf<List<DeviceAuthConfigState>>(emptyList())
        private set

    // -- Init ----------------------------------------------------------------

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val jApplied = loadAppliedJourneyFromDataStore()
            val dvApplied = loadAppliedDaVinciFromDataStore()
            val wApplied = loadAppliedWebFromDataStore()
            val daApplied = loadAppliedDeviceAuthConfig()
            val jCustom = loadCustomJourneyFromDataStore()
            val dvCustom = loadCustomDaVinciFromDataStore()
            val wCustom = loadCustomWebFromDataStore()
            val daCustom = loadCustomDeviceAuthFromDataStore()

            withContext(Dispatchers.Main) {
                customJourneyConfigs = jCustom
                customDaVinciConfigs = dvCustom
                customWebConfigs = wCustom
                customDeviceAuthConfigs = daCustom
                appliedJourneyConfig = jApplied
                appliedDaVinciConfig = dvApplied
                appliedWebConfig = wApplied
                appliedDeviceAuthConfig = daApplied
                buildJourneyInstance(jApplied)
                buildDaVinciInstance(dvApplied)
                buildWebInstance(wApplied)
            }
        }
    }

    // -- Select (apply a preset or custom config) ----------------------------

    fun selectJourneyConfig(config: JourneyConfigState) {
        buildJourneyInstance(config)
        appliedJourneyConfig = config
        viewModelScope.launch(Dispatchers.IO) { persistAppliedJourney(config) }
    }

    fun selectDaVinciConfig(config: OidcConfigState) {
        buildDaVinciInstance(config)
        appliedDaVinciConfig = config
        viewModelScope.launch(Dispatchers.IO) { persistAppliedDaVinci(config) }
    }

    fun selectWebConfig(config: OidcConfigState) {
        buildWebInstance(config)
        appliedWebConfig = config
        viewModelScope.launch(Dispatchers.IO) { persistAppliedWeb(config) }
    }

    fun selectDeviceAuthConfig(config: DeviceAuthConfigState) {
        buildDeviceAuthClient(config)
        appliedDeviceAuthConfig = config
        viewModelScope.launch(Dispatchers.IO) { persistAppliedDeviceAuthConfig(config) }
    }

    // -- Custom config CRUD --------------------------------------------------

    fun saveCustomJourneyConfig(config: JourneyConfigState, editIndex: Int?) {
        customJourneyConfigs = if (editIndex == null) {
            customJourneyConfigs + config
        } else {
            customJourneyConfigs.toMutableList().also { it[editIndex] = config }
        }
        selectJourneyConfig(config)
        viewModelScope.launch(Dispatchers.IO) { persistCustomJourneyConfigs(customJourneyConfigs) }
    }

    fun deleteCustomJourneyConfig(index: Int) {
        val deleted = customJourneyConfigs[index]
        customJourneyConfigs = customJourneyConfigs.toMutableList().also { it.removeAt(index) }
        // If the deleted config was active, fall back to the first preset
        if (appliedJourneyConfig?.display == deleted.display) {
            selectJourneyConfig(journeyPresets[0])
        }
        viewModelScope.launch(Dispatchers.IO) { persistCustomJourneyConfigs(customJourneyConfigs) }
    }

    fun saveCustomDaVinciConfig(config: OidcConfigState, editIndex: Int?) {
        customDaVinciConfigs = if (editIndex == null) {
            customDaVinciConfigs + config
        } else {
            customDaVinciConfigs.toMutableList().also { it[editIndex] = config }
        }
        selectDaVinciConfig(config)
        viewModelScope.launch(Dispatchers.IO) { persistCustomDaVinciConfigs(customDaVinciConfigs) }
    }

    fun deleteCustomDaVinciConfig(index: Int) {
        val deleted = customDaVinciConfigs[index]
        customDaVinciConfigs = customDaVinciConfigs.toMutableList().also { it.removeAt(index) }
        // If the deleted config was active, fall back to the first preset
        if (appliedDaVinciConfig?.display == deleted.display) {
            selectDaVinciConfig(daVinciPresets[0])
        }
        viewModelScope.launch(Dispatchers.IO) { persistCustomDaVinciConfigs(customDaVinciConfigs) }
    }

    fun saveCustomWebConfig(config: OidcConfigState, editIndex: Int?) {
        customWebConfigs = if (editIndex == null) {
            customWebConfigs + config
        } else {
            customWebConfigs.toMutableList().also { it[editIndex] = config }
        }
        selectWebConfig(config)
        viewModelScope.launch(Dispatchers.IO) { persistCustomWebConfigs(customWebConfigs) }
    }

    fun deleteCustomWebConfig(index: Int) {
        val deleted = customWebConfigs[index]
        customWebConfigs = customWebConfigs.toMutableList().also { it.removeAt(index) }
        // If the deleted config was active, fall back to the first preset
        if (appliedWebConfig?.display == deleted.display) {
            selectWebConfig(webPresets[0])
        }
        viewModelScope.launch(Dispatchers.IO) { persistCustomWebConfigs(customWebConfigs) }
    }

    fun saveCustomDeviceAuthConfig(config: DeviceAuthConfigState, editIndex: Int?) {
        customDeviceAuthConfigs = if (editIndex == null) {
            customDeviceAuthConfigs + config
        } else {
            customDeviceAuthConfigs.toMutableList().also { it[editIndex] = config }
        }
        selectDeviceAuthConfig(config)
        viewModelScope.launch(Dispatchers.IO) { persistCustomDeviceAuthConfigs(customDeviceAuthConfigs) }
    }

    fun deleteCustomDeviceAuthConfig(index: Int) {
        val deleted = customDeviceAuthConfigs[index]
        customDeviceAuthConfigs = customDeviceAuthConfigs.toMutableList().also { it.removeAt(index) }
        // If the deleted config was active, fall back to the first preset
        if (appliedDeviceAuthConfig?.display == deleted.display) {
            selectDeviceAuthConfig(DeviceAuthConfigState("dummy", "https://auth.test-one-pingone.com/dummy/as/.well-known/openid-configuration", "openid", "Device Authorization Test Config"))
        }
        viewModelScope.launch(Dispatchers.IO) { persistCustomDeviceAuthConfigs(customDeviceAuthConfigs) }
    }

    // -- SDK instance builders (delegate to package-level functions) ---------

    private fun buildJourneyInstance(config: JourneyConfigState) = buildJourney(config)
    private fun buildDaVinciInstance(config: OidcConfigState) = buildDaVinci(config)
    private fun buildWebInstance(config: OidcConfigState) = buildWeb(config)

    // -- DataStore: load applied configs -------------------------------------

    private suspend fun loadAppliedJourneyFromDataStore(): JourneyConfigState {
        val prefs = ContextProvider.context.settingDataStore.data.first()
        val clientId = prefs[stringPreferencesKey("j_clientId")] ?: return journeyPresets[0]
        return JourneyConfigState(
            serverUrl = prefs[stringPreferencesKey("j_serverUrl")] ?: journeyPresets[0].serverUrl,
            realm = prefs[stringPreferencesKey("j_realm")] ?: journeyPresets[0].realm,
            cookie = prefs[stringPreferencesKey("j_cookie")] ?: journeyPresets[0].cookie,
            clientId = clientId,
            discoveryEndpoint = prefs[stringPreferencesKey("j_discoveryEndpoint")] ?: "",
            scopes = prefs[stringPreferencesKey("j_scopes")] ?: "",
            redirectUri = prefs[stringPreferencesKey("j_redirectUri")] ?: "",
            display = prefs[stringPreferencesKey("j_display")] ?: "",
        )
    }

    private suspend fun loadAppliedDaVinciFromDataStore(): OidcConfigState {
        val prefs = ContextProvider.context.settingDataStore.data.first()
        val clientId = prefs[stringPreferencesKey("dv_clientId")] ?: return daVinciPresets[0]
        return OidcConfigState(
            clientId = clientId,
            discoveryEndpoint = prefs[stringPreferencesKey("dv_discoveryEndpoint")] ?: "",
            scopes = prefs[stringPreferencesKey("dv_scopes")] ?: "",
            redirectUri = prefs[stringPreferencesKey("dv_redirectUri")] ?: "",
            display = prefs[stringPreferencesKey("dv_display")] ?: "",
            arcValue = prefs[stringPreferencesKey("dv_arcValue")] ?: "",
        )
    }

    private suspend fun loadAppliedWebFromDataStore(): OidcConfigState {
        val prefs = ContextProvider.context.settingDataStore.data.first()
        val clientId = prefs[stringPreferencesKey("w_clientId")] ?: return webPresets[0]
        return OidcConfigState(
            clientId = clientId,
            discoveryEndpoint = prefs[stringPreferencesKey("w_discoveryEndpoint")] ?: "",
            scopes = prefs[stringPreferencesKey("w_scopes")] ?: "",
            redirectUri = prefs[stringPreferencesKey("w_redirectUri")] ?: "",
            display = prefs[stringPreferencesKey("w_display")] ?: "",
        )
    }

    private suspend fun loadAppliedDeviceAuthConfig(): DeviceAuthConfigState {
        val prefs = ContextProvider.context.settingDataStore.data.first()
        val clientId = prefs[stringPreferencesKey("da_clientId")] ?: return defaultDeviceAuthConfig
        return DeviceAuthConfigState(
            clientId = clientId,
            discoveryEndpoint = prefs[stringPreferencesKey("da_discoveryEndpoint")] ?: "",
            scopes = prefs[stringPreferencesKey("da_scopes")] ?: "",
            display = prefs[stringPreferencesKey("da_display")] ?: "",
        )
    }

    // -- DataStore: load custom configs --------------------------------------

    private suspend fun loadCustomJourneyFromDataStore(): List<JourneyConfigState> {
        val prefs = ContextProvider.context.settingDataStore.data.first()
        val json = prefs[stringPreferencesKey("j_custom_configs")] ?: return emptyList()
        return deserializeJourneyConfigs(json)
    }

    private suspend fun loadCustomDaVinciFromDataStore(): List<OidcConfigState> {
        val prefs = ContextProvider.context.settingDataStore.data.first()
        val json = prefs[stringPreferencesKey("dv_custom_configs")] ?: return emptyList()
        return deserializeOidcConfigs(json)
    }

    private suspend fun loadCustomWebFromDataStore(): List<OidcConfigState> {
        val prefs = ContextProvider.context.settingDataStore.data.first()
        val json = prefs[stringPreferencesKey("w_custom_configs")] ?: return emptyList()
        return deserializeOidcConfigs(json)
    }

    private suspend fun loadCustomDeviceAuthFromDataStore(): List<DeviceAuthConfigState> {
        val prefs = ContextProvider.context.settingDataStore.data.first()
        val json = prefs[stringPreferencesKey("da_custom_configs")] ?: return emptyList()
        return deserializeDeviceAuthConfigs(json)
    }

    // -- DataStore: persist applied configs ----------------------------------

    private suspend fun persistAppliedJourney(config: JourneyConfigState) {
        ContextProvider.context.settingDataStore.edit { prefs ->
            prefs[stringPreferencesKey("j_serverUrl")] = config.serverUrl
            prefs[stringPreferencesKey("j_realm")] = config.realm
            prefs[stringPreferencesKey("j_cookie")] = config.cookie
            prefs[stringPreferencesKey("j_clientId")] = config.clientId
            prefs[stringPreferencesKey("j_discoveryEndpoint")] = config.discoveryEndpoint
            prefs[stringPreferencesKey("j_scopes")] = config.scopes
            prefs[stringPreferencesKey("j_redirectUri")] = config.redirectUri
            prefs[stringPreferencesKey("j_display")] = config.display
        }
    }

    private suspend fun persistAppliedDaVinci(config: OidcConfigState) {
        ContextProvider.context.settingDataStore.edit { prefs ->
            prefs[stringPreferencesKey("dv_clientId")] = config.clientId
            prefs[stringPreferencesKey("dv_discoveryEndpoint")] = config.discoveryEndpoint
            prefs[stringPreferencesKey("dv_scopes")] = config.scopes
            prefs[stringPreferencesKey("dv_redirectUri")] = config.redirectUri
            prefs[stringPreferencesKey("dv_display")] = config.display
            prefs[stringPreferencesKey("dv_arcValue")] = config.arcValue
        }
    }

    private suspend fun persistAppliedWeb(config: OidcConfigState) {
        ContextProvider.context.settingDataStore.edit { prefs ->
            prefs[stringPreferencesKey("w_clientId")] = config.clientId
            prefs[stringPreferencesKey("w_discoveryEndpoint")] = config.discoveryEndpoint
            prefs[stringPreferencesKey("w_scopes")] = config.scopes
            prefs[stringPreferencesKey("w_redirectUri")] = config.redirectUri
            prefs[stringPreferencesKey("w_display")] = config.display
        }
    }

    private suspend fun persistAppliedDeviceAuthConfig(config: DeviceAuthConfigState) {
        ContextProvider.context.settingDataStore.edit { prefs ->
            prefs[stringPreferencesKey("da_clientId")] = config.clientId
            prefs[stringPreferencesKey("da_discoveryEndpoint")] = config.discoveryEndpoint
            prefs[stringPreferencesKey("da_scopes")] = config.scopes
            prefs[stringPreferencesKey("da_display")] = config.display
        }
    }

    // -- DataStore: persist custom configs -----------------------------------

    private suspend fun persistCustomJourneyConfigs(configs: List<JourneyConfigState>) {
        ContextProvider.context.settingDataStore.edit { prefs ->
            prefs[stringPreferencesKey("j_custom_configs")] = serializeJourneyConfigs(configs)
        }
    }

    private suspend fun persistCustomDaVinciConfigs(configs: List<OidcConfigState>) {
        ContextProvider.context.settingDataStore.edit { prefs ->
            prefs[stringPreferencesKey("dv_custom_configs")] = serializeOidcConfigs(configs)
        }
    }

    private suspend fun persistCustomWebConfigs(configs: List<OidcConfigState>) {
        ContextProvider.context.settingDataStore.edit { prefs ->
            prefs[stringPreferencesKey("w_custom_configs")] = serializeOidcConfigs(configs)
        }
    }

    private suspend fun persistCustomDeviceAuthConfigs(configs: List<DeviceAuthConfigState>) {
        ContextProvider.context.settingDataStore.edit { prefs ->
            prefs[stringPreferencesKey("da_custom_configs")] = serializeDeviceAuthConfigs(configs)
        }
    }

    // -- JSON serialization --------------------------------------------------

    private fun serializeJourneyConfigs(configs: List<JourneyConfigState>): String {
        val array = JSONArray()
        configs.forEach { c ->
            array.put(JSONObject().apply {
                put("serverUrl", c.serverUrl); put("realm", c.realm); put("cookie", c.cookie)
                put("clientId", c.clientId); put("discoveryEndpoint", c.discoveryEndpoint)
                put("scopes", c.scopes); put("redirectUri", c.redirectUri); put("display", c.display)
            })
        }
        return array.toString()
    }

    private fun deserializeJourneyConfigs(json: String): List<JourneyConfigState> = runCatching {
        val array = JSONArray(json)
        (0 until array.length()).map {
            val o = array.getJSONObject(it)
            JourneyConfigState(
                serverUrl = o.optString("serverUrl", ""),
                realm = o.optString("realm", ""),
                cookie = o.optString("cookie", ""),
                clientId = o.optString("clientId", ""),
                discoveryEndpoint = o.optString("discoveryEndpoint", ""),
                scopes = o.optString("scopes", ""),
                redirectUri = o.optString("redirectUri", ""),
                display = o.optString("display", ""),
            )
        }
    }.getOrDefault(emptyList())

    private fun serializeOidcConfigs(configs: List<OidcConfigState>): String {
        val array = JSONArray()
        configs.forEach { c ->
            array.put(JSONObject().apply {
                put("clientId", c.clientId); put("discoveryEndpoint", c.discoveryEndpoint)
                put("scopes", c.scopes); put("redirectUri", c.redirectUri); put("display", c.display)
                put("arcValue", c.arcValue)
            })
        }
        return array.toString()
    }

    private fun deserializeOidcConfigs(json: String): List<OidcConfigState> = runCatching {
        val array = JSONArray(json)
        (0 until array.length()).map {
            val o = array.getJSONObject(it)
            OidcConfigState(
                clientId = o.optString("clientId", ""),
                discoveryEndpoint = o.optString("discoveryEndpoint", ""),
                scopes = o.optString("scopes", ""),
                redirectUri = o.optString("redirectUri", ""),
                display = o.optString("display", ""),
                arcValue = o.optString("arcValue", ""),
            )
        }
    }.getOrDefault(emptyList())

    private fun serializeDeviceAuthConfigs(configs: List<DeviceAuthConfigState>): String {
        val array = JSONArray()
        configs.forEach { c ->
            array.put(JSONObject().apply {
                put("clientId", c.clientId); put("discoveryEndpoint", c.discoveryEndpoint)
                put("scopes", c.scopes); put("display", c.display)
            })
        }
        return array.toString()
    }

    private fun deserializeDeviceAuthConfigs(json: String): List<DeviceAuthConfigState> = runCatching {
        val array = JSONArray(json)
        (0 until array.length()).map {
            val o = array.getJSONObject(it)
            DeviceAuthConfigState(
                clientId = o.optString("clientId", ""),
                discoveryEndpoint = o.optString("discoveryEndpoint", ""),
                scopes = o.optString("scopes", ""),
                display = o.optString("display", ""),
            )
        }
    }.getOrDefault(emptyList())
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

private fun String.toScopeSet(): MutableSet<String> =
    split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableSet()

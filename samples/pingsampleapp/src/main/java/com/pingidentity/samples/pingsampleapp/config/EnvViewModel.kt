/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
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
import com.pingidentity.oidc.JsonConfigKey
import com.pingidentity.oidc.OidcClient
import com.pingidentity.oidc.OidcWebClient
import com.pingidentity.oidc.OidcDeviceClient
import com.pingidentity.oidc.toScopesJsonArray
import com.pingidentity.samples.pingsampleapp.settingDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.json.JSONArray
import org.json.JSONObject

// ---------------------------------------------------------------------------
// Config state data classes
// ---------------------------------------------------------------------------

data class JourneyConfigState(
    val serverUrl: String = "",
    val realm: String = "",
    val cookie: String = "",
    val clientId: String = "",
    val discoveryEndpoint: String = "",
    val scopes: String = "",
    val redirectUri: String = "",
    val display: String = "",
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
    val authorizationEndpoint: String = "",
    val tokenEndpoint: String = "",
    val userinfoEndpoint: String = "",
    val endSessionEndpoint: String = "",
    val revocationEndpoint: String = "",
    val deviceAuthorizationEndpoint: String = "",
    val acrValues: String = "",
)

// ---------------------------------------------------------------------------
// Default preset configs (used as fallbacks when no saved config exists)
// ---------------------------------------------------------------------------

internal val defaultJourneyConfig = JourneyConfigState(
    serverUrl = "https://www.example.com/am",
    realm = "alpha",
    cookie = "",
    clientId = "dummy",
    discoveryEndpoint = "https://www.example.com/am/oauth2/alpha/.well-known/openid-configuration",
    scopes = "openid,email,address,profile,phone",
    redirectUri = "org.forgerock.demo:/oauth2redirect",
    display = "Journey Test Config",
)

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
    discoveryEndpoint = "https://example.com/am/oauth2/alpha/.well-known/openid-configuration",
    scopes = "openid",
    display = "Device Authorization",
    authorizationEndpoint = "https://example.com/am/oauth2/alpha/authorize",
    tokenEndpoint = "https://example.com/am/oauth2/alpha/access_token",
    userinfoEndpoint = "https://example.com/am/oauth2/alpha/userinfo",
    endSessionEndpoint = "https://example.com/am/oauth2/alpha/session/end",
    revocationEndpoint = "https://example.com/am/oauth2/alpha/token/revoke",
    deviceAuthorizationEndpoint = "https://example.com/am/oauth2/realms/root/realms/alpha/device/code",
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

var journey: Journey? = null
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
    Journey(
        buildJsonObject {
            put(JsonConfigKey.LOG, "STANDARD")
            put(JsonConfigKey.JOURNEY, buildJsonObject {
                put(JsonConfigKey.SERVER_URL, config.serverUrl)
                put(JsonConfigKey.REALM, config.realm)
                if (config.cookie.isNotBlank()) put(JsonConfigKey.COOKIE_NAME, config.cookie)
            })
            put(JsonConfigKey.OIDC, buildJsonObject {
                put(JsonConfigKey.CLIENT_ID, config.clientId)
                put(JsonConfigKey.DISCOVERY_ENDPOINT, config.discoveryEndpoint)
                put(JsonConfigKey.SCOPES, config.scopes.toScopesJsonArray())
                put(JsonConfigKey.REDIRECT_URI, config.redirectUri)
                put(JsonConfigKey.DISPLAY, config.display)
                put(JsonConfigKey.STORAGE_FILENAME, "journey")
            })
        }
    ).onSuccess { journey = it }
        .onFailure {
            Logger.STANDARD.d("Failed to create Journey instance: ${it.message}")
            journey = null
        }
    OidcClient(
        buildJsonObject {
            put(JsonConfigKey.OIDC, buildJsonObject {
                put(JsonConfigKey.CLIENT_ID, config.clientId)
                put(JsonConfigKey.DISCOVERY_ENDPOINT, config.discoveryEndpoint)
                put(JsonConfigKey.SCOPES, config.scopes.toScopesJsonArray())
                put(JsonConfigKey.REDIRECT_URI, config.redirectUri)
                put(JsonConfigKey.DISPLAY, config.display)
            })
        }
    ).onSuccess { oidcClient = it }
        .onFailure {
            Logger.STANDARD.d("Failed to create OIDC client instance: ${it.message}")
            oidcClient = null
        }
    redirectUri = config.redirectUri.toUri()
}

internal fun buildDaVinci(config: OidcConfigState) {
    DaVinci(
        buildJsonObject {
            put(JsonConfigKey.LOG, "STANDARD")
            put(JsonConfigKey.OIDC, buildJsonObject {
                put(JsonConfigKey.CLIENT_ID, config.clientId)
                put(JsonConfigKey.DISCOVERY_ENDPOINT, config.discoveryEndpoint)
                put(JsonConfigKey.SCOPES, config.scopes.toScopesJsonArray())
                put(JsonConfigKey.REDIRECT_URI, config.redirectUri)
                put(JsonConfigKey.DISPLAY, config.display)
                if (config.arcValue.isNotBlank()) put(JsonConfigKey.ACR_VALUES, config.arcValue)
                put(JsonConfigKey.STORAGE_FILENAME, "daVinci")
            })
        }
    ).onSuccess { daVinci = it }
        .onFailure { Logger.STANDARD.d("Failed to create DaVinci instance: ${it.message}") }
    // Store in the DaVinci-specific global so it never overwrites Journey's redirectUri
    daVinciRedirectUri = config.redirectUri.toUri()
}

internal fun buildWeb(config: OidcConfigState) {
    OidcWebClient(
        buildJsonObject {
            put(JsonConfigKey.LOG, "STANDARD")
            put(JsonConfigKey.OIDC, buildJsonObject {
                put(JsonConfigKey.CLIENT_ID, config.clientId)
                put(JsonConfigKey.DISCOVERY_ENDPOINT, config.discoveryEndpoint)
                put(JsonConfigKey.SCOPES, config.scopes.toScopesJsonArray())
                put(JsonConfigKey.REDIRECT_URI, config.redirectUri)
                put(JsonConfigKey.DISPLAY, config.display)
            })
            put(JsonConfigKey.WEB, buildJsonObject {
                put(JsonConfigKey.WEB_CUSTOM_TAB_COLOR_SCHEME, CustomTabsIntent.COLOR_SCHEME_DARK)
                put(JsonConfigKey.WEB_AUTH_TAB_COLOR_SCHEME, CustomTabsIntent.COLOR_SCHEME_DARK)
            })
        }
    ).onSuccess { web = it }
        .onFailure {
            Logger.STANDARD.d("Failed to create OIDC Web client instance: ${it.message}")
            web = null
        }
}

internal fun buildDeviceAuthClient(config: DeviceAuthConfigState) {
    OidcDeviceClient(
        buildJsonObject {
            put(JsonConfigKey.OIDC, buildJsonObject {
                put(JsonConfigKey.CLIENT_ID, config.clientId)
                put(JsonConfigKey.DISCOVERY_ENDPOINT, config.discoveryEndpoint)
                put(JsonConfigKey.SCOPES, config.scopes.toScopesJsonArray())
                put(JsonConfigKey.DISPLAY, config.display)
                put(JsonConfigKey.STORAGE_FILENAME, "device_flow")
                if (config.acrValues.isNotBlank()) put(JsonConfigKey.ACR_VALUES, config.acrValues)
                put(JsonConfigKey.OPEN_ID, buildJsonObject {
                    if (config.authorizationEndpoint.isNotBlank()) put(
                        JsonConfigKey.AUTHORIZATION_ENDPOINT,
                        config.authorizationEndpoint
                    )
                    if (config.tokenEndpoint.isNotBlank()) put(
                        JsonConfigKey.TOKEN_ENDPOINT,
                        config.tokenEndpoint
                    )
                    if (config.userinfoEndpoint.isNotBlank()) put(
                        JsonConfigKey.USER_INFO_ENDPOINT,
                        config.userinfoEndpoint
                    )
                    if (config.endSessionEndpoint.isNotBlank()) put(
                        JsonConfigKey.END_SESSION_ENDPOINT,
                        config.endSessionEndpoint
                    )
                    if (config.revocationEndpoint.isNotBlank()) put(
                        JsonConfigKey.REVOCATION_ENDPOINT,
                        config.revocationEndpoint
                    )
                    if (config.deviceAuthorizationEndpoint.isNotBlank()) put(
                        JsonConfigKey.DEVICE_AUTHORIZATION_ENDPOINT,
                        config.deviceAuthorizationEndpoint
                    )
                })
            })
        }
    ).onSuccess { oidcDeviceClient = it }
        .onFailure {
            Logger.STANDARD.d("Failed to create OIDC Device client instance: ${it.message}")
            oidcDeviceClient = null
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
            authorizationEndpoint = prefs[stringPreferencesKey("da_authorizationEndpoint")] ?: "",
            tokenEndpoint = prefs[stringPreferencesKey("da_tokenEndpoint")] ?: "",
            userinfoEndpoint = prefs[stringPreferencesKey("da_userinfoEndpoint")] ?: "",
            endSessionEndpoint = prefs[stringPreferencesKey("da_endSessionEndpoint")] ?: "",
            revocationEndpoint = prefs[stringPreferencesKey("da_revocationEndpoint")] ?: "",
            deviceAuthorizationEndpoint = prefs[stringPreferencesKey("da_deviceAuthorizationEndpoint")] ?: "",
            acrValues = prefs[stringPreferencesKey("da_acrValues")] ?: "",
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
            clientId = "dummyPingOne",
            discoveryEndpoint = "https://auth.pingone.ca/dummy/as/.well-known/openid-configuration",
            scopes = "openid",
            display = "Dummy",
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
                buildDeviceAuthInstance(daApplied)
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

    fun duplicateJourneyConfig(config: JourneyConfigState) {
        customJourneyConfigs = customJourneyConfigs + config.copy(display = "Copy of ${config.display}")
        viewModelScope.launch(Dispatchers.IO) { persistCustomJourneyConfigs(customJourneyConfigs) }
    }

    fun duplicateDaVinciConfig(config: OidcConfigState) {
        customDaVinciConfigs = customDaVinciConfigs + config.copy(display = "Copy of ${config.display}")
        viewModelScope.launch(Dispatchers.IO) { persistCustomDaVinciConfigs(customDaVinciConfigs) }
    }

    fun duplicateWebConfig(config: OidcConfigState) {
        customWebConfigs = customWebConfigs + config.copy(display = "Copy of ${config.display}")
        viewModelScope.launch(Dispatchers.IO) { persistCustomWebConfigs(customWebConfigs) }
    }

    fun duplicateDeviceAuthConfig(config: DeviceAuthConfigState) {
        customDeviceAuthConfigs = customDeviceAuthConfigs + config.copy(display = "Copy of ${config.display}")
        viewModelScope.launch(Dispatchers.IO) { persistCustomDeviceAuthConfigs(customDeviceAuthConfigs) }
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
            selectDeviceAuthConfig(deviceAuthPresets[0])
        }
        viewModelScope.launch(Dispatchers.IO) { persistCustomDeviceAuthConfigs(customDeviceAuthConfigs) }
    }

    // -- SDK instance builders (delegate to package-level functions) ---------

    private fun buildJourneyInstance(config: JourneyConfigState) = buildJourney(config)
    private fun buildDaVinciInstance(config: OidcConfigState) = buildDaVinci(config)
    private fun buildWebInstance(config: OidcConfigState) = buildWeb(config)
    private fun buildDeviceAuthInstance(config: DeviceAuthConfigState) = buildDeviceAuthClient(config)

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
            authorizationEndpoint = prefs[stringPreferencesKey("da_authorizationEndpoint")] ?: "",
            tokenEndpoint = prefs[stringPreferencesKey("da_tokenEndpoint")] ?: "",
            userinfoEndpoint = prefs[stringPreferencesKey("da_userinfoEndpoint")] ?: "",
            endSessionEndpoint = prefs[stringPreferencesKey("da_endSessionEndpoint")] ?: "",
            revocationEndpoint = prefs[stringPreferencesKey("da_revocationEndpoint")] ?: "",
            deviceAuthorizationEndpoint = prefs[stringPreferencesKey("da_deviceAuthorizationEndpoint")] ?: "",
            acrValues = prefs[stringPreferencesKey("da_acrValues")] ?: "",
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
            prefs[stringPreferencesKey("da_authorizationEndpoint")] = config.authorizationEndpoint
            prefs[stringPreferencesKey("da_tokenEndpoint")] = config.tokenEndpoint
            prefs[stringPreferencesKey("da_userinfoEndpoint")] = config.userinfoEndpoint
            prefs[stringPreferencesKey("da_endSessionEndpoint")] = config.endSessionEndpoint
            prefs[stringPreferencesKey("da_revocationEndpoint")] = config.revocationEndpoint
            prefs[stringPreferencesKey("da_deviceAuthorizationEndpoint")] = config.deviceAuthorizationEndpoint
            prefs[stringPreferencesKey("da_acrValues")] = config.acrValues
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
                put("authorizationEndpoint", c.authorizationEndpoint)
                put("tokenEndpoint", c.tokenEndpoint)
                put("userinfoEndpoint", c.userinfoEndpoint)
                put("endSessionEndpoint", c.endSessionEndpoint)
                put("revocationEndpoint", c.revocationEndpoint)
                put("deviceAuthorizationEndpoint", c.deviceAuthorizationEndpoint)
                put("acrValues", c.acrValues)
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
                authorizationEndpoint = o.optString("authorizationEndpoint", ""),
                tokenEndpoint = o.optString("tokenEndpoint", ""),
                userinfoEndpoint = o.optString("userinfoEndpoint", ""),
                endSessionEndpoint = o.optString("endSessionEndpoint", ""),
                revocationEndpoint = o.optString("revocationEndpoint", ""),
                deviceAuthorizationEndpoint = o.optString("deviceAuthorizationEndpoint", ""),
                acrValues = o.optString("acrValues", ""),
            )
        }
    }.getOrDefault(emptyList())
}

/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
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
import com.pingidentity.android.ContextProvider
import com.pingidentity.davinci.DaVinci
import com.pingidentity.davinci.plugin.DaVinci
import com.pingidentity.davinci.user as daVinciUser
import com.pingidentity.journey.Journey
import com.pingidentity.journey.module.Oidc as JourneyOidc
import com.pingidentity.davinci.module.Oidc as DaVinciOidc
import com.pingidentity.journey.user as journeyUser
import com.pingidentity.logger.Logger
import com.pingidentity.logger.STANDARD
import com.pingidentity.oidc.OidcClient
import com.pingidentity.oidc.OidcClientConfig
import com.pingidentity.oidc.OidcWeb
import com.pingidentity.oidc.module.Web
import com.pingidentity.orchestrate.Workflow
import com.pingidentity.samples.pingsampleapp.settingDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

val testDaVinci by lazy {
    DaVinci {
        timeout = 30.seconds.inWholeMilliseconds
        logger = Logger.STANDARD
        module(DaVinciOidc) {
            clientId = "dummy"
            discoveryEndpoint =
                "https://auth.test-one-pingone.com/dummy/as/.well-known/openid-configuration"
            scopes = mutableSetOf("openid", "email", "address")
            redirectUri = "org.forgerock.demo://oauth2redirect"
            display = "DaVinci Test Config"
        }
    }
}

val forgeBlock by lazy {
    Journey {
        logger = Logger.STANDARD

        serverUrl = "https://openam-sdks.forgeblocks.com/am"
        realm = "alpha"
        cookie = "5421aeddf91aa20"
        // Oidc as module
        module(JourneyOidc) {
            clientId = "AndroidTest"
            discoveryEndpoint =
                "https://openam-sdks.forgeblocks.com/am/oauth2/alpha/.well-known/openid-configuration"
            scopes = mutableSetOf("openid", "email", "address", "profile", "phone")
            redirectUri = "org.forgerock.demo:/oauth2redirect"
            display = "Forgeblock Test Config"
            //storage = dataStore
        }
    }
}

val localhost by lazy {
    Journey {
        logger = Logger.STANDARD

        serverUrl = "http://192.168.86.32:8080/openam"
        realm = "root"
        // Oidc as module
        module(JourneyOidc) {
            clientId = "AndroidTest2"
            discoveryEndpoint =
                "http://192.168.86.32:8080/openam/oauth2/.well-known/openid-configuration"
            scopes = mutableSetOf("openid", "email", "address", "profile", "phone")
            redirectUri = "org.forgerock.demo:/oauth2redirect"
            display = "Localhost"
            //storage = dataStore
        }
    }
}

val prodDaVinci by lazy {
    DaVinci {
        logger = Logger.STANDARD
        module(DaVinciOidc) {
            clientId = "dummy"
            discoveryEndpoint =
                "https://auth.pingone.ca/dummy/as/.well-known/openid-configuration"
            scopes = mutableSetOf("openid", "email", "address", "phone", "profile")
            redirectUri = "org.forgerock.demo://oauth2redirect"
            display = "DaVinci Prod Config"
        }
    }
}

val social by lazy {
    DaVinci {
        logger = Logger.STANDARD
        module(DaVinciOidc) {
            clientId = "dummy"
            discoveryEndpoint =
                "https://auth.pingone.com/dummy/as/.well-known/openid-configuration"
            scopes = mutableSetOf("openid", "email", "address")
            redirectUri = "com.pingidentity.demo://oauth2redirect"
            display = "Social Config"
        }
    }
}

// Standalone OIDC configurations (not tied to Journey or DaVinci)
val oidcForgeBlock by lazy {
    OidcWeb {
        logger = Logger.STANDARD
        module(com.pingidentity.oidc.module.Oidc) {
            clientId = "AndroidTest"
            discoveryEndpoint = "https://openam-sdks.forgeblocks.com/am/oauth2/alpha/.well-known/openid-configuration"
            scopes = mutableSetOf("openid", "email", "address", "profile", "phone")
            redirectUri = "org.forgerock.demo:/oauth2redirect"
            display = "OIDC Forgeblock"
        }
        module(Web) {
            customTabsCustomizer = {
                setColorScheme(CustomTabsIntent.COLOR_SCHEME_DARK)
            }
            authTabCustomizer = {
                setColorScheme(CustomTabsIntent.COLOR_SCHEME_DARK)
            }
        }
    }
}

val oidcPingOne by lazy {
    OidcWeb {
        logger = Logger.STANDARD
        module(com.pingidentity.oidc.module.Oidc) {
            clientId = "dummy"
            discoveryEndpoint =
                "https://auth.test-one-pingone.com/dummy/as/.well-known/openid-configuration"
            scopes = mutableSetOf("openid", "email", "address")
            redirectUri = "org.forgerock.demo://oauth2redirect"
            display = "OIDC PingOne"
        }
        module(Web) {
            customTabsCustomizer = {
                setColorScheme(CustomTabsIntent.COLOR_SCHEME_DARK)
            }
            authTabCustomizer = {
                setColorScheme(CustomTabsIntent.COLOR_SCHEME_DARK)
            }
        }
    }
}

var journey = forgeBlock
var oidcClient: OidcClient? = null
var daVinci: DaVinci? = null
var web: OidcWeb? = null
lateinit var redirectUri: Uri //For Social Login redirect parameter using Auth Tab


class EnvViewModel : ViewModel() {
    private val journeyServers = listOf(forgeBlock, localhost)
    private val daVinciServers = listOf(testDaVinci, prodDaVinci, social)
    private val oidcServers = listOf(oidcForgeBlock, oidcPingOne)

    val oidcConfigs = listOf(
        forgeBlock.oidcConfig(),
        localhost.oidcConfig(),
        testDaVinci.oidcConfig(),
        prodDaVinci.oidcConfig(),
        social.oidcConfig(),
        oidcForgeBlock.oidcConfig(),
        oidcPingOne.oidcConfig(),
    )

    var current by mutableStateOf(forgeBlock.oidcConfig())
        private set

    init {
        CoroutineScope(Dispatchers.IO).launch { 
            current = readConfigFromDataStore()
            select(current)
        }
    }


    /**
     * Creates an OidcWeb instance with the provided OIDC configuration.
     * This is used to initialize centralized OIDC login for all authentication types.
     */
    private fun createOidcWeb(oidcConfig: OidcClientConfig): OidcWeb {
        return OidcWeb {
            logger = Logger.STANDARD
            module(com.pingidentity.oidc.module.Oidc) {
                clientId = oidcConfig.clientId
                discoveryEndpoint = oidcConfig.discoveryEndpoint
                scopes = oidcConfig.scopes
                redirectUri = oidcConfig.redirectUri
                signOutRedirectUri = oidcConfig.signOutRedirectUri
                loginHint = oidcConfig.loginHint
                state = oidcConfig.state
                nonce = oidcConfig.nonce
                acrValues = oidcConfig.acrValues
                prompt = oidcConfig.prompt
                display = oidcConfig.display
                uiLocales = oidcConfig.uiLocales
                additionalParameters = oidcConfig.additionalParameters
            }
            module(Web) {
                customTabsCustomizer = {
                    setColorScheme(CustomTabsIntent.COLOR_SCHEME_DARK)
                }
                authTabCustomizer = {
                    setColorScheme(CustomTabsIntent.COLOR_SCHEME_DARK)
                }
            }
        }
    }

    fun select(config: OidcClientConfig) {
        // Determine if this is a Journey, DaVinci, or standalone OIDC server
        val journeyServer = journeyServers.firstOrNull { it.oidcConfig().clientId == config.clientId }
        val daVinciServer = daVinciServers.firstOrNull { it.oidcConfig().clientId == config.clientId }
        val oidcServer = oidcServers.firstOrNull { it.oidcConfig().clientId == config.clientId }

        when {
            journeyServer != null -> {
                journey = journeyServer
                val oidcConfig = journeyServer.oidcConfig()
                redirectUri = oidcConfig.redirectUri.toUri()

                oidcClient = OidcClient {
                    clientId = oidcConfig.clientId
                    discoveryEndpoint = oidcConfig.discoveryEndpoint
                    scopes = oidcConfig.scopes
                    redirectUri = oidcConfig.redirectUri
                    display = oidcConfig.display ?: "Journey"
                }

                // Also initialize OidcWeb for centralized login compatibility
                web = createOidcWeb(oidcConfig)

                // Only logout if switching to a different Journey config
                if (current.clientId != config.clientId && journeyServers.any { it.oidcConfig().clientId == current.clientId }) {
                    CoroutineScope(Dispatchers.Default).launch {
                        journey.journeyUser()?.logout()
                    }
                }
            }
            daVinciServer != null -> {
                daVinci = daVinciServer
                val oidcConfig = daVinciServer.oidcConfig()
                redirectUri = oidcConfig.redirectUri.toUri()

                // Also initialize OidcWeb for centralized login compatibility
                web = createOidcWeb(oidcConfig)

                // Only logout if switching to a different DaVinci config
                if (current.clientId != config.clientId && daVinciServers.any { it.oidcConfig().clientId == current.clientId }) {
                    CoroutineScope(Dispatchers.Default).launch {
                        daVinci?.daVinciUser()?.logout()
                    }
                }
            }
            oidcServer != null -> {
                // Initialize standalone OIDC Web
                web = oidcServer
                val oidcConfig = oidcServer.oidcConfig()
                redirectUri = oidcConfig.redirectUri.toUri()

                // Only logout if switching to a different OIDC config
                if (current.clientId != config.clientId && oidcServers.any { it.oidcConfig().clientId == current.clientId }) {
                    CoroutineScope(Dispatchers.Default).launch {
                        web?.user()?.logout()
                    }
                }
            }
            else -> {
                // Default to forgeblock
                journey = forgeBlock
                val oidcConfig = forgeBlock.oidcConfig()
                redirectUri = oidcConfig.redirectUri.toUri()

                oidcClient = OidcClient {
                    clientId = oidcConfig.clientId
                    discoveryEndpoint = oidcConfig.discoveryEndpoint
                    scopes = oidcConfig.scopes
                    redirectUri = oidcConfig.redirectUri
                    display = oidcConfig.display ?: "Forgerock"
                }

                // Also initialize OidcWeb for centralized login compatibility
                web = createOidcWeb(oidcConfig)
            }
        }

        current = config

        CoroutineScope(Dispatchers.IO).launch {
            ContextProvider.context.settingDataStore.edit { preferences ->
                preferences[stringPreferencesKey("clientId")] = config.clientId
                preferences[stringPreferencesKey("discoveryEndpoint")] = config.discoveryEndpoint
                preferences[stringPreferencesKey("scopes")] = config.scopes.joinToString(",")
                preferences[stringPreferencesKey("redirectUri")] = config.redirectUri
                preferences[stringPreferencesKey("display")] = config.display ?: "Forgerock"
            }
        }
    }

    private suspend fun readConfigFromDataStore(): OidcClientConfig {
        val preferences = ContextProvider.context.settingDataStore.data.first()

        val clientId = preferences[stringPreferencesKey("clientId")]
        val discoveryEndpoint = preferences[stringPreferencesKey("discoveryEndpoint")]
        val scopes = preferences[stringPreferencesKey("scopes")]?.split(",")?.toMutableSet()
        val redirectUri = preferences[stringPreferencesKey("redirectUri")]
        val display = preferences[stringPreferencesKey("display")] ?: ""

        return if (clientId != null && discoveryEndpoint != null && scopes != null && redirectUri != null) {
            config {
                this.clientId = clientId
                this.discoveryEndpoint = discoveryEndpoint
                this.scopes = scopes
                this.redirectUri = redirectUri
                this.display = display
            }
        } else {
            forgeBlock.oidcConfig()
        }
    }
}

/**
 * Get the current [OidcClientConfig] from a Workflow instance (Journey or DaVinci).
 * Cannot use workflow.oidcClientConfig, since it requires the Workflow state to be initialized.
 */
private fun Workflow.oidcConfig(): OidcClientConfig {
    return config.modules.first { it.config is OidcClientConfig }.config as OidcClientConfig
}

/**
 * Get the current [OidcClientConfig] from an OidcWeb instance.
 */
private fun OidcWeb.oidcConfig(): OidcClientConfig {
    return config.modules.first { it.config is OidcClientConfig }.config as OidcClientConfig
}

private fun config(block: OidcClientConfig.() -> Unit): OidcClientConfig {
    return OidcClientConfig().apply(block)
}


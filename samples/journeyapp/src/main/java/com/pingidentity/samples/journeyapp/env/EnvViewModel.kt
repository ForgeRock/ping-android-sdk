/*
 * Copyright (c) 2025 - 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.samples.journeyapp.env

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import com.pingidentity.android.ContextProvider.context
import com.pingidentity.journey.Journey
import com.pingidentity.journey.module.Oidc
import com.pingidentity.journey.user
import com.pingidentity.logger.Logger
import com.pingidentity.logger.STANDARD
import com.pingidentity.oidc.OidcClient
import com.pingidentity.oidc.OidcClientConfig
import com.pingidentity.samples.journeyapp.settingDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

val forgeblock =  Journey {
    logger = Logger.STANDARD

    serverUrl = "https://openam-sdks.forgeblocks.com/am"
    realm = "alpha"
    cookie = "5421aeddf91aa20"

    // Oidc as module
    module(Oidc) {
        clientId = "iosClient"
        discoveryEndpoint =
            "https://openam-sdks.forgeblocks.com/am/oauth2/alpha/.well-known/openid-configuration"
        scopes = mutableSetOf("openid", "email", "address", "profile", "phone")
        redirectUri = "org.forgerock.demo:/oauth2redirect"
        //storage = dataStore
    }
}

val localhost =  Journey {
    logger = Logger.STANDARD

    serverUrl = "https://openam-keyless.forgeblocks.com/am"
    realm = "alpha"
    cookie = "89bf6dea4a81042"

    // Oidc as module
    module(Oidc) {
        clientId = "AndroidTest2"
        discoveryEndpoint =
            "https://openam-keyless.forgeblocks.com/am/oauth2/alpha/.well-known/openid-configuration"
        scopes = mutableSetOf("openid", "email", "address", "profile", "phone")
        redirectUri = "org.forgerock.demo:/oauth2redirect"
        //storage = dataStore
    }
}

var journey = forgeblock
lateinit var oidcClient: OidcClient
lateinit var redirectUri: Uri //For Social Login redirect parameter using Auth Tab

class EnvViewModel : ViewModel() {

    private val servers = listOf(forgeblock, localhost)
    val oidcConfigs = listOf(forgeblock.oidcConfig(), localhost.oidcConfig())

    var current by mutableStateOf(forgeblock.oidcConfig())
        private set

    init {
        CoroutineScope(Dispatchers.IO).launch {
            current = readConfigFromDataStore()
            select(current)
        }
    }

    fun select(config: OidcClientConfig) {

        servers.first { it.oidcConfig().clientId == config.clientId }.let { journey = it }

        val server = servers.firstOrNull { it.oidcConfig().clientId == config.clientId } ?: forgeblock
        journey = server

        val oidcConfig = server.oidcConfig()

        redirectUri = oidcConfig.redirectUri.toUri()

        oidcClient = OidcClient {
            clientId = oidcConfig.clientId
            discoveryEndpoint = oidcConfig.discoveryEndpoint
            scopes = oidcConfig.scopes
            redirectUri = oidcConfig.redirectUri
        }

        if (current.clientId != config.clientId) {
            CoroutineScope(Dispatchers.Default).launch {
                journey.user()?.logout()
            }
        }

        current = oidcConfig

        CoroutineScope(Dispatchers.IO).launch {
            context.settingDataStore.edit { preferences ->
                preferences[stringPreferencesKey("clientId")] = config.clientId
                preferences[stringPreferencesKey("discoveryEndpoint")] = config.discoveryEndpoint
                preferences[stringPreferencesKey("scopes")] = config.scopes.joinToString(",")
                preferences[stringPreferencesKey("redirectUri")] = config.redirectUri
            }
        }
    }

    private suspend fun readConfigFromDataStore(): OidcClientConfig {
        val preferences = context.settingDataStore.data.first()

        val clientId = preferences[stringPreferencesKey("clientId")]
        val discoveryEndpoint = preferences[stringPreferencesKey("discoveryEndpoint")]
        val scopes = preferences[stringPreferencesKey("scopes")]?.split(",")?.toMutableSet()
        val redirectUri = preferences[stringPreferencesKey("redirectUri")]

        return if (clientId != null && discoveryEndpoint != null && scopes != null && redirectUri != null) {
            config {
                this.clientId = clientId
                this.discoveryEndpoint = discoveryEndpoint
                this.scopes = scopes
                this.redirectUri = redirectUri
            }
        } else {
            forgeblock.oidcConfig()
        }

    }
}

/**
 * Get the current [OidcClientConfig] from the [DaVinci] instance.
 * Cannot use Davinci.oidcClientConfig, since it required the DaVinci state to be initialized.
 */
private fun Journey.oidcConfig(): OidcClientConfig {
    return config.modules.first { it.config is OidcClientConfig }.config as OidcClientConfig
}

private fun config(block: OidcClientConfig.() -> Unit): OidcClientConfig {
    return OidcClientConfig().apply(block)
}

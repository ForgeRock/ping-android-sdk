/*
 * Copyright (c) 2024 Ping Identity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.samples.app.env

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import com.pingidentity.android.ContextProvider.context
import com.pingidentity.davinci.DaVinci
import com.pingidentity.davinci.module.Oidc
import com.pingidentity.davinci.plugin.DaVinci
import com.pingidentity.logger.Logger
import com.pingidentity.logger.STANDARD
import com.pingidentity.oidc.OidcClient
import com.pingidentity.oidc.OidcClientConfig
import com.pingidentity.samples.app.User
import com.pingidentity.samples.app.settingDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

val test by lazy {
    DaVinci {
        timeout = 30
        logger = Logger.STANDARD
        module(Oidc) {
            clientId = "3172d977-8fdc-4e8b-b3c5-4f3a34cb7262"
            discoveryEndpoint =
                "https://auth.test-one-pingone.com/0c6851ed-0f12-4c9a-a174-9b1bf8b438ae/as/.well-known/openid-configuration"
            scopes = mutableSetOf("openid", "email", "address")
            redirectUri = "org.forgerock.demo://oauth2redirect"
        }
    }
}

val prod by lazy {
    DaVinci {
        logger = Logger.STANDARD
        module(Oidc) {
            clientId = "c12743f9-08e8-4420-a624-71bbb08e9fe1"
            discoveryEndpoint =
                "https://auth.pingone.ca/02fb4743-189a-4bc7-9d6c-a919edfe6447/as/.well-known/openid-configuration"
            scopes = mutableSetOf("openid", "email", "address", "phone", "profile")
            redirectUri = "org.forgerock.demo://oauth2redirect"
        }
    }
}

val social by lazy {
    DaVinci {
        logger = Logger.STANDARD
        module(Oidc) {
            clientId = "9c7767b5-3a9d-4e9c-9d65-9fc77ccfd284"
            discoveryEndpoint =
                "https://auth.pingone.com/c2a669c0-c396-4544-994d-9c6eb3fb1602/as/.well-known/openid-configuration"
            scopes = mutableSetOf("openid", "email", "address")
            redirectUri = "com.pingidentity.demo://oauth2redirect"
        }
    }
}

lateinit var daVinci: DaVinci
lateinit var oidcClient: OidcClient

class EnvViewModel : ViewModel() {

    val servers = listOf(test, prod, social)
    val oidcConfigs = listOf(test.oidcConfig(), prod.oidcConfig(), social.oidcConfig())

    var current by mutableStateOf(prod.oidcConfig())
        private set

    init {
        CoroutineScope(Dispatchers.IO).launch {
            current = readConfigFromDataStore()
            select(current)
        }
    }

    fun select(config: OidcClientConfig) {

        servers.first { it.oidcConfig().clientId == config.clientId }.let { daVinci = it }

        oidcClient = OidcClient {
            clientId = config.clientId
            discoveryEndpoint = config.discoveryEndpoint
            scopes = config.scopes
            redirectUri = config.redirectUri
        }

        if (current.clientId != config.clientId) {
            CoroutineScope(Dispatchers.Default).launch {
                User.user()?.logout()
            }
        }

        current = config

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
            prod.oidcConfig()
        }

    }
}

/**
 * Get the current [OidcClientConfig] from the [DaVinci] instance.
 * Cannot use Davinci.oidcClientConfig, since it required the DaVinci state to be initialized.
 */
private fun DaVinci.oidcConfig(): OidcClientConfig {
    return config.modules.first { it.config is OidcClientConfig }.config as OidcClientConfig
}

private fun config(block: OidcClientConfig.() -> Unit): OidcClientConfig {
    return OidcClientConfig().apply(block)
}

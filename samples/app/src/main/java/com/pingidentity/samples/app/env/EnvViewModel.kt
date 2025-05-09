/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
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
        logger = Logger.STANDARD
        module(Oidc) {
            clientId = "c12743f9-08e8-4420-a624-71bbb08e9fe1"
            discoveryEndpoint =
                "https://auth.pingone.ca/02fb4743-189a-4bc7-9d6c-a919edfe6447/as/.well-known/openid-configuration"
            scopes = mutableSetOf("openid", "email", "address")
            redirectUri = "org.forgerock.demo://oauth2redirect"
            //acrValues = "22eb75b5d31d371afe089d6e4a824f5c" //MFA Authentication
            //acrValues = "10acbb8fcc64eb479c47481d92786b3b" //MFA registration
            //acrValues = "93928296ac55765e57e30b99da8ddabe" //MFA Multiple Registration
            //acrValues = "9c15f567441c03e6d89f7f32f430ddc4" //OOTB Session Main with HTML
            //acrValues = "6eddf678ec495ad7f27c7eeea2a83936" // MFA FIDO2 Multiple Registration
            //acrValues = "75962e1d55fc0e621c80c42aa9118955" // PingOne Adaptive Authentication with Protect and MFA via OTP
            //acrValues = "2c233c3222e1eaf0686a3c063f05384b" // SDK Automation - MFA Device Registration/Authentication
            acrValues = "9da1b93991bcd577947da228ad4c741f" // Andy - MFA Device Registration/Authentication (PingOne Forms)
        }
        /*
        module(ProtectLifecycle) {
            pauseBehavioralDataOnSuccess = true
            resumeBehavioralDataOnStart = true
        }
         */
    }
}

val prod by lazy {
    DaVinci {
        logger = Logger.STANDARD
        module(Oidc) {
            clientId = "3172d977-8fdc-4e8b-b3c5-4f3a34cb7262"
            discoveryEndpoint = "https://auth.test-one-pingone.com/0c6851ed-0f12-4c9a-a174-9b1bf8b438ae/as/.well-known/openid-configuration"
            scopes = mutableSetOf("openid", "email", "address", "phone", "profile")
            redirectUri = "com.pingidentity.demo://oauth2redirect"
            acrValues = "10acbb8fcc64eb479c47481d92786b3b"
        }
    }
}

val social by lazy {
    DaVinci {
        logger = Logger.STANDARD
        module(Oidc) {
            clientId = "6044ba2a-e4b1-477f-babc-9f622b6e0ff3" /// HTML
//            clientId = "60de77d5-dd2c-41ef-8c40-f8bb2381a359"   /// FORMS
            discoveryEndpoint = "https://auth.pingone.com/c2a669c0-c396-4544-994d-9c6eb3fb1602/as/.well-known/openid-configuration"
            scopes = mutableSetOf("openid", "email", "address", "phone", "profile")
            redirectUri = "org.forgerock.demo://oauth2redirect"

            /*
            clientId = "9c7767b5-3a9d-4e9c-9d65-9fc77ccfd284"
            discoveryEndpoint =
                "https://auth.pingone.com/c2a669c0-c396-4544-994d-9c6eb3fb1602/as/.well-known/openid-configuration"
            scopes = mutableSetOf("openid", "email", "address")
            redirectUri = "com.pingidentity.demo://oauth2redirect"

             */
        }
    }
}


val fido by lazy {
    // android:apk-key-hash:J_FvtrE54Atz0eAala6Oy2M9bhBK2LYIpMUZJVTdSZ0
    DaVinci {
        logger = Logger.STANDARD
        module(Oidc) {
            clientId = "1507c80c-5f06-4c2e-a401-94dd77908047" /// HTML
            discoveryEndpoint = "https://skreddy-test.p14cqa.com/as/.well-known/openid-configuration"
            scopes = mutableSetOf("openid", "email", "address", "phone", "profile")
            redirectUri = "org.forgerock.demo://oauth2redirect"
        }
    }
}



lateinit var daVinci: DaVinci
lateinit var oidcClient: OidcClient

class EnvViewModel : ViewModel() {

    private val servers = listOf(test, prod, social, fido)
    val oidcConfigs = listOf(test.oidcConfig(), prod.oidcConfig(), social.oidcConfig(), fido.oidcConfig())

    var current by mutableStateOf(prod.oidcConfig())
        private set

    init {
        CoroutineScope(Dispatchers.IO).launch {
            current = readConfigFromDataStore()
            select(current)
        }
    }

    fun select(config: OidcClientConfig) {

        val server = servers.firstOrNull { it.oidcConfig().clientId == config.clientId } ?: prod
        daVinci = server

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

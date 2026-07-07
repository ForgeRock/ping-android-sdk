/*
 * Copyright (c) 2024 - 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci

import android.os.LocaleList
import com.pingidentity.davinci.module.ContinueNode
import com.pingidentity.davinci.module.NodeTransform
import com.pingidentity.davinci.module.Oidc
import com.pingidentity.davinci.plugin.DaVinci
import com.pingidentity.oidc.JsonConfigKey
import com.pingidentity.oidc.JsonConfigParser
import com.pingidentity.oidc.update
import com.pingidentity.orchestrate.WorkflowConfig
import com.pingidentity.orchestrate.module.Cookie
import com.pingidentity.orchestrate.module.CustomHeader
import com.pingidentity.orchestrate.module.CustomParameter
import com.pingidentity.orchestrate.module.CustomParameterConfig.Companion.START
import com.pingidentity.utils.toAcceptLanguage
import kotlinx.serialization.json.JsonObject

// typealias DaVinciConfig = WorkflowConfig
private const val ACCEPT_LANGUAGE = "Accept-Language"

class DaVinciConfig : WorkflowConfig()

/**
 * Function to create a DaVinci instance.
 * fun main() {
 *     val daVinci = DaVinci {
 *         module(Oidc) {
 *             clientId = "your-client-id"
 *             redirectUri = "your-redirect-uri"
 *             scopes = listOf("openid", "profile")
 *         }
 *     }
 * }
 *
 * @param block The configuration block.
 *
 * @return The DaVinci instance.
 */
fun DaVinci(block: DaVinciConfig.() -> Unit = {}): DaVinci {
    val config = DaVinciConfig()

    // Apply default
    config.apply {
        module(CustomHeader) {
            header(ACCEPT_LANGUAGE, LocaleList.getDefault().toAcceptLanguage())
        }
        module(CustomParameter) {
            phase = START
            parameter("response_mode", "pi.flow")
        }
        module(NodeTransform)
        //Module cookie has lower priority than Oidc, the Cookie module requires the request Url to be set
        //before it can be applied. The Oidc module will set the request Url
        module(ContinueNode)
        module(Oidc) //Add this here Just to preserve the order
        module(Cookie) {//Depends on the Oidc module
            persist = mutableListOf("ST", "ST-NO-SS")
        }
    }

    // Apply custom
    config.apply(block)

    return DaVinci(config)
}

/**
 * Creates a [DaVinci] instance from a JSON configuration.
 *
 * Required OIDC fields are nested under the `oidc` key. Example:
 * ```json
 * {
 *   "timeout": 30000,
 *   "log": "STANDARD",
 *   "oidc": {
 *     "clientId": "my-client-id",
 *     "discoveryEndpoint": "https://auth.example.com/.well-known/openid-configuration",
 *     "scopes": ["openid", "profile"],
 *     "redirectUri": "myapp://oauth2redirect",
 *     "signOutRedirectUri": "myapp://logout",
 *     "refreshThreshold": 60,
 *     "loginHint": "user@example.com",
 *     "state": "custom-state",
 *     "nonce": "custom-nonce",
 *     "display": "page",
 *     "prompt": "login",
 *     "uiLocales": "en-US",
 *     "acrValues": "Level3",
 *     "par": true,
 *     "additionalParameters": { "max_age": "3600" },
 *     "openId": {
 *       "authorizationEndpoint": "https://auth.example.com/authorize",
 *       "tokenEndpoint": "https://auth.example.com/token",
 *       "userinfoEndpoint": "https://auth.example.com/userinfo",
 *       "endSessionEndpoint": "https://auth.example.com/logout",
 *       "revocationEndpoint": "https://auth.example.com/revoke"
 *     }
 *   }
 * }
 * ```
 *
 * @param json The JSON configuration object.
 * @return A [Result] containing the [DaVinci] instance or an exception if the configuration is invalid.
 */
fun DaVinci(json: JsonObject): Result<DaVinci> {
    return runCatching {
        val jsonConfigParser = JsonConfigParser(json)
        DaVinci {
            logger = jsonConfigParser.logLevel()
            timeout = jsonConfigParser.timeoutMillis()
            module(Oidc) { //Add this here Just to preserve the order
                val oidcJsonConfig = JsonConfigParser(jsonConfigParser.required<JsonObject>(JsonConfigKey.OIDC))
                clientId = oidcJsonConfig.required<String>(JsonConfigKey.CLIENT_ID)
                discoveryEndpoint = oidcJsonConfig.required<String>(JsonConfigKey.DISCOVERY_ENDPOINT)
                scopes = oidcJsonConfig.scopeSet(JsonConfigKey.SCOPES)
                redirectUri = oidcJsonConfig.required<String>(JsonConfigKey.REDIRECT_URI)
                update(oidcJsonConfig)
            }
        }
    }
}
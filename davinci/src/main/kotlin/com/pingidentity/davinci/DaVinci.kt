/*
 * Copyright (c) 2024 - 2025 Ping Identity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci

import android.os.LocaleList
import com.pingidentity.davinci.module.NodeTransform
import com.pingidentity.davinci.module.Oidc
import com.pingidentity.davinci.plugin.DaVinci
import com.pingidentity.orchestrate.WorkflowConfig
import com.pingidentity.orchestrate.module.Cookie
import com.pingidentity.orchestrate.module.CustomHeader
import java.util.Locale

// typealias DaVinciConfig = WorkflowConfig
private const val X_REQUESTED_WITH = "x-requested-with"
private const val X_REQUESTED_PLATFORM = "x-requested-platform"

// Constants for header values
private const val PING_SDK = "ping-sdk"
private const val ANDROID = "android"

class DaVinciConfig : WorkflowConfig()

/**
 * Function to create a DaVinci instance.
 * @sample
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
            header(X_REQUESTED_WITH, PING_SDK)
            header(X_REQUESTED_PLATFORM, ANDROID)
            Locale.TRADITIONAL_CHINESE
            header("Accept-Language", LocaleList.getDefault().toAcceptLanguage())
        }
        module(NodeTransform)
        //Module cookie has lower priority than Oidc, the Cookie module requires the request Url to be set
        //before it can be applied. The Oidc module will set the request Url
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
 * An extension function for the LocaleList class that converts a list of locales
 * (language/region settings) into an HTTP Accept-Language header string.
 */
fun LocaleList.toAcceptLanguage(): String {
    if (isEmpty) return ""

    val languageTags = mutableListOf<String>()
    var currentQValue = 0.9

    (0 until size()).forEach { index ->
        val locale = this[index]

        // Add toLanguageTag version first
        if (index == 0) {
            languageTags.add(locale.toLanguageTag())
            currentQValue = 0.9
        } else {
            languageTags.add("${locale.toLanguageTag()};q=%.1f".format(currentQValue))
            currentQValue -= 0.1
        }

        // Add language version with next q-value
        if (locale.toLanguageTag() != locale.language) {
            languageTags.add("${locale.language};q=%.1f".format(currentQValue))
            currentQValue -= 0.1
        }
    }

    return languageTags.joinToString(", ")
}
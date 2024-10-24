/*
 * Copyright (c) 2024. PingIdentity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci

import com.pingidentity.davinci.module.NodeTransform
import com.pingidentity.davinci.module.Oidc
import com.pingidentity.davinci.plugin.DaVinci
import com.pingidentity.orchestrate.WorkflowConfig
import com.pingidentity.orchestrate.module.Cookie
import com.pingidentity.orchestrate.module.CustomHeader

// typealias DaVinciConfig = WorkflowConfig
private const val X_REQUESTED_WITH = "x-requested-with"
private const val X_REQUESTED_PLATFORM = "x-requested-platform"

// Constants for header values
private const val FORGEROCK_SDK = "forgerock-sdk"
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
            header(X_REQUESTED_WITH, FORGEROCK_SDK)
            header(X_REQUESTED_PLATFORM, ANDROID)
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

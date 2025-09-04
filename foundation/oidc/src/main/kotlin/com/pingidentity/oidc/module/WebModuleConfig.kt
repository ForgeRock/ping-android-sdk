/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.oidc.module

import android.content.Intent
import androidx.browser.auth.AuthTabIntent
import androidx.browser.customtabs.CustomTabsIntent
import com.pingidentity.browser.BrowserLauncher
import com.pingidentity.utils.PingDsl

/**
 * Configuration class for the WebModule
 *
 * WARNING: The customizer properties in this class affect the global [BrowserLauncher] configuration.
 * Setting these properties will modify the global state of [BrowserLauncher], which may affect
 * other components that use the browser functionality.
 *
 * These properties are NOT thread-safe. Configure them during application initialization
 * before any browser operations occur to avoid unexpected behavior.
 */
@PingDsl
class WebModuleConfig {
    /**
     * Customizer for CustomTabsIntent.Builder
     *
     * WARNING: This property modifies the global [BrowserLauncher.customTabsCustomizer].
     * Changes to this property will affect all browser launches throughout the application.
     */
    var customTabsCustomizer: (CustomTabsIntent.Builder).() -> Unit = BrowserLauncher.customTabsCustomizer
        set(value) {
            field = value
            BrowserLauncher.customTabsCustomizer = value
        }

    /**
     * Customizer for AuthTabIntent.Builder
     *
     * WARNING: This property modifies the global [BrowserLauncher.authTabCustomizer].
     * Changes to this property will affect all browser launches throughout the application.
     */
    var authTabCustomizer: (AuthTabIntent.Builder).() -> Unit = BrowserLauncher.authTabCustomizer
        set(value) {
            field = value
            BrowserLauncher.authTabCustomizer = value
        }

    /**
     * Customizer for the Intent used in browser launch
     *
     * WARNING: This property modifies the global [BrowserLauncher.intentCustomizer].
     * Changes to this property will affect all browser launches throughout the application.
     */
    var intentCustomizer: Intent.() -> Unit = BrowserLauncher.intentCustomizer
        set(value) {
            field = value
            BrowserLauncher.intentCustomizer = value
        }
}
/*
 * Copyright (c)  2025 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.profile.collector

import android.annotation.SuppressLint
import android.webkit.WebSettings
import com.pingidentity.android.ContextProvider
import kotlinx.serialization.Serializable

/**
 * Pre-configured device collector for gathering browser and WebView information.
 *
 * This collector captures browser-related device characteristics by examining the default
 * WebView user agent string. The user agent provides valuable device fingerprinting
 * information including:
 * - Android version and build details
 * - Device model and manufacturer information
 * - WebView/Chrome version details
 * - System architecture information
 * - Locale and language preferences
 *
 * The collector is implemented as a lazy-initialized singleton for optimal performance
 * and memory usage across the application lifecycle.
 *
 * **Usage Example:**
 * ```kotlin
 * val browserInfo = BrowserCollector.collect()
 * println("User Agent: ${browserInfo.userAgent}")
 * ```
 *
 * **Integration with Device Profile:**
 * ```kotlin
 * val collectors = listOf(BrowserCollector, /* other collectors */)
 * val deviceProfile = collectors.collect()
 * ```
 *
 * @since 1.0
 * @see DeviceCollector
 * @see BrowserData
 */
val BrowserCollector by lazy {
    DeviceCollector(key = "browser") {
        BrowserData(userAgent = WebSettings.getDefaultUserAgent(ContextProvider.context))
    }
}

/**
 * Data class representing browser and WebView characteristics of the device.
 *
 * This class encapsulates browser-related information that can be used for device
 * fingerprinting, compatibility checks, and security analysis. The data is primarily
 * derived from the WebView's default user agent string.
 *
 * **User Agent Format Example:**
 * ```
 * Mozilla/5.0 (Linux; Android 12; sdk_gphone64_x86_64 Build/SPB5.210812.003; wv)
 * AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/91.0.4472.114 Mobile Safari/537.36
 * ```
 *
 * The user agent string typically contains:
 * - Mozilla compatibility identifier
 * - Platform information (Linux; Android version)
 * - Device model and build information
 * - WebView indicator (wv)
 * - Rendering engine details (WebKit version)
 * - Browser engine information (Chrome version)
 * - Mobile/desktop classification
 *
 * This information is valuable for:
 * - Device fingerprinting and identification
 * - Browser compatibility detection
 * - Security analysis and fraud prevention
 * - Analytics and user experience optimization
 *
 * @property userAgent The default user agent string from the device's WebView implementation
 *
 * @since 1.0
 */
@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class BrowserData(
    /**
     * The default user agent string provided by the device's WebView.
     *
     * This string contains detailed information about the device's browser capabilities,
     * Android version, device model, and WebView implementation. It follows standard
     * user agent conventions and can be used for device fingerprinting and compatibility
     * determination.
     *
     * Example value:
     * "Mozilla/5.0 (Linux; Android 12; Pixel 6 Build/SD1A.210817.023; wv) AppleWebKit/537.36..."
     */
    val userAgent: String
)
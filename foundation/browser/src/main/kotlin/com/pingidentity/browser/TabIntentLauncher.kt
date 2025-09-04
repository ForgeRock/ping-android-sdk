/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.browser

import android.content.Intent
import android.net.Uri
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Interface for browser tab launchers used in authentication flows.
 *
 * This interface defines the common contract for different types of browser launchers
 * (e.g., Auth Tab or Custom Tab) and abstracts away the differences in their implementation.
 *
 * The library provides two implementations:
 * - [AuthTabIntentLauncher]: Uses Chrome's Auth Tab API for enhanced security with custom schemes
 * - [CustomTabsIntentLauncher]: Uses standard Chrome Custom Tabs
 *
 * The appropriate launcher is selected based on device capabilities and redirect URI scheme.
 */
interface TabIntentLauncher {

    /**
     * The ActivityResultLauncher used to launch the browser intent and receive results.
     */
    val activityResultLauncher: ActivityResultLauncher<Intent>

    /**
     * StateFlow to track and communicate the result of the browser launch.
     *
     * This flow emits ActivityResult objects containing the result code and data
     * from the browser activity.
     */
    val state: MutableStateFlow<ActivityResult?>

    /**
     * Launches a URL in a browser tab and waits for the authentication to complete.
     *
     * @param url The authentication URL to launch in the browser.
     * @param redirectUri The URI to which the authentication server should redirect after completion.
     *                    This should match the redirect URI configured with the authentication server.
     * @param pending Whether the launch is already pending (true) or needs to be initiated (false).
     *                When true, the method skips the launch but still waits for and processes the result.
     * @return A Result containing the redirect Uri with authentication data on success,
     *         or an appropriate exception on failure.
     */
    suspend fun launch(url: String, redirectUri: Uri, pending: Boolean = false): Result<Uri>
}
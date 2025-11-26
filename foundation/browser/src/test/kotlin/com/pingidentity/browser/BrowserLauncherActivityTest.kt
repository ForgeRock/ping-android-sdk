/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.browser

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import androidx.browser.auth.AuthTabIntent
import androidx.browser.customtabs.CustomTabsClient
import androidx.core.net.toUri
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.verify
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class BrowserLauncherActivityTest {

    private val context: Context by lazy { ApplicationProvider.getApplicationContext<Application>() }

    @Before
    fun setUp() {
        mockkStatic(CustomTabsClient::class)
        mockkObject(BrowserLauncher)
        mockkStatic(AuthTabIntent::class)

        // Mock BrowserLauncher static properties and methods
        every { BrowserLauncher.redirectUri } returns Uri.parse("https://example.com/callback")
        every { BrowserLauncher.logger } returns mockk(relaxed = true)
        every { BrowserLauncher.onLauncherCreated(any()) } returns Unit
        every { BrowserLauncher.reset() } returns Unit
    }

    @Test
    fun `test onCreate sets screen orientation to locked`() {
        // Given
        val scenario = ActivityScenario.launch(BrowserLauncherActivity::class.java)

        // Then
        scenario.onActivity { activity ->
            assert(activity.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_LOCKED)
        }

        scenario.close()
    }

    @Test
    fun `test uses AuthTabIntentLauncher when conditions are met`() {
        // Given
        val packageName = "com.android.chrome"
        every { CustomTabsClient.getPackageName(any(), any()) } returns packageName
        every { CustomTabsClient.isAuthTabSupported(any(), any()) } returns true

        // Mock AuthTabIntent.registerActivityResultLauncher
        every { AuthTabIntent.registerActivityResultLauncher(any(), any()) } returns mockk()

        // Create a slot to capture the launcher type
        val launcherSlot = slot<TabIntentLauncher>()

        // When
        val intent = Intent(context, BrowserLauncherActivity::class.java ).apply {
            putExtra(BrowserLauncher.EXTRA_REDIRECT_URI, Uri.parse("app://callback"))
        }

        val scenario = ActivityScenario.launch<BrowserLauncherActivity>(intent)

        // Then
        verify { BrowserLauncher.onLauncherCreated(capture(launcherSlot)) }
        assert(launcherSlot.captured is AuthTabIntentLauncher)

        scenario.close()
    }

    @Test
    fun `test uses CustomTabsIntentLauncher when AuthTab is not supported`() {
        // Given
        val packageName = "com.android.chrome"
        every { CustomTabsClient.getPackageName(any(), any()) } returns packageName
        every { CustomTabsClient.isAuthTabSupported(any(), any()) } returns false

        // Create a slot to verify the launcher type
        val launcherSlot = slot<TabIntentLauncher>()

        // When
        val scenario = ActivityScenario.launch(BrowserLauncherActivity::class.java)

        // Then
        verify { BrowserLauncher.onLauncherCreated(capture(launcherSlot)) }
        assert(launcherSlot.captured is CustomTabsIntentLauncher)

        scenario.close()
    }

    @Test
    fun `test uses CustomTabsIntentLauncher when redirect scheme is http`() {
        // Given
        val packageName = "com.android.chrome"
        every { CustomTabsClient.getPackageName(any(), any()) } returns packageName
        every { CustomTabsClient.isAuthTabSupported(any(), any()) } returns true

        // Create a slot to verify the launcher type
        val launcherSlot = slot<TabIntentLauncher>()

        // When
        val intent = Intent(context, BrowserLauncherActivity::class.java ).apply {
            putExtra(BrowserLauncher.EXTRA_REDIRECT_URI, Uri.parse("http://example.com/callback"))
        }

        val scenario = ActivityScenario.launch<BrowserLauncherActivity>(intent)

        // Then
        verify { BrowserLauncher.onLauncherCreated(capture(launcherSlot)) }
        assert(launcherSlot.captured is CustomTabsIntentLauncher)

        scenario.close()
    }

    @Test
    fun `test uses AuthTabIntentLauncher when redirect scheme is https`() {
        // Given
        val packageName = "com.android.chrome"
        every { CustomTabsClient.getPackageName(any(), any()) } returns packageName
        every { CustomTabsClient.isAuthTabSupported(any(), any()) } returns true

        // Create a slot to verify the launcher type
        val launcherSlot = slot<TabIntentLauncher>()

        // When
        val intent = Intent(context, BrowserLauncherActivity::class.java ).apply {
            putExtra(BrowserLauncher.EXTRA_REDIRECT_URI, Uri.parse("https://example.com/callback"))
        }

        val scenario = ActivityScenario.launch<BrowserLauncherActivity>(intent)

        // Then
        verify { BrowserLauncher.onLauncherCreated(capture(launcherSlot)) }
        assert(launcherSlot.captured is AuthTabIntentLauncher)

        scenario.close()
    }

    @Test
    fun `test uses CustomTabsIntentLauncher when redirect scheme is empty`() {
        // Given
        val packageName = "com.android.chrome"
        every { CustomTabsClient.getPackageName(any(), any()) } returns packageName
        every { CustomTabsClient.isAuthTabSupported(any(), any()) } returns true

        // Create a slot to verify the launcher type
        val launcherSlot = slot<TabIntentLauncher>()

        // When
        val intent = Intent(context, BrowserLauncherActivity::class.java ).apply {
            putExtra(BrowserLauncher.EXTRA_REDIRECT_URI, "".toUri())
        }
        val scenario = ActivityScenario.launch<BrowserLauncherActivity>(intent)

        // Then
        verify { BrowserLauncher.onLauncherCreated(capture(launcherSlot)) }
        assert(launcherSlot.captured is CustomTabsIntentLauncher)

        scenario.close()
    }

    @Test
    fun `test uses CustomTabsIntentLauncher when redirect scheme is null`() {
        // Given
        val packageName = "com.android.chrome"
        every { CustomTabsClient.getPackageName(any(), any()) } returns packageName
        every { CustomTabsClient.isAuthTabSupported(any(), any()) } returns true

        // Create a slot to verify the launcher type
        val launcherSlot = slot<TabIntentLauncher>()

        // When - Uri with null scheme
        val uri = mockk<Uri>()
        every { uri.scheme } returns null
        val intent = Intent(context, BrowserLauncherActivity::class.java ).apply {
            putExtra(BrowserLauncher.EXTRA_REDIRECT_URI, uri)
        }
        val scenario = ActivityScenario.launch<BrowserLauncherActivity>(intent)

        // Then
        verify { BrowserLauncher.onLauncherCreated(capture(launcherSlot)) }
        assert(launcherSlot.captured is CustomTabsIntentLauncher)

        scenario.close()
    }

    @Test
    fun `test onDestroy calls BrowserLauncher reset`() {
        // Given
        val scenario = ActivityScenario.launch(BrowserLauncherActivity::class.java)

        // When
        scenario.close()

        // Then
        verify { BrowserLauncher.reset() }
    }

    @Test
    fun `test uses default redirectUri when not provided in intent`() {
        // Given
        val customUri = Uri.parse("custom://example.com/callback")
        every { BrowserLauncher.redirectUri } returns customUri
        every { CustomTabsClient.getPackageName(any(), any()) } returns "com.android.chrome"
        every { CustomTabsClient.isAuthTabSupported(any(), any()) } returns true

        // Mock AuthTabIntent.registerActivityResultLauncher
        every { AuthTabIntent.registerActivityResultLauncher(any(), any()) } returns mockk()

        // Create a slot to verify the launcher type
        val launcherSlot = slot<TabIntentLauncher>()

        // When
        val scenario = ActivityScenario.launch(BrowserLauncherActivity::class.java)

        // Then - we should use the default URI which has a custom scheme
        verify { BrowserLauncher.onLauncherCreated(capture(launcherSlot)) }
        assert(launcherSlot.captured is AuthTabIntentLauncher)

        scenario.close()
    }
}

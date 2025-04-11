/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.browser
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.pingidentity.browser.CustomTabActivity.Companion.URL
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.android.controller.ActivityController
import org.robolectric.shadows.ShadowActivity
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class CustomTabActivityTest {

    private lateinit var activity: CustomTabActivity
    private lateinit var shadowActivity: ShadowActivity
    private lateinit var activityController: ActivityController<CustomTabActivity>

    companion object {
        private const val TEST_URL = "https://example.com"
    }

    @BeforeTest
    fun setup() {
        // Create an intent with a URL
        val intent = Intent().apply {
            putExtra(URL, TEST_URL)
        }

        activityController = Robolectric.buildActivity(CustomTabActivity::class.java, intent)
        val savedInstanceState = Bundle()

        activity = activityController.create(savedInstanceState).get()

        shadowActivity = Shadows.shadowOf(activity)
    }

    @Test
    fun `onCreate with saved instance state should remove URL extra`() {
        // Create a bundle to simulate saved instance state
        val savedInstanceState = Bundle()

        // Create a new activity controller with saved instance state
        val activityController = Robolectric.buildActivity(CustomTabActivity::class.java)
            .create(savedInstanceState)
        val activity = activityController.get()

        // Verify that URL extra is removed
        assertTrue(activity.intent.extras?.getString(URL) == null)
    }

    @Test
    fun `onResume with data intent should set result and finish`() {
        // Modify the intent to have data
        val dataUri = Uri.parse("https://result.com")
        activity.intent.data = dataUri

        // Call onResume
        activityController.resume()

        // Verify result
        assertEquals(Activity.RESULT_OK, shadowActivity.resultCode)

        // Verify result intent
        val resultIntent = shadowActivity.resultIntent
        assertNotNull(resultIntent)
        assertEquals(dataUri, resultIntent.data)

        // Verify activity is finished
        assertTrue(activity.isFinishing)
    }

    @Test
    fun `onResume with no url should set canceled result`() {
        // Call onResume
        activityController.resume()

        // Verify result
        assertEquals(Activity.RESULT_CANCELED, shadowActivity.resultCode)

        // Verify error extras
        val resultIntent = shadowActivity.resultIntent
        assertNotNull(resultIntent)
        assertEquals(
            CustomTabActivity.ERROR_CANCELED,
            resultIntent.getIntExtra(CustomTabActivity.ERROR, -1)
        )
        // Verify activity is finished
        assertTrue(activity.isFinishing)

    }

    @Test
    fun `onNewIntent should update intent`() {
        // Create a new intent
        val newIntent = Intent().apply {
            data = Uri.parse("https://new-intent.com")
        }

        // Call onNewIntent
        activityController.newIntent(newIntent)

        // Verify the intent was updated
        assertEquals(newIntent.data, activity.intent.data)
    }
}
/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.browser

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class IntentLauncherTest {

    @Test
    fun `launch with successful result`() = runTest {
        // Prepare mocks
        val mockLauncher = mockk<ActivityResultLauncher<Intent>>(relaxed = true)
        val state = MutableStateFlow<ActivityResult?>(null)

        // Create IntentLauncher
        val intentLauncher = IntentLauncher(mockLauncher, state)

        // Prepare test intent and uri
        val testIntent = Intent()
        val testUri = Uri.parse("content://test/uri")

        // Create a successful activity result
        val successResult = ActivityResult(
            Activity.RESULT_OK,
            Intent().apply {
                data = testUri
            }
        )

        // Simulate launching and getting result
        launch {
            state.update {
                successResult
            }
        }

        // Call launch method
        val result = intentLauncher.launch(testIntent)

        // Verify
        assertTrue(result.isSuccess)
        assertEquals(testUri, result.getOrNull())
        verify { mockLauncher.launch(testIntent) }

    }

    @Test
    fun `launch with canceled result`() = runTest {
        // Prepare mocks
        val mockLauncher = mockk<ActivityResultLauncher<Intent>>(relaxed = true)
        val state = MutableStateFlow<ActivityResult?>(null)

        // Create IntentLauncher
        val intentLauncher = IntentLauncher(mockLauncher, state)

        // Prepare test intent
        val testIntent = Intent()

        // Create a canceled activity result with browser cancel error
        val canceledResult = ActivityResult(
            Activity.RESULT_CANCELED,
            Intent().apply {
                putExtra(CustomTabActivity.ERROR, CustomTabActivity.ERROR_CANCELED)
            }
        )

        // Simulate launching and getting result
        launch {
            state.update { canceledResult }
        }

        // Call launch method and expect BrowserCanceledException
        val result = intentLauncher.launch(testIntent)

        // Verify
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is BrowserCanceledException)
        verify { mockLauncher.launch(testIntent) }
    }

    @Test
    fun `launch with activity not found error`() = runTest {
        // Prepare mocks
        val mockLauncher = mockk<ActivityResultLauncher<Intent>>(relaxed = true)
        val state = MutableStateFlow<ActivityResult?>(null)

        // Create IntentLauncher
        val intentLauncher = IntentLauncher(mockLauncher, state)

        // Prepare test intent
        val testIntent = Intent()

        // Create a canceled activity result with activity not found error
        val canceledResult = ActivityResult(
            Activity.RESULT_CANCELED,
            Intent().apply {
                putExtra(CustomTabActivity.ERROR, CustomTabActivity.ERROR_ACTIVITY_NOT_FOUND)
                putExtra(CustomTabActivity.ERROR_MESSAGE, "Test error message")
            }
        )

        // Simulate launching and getting result
        launch {
            state.update { canceledResult }
        }

        // Call launch method and expect ActivityNotFoundException
        val result = intentLauncher.launch(testIntent)

        // Verify
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ActivityNotFoundException)
        assertEquals("Test error message", result.exceptionOrNull()?.message)
        verify { mockLauncher.launch(testIntent) }

    }

    @Test
    fun `launch with no uri in result`() = runTest {
        // Prepare mocks
        val mockLauncher = mockk<ActivityResultLauncher<Intent>>(relaxed = true)
        val state = MutableStateFlow<ActivityResult?>(null)

        // Create IntentLauncher
        val intentLauncher = IntentLauncher(mockLauncher, state)

        // Prepare test intent
        val testIntent = Intent()

        // Create a successful activity result with no uri
        val successResultNoUri = ActivityResult(
            Activity.RESULT_OK,
            Intent() // Empty intent with no data
        )

        // Simulate launching and getting result
        launch {
            state.update { successResultNoUri }
        }

        // Call launch method and expect IllegalStateException
        val result = intentLauncher.launch(testIntent)

        // Verify
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
        assertEquals("No Uri found in response", result.exceptionOrNull()?.message)
        verify { mockLauncher.launch(testIntent) }

    }

    @Test
    fun `launch with pending flag`() = runTest {
        // Prepare mocks
        val mockLauncher = mockk<ActivityResultLauncher<Intent>>(relaxed = true)
        val state = MutableStateFlow<ActivityResult?>(null)

        // Create IntentLauncher
        val intentLauncher = IntentLauncher(mockLauncher, state)

        // Prepare test intent
        val testIntent = Intent()

        // Simulate launching with pending flag
        launch {
            state.update {
                ActivityResult(
                    Activity.RESULT_OK,
                    Intent().apply { data = Uri.parse("content://test/uri") }
                )
            }
        }

        // Call launch method with pending flag
        val result = intentLauncher.launch(testIntent, pending = true)

        // Verify
        assertTrue(result.isSuccess)
        // Verify launcher was NOT called due to pending flag
        verify(exactly = 0) { mockLauncher.launch(testIntent) }

    }
}
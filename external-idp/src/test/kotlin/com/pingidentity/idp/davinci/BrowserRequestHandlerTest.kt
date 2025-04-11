/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.idp.davinci

import android.net.Uri
import com.pingidentity.browser.BrowserLauncher
import com.pingidentity.orchestrate.ContinueNode
import io.mockk.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.URL
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@RunWith(RobolectricTestRunner::class)
class BrowserRequestHandlerTest {
    private lateinit var continueNode: ContinueNode
    private lateinit var browserRequestHandler: BrowserRequestHandler

    @BeforeTest
    fun setup() {
        continueNode = mockk()
        browserRequestHandler = BrowserRequestHandler(continueNode)

        mockkObject(BrowserLauncher)
    }

    @AfterTest
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `authorize successful case`() = runTest {
        // Arrange
        val continueUrl = "https://api.example.com/continue"
        val authUrl = "https://auth.example.com/login"
        val continueToken = "abc123"
        val successResult = Result.success(
            Uri.parse("https://callback.example.com?continueToken=$continueToken&other=params")
        )

        setupContinueNodeMock(continueUrl)
        mockBrowserLaunch(authUrl, successResult)

        // Act
        val request = browserRequestHandler.authorize(authUrl)

        // Assert
        assertEquals(continueUrl, request.builder.url.toString())
        assertEquals("Bearer $continueToken", request.builder.headers["Authorization"])
        coVerifyAll {
            continueNode.input
            BrowserLauncher.launch(URL(authUrl))
        }
    }

    @Test
    fun `authorize throws when continue URL not found`() = runTest {
        // Arrange
        val authUrl = "https://auth.example.com/login"
        setupContinueNodeMock(null)

        // Act & Assert
        assertFailsWith<IllegalStateException>("Continue URL not found") {
            browserRequestHandler.authorize(authUrl)
        }
        verify {
            continueNode.input
        }
    }

    @Test
    fun `authorize throws when browser launch fails`() = runTest {
        // Arrange
        val continueUrl = "https://api.example.com/continue"
        val authUrl = "https://auth.example.com/login"
        val exception = Exception("Browser launch failed")
        val failureResult = Result.failure<Uri>(exception)

        setupContinueNodeMock(continueUrl)
        mockBrowserLaunch(authUrl, failureResult)

        // Act & Assert
        assertFailsWith<Exception>("Browser launch failed") {
            browserRequestHandler.authorize(authUrl)
        }
        coVerifyAll {
            continueNode.input
            BrowserLauncher.launch(any())
        }
    }

    @Test
    fun `authorize throws IllegalStateException when browser result is failure with null exception`() = runTest {
        // Arrange
        val continueUrl = "https://api.example.com/continue"
        val authUrl = "https://auth.example.com/login"
        val failureResult = Result.failure<Uri>(IllegalStateException("Authorization failed"))

        setupContinueNodeMock(continueUrl)
        mockBrowserLaunch(authUrl, failureResult)

        // Act & Assert
        assertFailsWith<IllegalStateException>("Authorization failed") {
            browserRequestHandler.authorize(authUrl)
        }
        coVerifyAll {
            continueNode.input
            BrowserLauncher.launch(URL(authUrl))
        }
    }

    private fun setupContinueNodeMock(continueUrl: String?) {
        val linksJson = if (continueUrl != null) {
            buildJsonObject {
                put("continue", buildJsonObject {
                    put("href", JsonPrimitive(continueUrl))
                })
            }
        } else {
            JsonObject(emptyMap())
        }

        val inputJson = buildJsonObject {
            put("_links", linksJson)
        }

        every { continueNode.input } returns inputJson
    }

    private fun mockBrowserLaunch(url: String, result: Result<Uri>) {
        coEvery {
            BrowserLauncher.launch(URL(url))
        } returns result
    }


}
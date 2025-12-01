/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.idp.davinci

import android.net.Uri
import androidx.core.net.toUri
import com.pingidentity.browser.BrowserLauncher
import com.pingidentity.network.HttpClient
import com.pingidentity.network.ktor.KtorHttpRequest
import com.pingidentity.orchestrate.ContinueNode
import com.pingidentity.orchestrate.Workflow
import com.pingidentity.orchestrate.WorkflowConfig
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyAll
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
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
        continueNode = mockk(relaxed = true)
        val workFlow = mockk<Workflow>(relaxed = true)
        val workflowConfig = mockk<WorkflowConfig>(relaxed = true)
        val httpclient = mockk<HttpClient>(relaxed = true)
        every { continueNode.workflow } returns workFlow
        every { workFlow.config } returns workflowConfig
        every { workflowConfig.httpClient } returns httpclient
        every { httpclient.request() } returns KtorHttpRequest()


        browserRequestHandler = BrowserRequestHandler(continueNode, "https://example.com/callback".toUri())

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
        val request = browserRequestHandler.authorize(authUrl) as KtorHttpRequest

        // Assert
        assertEquals(continueUrl, request.url)
        assertEquals("Bearer $continueToken", request.builder.headers["Authorization"])
        coVerifyAll {
            continueNode.input
            continueNode.workflow
            BrowserLauncher.launch(URL(authUrl), any())
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
            BrowserLauncher.launch(any(), any())
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
            BrowserLauncher.launch(URL(authUrl), any())
        }
    }

    @Test
    fun `authorize captures redirect URL correctly`() = runTest {
        // Arrange
        val continueUrl = "https://api.example.com/continue"
        val authUrl = "https://auth.example.com/login"
        val redirectUri = "https://example.com/callback".toUri()
        val continueToken = "abc123"
        val successResult = Result.success(
            "$redirectUri?continueToken=$continueToken".toUri()
        )

        setupContinueNodeMock(continueUrl)
        mockBrowserLaunch(authUrl, successResult)

        // Act
        browserRequestHandler.authorize(authUrl)

        // Assert
        coVerify {
            BrowserLauncher.launch(URL(authUrl), redirectUri)
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
            BrowserLauncher.launch(URL(url), any())
        } returns result
    }

}
/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.idp.journey

import android.net.Uri
import androidx.core.net.toUri
import com.pingidentity.browser.BrowserLauncher
import com.pingidentity.browser.BrowserLauncher.launch
import com.pingidentity.idp.IdpClient
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.URL

@RunWith(RobolectricTestRunner::class)
class AppleHandlerTest {

    private lateinit var appleHandler: AppleHandler
    private lateinit var mockIdpClient: IdpClient
    private lateinit var mockUri: Uri

    @Before
    fun setup() {
        appleHandler = AppleHandler("https://example.com/callback".toUri())

        mockIdpClient = mockk<IdpClient> {
            every { clientId } returns "test.client.id"
            every { redirectUri } returns "https://example.com/callback"
            every { scopes } returns listOf("email", "name")
            every { nonce } returns "test-nonce-123"
        }

        mockUri = mockk<Uri> {
            every { queryParameterNames } returns setOf("code", "state", "id_token")
            every { getQueryParameter("code") } returns "auth-code-123"
            every { getQueryParameter("state") } returns "state-value"
            every { getQueryParameter("id_token") } returns "id-token-value"
        }

        mockkObject(BrowserLauncher)
    }

    @After
    fun tearDown() {
        unmockkObject(BrowserLauncher)
    }

    @Test
    fun `test authorize constructs correct request and returns form post entry`() = runTest {
        // Given
        coEvery { launch(any(), any()) } returns Result.success(mockUri)

        // When
        val result = appleHandler.authorize(mockIdpClient)

        // Then
        assertEquals("form_post_entry", result.token)
        assertEquals(3, result.additionalParameters.size)
        assertEquals("auth-code-123", result.additionalParameters["code"])
        assertEquals("state-value", result.additionalParameters["state"])
        assertEquals("id-token-value", result.additionalParameters["id_token"])
    }

    @Test
    fun `test authorize with failed browser launch`() = runTest {
        // Given
        val expectedException = RuntimeException("Browser launch failed")
        coEvery { launch(any(), any()) } returns Result.failure(expectedException)

        // When
        val exception = runCatching { appleHandler.authorize(mockIdpClient) }
            .exceptionOrNull()

        // Then
        assertTrue(exception is RuntimeException)
        assertEquals("Browser launch failed", exception?.message)
    }

    @Test
    fun `test authorize constructs URI with correct parameters`() = runTest {
        // Given
        val capturedUrl = mutableListOf<URL>()
        coEvery { launch(capture(capturedUrl), any()) } returns Result.success(mockUri)

        // When
        appleHandler.authorize(mockIdpClient)

        // Then
        val url = capturedUrl.first().toString()
        assertTrue(url.startsWith("https://appleid.apple.com/auth/authorize"))
        assertTrue(url.contains("client_id=test.client.id"))
        assertTrue(url.contains("redirect_uri=https%3A%2F%2Fexample.com%2Fcallback"))
        assertTrue(url.contains("response_mode=form_post"))
        assertTrue(url.contains("response_type=code"))
        assertTrue(url.contains("scope=email%20name"))
        assertTrue(url.contains("nonce=test-nonce-123"))
    }

    @Test
    fun `test token type is authorization_code`() {
        // Then
        assertEquals("authorization_code", appleHandler.tokenType)
    }

    @Test
    fun `test authorize with empty response from browser`() = runTest {
        // Given
        val emptyUri = mockk<Uri> {
            every { queryParameterNames } returns emptySet()
        }
        coEvery { launch(any(), any()) } returns Result.success(emptyUri)

        // When
        val result = appleHandler.authorize(mockIdpClient)

        // Then
        assertEquals("form_post_entry", result.token)
        assertTrue(result.additionalParameters.isEmpty())
    }

}

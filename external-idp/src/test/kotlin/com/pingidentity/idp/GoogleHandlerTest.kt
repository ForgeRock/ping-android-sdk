/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.idp

import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.pingidentity.android.ContextProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class GoogleHandlerTest {
    private lateinit var googleHandler: GoogleHandler
    private lateinit var mockCredentialManager: CredentialManager
    private lateinit var mockIdpClient: IdpClient
    private lateinit var mockContext: Context
    private lateinit var mockActivity: Activity

    @BeforeTest
    fun setUp() {
        googleHandler = GoogleHandler()
        mockCredentialManager = mockk()
        mockIdpClient = mockk()
        mockContext = mockk()
        mockActivity = mockk()

        // Mock CredentialManager.create()
        mockkObject(CredentialManager.Companion)
        every {
            CredentialManager.create(mockContext)
        } returns mockCredentialManager

        // Mock ContextProvider
        mockkObject(ContextProvider)
        every { ContextProvider.context } returns mockContext

        mockkObject(ContextProvider)
        every { ContextProvider.context } returns mockContext
        every { ContextProvider.currentActivity } returns mockActivity
    }

    @AfterTest
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `test successful authorization`() = runBlocking {
        // Given
        val clientId = "test_client_id"
        val nonce = "test_nonce"
        val idToken = "test_id_token"

        every { mockIdpClient.clientId } returns clientId
        every { mockIdpClient.nonce } returns nonce

        // Mock credential response
        val mockCustomCredential = mockk<CustomCredential>()
        every { mockCustomCredential.type } returns GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        val mockResponse = mockk<GetCredentialResponse>()
        every { mockResponse.credential } returns mockCustomCredential

        // Mock GoogleIdTokenCredential
        val mockGoogleIdTokenCredential = mockk<GoogleIdTokenCredential>()
        mockkObject(GoogleIdTokenCredential.Companion)
        every { GoogleIdTokenCredential.createFrom(any()) } returns mockGoogleIdTokenCredential

        every { mockGoogleIdTokenCredential.idToken } returns idToken

        coEvery {
            mockCredentialManager.getCredential(any(), any<GetCredentialRequest>())
        } returns mockResponse

        every { mockResponse.credential } returns mockCustomCredential
        every { mockCustomCredential.data } returns mockk()

        // When
        val result = googleHandler.authorize(mockIdpClient)

        // Then
        assertEquals(idToken, result.token)
        coVerify { mockCredentialManager.getCredential(any(), any<GetCredentialRequest>()) }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `test missing client ID throws exception`() = runTest {
        // Given
        every { mockIdpClient.clientId } returns null
        every { mockIdpClient.nonce } returns "test_nonce"

        // When
        googleHandler.authorize(mockIdpClient)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `test missing nonce throws exception`() = runTest {
        // Given
        every { mockIdpClient.clientId } returns "test_client_id"
        every { mockIdpClient.nonce } returns null

        // When
        googleHandler.authorize(mockIdpClient)
    }

    @Test(expected = IllegalStateException::class)
    fun `test invalid credential type throws exception`() = runTest {
        // Given
        every { mockIdpClient.clientId } returns "test_client_id"
        every { mockIdpClient.nonce } returns "test_nonce"

        val mockCustomCredential = mockk<CustomCredential>()
        every { mockCustomCredential.type } returns "invalid_type"
        val mockResponse = mockk<GetCredentialResponse>()
        every { mockResponse.credential } returns mockCustomCredential

        coEvery {
            mockCredentialManager.getCredential(any(), any<GetCredentialRequest>())
        } returns mockResponse

        // When
        googleHandler.authorize(mockIdpClient)
    }

    @Test
    fun `verify token type is set correctly`() {
        assertEquals("id_token", googleHandler.tokenType)
    }
}
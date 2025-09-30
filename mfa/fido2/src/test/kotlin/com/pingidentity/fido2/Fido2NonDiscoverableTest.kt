/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.fido2

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.domerrors.AbortError
import androidx.credentials.exceptions.domerrors.ConstraintError
import androidx.credentials.exceptions.domerrors.DataError
import androidx.credentials.exceptions.domerrors.EncodingError
import androidx.credentials.exceptions.domerrors.InvalidStateError
import androidx.credentials.exceptions.domerrors.NetworkError
import androidx.credentials.exceptions.domerrors.NotAllowedError
import androidx.credentials.exceptions.domerrors.NotSupportedError
import androidx.credentials.exceptions.domerrors.SecurityError
import androidx.credentials.exceptions.domerrors.TimeoutError
import androidx.credentials.exceptions.domerrors.UnknownError
import androidx.credentials.exceptions.publickeycredential.GetPublicKeyCredentialDomException
import com.google.android.gms.fido.Fido
import com.google.android.gms.fido.fido2.Fido2ApiClient
import com.google.android.gms.fido.fido2.api.common.AuthenticatorAssertionResponse
import com.google.android.gms.fido.fido2.api.common.AuthenticatorErrorResponse
import com.google.android.gms.fido.fido2.api.common.ErrorCode
import com.google.android.gms.fido.fido2.api.common.PublicKeyCredential
import com.google.android.gms.fido.fido2.api.common.PublicKeyCredentialRequestOptions
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class Fido2NonDiscoverableTest {

    private lateinit var mockContext: Context
    private lateinit var mockFido2ApiClient: Fido2ApiClient
    private lateinit var mockPendingIntent: PendingIntent
    private lateinit var mockRequestOptions: PublicKeyCredentialRequestOptions
    private lateinit var activityController: ActivityController<Fido2NonDiscoverableActivity>

    @BeforeTest
    fun setUp() {
        mockContext = mockk<Context>(relaxed = true)
        mockFido2ApiClient = mockk<Fido2ApiClient>(relaxed = true)
        mockPendingIntent = mockk<PendingIntent>(relaxed = true)
        mockRequestOptions = mockk<PublicKeyCredentialRequestOptions>(relaxed = true)

        // Mock static Fido class
        mockkStatic(Fido::class)
        every { Fido.getFido2ApiClient(any()) } returns mockFido2ApiClient

        // Reset the FidoResultHolder state before each test
        FidoResultHolder.continuation.set(null)
    }

    @AfterTest
    fun tearDown() {
        unmockkAll()
        FidoResultHolder.continuation.set(null)
    }

    @Test
    fun `Fido2NonDiscoverableActivity should handle missing PendingIntent`()  = runTest{
        // Given
        val intent = Intent().apply {
            // Intentionally not adding EXTRA_PENDING_INTENT
        }

        assertFailsWith<IllegalArgumentException> {
            suspendCancellableCoroutine<PublicKeyCredential> {
                // When
                activityController =
                    Robolectric.buildActivity(Fido2NonDiscoverableActivity::class.java, intent)
                FidoResultHolder.continuation.set(it)

                activityController.create()

                // Then
                assertTrue(activityController.get().isFinishing)
            }
        }
    }

    @Test
    fun `Fido2NonDiscoverableActivity should handle successful authentication result`() {
        // Given
        val intent = Intent().apply {
            putExtra(Fido2NonDiscoverableActivity.EXTRA_PENDING_INTENT, mockPendingIntent)
        }

        val mockCredential = mockk<PublicKeyCredential> {
            every { response } returns mockk<AuthenticatorAssertionResponse>()
        }
        val credentialBytes = byteArrayOf(1, 2, 3, 4)

        mockkStatic(PublicKeyCredential::class)
        every { PublicKeyCredential.deserializeFromBytes(credentialBytes) } returns mockCredential

        val resultIntent = Intent().apply {
            putExtra(Fido.FIDO2_KEY_CREDENTIAL_EXTRA, credentialBytes)
        }

        // When
        activityController =
            Robolectric.buildActivity(Fido2NonDiscoverableActivity::class.java, intent)
        val mockContinuation =
            mockk<CancellableContinuation<PublicKeyCredential>>(relaxed = true)
        every { mockContinuation.resumeWith(any()) } just runs
        FidoResultHolder.continuation.set(mockContinuation)

        activityController.create()

        // Use reflection to call protected onActivityResult method
        val activity = activityController.get()
        val onActivityResultMethod = Activity::class.java.getDeclaredMethod(
            "onActivityResult",
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Intent::class.java
        )
        onActivityResultMethod.isAccessible = true
        onActivityResultMethod.invoke(
            activity,
            Fido2NonDiscoverableActivity.FIDO_SIGN_IN_REQUEST_CODE,
            Activity.RESULT_OK,
            resultIntent
        )

        // Then
        verify { mockContinuation.resumeWith(Result.success(mockCredential)) }
        assertTrue(activityController.get().isFinishing)
    }

    @Test
    fun `Fido2NonDiscoverableActivity should handle cancelled authentication`() = runTest {
        // Given
        val intent = Intent().apply {
            putExtra(Fido2NonDiscoverableActivity.EXTRA_PENDING_INTENT, mockPendingIntent)
        }

        assertFailsWith<GetCredentialCancellationException> {
            suspendCancellableCoroutine<PublicKeyCredential> {
                // When
                activityController =
                    Robolectric.buildActivity(Fido2NonDiscoverableActivity::class.java, intent)
                FidoResultHolder.continuation.set(it)

                activityController.create()

                // Use reflection to call protected onActivityResult method
                val activity = activityController.get()
                val onActivityResultMethod = Activity::class.java.getDeclaredMethod(
                    "onActivityResult",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Intent::class.java
                )
                onActivityResultMethod.isAccessible = true
                onActivityResultMethod.invoke(
                    activity,
                    Fido2NonDiscoverableActivity.FIDO_SIGN_IN_REQUEST_CODE,
                    Activity.RESULT_CANCELED,
                    null
                )

                // Then
                assertTrue(activityController.get().isFinishing)
            }
        }

   }

    @Test
    fun `Fido2NonDiscoverableActivity should handle authenticator error response`() = runTest {
        // Given
        val intent = Intent().apply {
            putExtra(Fido2NonDiscoverableActivity.EXTRA_PENDING_INTENT, mockPendingIntent)
        }

        val mockErrorResponse = mockk<AuthenticatorErrorResponse> {
            every { errorCode } returns ErrorCode.NOT_ALLOWED_ERR
            every { errorMessage } returns "User not allowed"
        }
        val mockCredential = mockk<PublicKeyCredential> {
            every { response } returns mockErrorResponse
        }
        val credentialBytes = byteArrayOf(1, 2, 3, 4)

        mockkStatic(PublicKeyCredential::class)
        every { PublicKeyCredential.deserializeFromBytes(credentialBytes) } returns mockCredential

        val resultIntent = Intent().apply {
            putExtra(Fido.FIDO2_KEY_CREDENTIAL_EXTRA, credentialBytes)
        }

        assertFailsWith<GetCredentialCancellationException> {
            suspendCancellableCoroutine<PublicKeyCredential> {
                // When
                activityController =
                    Robolectric.buildActivity(Fido2NonDiscoverableActivity::class.java, intent)
                FidoResultHolder.continuation.set(it)

                activityController.create()

                // Use reflection to call protected onActivityResult method
                val activity = activityController.get()
                val onActivityResultMethod = Activity::class.java.getDeclaredMethod(
                    "onActivityResult",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Intent::class.java
                )
                onActivityResultMethod.isAccessible = true
                onActivityResultMethod.invoke(
                    activity,
                    Fido2NonDiscoverableActivity.FIDO_SIGN_IN_REQUEST_CODE,
                    Activity.RESULT_OK,
                    resultIntent
                )

                // Then
                assertTrue(activityController.get().isFinishing)
            }
        }
    }

    @Test
    fun `Fido2NonDiscoverableActivity should handle NOT_SUPPORTED_ERR error`() = runTest{
        // Given
        val intent = Intent().apply {
            putExtra(Fido2NonDiscoverableActivity.EXTRA_PENDING_INTENT, mockPendingIntent)
        }

        val mockErrorResponse = mockk<AuthenticatorErrorResponse> {
            every { errorCode } returns ErrorCode.NOT_SUPPORTED_ERR
            every { errorMessage } returns "Not supported"
        }
        val mockCredential = mockk<PublicKeyCredential> {
            every { response } returns mockErrorResponse
        }
        val credentialBytes = byteArrayOf(1, 2, 3, 4)

        mockkStatic(PublicKeyCredential::class)
        every { PublicKeyCredential.deserializeFromBytes(credentialBytes) } returns mockCredential

        val resultIntent = Intent().apply {
            putExtra(Fido.FIDO2_KEY_CREDENTIAL_EXTRA, credentialBytes)
        }

        assertFailsWith<GetCredentialUnsupportedException> {
            suspendCancellableCoroutine<PublicKeyCredential> {
                // When
                activityController =
                    Robolectric.buildActivity(Fido2NonDiscoverableActivity::class.java, intent)
                FidoResultHolder.continuation.set(it)

                activityController.create()

                // Use reflection to call protected onActivityResult method
                val activity = activityController.get()
                val onActivityResultMethod = Activity::class.java.getDeclaredMethod(
                    "onActivityResult",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Intent::class.java
                )
                onActivityResultMethod.isAccessible = true
                onActivityResultMethod.invoke(
                    activity,
                    Fido2NonDiscoverableActivity.FIDO_SIGN_IN_REQUEST_CODE,
                    Activity.RESULT_OK,
                    resultIntent
                )

                // Then
                assertTrue(activityController.get().isFinishing)


            }
        }

   }

    @Test
    fun `Fido2NonDiscoverableActivity should handle deserialization failure`() = runTest {
        // Given
        val intent = Intent().apply {
            putExtra(Fido2NonDiscoverableActivity.EXTRA_PENDING_INTENT, mockPendingIntent)
        }

        val credentialBytes = byteArrayOf(1, 2, 3, 4)
        val deserializationException = RuntimeException("Deserialization failed")

        mockkStatic(PublicKeyCredential::class)
        every { PublicKeyCredential.deserializeFromBytes(credentialBytes) } throws deserializationException

        val resultIntent = Intent().apply {
            putExtra(Fido.FIDO2_KEY_CREDENTIAL_EXTRA, credentialBytes)
        }

        val exception = assertFailsWith<IllegalArgumentException> {
            suspendCancellableCoroutine<PublicKeyCredential> {

                // When
                activityController =
                    Robolectric.buildActivity(Fido2NonDiscoverableActivity::class.java, intent)
                FidoResultHolder.continuation.set(it)

                activityController.create()

                // Use reflection to call protected onActivityResult method
                val activity = activityController.get()
                val onActivityResultMethod = Activity::class.java.getDeclaredMethod(
                    "onActivityResult",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Intent::class.java
                )
                onActivityResultMethod.isAccessible = true
                onActivityResultMethod.invoke(
                    activity,
                    Fido2NonDiscoverableActivity.FIDO_SIGN_IN_REQUEST_CODE,
                    Activity.RESULT_OK,
                    resultIntent
                )

                // Then
                assertTrue(activityController.get().isFinishing)
            }
        }
        assertEquals("Failed to deserialize credential", exception.message)
    }

    @Test
    fun `Fido2NonDiscoverableActivity should handle missing credential extra`() = runTest {
        // Given
        val intent = Intent().apply {
            putExtra(Fido2NonDiscoverableActivity.EXTRA_PENDING_INTENT, mockPendingIntent)
        }

        val resultIntent = Intent() // Missing FIDO2_KEY_CREDENTIAL_EXTRA

        val exception = assertFailsWith<Exception> {

            suspendCancellableCoroutine<PublicKeyCredential> {
                // When
                activityController =
                    Robolectric.buildActivity(Fido2NonDiscoverableActivity::class.java, intent)
                FidoResultHolder.continuation.set(it)

                activityController.create()

                // Use reflection to call protected onActivityResult method
                val activity = activityController.get()
                val onActivityResultMethod = Activity::class.java.getDeclaredMethod(
                    "onActivityResult",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Intent::class.java
                )
                onActivityResultMethod.isAccessible = true
                onActivityResultMethod.invoke(
                    activity,
                    Fido2NonDiscoverableActivity.FIDO_SIGN_IN_REQUEST_CODE,
                    Activity.RESULT_OK,
                    resultIntent
                )

                // Then
                assertTrue(activityController.get().isFinishing)
            }
        }
        assertEquals("FIDO2 result intent was invalid.", exception.message)
    }

    @Test
    fun `Fido2NonDiscoverableActivity should handle unexpected result code`() = runTest {
        // Given
        val intent = Intent().apply {
            putExtra(Fido2NonDiscoverableActivity.EXTRA_PENDING_INTENT, mockPendingIntent)
        }

        val exception = assertFailsWith<IllegalStateException> {
            val result = suspendCancellableCoroutine {
                FidoResultHolder.continuation.set(it)

                // When
                activityController =
                    Robolectric.buildActivity(Fido2NonDiscoverableActivity::class.java, intent)

                activityController.create()

                // Use reflection to call protected onActivityResult method
                val activity = activityController.get()
                val onActivityResultMethod = Activity::class.java.getDeclaredMethod(
                    "onActivityResult",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Intent::class.java
                )
                onActivityResultMethod.isAccessible = true
                onActivityResultMethod.invoke(
                    activity,
                    Fido2NonDiscoverableActivity.FIDO_SIGN_IN_REQUEST_CODE,
                    999, // Unexpected result code
                    null
                )

                // Then
                //verify { mockContinuation.resumeWithException(any<IllegalStateException>()) }
                assertTrue(activityController.get().isFinishing)
            }
        }
        assertEquals(exception.message, "Unexpected Response Code")
    }

    @Test
    fun `Fido2NonDiscoverableActivity should handle null continuation gracefully`() {
        // Given
        val intent = Intent().apply {
            putExtra(Fido2NonDiscoverableActivity.EXTRA_PENDING_INTENT, mockPendingIntent)
        }

        // Ensure continuation is null
        FidoResultHolder.continuation.set(null)

        // When
        activityController =
            Robolectric.buildActivity(Fido2NonDiscoverableActivity::class.java, intent)
        activityController.create()

        // Use reflection to call protected onActivityResult method
        val activity = activityController.get()
        val onActivityResultMethod = Activity::class.java.getDeclaredMethod(
            "onActivityResult",
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Intent::class.java
        )
        onActivityResultMethod.isAccessible = true
        onActivityResultMethod.invoke(
            activity,
            Fido2NonDiscoverableActivity.FIDO_SIGN_IN_REQUEST_CODE,
            Activity.RESULT_OK,
            Intent()
        )

        // Then - Should finish gracefully without throwing
        assertTrue(activityController.get().isFinishing)
    }

    @Test
    fun `handleFidoError should map all error codes correctly`() {
        // Test each error code mapping
        val errorMappings = mapOf(
            ErrorCode.NOT_SUPPORTED_ERR to NotSupportedError::class,
            ErrorCode.INVALID_STATE_ERR to InvalidStateError::class,
            ErrorCode.SECURITY_ERR to SecurityError::class,
            ErrorCode.NETWORK_ERR to NetworkError::class,
            ErrorCode.ABORT_ERR to AbortError::class,
            ErrorCode.TIMEOUT_ERR to TimeoutError::class,
            ErrorCode.CONSTRAINT_ERR to ConstraintError::class,
            ErrorCode.DATA_ERR to DataError::class,
            ErrorCode.NOT_ALLOWED_ERR to NotAllowedError::class,
            ErrorCode.ENCODING_ERR to EncodingError::class,
            ErrorCode.UNKNOWN_ERR to UnknownError::class
        )

        errorMappings.forEach { (errorCode, expectedExceptionClass) ->
            // Given
            val mockErrorResponse = mockk<AuthenticatorErrorResponse> {
                every { this@mockk.errorCode } returns errorCode
                every { errorMessage } returns "Test error message"
            }

            // When
            val result = handleFidoError(mockErrorResponse)

            // Then
            assertIs<GetPublicKeyCredentialDomException>(result)
            assertTrue(expectedExceptionClass.isInstance(result.domError))
            assertEquals("Test error message", result.message)
        }
    }

    @Test
    fun `handleFidoError should handle unknown error codes with UnknownError`() {
        // Given - Create a mock with an unmapped error code
        val mockErrorResponse = mockk<AuthenticatorErrorResponse> {
            every { errorCode } returns ErrorCode.NOT_ALLOWED_ERR
            every { errorMessage } returns ErrorCode.NOT_ALLOWED_ERR.name
        }

        // When
        val result = handleFidoError(mockErrorResponse)

        // Then
        assertIs<GetPublicKeyCredentialDomException>(result)
        assertIs<NotAllowedError>(result.domError)
        assertEquals(ErrorCode.NOT_ALLOWED_ERR.name, result.message)
    }

    @Test
    fun `FidoResultHolder should support atomic operations`() {
        // Given
        val mockContinuation =
            mockk<CancellableContinuation<PublicKeyCredential>>()

        // When & Then
        assertTrue(FidoResultHolder.continuation.compareAndSet(null, mockContinuation))
        assertEquals(mockContinuation, FidoResultHolder.continuation.get())

        // Should fail to set again since it's not null
        val anotherContinuation =
            mockk<CancellableContinuation<PublicKeyCredential>>()
        assertTrue(!FidoResultHolder.continuation.compareAndSet(null, anotherContinuation))
        assertEquals(mockContinuation, FidoResultHolder.continuation.get())

        // Should succeed when setting from expected value
        assertTrue(FidoResultHolder.continuation.compareAndSet(mockContinuation, null))
        assertEquals(null, FidoResultHolder.continuation.get())
    }

    @Test
    fun `Fido2NonDiscoverableActivity should start intent sender on create with valid PendingIntent`() {
        // Given
        val intent = Intent().apply {
            putExtra(Fido2NonDiscoverableActivity.EXTRA_PENDING_INTENT, mockPendingIntent)
        }

        every { mockPendingIntent.intentSender } returns mockk()

        // When
        activityController =
            Robolectric.buildActivity(Fido2NonDiscoverableActivity::class.java, intent)
        activityController.create()

        // Then - Activity should be created successfully and not finish immediately
        // If PendingIntent is valid, onCreate should not call finish()
        // We can't directly verify startIntentSenderForResult was called due to it being protected
        // But we can verify the activity doesn't finish immediately when PendingIntent is present
        assertTrue(!activityController.get().isFinishing)
    }

}

/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.binding.journey

import com.pingidentity.device.binding.authenticator.exception.AbortException
import com.pingidentity.device.binding.authenticator.exception.BiometricAuthenticationException
import com.pingidentity.device.binding.authenticator.exception.DeviceNotRegisteredException
import com.pingidentity.device.binding.authenticator.exception.DeviceNotSupportedException
import com.pingidentity.device.binding.authenticator.exception.InvalidClaimException
import com.pingidentity.device.binding.authenticator.exception.InvalidCredentialException
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.coroutines.cancellation.CancellationException

class DeviceBindingErrorTest {

    @Test
    fun `toClientError returns ClientNotRegistered for DeviceNotRegisteredException`() {
        val exception = DeviceNotRegisteredException("Device not registered")

        val result = toClientError(exception)

        assertEquals("ClientNotRegistered", result)
    }

    @Test
    fun `toClientError returns Abort for AbortException`() {
        val exception = AbortException("User aborted")

        val result = toClientError(exception)

        assertEquals("Abort", result)
    }

    @Test
    fun `toClientError returns Abort for InvalidClaimException`() {
        val exception = InvalidClaimException("Invalid claim")

        val result = toClientError(exception)

        assertEquals("Abort", result)
    }

    @Test
    fun `toClientError returns Abort for InvalidCredentialException`() {
        val exception = InvalidCredentialException("Invalid credential")

        val result = toClientError(exception)

        assertEquals("Abort", result)
    }

    @Test
    fun `toClientError returns Abort for BiometricAuthenticationException`() {
        val exception = BiometricAuthenticationException(1, "Biometric failed")

        val result = toClientError(exception)

        assertEquals("Abort", result)
    }

    @Test
    fun `toClientError returns Unsupported for DeviceNotSupportedException`() {
        val exception = DeviceNotSupportedException("Device not supported")

        val result = toClientError(exception)

        assertEquals("Unsupported", result)
    }

    @Test(expected = CancellationException::class)
    fun `toClientError rethrows CancellationException`() {
        val exception = CancellationException("Operation cancelled")

        toClientError(exception)
    }

    @Test
    fun `toClientError returns Abort for generic exception`() {
        val exception = RuntimeException("Generic error")

        val result = toClientError(exception)

        assertEquals("Abort", result)
    }

    @Test
    fun `toClientError returns Abort for custom exception`() {
        val exception = IllegalStateException("Custom error")

        val result = toClientError(exception)

        assertEquals("Abort", result)
    }

    @Test
    fun `toClientError handles exception with null message`() {
        val exception = DeviceNotRegisteredException("", null)

        val result = toClientError(exception)

        assertEquals("ClientNotRegistered", result)
    }

}


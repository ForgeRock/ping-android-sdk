/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.fido

import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import com.pingidentity.logger.Logger
import com.pingidentity.logger.NONE
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the holder's own state machine, including the teardown path
 * ([FidoPendingAuthenticationHolder.Reservation.cancel]) the Journey callback doesn't
 * exercise yet but the DaVinci collector will (DV-24867).
 */
class FidoPendingAuthenticationHolderTest {

    private val deliveries = mutableListOf<JsonObject>()
    private val errors = mutableListOf<Throwable>()

    private fun holder() = FidoPendingAuthenticationHolder(
        logger = Logger.NONE,        onDelivered = { deliveries.add(it) },
        onError = { errors.add(it) },
    )

    private fun pendingRequest() = FidoPendingAuthentication(
        GetCredentialRequest(listOf(GetPublicKeyCredentialOption("{}")))
    )

    private fun responseWith(assertionJson: String): GetCredentialResponse {
        val mockPublicKeyCredential = mockk<PublicKeyCredential> {
            every { authenticationResponseJson } returns assertionJson
        }
        return mockk {
            every { credential } returns mockPublicKeyCredential
        }
    }

    @Test
    fun `install publishes the request and delivers its assertion`() = runTest {
        val holder = holder()
        val reservation = holder.beginCeremony()
        val pending = pendingRequest()
        assertTrue(reservation.install(pending))

        pending.request.callback(
            responseWith("""{"id":"id1","rawId":"raw1","response":{}}""")
        )

        assertEquals(1, deliveries.size)
        assertEquals("raw1", deliveries.single()["rawId"]?.toString()?.trim('"'))
        assertEquals(0, errors.size)
    }

    @Test
    fun `install after a newer ceremony began is refused and the request is cancelled`() = runTest {
        val holder = holder()
        val stale = holder.beginCeremony()
        holder.beginCeremony() // newer ceremony supersedes the stale one
        val pending = pendingRequest()

        assertFalse(stale.install(pending))
        // The discarded request is cancelled, not left live
        assertTrue(pending.await().exceptionOrNull() is CancellationException)
        assertEquals(0, deliveries.size)
    }

    @Test
    fun `cancel tears down the ceremony and rejects a late install`() = runTest {
        val holder = holder()
        val reservation = holder.beginCeremony()
        val pending = pendingRequest()
        assertTrue(reservation.install(pending))

        reservation.cancel()

        // The installed request was released
        assertTrue(pending.await().exceptionOrNull() is CancellationException)
        assertEquals(0, deliveries.size)

        // A late install of the cancelled reservation cannot resurrect its request
        val lateRequest = pendingRequest()
        assertFalse(reservation.install(lateRequest))
        assertTrue(lateRequest.await().exceptionOrNull() is CancellationException)
        assertEquals(0, deliveries.size)
    }

    @Test
    fun `stale cancel is a no-op and never touches a newer ceremony's request`() = runTest {
        val holder = holder()
        val stale = holder.beginCeremony()
        holder.beginCeremony() // newer ceremony
        val current = pendingRequest()
        holder.beginCeremony().install(current) // install into the newest reservation

        stale.cancel()

        // The current ceremony's request is untouched by the stale cancel
        current.request.callback(
            responseWith("""{"id":"id2","rawId":"raw2","response":{}}""")
        )
        assertEquals(1, deliveries.size)
    }
}

/*
 * Copyright (c) 2026 - 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.samples.pingsampleapp.journey

import android.util.Log
import com.pingidentity.fido.journey.FidoAuthenticationCallback
import com.pingidentity.journey.Journey
import com.pingidentity.journey.plugin.callbacks
import com.pingidentity.journey.start
import com.pingidentity.orchestrate.ContinueNode
import com.pingidentity.orchestrate.SuccessNode

private const val RESTORE_CREDENTIAL_JOURNEY_NAME = "WebAuthnRestoreAndy"
private const val TAG = "RestoreCredentialSignIn"

/**
 * Attempts a silent, Tier-2 (foreground) Restore Credentials sign-in.
 *
 * Starts the "RestoreCredential" journey and, if the server returns a
 * [FidoAuthenticationCallback], signs in with the device's restore credential and submits the
 * result to complete the journey. This is a best-effort attempt: any failure - no restore
 * credential on this device, the journey isn't configured server-side, a network error, or the
 * journey not returning a [FidoAuthenticationCallback] - is swallowed, leaving the caller on
 * its normal cold-start flow.
 *
 * @return true if the sign-in succeeded and the journey reached a [SuccessNode].
 */
suspend fun Journey.attemptRestoreCredentialSignIn(): Boolean =
    runCatching {
        val continueNode = start(RESTORE_CREDENTIAL_JOURNEY_NAME) as? ContinueNode
            ?: return@runCatching false
        val callback = continueNode.callbacks
            .filterIsInstance<FidoAuthenticationCallback>()
            .firstOrNull() ?: return@runCatching false

        callback.restore().getOrThrow()
        continueNode.next() is SuccessNode
    }.onFailure {
        Log.d(TAG, "Restore credential sign-in not available: ${it.message}")
    }.getOrDefault(false)

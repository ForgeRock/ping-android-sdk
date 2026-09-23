/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.fido

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject

/**
 * Single-slot holder for the in-flight pending (conditional-mediation) request.
 *
 * The Journey callback enforces this lifecycle: a request superseded by a new ceremony
 * ([cancel], called at the start of both `authenticate` and `pendingAuthenticate`) is
 * cancelled so an abandoned conditional ceremony cannot deliver an assertion into the new
 * one. Delivery is observed exactly once via [FidoPendingAuthentication.observe], with
 * cancellation treated as teardown rather than a ceremony failure (the androidx pending path
 * itself never propagates errors). When the DaVinci collector gains pending support
 * (DV-24867), it shares this holder.
 *
 * **Threading:** written on the caller's coroutine thread; cancelled from app lifecycle or
 * workflow-close code on other threads — hence [Volatile].
 *
 * @param onDelivered Called on the androidx delivery/cancellation thread when the ceremony
 *   completes successfully — must be cheap and non-suspending (it stores the assertion into
 *   the collector's payload or submits the Journey outcome).
 * @param onError Called on the androidx delivery thread when delivery fails with a
 *   non-cancellation error — routes to the variant's `handleError`.
 * @param onLogD Debug-log hook for the holder's lifecycle transitions; failures are logged
 *   through [onLogE].
 * @param onLogE Error-log hook carrying the failing exception.
 */
internal class FidoPendingAuthenticationHolder(
    private val onDelivered: (JsonObject) -> Unit,
    private val onError: (Throwable) -> Unit,
    private val onLogD: (String) -> Unit,
    private val onLogE: (String, Throwable) -> Unit,
) {

    @Volatile
    private var pending: FidoPendingAuthentication? = null

    /**
     * Cancels the in-flight request (if any) and clears the slot — the supersede step before
     * a new ceremony, and the teardown step on close/screen disposal.
     */
    fun cancel() {
        pending?.cancel()
        pending = null
    }

    /**
     * Stores [pending] as the in-flight request and observes its completion, forwarding the
     * outcome to [onDelivered] or [onError]/[onLogE]. The caller supersedes via [cancel]
     * first; a registered request whose observer has already fired (delivered or cancelled)
     * can no longer deliver into anything new.
     */
    fun register(pending: FidoPendingAuthentication) {
        this.pending = pending
        pending.observe { result ->
            result.onSuccess { assertion ->
                onLogD("FIDO2 pending authentication successful")
                onDelivered(assertion)
            }.onFailure { exception ->
                // Cancellation is teardown, not a ceremony failure — the androidx pending
                // path itself never propagates errors.
                if (exception is CancellationException) {
                    onLogD("FIDO2 pending authentication cancelled")
                } else {
                    onLogE("FIDO2 pending authentication failed", exception)
                    onError(exception)
                }
            }
        }
    }
}

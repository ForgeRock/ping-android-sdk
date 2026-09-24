/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.fido

import com.pingidentity.logger.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import java.util.concurrent.atomic.AtomicLong

/**
 * Single-slot holder for the in-flight pending (conditional-mediation) request.
 *
 * The Journey callback enforces this lifecycle: a request superseded by a new ceremony
 * ([beginCeremony], called at the start of both `authenticate` and `pendingAuthenticate`) is
 * cancelled so an abandoned conditional ceremony cannot deliver an assertion into the new
 * one. Delivery is observed exactly once via [FidoPendingAuthentication.observe], with
 * cancellation treated as teardown rather than a ceremony failure (the androidx pending path
 * itself never propagates errors). When the DaVinci collector gains pending support
 * (DV-24867), it shares this holder.
 *
 * **Concurrency — generation token.** A ceremony spans a suspension point
 * (`FidoClient.pendingAuthenticate` builds the request asynchronously), so
 * cancel-then-suspend-then-register is not atomic: a concurrent ceremony can cancel the slot
 * in between, and a naive [register] would then install a request that a newer ceremony
 * already superseded — resurrecting it as current. [beginCeremony] returns a reservation
 * (a monotonically increasing generation); [register] accepts the request **only if the
 * reservation is still the latest one**, so a stale ceremony's late install is discarded
 * instead of becoming current. Combined with the ownership check in the observer (delivery
 * requires the delivering request to still occupy the slot), both halves of the lifecycle —
 * install and delivery — are generation-guarded, and `@Volatile`'s per-write visibility
 * suffices: no lock is needed.
 *
 * **Threading:** written from the caller's coroutine thread(s); cancelled from app lifecycle
 * or workflow-close code on other threads — hence [Volatile].
 *
 * @param onDelivered Called on the androidx delivery/cancellation thread when the ceremony
 *   completes successfully — must be cheap and non-suspending (it stores the assertion into
 *   the collector's payload or submits the Journey outcome).
 * @param onError Called on the androidx delivery thread when delivery fails with a
 *   non-cancellation error — routes to the variant's `handleError`, which logs it.
 * @param logger The variant's logger for the holder's lifecycle transitions (delivery,
 *   cancellation, stale-drop) that `handleError` never sees.
 */
internal class FidoPendingAuthenticationHolder(
    private val logger: Logger,
    private val onDelivered: (JsonObject) -> Unit,
    private val onError: (Throwable) -> Unit,
) {

    /**
     * Monotonically increasing ceremony generation. [beginCeremony] increments it; a
     * [Reservation] may install its request only while it is still the latest one.
     */
    private val generation = AtomicLong(0)

    /** The current generation — the only one a [Reservation.install] may write into. */
    private val currentGeneration: Long get() = generation.get()

    @Volatile
    private var pending: FidoPendingAuthentication? = null

    /**
     * A reservation obtained from [beginCeremony] before starting a ceremony. [cancel]
     * enacts the supersede (cancels + clears the slot); [install] publishes the ceremony's
     * eventual request if the reservation is still current.
     */
    inner class Reservation internal constructor(private val claimed: Long) {

        /** Whether this reservation is still the latest ceremony started. */
        val isCurrent: Boolean get() = currentGeneration == claimed

        /**
         * Cancels the in-flight request (if any) and clears the slot — the supersede step
         * that starts this reservation's ceremony. A concurrent newer ceremony's supersede
         * wins; enacting this reservation afterwards is a harmless no-op (nothing left to
         * cancel for it).
         */
        fun cancel() {
            pending?.cancel()
            pending = null
        }

        /**
         * Publishes [pending] as the in-flight request and observes its completion — but
         * only while this reservation is still current. A ceremony that suspended in
         * [com.pingidentity.fido.FidoClient.pendingAuthenticate] while a newer ceremony
         * superseded it must **not** resurrect its superseded request as current; the
         * stale request is cancelled and discarded instead.
         *
         * @return true if the request was installed (and is now the observed current one);
         *   false if a newer ceremony superseded this one — the caller should cancel or
         *   discard the returned request.
         */
        fun install(pending: FidoPendingAuthentication): Boolean {
            if (!isCurrent) {
                logger.d("FIDO2 pending authentication superseded before install; discarding")
                pending.cancel()
                return false
            }
            this@FidoPendingAuthenticationHolder.pending = pending
            pending.observe { result ->
                // Ownership gate: only the request that is *still* current may deliver into
                // the wrapper. A superseded request's late observer callback (the androidx
                // delivery thread can race a concurrent supersede on another thread) is
                // dropped — otherwise a stale ceremony could update the payload or submit
                // the Journey outcome after a newer ceremony started.
                if (this@FidoPendingAuthenticationHolder.pending !== pending) {
                    logger.d("FIDO2 pending authentication superseded; dropping stale delivery")
                    return@observe
                }
                result.onSuccess { assertion ->
                    logger.d("FIDO2 pending authentication successful")
                    onDelivered(assertion)
                }.onFailure { exception ->
                    // Cancellation is teardown, not a ceremony failure — the androidx
                    // pending path itself never propagates errors.
                    if (exception is CancellationException) {
                        logger.d("FIDO2 pending authentication cancelled")
                    } else {
                        onError(exception)
                    }
                }
            }
            return true
        }
    }

    /**
     * Starts a new ceremony: supersedes (cancels + clears) any in-flight request and returns
     * a reservation that may [Reservation.install] its request while it remains the latest
     * ceremony. Called at the start of both `authenticate` and `pendingAuthenticate`, and by
     * close/disposal for pure teardown.
     */
    fun beginCeremony(): Reservation {
        val claimed = generation.incrementAndGet()
        pending?.cancel()
        pending = null
        return Reservation(claimed)
    }
}

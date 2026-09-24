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
import java.util.concurrent.atomic.AtomicReference

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
 * **Concurrency — one atomic cell.** A ceremony spans a suspension point
 * (`FidoClient.pendingAuthenticate` builds the request asynchronously), so
 * cancel-then-suspend-then-install is not atomic: a concurrent ceremony can supersede the
 * slot in between, and a naive install would then publish a request a newer ceremony
 * already superseded — resurrecting it as current, the exact failure this class exists to
 * prevent. The generation and the current request therefore live in **one atomic
 * reference** ([slot]): every state transition is a single `updateAndGet`/`getAndUpdate`,
 * so a check-then-write (install, cancel, supersede) can never interleave with a concurrent
 * one — there is no window and no lock. Terminal transitions (a newer ceremony, or
 * [Reservation.cancel]) also **bump the generation**, so a cancelled reservation's late
 * install is refused the same way a superseded one is. Requests are cancelled *outside* the
 * atomic update — [FidoPendingAuthentication.cancel] runs the delivery observers
 * synchronously, and it must never run while the slot is mid-update. Delivery stays
 * lock-free: the observer re-reads the slot and drops the delivery unless its request still
 * occupies it.
 *
 * **Threading:** written from the caller's coroutine thread(s); cancelled from app lifecycle
 * or workflow-close code on other threads.
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

    /** Immutable slot state: the latest ceremony's generation and its in-flight request. */
    private class Slot(val generation: Long, val pending: FidoPendingAuthentication?)

    /**
     * The single state cell. Holding generation and occupant in one atomic reference makes
     * every check-then-write below a single atomic update — a newer ceremony can never
     * interleave between the check and the write.
     */
    private val slot = AtomicReference(Slot(0, null))

    /**
     * A reservation obtained from [beginCeremony] before starting a ceremony. [cancel]
     * tears this ceremony down (invalidating the reservation); [install] publishes the
     * ceremony's eventual request if the reservation is still current.
     */
    inner class Reservation internal constructor(private val claimed: Long) {

        /**
         * Tears this reservation's ceremony down: clears the in-flight request and bumps
         * the generation, so neither a late [install] of this reservation nor a stale
         * repeat of [cancel] can resurrect anything afterwards. No-op when a newer
         * ceremony has already superseded this reservation.
         */
        fun cancel() {
            val previous = slot.getAndUpdate { s ->
                if (s.generation == claimed) Slot(s.generation + 1, null) else s
            }
            // Only when the update applied does the previous occupant belong to this
            // ceremony — outside any lock: cancel() runs the delivery observers synchronously.
            if (previous.generation == claimed) previous.pending?.cancel()
        }

        /**
         * Publishes [pending] as the in-flight request and observes its completion — but
         * only while this reservation is still current. A ceremony that suspended in
         * [com.pingidentity.fido.FidoClient.pendingAuthenticate] while a newer ceremony
         * superseded it must **not** resurrect its superseded request as current; the stale
         * request is cancelled and discarded instead.
         *
         * @return true if the request was installed (and is now the observed current one);
         *   false if a newer ceremony superseded this one — the caller should cancel or
         *   discard the returned request.
         */
        fun install(pending: FidoPendingAuthentication): Boolean {
            val updated = slot.updateAndGet { s ->
                if (s.generation == claimed) Slot(s.generation, pending) else s
            }
            if (updated.pending !== pending) {
                logger.d("FIDO2 pending authentication superseded before install; discarding")
                // Outside any lock: cancel() runs the delivery observers synchronously.
                pending.cancel()
                return false
            }
            pending.observe { result ->
                // Ownership gate: only the request that still occupies the slot may deliver
                // into the wrapper. A superseded request's late observer callback (the
                // androidx delivery thread can race a concurrent supersede on another
                // thread) is dropped — otherwise a stale ceremony could update the payload
                // or submit the Journey outcome after a newer ceremony started.
                if (slot.get().pending !== pending) {
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
        val previous = slot.getAndUpdate { Slot(it.generation + 1, null) }
        // Outside any lock: cancel() runs the delivery observers synchronously.
        previous.pending?.cancel()
        return Reservation(previous.generation + 1)
    }
}

/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.fido

import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.PendingGetCredentialRequest
import androidx.credentials.PublicKeyCredential
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException

/**
 * A pending (View-attachable) FIDO authentication request produced by
 * [FidoClient.pendingAuthenticate].
 *
 * Attach [request] to the View that should surface passkey suggestions (typically the username
 * field) via the androidx View extension:
 *
 * ```kotlin
 * view.pendingGetCredentialRequest = pending.request
 * ```
 *
 * The request is exercised when the user focuses the View; the assertion is delivered to every
 * caller of [await].
 *
 * **Error contract (androidx-imposed):** the underlying androidx callback delivers only final
 * `GetCredentialResponse`s — no errors are ever propagated to it. Consequently, if the user
 * dismisses the keyboard suggestions without picking one, [await] simply stays suspended:
 * nothing failed, so there is no error to report. Apps should treat non-completion as the
 * signal to keep the modal fallback ("Use a passkey" button) visible, not as an error.
 * Cancellation — the caller's coroutine scope ending, or an explicit [cancel] — is the only way
 * a dismissed request stops awaiting.
 *
 * The delivered assertion is the same [JsonObject] the modal `authenticate` path produces
 * (identical unwrapping of `PublicKeyCredential.authenticationResponseJson`), so downstream
 * payload handling needs no changes.
 *
 * @property request The androidx request to set on a View via
 * `view.pendingGetCredentialRequest = pending.request`.
 * @see isConditionalMediationSupported
 */
class FidoPendingAuthentication internal constructor(
    credentialRequest: GetCredentialRequest,
) {

    /**
     * Single completion slot shared by all awaiters. Completing it releases every suspended
     * [await], so [complete] performs the completion **last** — after the result is published
     * and the observers have run — so observer writes happen-before [await] resumes.
     */
    private val deferred = CompletableDeferred<Result<JsonObject>>()

    /**
     * Observers notified when the deferred is completed (delivery or cancellation).
     * [CopyOnWriteArrayList] keeps registration and notification lock-free on the androidx
     * callback thread. Each observer carries its own [AtomicBoolean] latch so it is invoked
     * exactly once even if a late registration races the winning completion.
     */
    private val observers = CopyOnWriteArrayList<Observer>()

    /**
     * Exactly-once arbiter for [complete]: the first caller to flip it from `false` owns the
     * delivery sequence (publish result, notify observers, complete the deferred); every other
     * concurrent caller is a no-op. Plays the role `CompletableDeferred.complete()`'s return
     * value played before, but *before* the side effects instead of after them.
     */
    private val completionClaimed = AtomicBoolean(false)

    /**
     * The winning completion, set exactly once by the caller that won the
     * [completionClaimed] CAS. Read by [observe] to deliver to late registrants without
     * awaiting.
     */
    @Volatile
    private var finalResult: Result<JsonObject>? = null

    /**
     * The androidx request to set on a View via
     * `view.pendingGetCredentialRequest = pending.request`.
     */
    val request: PendingGetCredentialRequest =
        PendingGetCredentialRequest(credentialRequest, ::onResponse)

    /**
     * Awaits the assertion produced when the user completes the ceremony from the attached
     * View's suggestions.
     *
     * Suspends until the androidx callback delivers a final response, [cancel] is called, or
     * the caller's coroutine is cancelled. As described in the class KDoc, a dismissed
     * suggestion sheet produces **no** callback — this function stays suspended in that case;
     * treat non-completion as "fall back to the modal flow", not as an error.
     *
     * Multiple callers may await concurrently; all receive the same result.
     *
     * @return The assertion as a [JsonObject] (same shape as the modal `authenticate` result)
     * on success, or a `Result.failure` carrying a
     * [kotlin.coroutines.cancellation.CancellationException] once [cancel] has been called
     * (re-await after cancel is a no-op returning that same failure), or the conversion
     * failure if the delivered credential was not a public-key credential.
     */
    suspend fun await(): Result<JsonObject> = deferred.await()

    /**
     * Registers a module-internal observer notified exactly once when the deferred is completed
     * — either by a delivered response or by [cancel]. Used by the DaVinci collector and
     * Journey callback variants so the assertion reaches `payload()` even if the app never
     * calls [await]. Not part of the app-facing API.
     *
     * Observers registered after the deferred has already completed are invoked immediately
     * with the stored result, so registration order never loses a delivery.
     *
     * @param observer Called with the completed [Result] on the completing thread (the androidx
     * callback thread or the cancelling caller's); must be cheap and non-suspending
     */
    internal fun observe(observer: (Result<JsonObject>) -> Unit) {
        val entry = Observer(observer)
        val settled = finalResult
        if (settled != null) {
            entry.invoke(settled)
            return
        }
        observers.add(entry)
        finalResult?.let { settled -> entry.invoke(settled) }
    }

    /**
     * Completes [await] with a `CancellationException`-bearing failure for callers still
     * suspended on an abandoned request (e.g. a collector being closed).
     *
     * If the response has already been delivered, this is a no-op — the delivered response
     * wins. Repeated calls are also no-ops, and a subsequent [await] returns the cancellation
     * failure instead of hanging.
     *
     * This does not detach the request from its View; clear it with
     * `view.pendingGetCredentialRequest = null` if needed.
     */
    fun cancel() {
        complete(Result.failure(CancellationException("FIDO pending authentication cancelled")))
    }

    /**
     * Converts the androidx callback's final response into the assertion JsonObject — the exact
     * unwrapping the modal `authenticate` performs — and completes the deferred.
     *
     * Per the androidx contract, errors are never propagated here; only a final
     * [GetCredentialResponse] arrives (if any). Conversion failures are surfaced as
     * `Result.failure` to awaiters rather than thrown into the androidx machinery.
     */
    private fun onResponse(response: GetCredentialResponse) {
        val result = runCatching {
            when (val credential = response.credential) {
                is PublicKeyCredential -> Json.parseToJsonElement(
                    credential.authenticationResponseJson
                ).jsonObject

                else -> throw IllegalStateException(
                    "Unexpected result type: ${response.credential::class.simpleName}"
                )
            }
        }
        complete(result)
    }

    /**
     * Publishes the winning result and completes the deferred (idempotently — first call wins).
     *
     * The completion claim is arbitrated with a compare-and-set on [completionClaimed] so
     * exactly one caller runs the delivery sequence below (concurrent deliveries and
     * [cancel] calls race here on the androidx callback thread vs. the cancelling caller's).
     *
     * Ordering is load-bearing for visibility: [finalResult] is written and the observers
     * (which store the assertion into the collector/callback that `payload()` and the
     * workflow's `next()` later read) are invoked **before** [deferred.complete] releases the
     * suspended [await]ers. Otherwise an awaiter could resume and read stale collector state
     * with no happens-before edge — a data race that can silently lose the authentication
     * result. Releasing the awaiter is therefore the last step: even if an observer throws,
     * the [finally] block completes the deferred so awaiters never hang.
     */
    private fun complete(result: Result<JsonObject>) {
        if (!completionClaimed.compareAndSet(false, true)) return
        finalResult = result
        try {
            observers.forEach { it.invoke(result) }
        } finally {
            // An observer must not prevent the release — deferred.complete in the finally
            // guarantees awaiters resume even if an observer throws.
            observers.clear()
            deferred.complete(result)
        }
    }

    /**
     * An observer registration with a once-only latch, so a registration racing the winning
     * completion is invoked exactly once no matter which thread notices first.
     */
    private class Observer(val callback: (Result<JsonObject>) -> Unit) {
        private val delivered = AtomicBoolean(false)

        fun invoke(result: Result<JsonObject>) {
            if (delivered.compareAndSet(false, true)) callback(result)
        }
    }
}

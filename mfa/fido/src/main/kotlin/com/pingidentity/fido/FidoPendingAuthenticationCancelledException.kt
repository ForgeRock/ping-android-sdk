/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.fido

/**
 * A pending (conditional-mediation) authentication ceremony was abandoned — cancelled or
 * superseded — before delivering an assertion.
 *
 * Deliberately **not** a [kotlin.coroutines.cancellation.CancellationException]: that type is
 * coroutine machinery, and a `Result.failure` carrying it would be swallowed as cooperative
 * cancellation if the caller unwraps with `getOrThrow()` — ending their coroutine silently
 * instead of surfacing the failure. Carrying this dedicated type keeps the failure a plain,
 * catchable result.
 *
 * Two distinct teardowns produce it, distinguishable by [reason]:
 * - [Reason.CANCELLED] — the request's [FidoPendingAuthentication.cancel] was called
 *   (e.g. app lifecycle teardown); no error outcome is written anywhere.
 * - [Reason.SUPERSEDED] — a newer ceremony started while this one was still building its
 *   request, so the stale request was discarded; the newer ceremony owns the Journey node.
 *
 * @param reason Which teardown cancelled the ceremony
 * @param message Human-readable detail for logs
 */
class FidoPendingAuthenticationCancelledException(
    val reason: Reason,
    message: String,
) : IllegalStateException(message) {

    /** Why the pending ceremony ended without delivering. */
    enum class Reason {
        /** The request was explicitly cancelled (lifecycle teardown / supersede cancel). */
        CANCELLED,

        /** A newer ceremony superseded this one before its request was built. */
        SUPERSEDED,
    }
}

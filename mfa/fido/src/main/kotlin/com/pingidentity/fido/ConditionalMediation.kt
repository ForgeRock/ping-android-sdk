/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.fido

import android.os.Build

/**
 * Whether conditional mediation (autofill with passkeys) can deliver a pending credential
 * request on this device.
 *
 * Mirrors the OS gate inside androidx.credentials' View handler
 * (`CredentialManagerViewHandler.Api35Impl`): the framework only wires
 * `View.setPendingCredentialRequest` on Android 15 (API 35), or on an API 34 preview. Below
 * that, the request tag is still set on the View but suggestions are never delivered, so the
 * feature is reported as unsupported.
 *
 * The androidx.credentials library-version gate is compile-time for SDK consumers: this module
 * pins androidx.credentials 1.5.0+ (currently 1.6.0) as an `api` dependency, so
 * `PendingGetCredentialRequest` is always on the classpath and only the OS gate varies at
 * runtime.
 *
 * A `true` value does not promise Compose support — the pending request must be attached to an
 * Android `View` (`view.pendingGetCredentialRequest = pending.request`); there is no Compose
 * equivalent upstream.
 */
val isConditionalMediationSupported: Boolean
    get() = Build.VERSION.SDK_INT >= 35 ||
        (Build.VERSION.SDK_INT == 34 && Build.VERSION.PREVIEW_SDK_INT > 0)

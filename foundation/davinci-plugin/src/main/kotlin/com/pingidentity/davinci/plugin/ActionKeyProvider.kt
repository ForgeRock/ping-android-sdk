/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci.plugin

/**
 * Adopted by collectors that supply an `actionKey` directly rather than contributing to `formData`.
 *
 * When [actionKey] is non-null the collector is considered ready to submit and its event type is
 * used as the DaVinci `eventType`. When [actionKey] is null the collector is not yet ready,
 * regardless of what [Collector.payload] returns.
 *
 * Used by the FIDO error path to propagate WebAuthn DOMException names to the DaVinci server,
 * and by SubmitCollector/FlowCollector to signal the user's chosen action.
 */
interface ActionKeyProvider {
    val actionKey: String?
}

/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci.plugin

/**
 * Adopted by collectors that supply an `actionKey` directly rather than contributing to `formData`.
 * Used by the FIDO error path to propagate WebAuthn DOMException names to the DaVinci server.
 */
interface ActionKeyProvider {
    val actionKey: String?
}

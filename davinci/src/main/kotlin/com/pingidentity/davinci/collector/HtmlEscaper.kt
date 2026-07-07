/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci.collector

/**
 * Escapes characters that are unsafe inside HTML text content or attribute values.
 *
 * The `&` replacement **must** run first to prevent any subsequent replacement from
 * producing an entity that then gets double-escaped (e.g. `<` → `&lt;` → `&amp;lt;`).
 *
 * Characters escaped:
 * - `&`  → `&amp;`
 * - `<`  → `&lt;`
 * - `>`  → `&gt;`
 * - `"`  → `&quot;`
 * - `'`  → `&#39;`
 */
fun String.escapeHtml(): String = this
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
    .replace("'", "&#39;")


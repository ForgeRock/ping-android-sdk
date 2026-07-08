/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci.module

/**
 * Sealed exception hierarchy for errors that occur while parsing or resuming a DaVinci metadata
 * node. Parsing failures bubble out of `Transform.tryMetadataNode()` and are caught by the outer
 * `catch { }` in `Workflow.start` / `Workflow.next`, producing a `FailureNode`.
 *
 * @param message Human-readable description of the error. Must **not** include any user-supplied
 *   or server-supplied values (tokens, configs, PII).
 * @param cause   The underlying throwable, if any.
 */
sealed class MetadataException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {

    /**
     * Thrown when the server-sent metadata payload is structurally invalid — a required key is
     * absent or carries an unexpected JSON type.
     *
     * @param message Names the offending key (e.g. `"Metadata payload is missing required key 'TYPE'"`).
     *   Never embeds the key's value or any surrounding payload data.
     */
    class Malformed(message: String) : MetadataException(message)

    /**
     * Thrown when `MetadataNode.asRequest()` cannot build the resume request because `_links.next.href`
     * is absent from the metadata response.
     */
    class MissingResumeLink : MetadataException("Metadata node has no _links.next.href")

    /**
     * Thrown (in strict-mode, future use) when the server sends a `TYPE` value the SDK does not
     * recognize. Reserved for V1; the SDK does **not** validate TYPE against a whitelist by default
     * (see D5 in decisions.md).
     *
     * @property rawType The unrecognized TYPE string as received from the server.
     */
    class UnsupportedType(val rawType: String) : MetadataException("Unknown or unsupported metadata TYPE")
}

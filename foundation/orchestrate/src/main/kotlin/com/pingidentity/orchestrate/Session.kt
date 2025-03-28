/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.orchestrate

/**
 * Interface for a Session. A Session represents a user's session in the application.
 */
interface Session {
    val value: String
}

/**
 * Object for an EmptySession. An EmptySession represents a session with no value.
 */
data object EmptySession : Session {
    /**
     * Returns the value of the empty session.
     * @return The value of the empty session as a String.
     */
    override val value: String
        get() = ""
}
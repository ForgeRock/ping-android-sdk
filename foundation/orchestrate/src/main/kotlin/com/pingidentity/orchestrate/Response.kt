/*
 * Copyright (c) 2024 Ping Identity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.orchestrate

import com.pingidentity.orchestrate.module.Cookies
import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse

/**
 * Class for a Response. A Response represents a response received from a network request.
 * @property request The request that was sent to get this response.
 * @property httpResponse The HttpResponse received from the network request.
 */
class Response(val request: Request, private val httpResponse: HttpResponse) {
    /**
     * Returns the body of the response.
     * @return The body of the response as a String.
     */
    suspend fun body(): String {
        return httpResponse.body()
    }

    /**
     * Returns the status code of the response.
     * @return The status code of the response as an Int.
     */
    fun status(): Int {
        return httpResponse.status.value
    }

    /**
     * Returns the cookies from the response.
     * @return The cookies from the response as a Cookies object.
     */
    fun cookies(): Cookies = httpResponse.headers.getAll("Set-Cookie") ?: emptyList()

    /**
     * Returns the value of a specific header from the response.
     * @param name The name of the header.
     * @return The value of the header as a String.
     */
    fun header(name: String) = httpResponse.headers[name]
}
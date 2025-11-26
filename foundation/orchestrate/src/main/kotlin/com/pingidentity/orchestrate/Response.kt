/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.orchestrate

import com.pingidentity.orchestrate.module.Cookies
import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse


interface Response {

    val request: Request

    /**
     * Returns the body of the response.
     * @return The body of the response as a String.
     */
    suspend fun body(): String

    /**
     * Returns the status code of the response.
     * @return The status code of the response as an Int.
     */
    fun status(): Int

    /**
     * Returns the cookies from the response.
     * @return The cookies from the response as a Cookies object.
     */
    fun cookies(): Cookies

    /**
     * Returns the value of a specific header from the response.
     * @param name The name of the header.
     * @return The value of the header as a String.
     */
    fun header(name: String): String?
}
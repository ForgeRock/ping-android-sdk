/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.orchestrate

import androidx.annotation.VisibleForTesting
import com.pingidentity.orchestrate.module.Cookies
import com.pingidentity.orchestrate.module.cookie
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.cookie
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.headers
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.Cookie
import io.ktor.http.HttpMethod.Companion.Post
import io.ktor.http.ParametersBuilder
import io.ktor.http.contentType
import io.ktor.http.parseServerSetCookieHeader
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

/**
 * Typealias for FormBuilder.
 */
typealias FormBuilder = ParametersBuilder

/**
 * Class for a Request. A Request represents a request to be sent over the network.
 */
class Request {
    @VisibleForTesting
    val builder = HttpRequestBuilder()
    var hasUrl = false

    /**
     * Sets the URL of the request.
     * @param url The URL to be set.
     */
    fun url(url: String) {
        hasUrl = true
        builder.url(url)
    }

    /**
     * Adds a parameter to the request.
     * @param name The name of the parameter.
     * @param value The value of the parameter.
     */
    fun parameter(
        name: String,
        value: String,
    ) {
        builder.url {
            parameters.append(name, value)
        }
    }

    /**
     * Adds a header to the request.
     * @param name The name of the header.
     * @param value The value of the header.
     */
    fun header(
        name: String,
        value: String,
    ) {
        builder.headers {
            append(name, value)
        }
    }

    /**
     * Adds cookies to the request.
     * @param cookies The cookies to be added.
     */
    fun cookies(cookies: Cookies) {
        cookies.forEach {
            cookie(it)
        }
    }

    /**
     * Adds a cookie to the request.
     * @param cookie The cookie to be added.
     */
    internal fun cookie(cookie: Cookie) {
        cookie.apply {
            builder.cookie(
                name,
                value,
                encoding, // This is not available from the ktor lib
                maxAge ?: 0,
                expires,
                domain,
                path,
                secure,
                httpOnly,
                extensions,
            )
        }
    }

    /**
     * Adds a cookie to the request.
     * @param cookie The cookie to be added.
     */
    private fun cookie(cookie: String) {
        parseServerSetCookieHeader(cookie).apply {
            builder.cookie(
                name,
                value,
                maxAge ?: 0,
                expires,
                domain,
                path,
                secure,
                httpOnly,
                extensions,
            )
        }
    }

    /**
     * Sets the body of the request.
     * @param body The body to be set.
     */
    fun body(body: JsonObject = buildJsonObject {}) {
        builder.method = Post
        builder.contentType(io.ktor.http.ContentType.Application.Json)
        builder.setBody(body.toString())
    }

    /**
     * Sets the form of the request.
     * @param formBuilder The form to be set.
     */
    fun form(formBuilder: FormBuilder.() -> Unit) {
        builder.method = Post
        builder.setBody(
            FormDataContent(ParametersBuilder().apply(formBuilder).build()),
        )
    }
}
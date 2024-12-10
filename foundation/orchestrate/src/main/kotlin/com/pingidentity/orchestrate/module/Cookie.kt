/*
 * Copyright (c) 2024 Ping Identity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.orchestrate.module

import com.pingidentity.android.ContextProvider
import com.pingidentity.orchestrate.Module
import com.pingidentity.orchestrate.Request
import com.pingidentity.orchestrate.SharedContext
import com.pingidentity.orchestrate.Workflow
import com.pingidentity.storage.DataStoreStorage
import com.pingidentity.storage.Storage
import com.pingidentity.utils.PingDsl
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import io.ktor.http.Cookie
import io.ktor.http.CookieEncoding
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMessageBuilder
import io.ktor.http.Url
import io.ktor.http.parseServerSetCookieHeader
import io.ktor.http.renderCookieHeader
import io.ktor.http.renderSetCookieHeader
import io.ktor.util.date.GMTDate

internal const val TEMP_COOKIE = "TEMP_COOKIE"
internal const val COOKIE_STORAGE = "COOKIE_STORAGE"

typealias Cookies = List<String>

//This may be a defect for HttpMessageBuilder.cookie, the encoding is missing
fun HttpMessageBuilder.cookie(
    name: String,
    value: String,
    encoding: CookieEncoding = CookieEncoding.RAW,
    maxAge: Int = 0,
    expires: GMTDate? = null,
    domain: String? = null,
    path: String? = null,
    secure: Boolean = false,
    httpOnly: Boolean = false,
    extensions: Map<String, String?> = emptyMap()
) {
    val renderedCookie = Cookie(
        name = name,
        value = value,
        encoding = encoding,
        maxAge = maxAge,
        expires = expires,
        domain = domain,
        path = path,
        secure = secure,
        httpOnly = httpOnly,
        extensions = extensions
    ).let(::renderCookieHeader)

    if (HttpHeaders.Cookie !in headers) {
        headers.append(HttpHeaders.Cookie, renderedCookie)
        return
    }
    // Client cookies are stored in a single header "Cookies" and multiple values are separated with ";"
    headers[HttpHeaders.Cookie] = headers[HttpHeaders.Cookie] + "; " + renderedCookie
}

@PingDsl
class CookieConfig {

    lateinit var storage: Storage<Cookies>

    /**
     * A list of Cookies name that should be persisted to the storage.
     * For cookies that should not be persisted, do not add the cookie name to this list.
     */
    var persist = mutableListOf<String>()

    fun init() {
        if (!this::storage.isInitialized) {
            storage = DataStoreStorage(ContextProvider.context.defaultCookieDataStore, false)
        }
    }
}

val Cookie =
    Module.of(::CookieConfig) {

        fun cookieStorage(flowContext: SharedContext): AcceptAllCookiesStorage {
            return flowContext.getOrPut(TEMP_COOKIE) { AcceptAllCookiesStorage() }
                    as AcceptAllCookiesStorage
        }

        suspend fun inject(url: Url, cookies: Cookies, request: Request) {
            val storage = AcceptAllCookiesStorage()
            cookies.map { cookie ->
                parseServerSetCookieHeader(cookie)
            }.forEach { cookie ->
                storage.addCookie(url, cookie)
            }
            storage.get(url).forEach { cookie ->
                request.cookie(cookie)
            }
        }

        suspend fun inject(flowContext: SharedContext, url: Url, request: Request) {
            cookieStorage(flowContext).let { cookiesStorage ->
                cookiesStorage.get(url).forEach { cookie ->
                    request.cookie(cookie)
                }
            }
        }

        init {
            config.init()
            sharedContext[COOKIE_STORAGE] = config.storage
        }

        start {
            val url = it.builder.url.build()
            config.storage.get()?.let { cookies ->
                inject(url, cookies, it)
            }
            it
        }

        next { _, request ->
            val url = request.builder.url.build()
            inject(flowContext, url, request)
            config.storage.get()?.let { cookies ->
                inject(url, cookies, request)
            }
            request
        }

        response { response ->
            // Split cookies into persist and non-persist
            val (persistCookies, other) =
                response.cookies().map { s ->
                    // Parse cookies
                    parseServerSetCookieHeader(s)
                }.partition { cookie ->
                    cookie.name in config.persist
                }

            val url = response.request.builder.url.build()
            // Persist cookies
            if (persistCookies.isNotEmpty()) {
                val storage = AcceptAllCookiesStorage()
                // Store existing cookies to temp storage
                config.storage.get()?.let { cookies ->
                    cookies.map {
                        parseServerSetCookieHeader(it)
                    }.forEach {
                        storage.addCookie(url, it)
                    }
                }
                // Clear existing cookies
                config.storage.delete()

                // Add new cookies to temp storage
                persistCookies.map {
                    storage.addCookie(url, it)
                }

                // Save cookies to storage
                storage.get(url).map { cookie ->
                    renderSetCookieHeader(cookie)
                }.also { s ->
                    config.storage.save(s)
                }
            }

            // Store non-persist cookies to flow context
            other.forEach { cookie ->
                cookieStorage(flowContext).addCookie(url, cookie)
            }
        }

        signOff {
            //Inject stored cookies to the request
            config.storage.get()?.let { cookies ->
                it.cookies(cookies)
            }
            //Delete stored cookies
            config.storage.delete()
            it
        }
    }

suspend fun Workflow.hasCookies(): Boolean {
    return sharedContext.getValue<Storage<Cookies>>(COOKIE_STORAGE)?.get() != null
}

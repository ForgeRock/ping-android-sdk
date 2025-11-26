/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.client

import android.net.Uri
import com.pingidentity.utils.PingDsl
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.prepareRequest
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod.Companion.Delete
import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.http.HttpMethod.Companion.Put
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod.Companion.Post
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.net.URL
import kotlin.collections.emptyList
import kotlin.collections.map

/**
 * Configuration builder for DeviceClient.
 *
 * Use this class to configure authentication and connection details for device management.
 */
@PingDsl
class DeviceClientConfig {
    /** SSO token string for authentication. */
    var ssoTokenString: String = ""
    /** Server URL. */
    var serverUrl: URL = URL("http://localhost")
    /** Realm name. */
    var realm: String = "root"
        get() {
            return if (field.isNotBlank() && field.startsWith("/")) {
                field.substring(1)
            } else {
                field
            }
        }
    /** Cookie name for authentication. */
    var cookieName: String = "iPlanetDirectoryPro"
    /** HTTP client instance. */
    var httpClient: HttpClient = HttpClient()
}

/**
 * Main client for managing user devices.
 *
 * Provides access to device operations for different device types (OATH, Push, Bound, WebAuthn, Profile).
 */
class DeviceClient(block: DeviceClientConfig.() -> Unit) {
    private val config: DeviceClientConfig = DeviceClientConfig().apply(block)
    private val httpClient: HttpClient = config.httpClient

    private var cachedUserId: String? = null

    /**
     * Get the user ID from session, with caching to avoid redundant API calls.
     */
    private suspend fun getCachedUserId(): String {
        if (cachedUserId == null) {
            cachedUserId = getUserIdFromSession(config)
        }
        return cachedUserId ?: ""
    }

    /** OATH device operations (read/delete). */
    val oathDeviceClient: ImmutableDevice<OathDevice> by lazy {
        object : ImmutableDevice<OathDevice> {
            override suspend fun devices(): List<OathDevice> {
                return withContext(Dispatchers.IO) {
                    devices<OathDevice>(config, "devices/2fa/oath")
                }
            }

            override suspend fun delete(device: OathDevice) {
                withContext(Dispatchers.IO) {
                    delete<OathDevice>(config, device)
                }
            }
        }
    }

    /** Push device operations (read/delete). */
    val pushDeviceClient: ImmutableDevice<PushDevice> by lazy {
        object : ImmutableDevice<PushDevice> {
            override suspend fun devices(): List<PushDevice> {
                return withContext(Dispatchers.IO) {
                    devices<PushDevice>(config, "devices/2fa/push")
                }
            }

            override suspend fun delete(device: PushDevice) {
                withContext(Dispatchers.IO) {
                    delete<PushDevice>(config, device)
                }
            }
        }
    }

    /** Bound device operations (read/delete/update). */
    val boundDevice: MutableDevice<BoundDevice> by lazy {
        object : MutableDevice<BoundDevice> {
            override suspend fun devices(): List<BoundDevice> {
                return withContext(Dispatchers.IO) {
                    devices<BoundDevice>(config, "devices/2fa/binding")
                }
            }

            override suspend fun delete(device: BoundDevice) {
                withContext(Dispatchers.IO) {
                    delete<BoundDevice>(config, device)
                }
            }

            override suspend fun update(device: BoundDevice) {
                withContext(Dispatchers.IO) {
                    update<BoundDevice>(
                        config = config,
                        device = device,
                    )
                }
            }
        }
    }

    /** WebAuthn device operations (read/delete/update). */
    val webAuthnDevice: MutableDevice<WebAuthnDevice> by lazy {
        object : MutableDevice<WebAuthnDevice> {
            override suspend fun devices(): List<WebAuthnDevice> {
                return withContext(Dispatchers.IO) {
                    devices<WebAuthnDevice>(config, "devices/2fa/webauthn")
                }
            }

            override suspend fun delete(device: WebAuthnDevice) {
                withContext(Dispatchers.IO) {
                    delete<WebAuthnDevice>(config, device)
                }
            }

            override suspend fun update(device: WebAuthnDevice) {
                withContext(Dispatchers.IO) {
                    update<WebAuthnDevice>(
                        config = config,
                        device = device,
                    )
                }
            }
        }
    }

    /** Profile device operations (read/delete/update). */
    val profileDevice: MutableDevice<ProfileDevice> by lazy {
        object : MutableDevice<ProfileDevice> {
            override suspend fun devices(): List<ProfileDevice> {
                return withContext(Dispatchers.IO) {
                    devices<ProfileDevice>(config, "devices/profile")
                }
            }

            override suspend fun delete(device: ProfileDevice) {
                withContext(Dispatchers.IO) {
                    delete<ProfileDevice>(config, device)
                }
            }

            override suspend fun update(device: ProfileDevice) {
                withContext(Dispatchers.IO) {
                    update<ProfileDevice>(
                        config = config,
                        device = device,
                    )
                }
            }
        }
    }

    /**
     * Fetch a list of devices of type [T] from the server.
     *
     * @param config The client configuration.
     * @param path The API path for the device type.
     * @return List of devices of type [T].
     */
    private suspend inline fun <reified T : Device> devices(
        config: DeviceClientConfig,
        path: String,
    ): List<T> {
        val userId = getCachedUserId()
        if (userId.isBlank()) {
            return emptyList()
        }
        val request = httpClient.prepareRequest {
            url(
                composeUrlForDeviceList(
                    config = config,
                    path = path,
                    userId = userId,
                )
            )
            header(config.cookieName, config.ssoTokenString)
            header(CONTENT_TYPE_KEY, CONTENT_TYPE_JSON)
            header(ACCEPT_API_VERSION_KEY, ACCEPT_API_VERSION_VALUE)
            method = Get
        }
        val body = request.execute().bodyAsText()
        val jsonObject = Json.parseToJsonElement(body).jsonObject
        val result = jsonObject["result"]?.jsonArray
        return result?.map {
            val obj = it.jsonObject
            Json.decodeFromString(obj.toString())
        } ?: emptyList()
    }

    /**
     * Delete a device of type [T] on the server.
     *
     * @param config The client configuration.
     * @param device The device to delete.
     * @return The HTTP response from the server.
     */
    private suspend inline fun <reified T : Device> delete(
        config: DeviceClientConfig,
        device: T,
    ): HttpResponse {
        val userId = getCachedUserId()
        if (userId.isBlank()) {
            throw IllegalStateException("User ID cannot be blank.")
        }
        val urlString = composeUrlForDevice(
            config = config,
            device = device,
            userId = userId,
        )
        val request = httpClient.prepareRequest {
            url(urlString)
            header(config.cookieName, config.ssoTokenString)
            header(CONTENT_TYPE_KEY, CONTENT_TYPE_JSON)
            header(ACCEPT_API_VERSION_KEY, ACCEPT_API_VERSION_VALUE)
            method = Delete
        }
        return request.execute()
    }

    /**
     * Update a device of type [T] on the server.
     *
     * @param config The client configuration.
     * @param device The device to update.
     * @return The HTTP response from the server.
     */
    private suspend inline fun <reified T : Device> update(
        config: DeviceClientConfig,
        device: T,
    ): HttpResponse {
        val userId = getCachedUserId()
        if (userId.isBlank()) {
            throw IllegalStateException("User ID cannot be blank.")
        }
        val request = httpClient.prepareRequest {
            url(composeUrlForDevice(
                config = config,
                device = device,
                userId = userId,
            ))
            header(config.cookieName, config.ssoTokenString)
            header(CONTENT_TYPE_KEY, CONTENT_TYPE_JSON)
            header(ACCEPT_API_VERSION_KEY, ACCEPT_API_VERSION_VALUE)
            method = Put
            setBody(Json.encodeToString(device))
            contentType(ContentType.Application.Json)
        }
        return request.execute()
    }

    internal suspend fun getUserIdFromSession(
        config: DeviceClientConfig,
    ): String {
        val uri = composeBaseUrl(config)
            .appendPath("sessions")
            .appendQueryParameter("_action", "getSessionInfo")
            .build().toString()
        val request = httpClient.prepareRequest {
            url(uri)
            header(CONTENT_TYPE_KEY, CONTENT_TYPE_JSON)
            header(ACCEPT_API_VERSION_KEY, API_VERSION_2_1)
            header(config.cookieName, config.ssoTokenString)
            method = Post
        }
        val response = request.execute()
        val body = response.bodyAsText()
        val jsonObject = Json.parseToJsonElement(body).jsonObject
        return jsonObject["username"]?.toString()?.replace("\"", "") ?: ""
    }

    /**
     * Compose the base URL for device endpoints using the provided config.
     */
    private fun composeBaseUrl(config: DeviceClientConfig): Uri.Builder {
        return Uri.Builder()
            .encodedPath(config.serverUrl.toString())
            .appendPath("json")
            .appendPath("realms")
            .appendEncodedPath(config.realm)
    }

    /**
     * Compose the URL for fetching a list of devices of a specific type.
     */
    private fun composeUrlForDeviceList(
        config: DeviceClientConfig,
        path: String,
        userId: String,
    ): String {
        return composeBaseUrl(config)
            .appendPath("users")
            .appendEncodedPath(userId)
            .appendEncodedPath(path)
            .appendQueryParameter("_queryFilter", "true")
            .build().toString()
    }

    /**
     * Compose the URL for a specific device resource.
     */
    private fun composeUrlForDevice(
        config: DeviceClientConfig,
        device: Device,
        userId: String,
    ): String {
        val uri = composeBaseUrl(config)
            .appendPath("users")
            .appendEncodedPath(userId)
            .appendEncodedPath(device.urlSuffix)
            .appendEncodedPath(device.id)
        return uri.build().toString()
    }

    companion object {
        private const val CONTENT_TYPE_KEY = "Content-Type"
        private const val CONTENT_TYPE_JSON = "application/json"
        private const val ACCEPT_API_VERSION_KEY = "Accept-API-Version"
        private const val ACCEPT_API_VERSION_VALUE = "resource=1.0"
        private const val API_VERSION_2_1 = "resource=2.1, protocol=1.0"
    }
}
/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.client

import android.net.Uri
import com.pingidentity.utils.PingDsl
import io.ktor.client.HttpClient
import io.ktor.client.request.headers
import io.ktor.client.request.request
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod.Companion.Delete
import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.http.HttpMethod.Companion.Put
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.collections.emptyList
import kotlin.collections.map

@PingDsl
class DeviceClientConfig {
    var ssoTokenString: String? = null
    var serverUrl: String = ""
    var realm: String = ""
    var httpClient: HttpClient = HttpClient()
}

class DeviceClient(block: DeviceClientConfig.() -> Unit) {
    private val config: DeviceClientConfig = DeviceClientConfig().apply(block)
    private val httpClient: HttpClient = config.httpClient
    private val ssoTokenString = config.ssoTokenString

    val oathDeviceClient: DeviceImplementation<OathDevice> by lazy {
        object : DeviceImplementation<OathDevice> {
            override suspend fun get(): List<OathDevice> {
                return withContext(Dispatchers.IO) {
                    // Use journey and httpClient to fetch Oath devices
                    val urlString = composeUrl(config, "devices/2fa/oath")
                    val response = httpClient.request {
                        url(urlString)
                        headers {
                            append("Authorization", "Bearer $ssoTokenString")
                            append("Content-Type", "application/json")
                        }
                        method = Get
                    }
                    getDevices<OathDevice>(response)
                }
            }

            override suspend fun delete(device: OathDevice) {
                withContext(Dispatchers.IO) {
                    httpClient.request {
                        url.apply { composeUrlForDevice(config, device) }
                        headers {
                            append("Authorization", "Bearer $ssoTokenString")
                            append("Content-Type", "application/json")
                        }
                        method = Delete
                    }
                }
            }

            override suspend fun update(device: OathDevice) {
                withContext(Dispatchers.IO) {
                    httpClient.request {
                        url.apply { composeUrlForDevice(config, device) }
                        headers {
                            append("Authorization", "Bearer $ssoTokenString")
                            append("Content-Type", "application/json")
                        }
                        method = Put
                        contentType(ContentType.Application.Json)
                        setBody(Json.encodeToString(device))
                    }
                }
            }
        }
    }

    val pushDeviceClient: DeviceImplementation<PushDevice> by lazy {
        object : DeviceImplementation<PushDevice> {
            override suspend fun get(): List<PushDevice> {
                return withContext(Dispatchers.IO) {
                    // Use journey and httpClient to fetch Push devices
                    val response = httpClient.request {
                        url.apply { composeUrl(config, "devices/2fa/push") }
                        headers {
                            append("Authorization", "Bearer $ssoTokenString")
                            append("Content-Type", "application/json")
                        }
                        method = Get
                    }
                    getDevices<PushDevice>(response)
                }
            }

            override suspend fun delete(device: PushDevice) {
                withContext(Dispatchers.IO) {
                    httpClient.request {
                        url.apply { composeUrlForDevice(config, device) }
                        headers {
                            append("Authorization", "Bearer $ssoTokenString")
                            append("Content-Type", "application/json")
                        }
                        method = Delete
                    }
                }
            }

            override suspend fun update(device: PushDevice) {
                withContext(Dispatchers.IO) {
                    httpClient.request {
                        url.apply { composeUrlForDevice(config, device) }
                        headers {
                            append("Authorization", "Bearer $ssoTokenString")
                            append("Content-Type", "application/json")
                        }
                        method = Put
                        contentType(ContentType.Application.Json)
                        setBody(Json.encodeToString(device))
                    }
                }
            }
        }
    }

    val boundDevice: DeviceImplementation<BoundDevice> by lazy {
        object : DeviceImplementation<BoundDevice> {
            override suspend fun get(): List<BoundDevice> {
                return withContext(Dispatchers.IO) {
                    // Use journey and httpClient to fetch Push devices
                    val response = httpClient.request {
                        url.apply { composeUrl(config, "devices/2fa/binding") }
                        headers {
                            append("Authorization", "Bearer $ssoTokenString")
                            append("Content-Type", "application/json")
                        }
                        method = Get
                    }
                    getDevices<BoundDevice>(response)
                }
            }

            override suspend fun delete(device: BoundDevice) {
                withContext(Dispatchers.IO) {
                    httpClient.request {
                        url.apply { composeUrlForDevice(config, device) }
                        headers {
                            append("Authorization", "Bearer $ssoTokenString")
                            append("Content-Type", "application/json")
                        }
                        method = Delete
                    }
                }
            }

            override suspend fun update(device: BoundDevice) {
                withContext(Dispatchers.IO) {
                    httpClient.request {
                        url.apply { composeUrlForDevice(config, device) }
                        headers {
                            append("Authorization", "Bearer $ssoTokenString")
                            append("Content-Type", "application/json")
                        }
                        method = Put
                        contentType(ContentType.Application.Json)
                        setBody(Json.encodeToString(device))
                    }
                }
            }
        }
    }

    val webAuthnDevice: DeviceImplementation<WebAuthnDevice> by lazy {
        object : DeviceImplementation<WebAuthnDevice> {
            override suspend fun get(): List<WebAuthnDevice> {
                // Implementation to fetch WebAuthn devices
                return emptyList()
            }

            override suspend fun delete(device: WebAuthnDevice) {
                withContext(Dispatchers.IO) {
                    httpClient.request {
                        url.apply { composeUrlForDevice(config, device) }
                        headers {
                            append("Authorization", "Bearer $ssoTokenString")
                            append("Content-Type", "application/json")
                        }
                        method = Delete
                    }
                }
            }

            override suspend fun update(device: WebAuthnDevice) {
                withContext(Dispatchers.IO) {
                    httpClient.request {
                        url.apply { composeUrlForDevice(config, device) }
                        headers {
                            append("Authorization", "Bearer $ssoTokenString")
                            append("Content-Type", "application/json")
                        }
                        method = Put
                        contentType(ContentType.Application.Json)
                        setBody(Json.encodeToString(device))
                    }
                }
            }
        }
    }

    val profileDevice: DeviceImplementation<ProfileDevice> by lazy {
        object : DeviceImplementation<ProfileDevice> {
            override suspend fun get(): List<ProfileDevice> {
                // Implementation to fetch Profile devices
                return emptyList()
            }

            override suspend fun delete(device: ProfileDevice) {
                withContext(Dispatchers.IO) {
                    httpClient.request {
                        url.apply { composeUrlForDevice(config, device) }
                        headers {
                            append("Authorization", "Bearer $ssoTokenString")
                            append("Content-Type", "application/json")
                        }
                        method = Delete
                    }
                }
            }

            override suspend fun update(device: ProfileDevice) {
                withContext(Dispatchers.IO) {
                    httpClient.request {
                        url.apply { composeUrlForDevice(config, device) }
                        headers {
                            append("Authorization", "Bearer $ssoTokenString")
                            append("Content-Type", "application/json")
                        }
                        method = Put
                        contentType(ContentType.Application.Json)
                        setBody(Json.encodeToString(device))
                    }
                }
            }
        }
    }

    private suspend inline fun <reified T> getDevices(response: HttpResponse): List<T> {
        val body = response.bodyAsText()
        val jsonObject = Json.parseToJsonElement(body).jsonObject
        val result = jsonObject["result"]?.jsonArray
        return result?.map {
            val obj = it.jsonObject
            Json.decodeFromString(obj.toString())
        } ?: emptyList()
    }

    private fun composeUrl(
        config: DeviceClientConfig,
        path: String,
    ): String {
        return Uri.Builder()
            .encodedPath(config.serverUrl)
            .appendPath("json")
            .appendPath("realms")
            .appendPath(config.realm)
            .appendPath("users")
            .appendPath(config.ssoTokenString ?: "")
            .appendEncodedPath(path)
            .appendQueryParameter("_queryFilter", "true")
            .build().toString()
    }

    private fun composeUrlForDevice(
        config: DeviceClientConfig,
        device: Device,
    ): Uri {
        val uri = Uri.Builder()
            .encodedPath(config.serverUrl)
            .appendPath("json")
            .appendPath("realms")
            .appendPath(config.realm)
            .appendPath("users")
            .appendPath(config.ssoTokenString ?: "")
            .appendEncodedPath(device.urlSuffix)
            .appendEncodedPath(device.id)
        return uri.build()
    }
}
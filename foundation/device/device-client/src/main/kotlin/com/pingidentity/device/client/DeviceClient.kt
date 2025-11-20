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
import io.ktor.client.request.headers
import io.ktor.client.request.prepareRequest
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
    var cookieName: String = ""
    var userId: String = ""
    var httpClient: HttpClient = HttpClient()
}

class DeviceClient(block: DeviceClientConfig.() -> Unit) {
    private val config: DeviceClientConfig = DeviceClientConfig().apply(block)
    private val httpClient: HttpClient = config.httpClient
    private val ssoTokenString = config.ssoTokenString

    val oathDeviceClient: ImmutableDevice<OathDevice> by lazy {
        object : ImmutableDevice<OathDevice> {
            override suspend fun getDevices(): List<OathDevice> {
                return withContext(Dispatchers.IO) {
                    val response = execute(config, "devices/2fa/oath")
                    getDevices<OathDevice>(response)
                }
            }

            override suspend fun deleteDevice(device: OathDevice) {
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
        }
    }

    val pushDeviceClient: ImmutableDevice<PushDevice> by lazy {
        object : ImmutableDevice<PushDevice> {
            override suspend fun getDevices(): List<PushDevice> {
                return withContext(Dispatchers.IO) {
                    // Use journey and httpClient to fetch Push devices
                    val response = execute(config, "devices/2fa/push")
                    getDevices<PushDevice>(response)
                }
            }

            override suspend fun deleteDevice(device: PushDevice) {
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
        }
    }

    val boundDevice: MutableDevice<BoundDevice> by lazy {
        object : MutableDevice<BoundDevice> {
            override suspend fun getDevices(): List<BoundDevice> {
                return withContext(Dispatchers.IO) {
                    // Use journey and httpClient to fetch Push devices
                    val response = execute(config, "devices/2fa/binding")
                    getDevices<BoundDevice>(response)
                }
            }

            override suspend fun deleteDevice(device: BoundDevice) {
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

            override suspend fun updateDevice(device: BoundDevice) {
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

    val webAuthnDevice: MutableDevice<WebAuthnDevice> by lazy {
        object : MutableDevice<WebAuthnDevice> {
            override suspend fun getDevices(): List<WebAuthnDevice> {
                val response = execute(config, "devices/2fa/webauthn")
                return getDevices<WebAuthnDevice>(response)
            }

            override suspend fun deleteDevice(device: WebAuthnDevice) {
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

            override suspend fun updateDevice(device: WebAuthnDevice) {
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

    val profileDevice: MutableDevice<ProfileDevice> by lazy {
        object : MutableDevice<ProfileDevice> {
            override suspend fun getDevices(): List<ProfileDevice> {
                // Implementation to fetch Profile devices
                val response = execute(config, "devices/profile")
                return getDevices<ProfileDevice>(response)
            }

            override suspend fun deleteDevice(device: ProfileDevice) {
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

            override suspend fun updateDevice(device: ProfileDevice) {
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
        println("Status: ${response.status} -> ${response.bodyAsText()}")
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
            .appendEncodedPath(config.realm)
            .appendPath("users")
            .appendPath(config.userId) // Placeholder user ID
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
            .appendEncodedPath(config.realm)
            .appendPath("users")
            .appendEncodedPath(device.urlSuffix)
            .appendEncodedPath(device.id)
        return uri.build()
    }

    private suspend fun execute(
        config: DeviceClientConfig,
        path: String,
        requestType: RequestType = RequestType.LIST,
    ): HttpResponse {
        val urlString = composeUrl(config, path)
        println(urlString)
        val request = httpClient.prepareRequest {
            url(urlString)
            header(config.cookieName, config.ssoTokenString ?: "")
            header("Content-Type", "application/json")
            header("Accept-API-Version", "resource=1.0")
            header("x-requested-platform", "Android")
            method = when (requestType) {
                RequestType.LIST -> Get
                RequestType.DELETE -> Delete
                RequestType.UPDATE -> Put
            }
        }
        return request.execute()
    }

    private enum class RequestType {
        LIST,
        DELETE,
        UPDATE
    }
}
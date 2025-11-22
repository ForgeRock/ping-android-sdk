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
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.serializer
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
                    val response = execute(
                        config = config,
                        path = "devices/2fa/oath",
                        device = device,
                        requestType = RequestType.DELETE,
                    )
                    println("Delete OATH Device Response: ${response.status} -> ${response.bodyAsText()}")
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
                    val response = execute(
                        config = config,
                        path = "devices/2fa/push/${device.id}",
                        device = device,
                        requestType = RequestType.DELETE,
                    )
                    println("Delete Push Device Response: ${response.status} -> ${response.bodyAsText()}")
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
                    val response = execute(
                        config = config,
                        path = "devices/2fa/binding",
                        device = device,
                        requestType = RequestType.DELETE,
                    )
                    println("Delete Bound Device Response: ${response.status} -> ${response.bodyAsText()}")
                }
            }

            override suspend fun updateDevice(device: BoundDevice) {
                withContext(Dispatchers.IO) {
                    val response = execute(
                        config = config,
                        path = "devices/2fa/binding",
                        device = device,
                        requestType = RequestType.UPDATE,
                    )
                    println("Update Bound Device Response: ${response.status} -> ${response.bodyAsText()}")
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
                    val response = execute(
                        config = config,
                        path = "devices/2fa/webauthn",
                        device = device,
                        requestType = RequestType.DELETE,
                    )
                    println("Delete WebAuthnDevice Device Response: ${response.status} -> ${response.bodyAsText()}")
                }
            }

            override suspend fun updateDevice(device: WebAuthnDevice) {
                withContext(Dispatchers.IO) {
                    val response = execute(
                        config = config,
                        path = "devices/2fa/webauthn",
                        device = device,
                        requestType = RequestType.UPDATE,
                    )
                    println("Update WebAuthnDevice Device Response: ${response.status} -> ${response.bodyAsText()}")
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
                    val response = execute(
                        config = config,
                        path = "devices/profile",
                        device = device,
                        requestType = RequestType.DELETE,
                    )
                    println("Delete ProfileDevice Device Response: ${response.status} -> ${response.bodyAsText()}")
                }
            }

            override suspend fun updateDevice(device: ProfileDevice) {
                withContext(Dispatchers.IO) {
                    val response = execute(
                        config = config,
                        path = "devices/profile",
                        device = device,
                        requestType = RequestType.UPDATE,
                    )
                    println("Update ProfileDevice Device Response: ${response.status} -> ${response.bodyAsText()}")
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

    private fun composeBaseUrl(config: DeviceClientConfig): Uri.Builder {
        return Uri.Builder()
            .encodedPath(config.serverUrl)
            .appendPath("json")
            .appendPath("realms")
            .appendPath("alpha")
            .appendPath("users")
            .appendPath(config.userId) // Placeholder user ID
    }

    private fun composeUrlForDeviceList(
        config: DeviceClientConfig,
        path: String,
    ): String {
        return composeBaseUrl(config)
            .appendEncodedPath(path)
            .appendQueryParameter("_queryFilter", "true")
            .build().toString()
    }

    private fun composeUrlForDevice(
        config: DeviceClientConfig,
        device: Device,
    ): String {
        val uri = composeBaseUrl(config)
            .appendEncodedPath(device.urlSuffix)
            .appendEncodedPath(device.id)
        return uri.build().toString()
    }

    private suspend fun execute(
        config: DeviceClientConfig,
        path: String,
        device: Device? = null,
        requestType: RequestType = RequestType.LIST,
    ): HttpResponse {
        val urlString = if (requestType == RequestType.LIST) {
            composeUrlForDeviceList(config, path)
        } else {
            composeUrlForDevice(
                config = config,
                device = device!!,
            )
        }
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
            if (requestType == RequestType.UPDATE) {
                setBody(Json.encodeToString(Json.serializersModule.serializer(), device!!))
                contentType(ContentType.Application.Json)
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
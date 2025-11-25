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
import kotlin.collections.emptyList
import kotlin.collections.map

@PingDsl
class DeviceClientConfig {
    var ssoTokenString: String? = null
    var serverUrl: String = ""
    var realm: String = ""
        get() {
            return if (field.isNotBlank() && field.startsWith("/")) {
                field.substring(1)
            } else {
                field
            }
        }
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
                    getDeviceList<OathDevice>(config, "devices/2fa/oath")
                }
            }

            override suspend fun deleteDevice(device: OathDevice) {
                withContext(Dispatchers.IO) {
                    deleteDevice<OathDevice>(config, device)
                }
            }
        }
    }

    val pushDeviceClient: ImmutableDevice<PushDevice> by lazy {
        object : ImmutableDevice<PushDevice> {
            override suspend fun getDevices(): List<PushDevice> {
                return withContext(Dispatchers.IO) {
                    getDeviceList<PushDevice>(config, "devices/2fa/push")
                }
            }

            override suspend fun deleteDevice(device: PushDevice) {
                withContext(Dispatchers.IO) {
                    deleteDevice<PushDevice>(config, device)
                }
            }
        }
    }

    val boundDevice: MutableDevice<BoundDevice> by lazy {
        object : MutableDevice<BoundDevice> {
            override suspend fun getDevices(): List<BoundDevice> {
                return withContext(Dispatchers.IO) {
                    getDeviceList<BoundDevice>(config, "devices/2fa/binding")
                }
            }

            override suspend fun deleteDevice(device: BoundDevice) {
                withContext(Dispatchers.IO) {
                    deleteDevice<BoundDevice>(config, device)
                }
            }

            override suspend fun updateDevice(device: BoundDevice) {
                withContext(Dispatchers.IO) {
                    updateDevice<BoundDevice>(
                        config = config,
                        device = device,
                    )
                }
            }
        }
    }

    val webAuthnDevice: MutableDevice<WebAuthnDevice> by lazy {
        object : MutableDevice<WebAuthnDevice> {
            override suspend fun getDevices(): List<WebAuthnDevice> {
                return withContext(Dispatchers.IO) {
                    getDeviceList<WebAuthnDevice>(config, "devices/2fa/webauthn")
                }
            }

            override suspend fun deleteDevice(device: WebAuthnDevice) {
                withContext(Dispatchers.IO) {
                    deleteDevice<WebAuthnDevice>(config, device)
                }
            }

            override suspend fun updateDevice(device: WebAuthnDevice) {
                withContext(Dispatchers.IO) {
                    updateDevice<WebAuthnDevice>(
                        config = config,
                        device = device,
                    )
                }
            }
        }
    }

    val profileDevice: MutableDevice<ProfileDevice> by lazy {
        object : MutableDevice<ProfileDevice> {
            override suspend fun getDevices(): List<ProfileDevice> {
                return withContext(Dispatchers.IO) {
                    getDeviceList<ProfileDevice>(config, "devices/profile")
                }
            }

            override suspend fun deleteDevice(device: ProfileDevice) {
                withContext(Dispatchers.IO) {
                    deleteDevice<ProfileDevice>(config, device)
                }
            }

            override suspend fun updateDevice(device: ProfileDevice) {
                withContext(Dispatchers.IO) {
                    updateDevice<ProfileDevice>(
                        config = config,
                        device = device,
                    )
                }
            }
        }
    }

    private fun composeBaseUrl(config: DeviceClientConfig): Uri.Builder {
        return Uri.Builder()
            .encodedPath(config.serverUrl)
            .appendPath("json")
            .appendPath("realms")
            .appendEncodedPath(config.realm)
            .appendPath("users")
            .appendPath(config.userId)
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

    private suspend inline fun <reified T : Device> getDeviceList(
        config: DeviceClientConfig,
        path: String,
    ): List<T> {
        val request = httpClient.prepareRequest {
            url(composeUrlForDeviceList(config, path))
            header(config.cookieName, config.ssoTokenString ?: "")
            header("Content-Type", "application/json")
            header("Accept-API-Version", "resource=1.0")
            header("x-requested-platform", "Android")
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

    private suspend inline fun <reified T : Device> deleteDevice(
        config: DeviceClientConfig,
        device: T,
    ): HttpResponse {
        val urlString = composeUrlForDevice(
            config = config,
            device = device,
        )
        val request = httpClient.prepareRequest {
            url(urlString)
            header(config.cookieName, config.ssoTokenString ?: "")
            header("Content-Type", "application/json")
            header("Accept-API-Version", "resource=1.0")
            header("x-requested-platform", "Android")
            method = Delete
        }
        return request.execute()
    }

    private suspend inline fun <reified T : Device> updateDevice(
        config: DeviceClientConfig,
        device: T,
    ): HttpResponse {
        val request = httpClient.prepareRequest {
            url(composeUrlForDevice(
                config = config,
                device = device,
            ))
            header(config.cookieName, config.ssoTokenString ?: "")
            header("Content-Type", "application/json")
            header("Accept-API-Version", "resource=1.0")
            header("x-requested-platform", "Android")
            method = Put
            setBody(Json.encodeToString(device))
            contentType(ContentType.Application.Json)
        }
        return request.execute()
    }
}
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
import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.http.cio.Response
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.slf4j.MDC.put
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
                    val response = httpClient.request {
                        url.apply { composeUrl(config, "devices/2fa/oath") }
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
                // Implementation to delete an Oath device
            }

            override suspend fun update(device: OathDevice) {
                // Implementation to update an Oath device
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
                // Implementation to delete a Push device
            }

            override suspend fun update(device: PushDevice) {
                // Implementation to update a Push device
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
                // Implementation to delete a Bound device
            }

            override suspend fun update(device: BoundDevice) {
                // Implementation to update a Bound device
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
                // Implementation to delete a WebAuthn device
            }

            override suspend fun update(device: WebAuthnDevice) {
                // Implementation to update a WebAuthn device
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
                // Implementation to delete a Profile device
            }

            override suspend fun update(device: ProfileDevice) {
                // Implementation to update a Profile device
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
    ): Uri {
        val uri = Uri.Builder()
            .encodedPath(config.serverUrl)
            .appendPath("json")
            .appendPath("realms")
            .appendPath(config.realm)
            .appendPath("users")
            .appendPath(config.ssoTokenString ?: "")
            .appendEncodedPath(path)
            .appendQueryParameter("_queryFilter", "true")
        return uri.build()
    }
}
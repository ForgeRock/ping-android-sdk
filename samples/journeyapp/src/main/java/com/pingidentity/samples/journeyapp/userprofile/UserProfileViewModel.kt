/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.samples.journeyapp.userprofile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pingidentity.device.client.DeviceClient
import com.pingidentity.journey.session
import com.pingidentity.journey.user
import com.pingidentity.samples.journeyapp.env.journey
import com.pingidentity.utils.Result
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlinx.serialization.json.jsonPrimitive

class UserProfileViewModel : ViewModel() {
    var state = MutableStateFlow(UserProfileState())
        private set

    fun userinfo() {
        viewModelScope.launch {
            journey.user()?.let { user ->
                when (val result = user.userinfo(false)) {
                    is Result.Failure ->
                        state.update { s ->
                            s.copy(user = null, error = result.value)
                        }

                    is Result.Success -> {
                        state.update { s ->
                            s.copy(user = result.value, error = null)
                        }
                    }
                }
            }
        }
    }

    fun toggleDeviceInfo() {
        state.update { s ->
            s.copy(showDeviceInfo = !s.showDeviceInfo)
        }
    }

    fun setDeviceType(deviceType: DeviceType) {
        state.update { s ->
            s.copy(selectedDeviceType = deviceType)
        }
        viewModelScope.launch {
            val user = journey.user() ?: return@launch
            val userInfo = user.userinfo(false) as Result.Success
            val deviceClient = DeviceClient {
                ssoTokenString = user.session().value
                serverUrl = "https://openam-sdks.forgeblocks.com/am"
                realm = user.session().realm
                cookieName = "5421aeddf91aa20"
                userId = userInfo.value["sub"]?.jsonPrimitive?.content ?: ""
            }
            try {
                when (deviceType) {
                    DeviceType.OATH -> {
                        val devices = deviceClient.oathDeviceClient.getDevices()
                        val deviceNames = devices.map { it.deviceName }
                        state.update { s ->
                            s.copy(deviceList = deviceNames)
                        }
                    }

                    DeviceType.PUSH -> {
                        val devices = deviceClient.pushDeviceClient.getDevices()
                        val deviceNames = devices.map { it.deviceName }
                        state.update { s ->
                            s.copy(deviceList = deviceNames)
                        }
                    }
                    DeviceType.BOUND -> {
                        val devices = deviceClient.boundDevice.getDevices()
                        val deviceNames = devices.map { it.deviceName }
                        state.update { s ->
                            s.copy(deviceList = deviceNames)
                        }
                    }
                    DeviceType.WEBAUTHN -> {
                        val devices = deviceClient.webAuthnDevice.getDevices()
                        val deviceNames = devices.map { it.deviceName }
                        state.update { s ->
                            s.copy(deviceList = deviceNames)
                        }
                    }
                }
            } catch (exception: Exception) {
                yield()
                state.update { s ->
                    s.copy(deviceList = emptyList())
                }
                println(exception.message)
            }
        }
    }
}

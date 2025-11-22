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
            s.copy(selectedDeviceType = deviceType, isLoading = true)
        }
        viewModelScope.launch {
            val deviceClient = buildDeviceClient() ?: return@launch
            try {
                when (deviceType) {
                    DeviceType.OATH -> {
                        val devices = deviceClient.oathDeviceClient.getDevices()
                        val deviceNames = devices.map { it.deviceName }
                        state.update { s ->
                            s.copy(deviceList = deviceNames, isLoading = false)
                        }
                    }

                    DeviceType.PUSH -> {
                        val devices = deviceClient.pushDeviceClient.getDevices()
                        val deviceNames = devices.map { it.deviceName }
                        state.update { s ->
                            s.copy(deviceList = deviceNames, isLoading = false)
                        }
                    }
                    DeviceType.BOUND -> {
                        val devices = deviceClient.boundDevice.getDevices()
                        val deviceNames = devices.map { it.deviceName }
                        state.update { s ->
                            s.copy(deviceList = deviceNames, isLoading = false)
                        }
                    }
                    DeviceType.WEBAUTHN -> {
                        val devices = deviceClient.webAuthnDevice.getDevices()
                        val deviceNames = devices.map { it.deviceName }
                        state.update { s ->
                            s.copy(deviceList = deviceNames, isLoading = false)
                        }
                    }
                    DeviceType.PROFILE -> {
                        val devices = deviceClient.profileDevice.getDevices()
                        val deviceNames = devices.map { it.deviceName }
                        state.update { s ->
                            s.copy(deviceList = deviceNames, isLoading = false)
                        }
                    }
                }
            } catch (exception: Exception) {
                yield()
                state.update { s ->
                    s.copy(deviceList = emptyList(), isLoading = false)
                }
                println(exception.message)
            }
        }
    }

    fun onEditDevice(deviceName: String) {
        viewModelScope.launch {
            println("Edit device: $deviceName")
            val deviceClient = buildDeviceClient() ?: return@launch
            try {
                when (state.value.selectedDeviceType) {
                    DeviceType.OATH -> {
                        // Send an update that this is not possible.
                    }
                    DeviceType.PUSH -> {
                        // Send an update that this is not possible.
                    }
                    DeviceType.BOUND -> {
                        val devices = deviceClient.boundDevice.getDevices()
                        val deviceToUpdate = devices.find { it.deviceName == deviceName }
                        deviceToUpdate?.let {
                            deviceClient.boundDevice.updateDevice(it)
                            setDeviceType(DeviceType.BOUND)
                        }
                    }
                    DeviceType.WEBAUTHN -> {
                        val devices = deviceClient.webAuthnDevice.getDevices()
                        val deviceToUpdate = devices.find { it.deviceName == deviceName }
                        deviceToUpdate?.let {
                            deviceClient.webAuthnDevice.updateDevice(it)
                            setDeviceType(DeviceType.WEBAUTHN)
                        }
                    }
                    DeviceType.PROFILE -> {
                        val devices = deviceClient.profileDevice.getDevices()
                        val deviceToUpdate = devices.find { it.deviceName == deviceName }
                        deviceToUpdate?.let {
                            deviceClient.profileDevice.updateDevice(it)
                            setDeviceType(DeviceType.PROFILE)
                        }
                    }
                }
            } catch (exception: Exception) {
                println("Error editing device: ${exception.message}")
            }
        }
    }

    fun onDeleteDevice(deviceName: String) {
        viewModelScope.launch {
            val deviceClient = buildDeviceClient() ?: return@launch
            try {
                when (state.value.selectedDeviceType) {
                    DeviceType.OATH -> {
                        val devices = deviceClient.oathDeviceClient.getDevices()
                        val deviceToDelete = devices.find { it.deviceName == deviceName }
                        deviceToDelete?.let {
                            deviceClient.oathDeviceClient.deleteDevice(it)
                            // Refresh the device list
                            setDeviceType(DeviceType.OATH)
                        }
                    }
                    DeviceType.PUSH -> {
                        val devices = deviceClient.pushDeviceClient.getDevices()
                        val deviceToDelete = devices.find { it.deviceName == deviceName }
                        deviceToDelete?.let {
                            deviceClient.pushDeviceClient.deleteDevice(it)
                            setDeviceType(DeviceType.PUSH)
                        }
                    }
                    DeviceType.BOUND -> {
                        val devices = deviceClient.boundDevice.getDevices()
                        val deviceToDelete = devices.find { it.deviceName == deviceName }
                        deviceToDelete?.let {
                            deviceClient.boundDevice.deleteDevice(it)
                            setDeviceType(DeviceType.BOUND)
                        }
                    }
                    DeviceType.WEBAUTHN -> {
                        val devices = deviceClient.webAuthnDevice.getDevices()
                        val deviceToDelete = devices.find { it.deviceName == deviceName }
                        deviceToDelete?.let {
                            deviceClient.webAuthnDevice.deleteDevice(it)
                            setDeviceType(DeviceType.WEBAUTHN)
                        }
                    }
                    DeviceType.PROFILE -> {
                        val devices = deviceClient.profileDevice.getDevices()
                        val deviceToDelete = devices.find { it.deviceName == deviceName }
                        deviceToDelete?.let {
                            deviceClient.profileDevice.deleteDevice(it)
                            setDeviceType(DeviceType.PROFILE)
                        }
                    }
                }
            } catch (exception: Exception) {
                println("Error deleting device: ${exception.message}")
                // Optionally refresh the list to ensure consistency
                setDeviceType(state.value.selectedDeviceType)
            }
        }
    }

    private suspend fun buildDeviceClient(): DeviceClient? {
        val user = journey.user() ?: return null
        val userInfo = user.userinfo(false) as? Result.Success ?: return null
        return DeviceClient {
            ssoTokenString = user.session().value
            serverUrl = "https://openam-sdks.forgeblocks.com/am"
            realm = user.session().realm
            cookieName = "5421aeddf91aa20"
            userId = userInfo.value["sub"]?.jsonPrimitive?.content ?: ""
        }
    }
}

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
import java.net.URL

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

    fun openEditDialog(deviceName: String) {
        state.update { s ->
            s.copy(showEditDialog = true, deviceToEdit = deviceName, newDeviceName = deviceName)
        }
    }

    fun updateNewDeviceName(newName: String) {
        state.update { s ->
            s.copy(newDeviceName = newName)
        }
    }

    fun cancelEditDialog() {
        state.update { s ->
            s.copy(showEditDialog = false, deviceToEdit = null, newDeviceName = "")
        }
    }

    fun confirmEditDevice() {
        val deviceName = state.value.deviceToEdit ?: return
        val newName = state.value.newDeviceName.trim()

        if (newName.isEmpty()) {
            return
        }

        // Close the dialog
        state.update { s ->
            s.copy(showEditDialog = false, deviceToEdit = null, newDeviceName = "")
        }

        // Call the actual edit function with the new name
        onEditDevice(deviceName, newName)
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
                        val deviceResult = deviceClient.oathDevice.devices()
                        val devices = deviceResult.getOrThrow()
                        val deviceNames = devices.map { it.deviceName }
                        state.update { s ->
                            s.copy(deviceList = deviceNames, isLoading = false)
                        }
                    }

                    DeviceType.PUSH -> {
                        val deviceResult = deviceClient.pushDevice.devices()
                        val devices = deviceResult.getOrThrow()
                        val deviceNames = devices.map { it.deviceName }
                        state.update { s ->
                            s.copy(deviceList = deviceNames, isLoading = false)
                        }
                    }
                    DeviceType.BOUND -> {
                        val deviceResult = deviceClient.boundDevice.devices()
                        val devices = deviceResult.getOrThrow()
                        val deviceNames = devices.map { it.deviceName }
                        state.update { s ->
                            s.copy(deviceList = deviceNames, isLoading = false)
                        }
                    }
                    DeviceType.WEBAUTHN -> {
                        val deviceResult = deviceClient.webAuthnDevice.devices()
                        val devices = deviceResult.getOrThrow()
                        val deviceNames = devices.map { it.deviceName }
                        state.update { s ->
                            s.copy(deviceList = deviceNames, isLoading = false)
                        }
                    }
                    DeviceType.PROFILE -> {
                        val deviceResult = deviceClient.profileDevice.devices()
                        val devices = deviceResult.getOrThrow()
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

    fun onEditDevice(deviceName: String, newDeviceName: String) {
        viewModelScope.launch {
            val deviceClient = buildDeviceClient() ?: return@launch
            try {
                when (state.value.selectedDeviceType) {
                    DeviceType.OATH -> {
                        val devices = deviceClient.oathDevice.devices().getOrThrow()
                        val deviceToUpdate = devices.find { it.deviceName == deviceName }
                        deviceToUpdate?.let {
                            it.deviceName = newDeviceName
                            deviceClient.oathDevice.update(it)
                            setDeviceType(DeviceType.OATH)
                        }
                    }
                    DeviceType.PUSH -> {
                        val devices = deviceClient.pushDevice.devices().getOrThrow()
                        val deviceToUpdate = devices.find { it.deviceName == deviceName }
                        deviceToUpdate?.let {
                            it.deviceName = newDeviceName
                            deviceClient.pushDevice.update(it)
                            setDeviceType(DeviceType.PUSH)
                        }
                    }
                    DeviceType.BOUND -> {
                        val devices = deviceClient.boundDevice.devices().getOrThrow()
                        val deviceToUpdate = devices.find { it.deviceName == deviceName }
                        deviceToUpdate?.let {
                            it.deviceName = newDeviceName
                            deviceClient.boundDevice.update(it)
                            // Refresh only the device list, reusing cached userId
                            setDeviceType(DeviceType.BOUND)
                        }
                    }
                    DeviceType.WEBAUTHN -> {
                        val devices = deviceClient.webAuthnDevice.devices().getOrThrow()
                        val deviceToUpdate = devices.find { it.deviceName == deviceName }
                        deviceToUpdate?.let {
                            it.deviceName = newDeviceName
                            deviceClient.webAuthnDevice.update(it)
                            setDeviceType(DeviceType.WEBAUTHN)
                        }
                    }
                    DeviceType.PROFILE -> {
                        val devices = deviceClient.profileDevice.devices().getOrThrow()
                        val deviceToUpdate = devices.find { it.deviceName == deviceName }
                        deviceToUpdate?.let {
                            it.deviceName = newDeviceName
                            deviceClient.profileDevice.update(it)
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
                        val devices = deviceClient.oathDevice.devices().getOrThrow()
                        val deviceToDelete = devices.find { it.deviceName == deviceName }
                        deviceToDelete?.let {
                            deviceClient.oathDevice.delete(it)
                            // Refresh the device list
                            setDeviceType(DeviceType.OATH)
                        }
                    }
                    DeviceType.PUSH -> {
                        val devices = deviceClient.pushDevice.devices().getOrThrow()
                        val deviceToDelete = devices.find { it.deviceName == deviceName }
                        deviceToDelete?.let {
                            deviceClient.pushDevice.delete(it)
                            setDeviceType(DeviceType.PUSH)
                        }
                    }
                    DeviceType.BOUND -> {
                        val devices = deviceClient.boundDevice.devices().getOrThrow()
                        val deviceToDelete = devices.find { it.deviceName == deviceName }
                        deviceToDelete?.let {
                            deviceClient.boundDevice.delete(it)
                            setDeviceType(DeviceType.BOUND)
                        }
                    }
                    DeviceType.WEBAUTHN -> {
                        val devices = deviceClient.webAuthnDevice.devices().getOrThrow()
                        val deviceToDelete = devices.find { it.deviceName == deviceName }
                        deviceToDelete?.let {
                            deviceClient.webAuthnDevice.delete(it)
                            setDeviceType(DeviceType.WEBAUTHN)
                        }
                    }
                    DeviceType.PROFILE -> {
                        val devices = deviceClient.profileDevice.devices().getOrThrow()
                        val deviceToDelete = devices.find { it.deviceName == deviceName }
                        deviceToDelete?.let {
                            deviceClient.profileDevice.delete(it)
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
        return DeviceClient {
            ssoTokenString = user.session().value
            serverUrl = URL("https://openam-sdks.forgeblocks.com/am")
            realm = user.session().realm
            cookieName = "5421aeddf91aa20"
        }
    }
}

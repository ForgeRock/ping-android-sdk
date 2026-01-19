/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.samples.pingsampleapp.devicemanagement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pingidentity.device.client.DeviceClient
import com.pingidentity.journey.session
import com.pingidentity.journey.user
import com.pingidentity.logger.Logger
import com.pingidentity.logger.STANDARD
import com.pingidentity.samples.pingsampleapp.config.journey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import java.net.URL

data class DeviceManagementViewState(
    var deviceList: List<String> = emptyList(),
    var selectedDeviceType: DeviceType = DeviceType.OATH,
    var isLoading: Boolean = false,
    var showEditDialog: Boolean = false,
    var deviceToEdit: String? = null,
    var newDeviceName: String = ""
)

enum class DeviceType {
    OATH,
    PUSH,
    BOUND,
    WEBAUTHN,
    PROFILE
}

class DeviceManagementViewModel(
    private val logger: Logger = Logger.STANDARD
): ViewModel() {
    var state = MutableStateFlow(DeviceManagementViewState())
        private set

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
                logger.e(exception.message ?: "Error retrieving devices", exception)
            }
        }
    }

    fun onEditDevice(deviceName: String, newDeviceName: String) {
        viewModelScope.launch {
            val deviceClient = buildDeviceClient() ?: return@launch
            when (state.value.selectedDeviceType) {
                DeviceType.OATH -> {
                    val devices = deviceClient.oathDevice.devices().getOrThrow()
                    val deviceToUpdate = devices.find { it.deviceName == deviceName }
                    deviceToUpdate?.let {
                        it.deviceName = newDeviceName
                        deviceClient.oathDevice.update(it)
                            .onSuccess {
                                logger.i("Device updated successfully")
                                setDeviceType(DeviceType.OATH)
                            }
                            .onFailure { exception ->
                                logger.e("Error editing device: ${exception.message}", exception)
                                // Optionally refresh the list to ensure consistency
                                setDeviceType(state.value.selectedDeviceType)
                            }
                    }
                }
                DeviceType.PUSH -> {
                    val devices = deviceClient.pushDevice.devices().getOrThrow()
                    val deviceToUpdate = devices.find { it.deviceName == deviceName }
                    deviceToUpdate?.let {
                        it.deviceName = newDeviceName
                        deviceClient.pushDevice.update(it)
                            .onSuccess {
                                logger.i("Device updated successfully")
                                setDeviceType(DeviceType.PUSH)
                            }
                            .onFailure { exception ->
                                logger.e("Error editing device: ${exception.message}", exception)
                                // Optionally refresh the list to ensure consistency
                                setDeviceType(state.value.selectedDeviceType)
                            }
                    }
                }
                DeviceType.BOUND -> {
                    val devices = deviceClient.boundDevice.devices().getOrThrow()
                    val deviceToUpdate = devices.find { it.deviceName == deviceName }
                    deviceToUpdate?.let {
                        it.deviceName = newDeviceName
                        deviceClient.boundDevice.update(it)
                            .onSuccess {
                                logger.i("Device updated successfully")
                                setDeviceType(DeviceType.BOUND)
                            }
                            .onFailure { exception ->
                                logger.e("Error editing device: ${exception.message}")
                                // Optionally refresh the list to ensure consistency
                                setDeviceType(state.value.selectedDeviceType)
                            }
                    }
                }
                DeviceType.WEBAUTHN -> {
                    val devices = deviceClient.webAuthnDevice.devices().getOrThrow()
                    val deviceToUpdate = devices.find { it.deviceName == deviceName }
                    deviceToUpdate?.let {
                        it.deviceName = newDeviceName
                        deviceClient.webAuthnDevice.update(it)
                            .onSuccess {
                                logger.i("Device updated successfully")
                                setDeviceType(DeviceType.WEBAUTHN)
                            }
                            .onFailure { exception ->
                                logger.e("Error editing device: ${exception.message}", exception)
                                // Optionally refresh the list to ensure consistency
                                setDeviceType(state.value.selectedDeviceType)
                            }
                    }
                }
                DeviceType.PROFILE -> {
                    val devices = deviceClient.profileDevice.devices().getOrThrow()
                    val deviceToUpdate = devices.find { it.deviceName == deviceName }
                    deviceToUpdate?.let {
                        it.deviceName = newDeviceName
                        deviceClient.profileDevice.update(it)
                            .onSuccess {
                                logger.i("Device updated successfully")
                                setDeviceType(DeviceType.PROFILE)
                            }
                            .onFailure { exception ->
                                logger.e("Error editing device: ${exception.message}", exception)
                                // Optionally refresh the list to ensure consistency
                                setDeviceType(state.value.selectedDeviceType)
                            }
                    }
                }
            }
        }
    }

    fun onDeleteDevice(deviceName: String) {
        viewModelScope.launch {
            val deviceClient = buildDeviceClient() ?: return@launch
            when (state.value.selectedDeviceType) {
                DeviceType.OATH -> {
                    val devices = deviceClient.oathDevice.devices().getOrThrow()
                    val deviceToDelete = devices.find { it.deviceName == deviceName }
                    deviceToDelete?.let {
                        deviceClient.oathDevice.delete(it)
                            .onSuccess {
                                logger.i("Device deleted successfully")
                                // Refresh the device list
                                setDeviceType(DeviceType.OATH)
                            }
                            .onFailure { exception ->
                                logger.e("Error deleting device: ${exception.message}", exception)
                                // Optionally refresh the list to ensure consistency
                                setDeviceType(state.value.selectedDeviceType)
                            }
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
                            .onSuccess {
                                logger.i("Device deleted successfully")
                                // Refresh the device list
                                setDeviceType(DeviceType.BOUND)
                            }
                            .onFailure { exception ->
                                logger.e("Error deleting device: ${exception.message}", exception)
                                // Optionally refresh the list to ensure consistency
                                setDeviceType(state.value.selectedDeviceType)
                            }
                    }
                }
                DeviceType.WEBAUTHN -> {
                    val devices = deviceClient.webAuthnDevice.devices().getOrThrow()
                    val deviceToDelete = devices.find { it.deviceName == deviceName }
                    deviceToDelete?.let {
                        deviceClient.webAuthnDevice.delete(it)
                            .onSuccess {
                                logger.i("Device deleted successfully")
                                // Refresh the device list
                                setDeviceType(DeviceType.WEBAUTHN)
                            }
                            .onFailure { exception ->
                                logger.e("Error deleting device: ${exception.message}", exception)
                                // Optionally refresh the list to ensure consistency
                                setDeviceType(state.value.selectedDeviceType)
                            }
                    }
                }
                DeviceType.PROFILE -> {
                    val devices = deviceClient.profileDevice.devices().getOrThrow()
                    val deviceToDelete = devices.find { it.deviceName == deviceName }
                    deviceToDelete?.let {
                        deviceClient.profileDevice.delete(it)
                            .onSuccess {
                                logger.i("Device deleted successfully")
                                // Refresh the device list
                                setDeviceType(DeviceType.PROFILE)
                            }
                            .onFailure { exception ->
                                logger.e("Error deleting device: ${exception.message}", exception)
                                // Optionally refresh the list to ensure consistency
                                setDeviceType(state.value.selectedDeviceType)
                            }
                    }
                }
            }
            try {
            } catch (exception: Exception) {
                logger.e("Error deleting device: ${exception.message}", exception)

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
            logger = Logger.STANDARD
        }
    }

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun factory(): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return DeviceManagementViewModel() as T
                }
            }
        }
    }
}
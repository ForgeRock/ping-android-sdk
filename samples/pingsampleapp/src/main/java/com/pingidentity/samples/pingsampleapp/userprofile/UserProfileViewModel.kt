/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.samples.pingsampleapp.userprofile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pingidentity.journey.user
import com.pingidentity.oidc.OidcError
import com.pingidentity.samples.pingsampleapp.config.journey
import com.pingidentity.utils.Result
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

data class UserProfileViewState(
    var user: JsonObject? = null,
    var error: OidcError? = null,
    var showRawUserInfo: Boolean = false,
)

class UserProfileViewModel : ViewModel() {

    private val json: Json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    var state = MutableStateFlow(UserProfileViewState())
        private set

    val formattedUserInfo: String
        get() = state.value.user?.let {
            json.encodeToString(JsonObject.serializer(), it)
        } ?: state.value.error?.toString() ?: "No user information available"

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
            s.copy(showRawUserInfo = !s.showRawUserInfo)
        }
    }

    companion object {
        fun factory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return UserProfileViewModel() as T
            }
        }
    }
}

/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.samples.pingsampleapp.userprofile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pingidentity.journey.user as journeyUser
import com.pingidentity.davinci.user as davinciUser
import com.pingidentity.oidc.OidcError
import com.pingidentity.samples.pingsampleapp.config.daVinci
import com.pingidentity.samples.pingsampleapp.config.journey
import com.pingidentity.samples.pingsampleapp.config.web
import com.pingidentity.utils.Result
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

enum class UserProfileType {
    JOURNEY,
    DAVINCI,
    OIDC
}

data class UserProfileViewState(
    var selectedTab: UserProfileType = UserProfileType.JOURNEY,
    var journeyUser: JsonObject? = null,
    var journeyError: OidcError? = null,
    var daVinciUser: JsonObject? = null,
    var daVinciError: OidcError? = null,
    var oidcUser: JsonObject? = null,
    var oidcError: OidcError? = null,
    var showRawJourneyUserInfo: Boolean = false,
    var showRawDaVinciUserInfo: Boolean = false,
    var showRawOidcUserInfo: Boolean = false,
)

class UserProfileViewModel : ViewModel() {

    private val json: Json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    var state = MutableStateFlow(UserProfileViewState())
        private set

    val formattedJourneyUserInfo: String
        get() = state.value.journeyUser?.let {
            json.encodeToString(JsonObject.serializer(), it)
        } ?: state.value.journeyError?.toString() ?: "No user information available"

    val formattedDaVinciUserInfo: String
        get() = state.value.daVinciUser?.let {
            json.encodeToString(JsonObject.serializer(), it)
        } ?: state.value.daVinciError?.toString() ?: "No user information available"

    val formattedOidcUserInfo: String
        get() = state.value.oidcUser?.let {
            json.encodeToString(JsonObject.serializer(), it)
        } ?: state.value.oidcError?.toString() ?: "No user information available"

    fun selectTab(tabType: UserProfileType) {
        state.update { it.copy(selectedTab = tabType) }
    }

    fun userinfo() {
        // Load user info for ALL authentication types to ensure all sessions are displayed
        journeyUserInfo()
        daVinciUserInfo()
        oidcUserInfo()
    }

    fun toggleUserInfo() {
        when (state.value.selectedTab) {
            UserProfileType.JOURNEY -> toggleJourneyUserInfo()
            UserProfileType.DAVINCI -> toggleDaVinciUserInfo()
            UserProfileType.OIDC -> toggleOidcUserInfo()
        }
    }

    // Journey Operations
    private fun journeyUserInfo() {
        viewModelScope.launch {
            journey.journeyUser()?.let { user ->
                when (val result = user.userinfo(false)) {
                    is Result.Failure ->
                        state.update { s ->
                            s.copy(journeyUser = null, journeyError = result.value)
                        }

                    is Result.Success -> {
                        state.update { s ->
                            s.copy(journeyUser = result.value, journeyError = null)
                        }
                    }
                }
            }
        }
    }

    private fun toggleJourneyUserInfo() {
        state.update { s ->
            s.copy(showRawJourneyUserInfo = !s.showRawJourneyUserInfo)
        }
    }

    // DaVinci Operations
    private fun daVinciUserInfo() {
        viewModelScope.launch {
            daVinci?.davinciUser()?.let { user ->
                when (val result = user.userinfo(false)) {
                    is Result.Failure ->
                        state.update { s ->
                            s.copy(daVinciUser = null, daVinciError = result.value)
                        }

                    is Result.Success -> {
                        state.update { s ->
                            s.copy(daVinciUser = result.value, daVinciError = null)
                        }
                    }
                }
            }
        }
    }

    private fun toggleDaVinciUserInfo() {
        state.update { s ->
            s.copy(showRawDaVinciUserInfo = !s.showRawDaVinciUserInfo)
        }
    }

    // OIDC Operations
    private fun oidcUserInfo() {
        viewModelScope.launch {
            web?.user()?.let { user ->
                when (val result = user.userinfo(false)) {
                    is Result.Failure ->
                        state.update { s ->
                            s.copy(oidcUser = null, oidcError = result.value)
                        }

                    is Result.Success -> {
                        state.update { s ->
                            s.copy(oidcUser = result.value, oidcError = null)
                        }
                    }
                }
            }
        }
    }

    private fun toggleOidcUserInfo() {
        state.update { s ->
            s.copy(showRawOidcUserInfo = !s.showRawOidcUserInfo)
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

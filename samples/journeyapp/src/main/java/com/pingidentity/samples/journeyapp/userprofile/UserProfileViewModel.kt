/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.samples.journeyapp.userprofile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pingidentity.journey.user
import com.pingidentity.samples.journeyapp.journey.journey
import com.pingidentity.utils.Result
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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

                    is Result.Success ->
                        state.update { s ->
                            s.copy(user = result.value, error = null)
                        }
                }
            }
        }
    }
}

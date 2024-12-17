/*
 * Copyright (c) 2024 Ping Identity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.samples.app.centralize

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pingidentity.samples.app.Mode
import com.pingidentity.samples.app.User
import com.pingidentity.samples.app.env.oidcClient
import com.pingidentity.utils.Result
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CentralizeLoginViewModel : ViewModel() {
    var state = MutableStateFlow(CentralizeState())
        private set

    fun login() {
        viewModelScope.launch {
            User.current(Mode.CENTRALIZE)
            when (val result = oidcClient.token()) {
                is Result.Failure -> {
                    state.update {
                        it.copy(token = null, error = result.value)
                    }
                }

                is Result.Success -> {
                    state.update {
                        it.copy(token = result.value, error = null)
                    }
                }
            }
        }
    }

    fun reset() {
        state.update {
            it.copy(null, null)
        }
    }
}

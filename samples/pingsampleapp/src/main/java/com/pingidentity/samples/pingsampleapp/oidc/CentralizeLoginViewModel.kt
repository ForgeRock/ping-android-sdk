/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.samples.pingsampleapp.oidc

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pingidentity.samples.pingsampleapp.config.web
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CentralizeLoginViewModel: ViewModel() {
    var state = MutableStateFlow(CentralizeState())
        private set

    fun login() {
        if (web == null) {
            state.update {
                it.copy(error = Exception("Select OiDC from Configuration"))
            }
            return
        }
        viewModelScope.launch {
            web!!.authorize {
                // no config
            }.onSuccess { user ->
                state.update {
                    it.copy(user = user, error = null)
                }
            }.onFailure { throwable ->
                state.update {
                    it.copy(user = null, error = throwable)
                }
            }
        }
    }
}
/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.pingonemfapp.davinci

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pingidentity.davinci.module.id
import com.pingidentity.orchestrate.ContinueNode
import com.pingidentity.pingonemfapp.config.daVinci
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for driving the DaVinci flow in the PingOne MFA sample app.
 * It owns the current DaVinci node state, exposes loading state to the UI,
 * and advances the flow as the user submits each step.
 */
class DaVinciViewModel : ViewModel() {

    var state = MutableStateFlow(DaVinciState())
        private set

    var loading = MutableStateFlow(false)
        private set

    init {
        start()
    }

    /**
     * Advances the DaVinci flow from the provided [ContinueNode].
     */
    fun next(current: ContinueNode) {
        loading.update {
            true
        }
        viewModelScope.launch {
            val next = current.next()
            state.update {
                it.copy(node = next, counter = it.counter + 1)
            }
            loading.update {
                false
            }
        }
    }

    /**
     * Starts the DaVinci flow from the configured environment.
     */
    fun start() {
        loading.update {
            true
        }
        viewModelScope.launch {
            val next = daVinci.start()
            state.update {
                it.copy(node = next, counter = it.counter + 1)
            }
            loading.update {
                false
            }
        }
    }

    /**
     * Triggers UI recomposition without changing the current DaVinci node.
     */
    fun refresh() {
        state.update {
            it.copy(node = it.node, counter = it.counter + 1)
        }
    }
}

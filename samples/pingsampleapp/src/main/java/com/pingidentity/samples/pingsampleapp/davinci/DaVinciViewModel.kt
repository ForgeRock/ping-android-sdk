/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.samples.pingsampleapp.davinci

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pingidentity.orchestrate.ContinueNode
import com.pingidentity.samples.pingsampleapp.config.daVinci
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DaVinciViewModel: ViewModel() {
    var state = MutableStateFlow(DaVinciState())
        private set

    var loading = MutableStateFlow(false)
        private set

    init {
        start()
    }

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

    fun start() {
        if (daVinci == null) {
            state.update {
                it.copy(node = null, error = "DaVinci not configured")
            }
            return
        }
        loading.update {
            true
        }
        viewModelScope.launch {
            val next = daVinci?.start()

            state.update {
                it.copy(node = next, counter = it.counter + 1)
            }
            loading.update {
                false
            }
        }
    }

    fun refresh() {
        state.update {
            it.copy(node = it.node, counter = it.counter + 1)
        }
    }
}
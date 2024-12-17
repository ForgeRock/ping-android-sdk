/*
 * Copyright (c) 2024 Ping Identity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.samples.app.davinci

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pingidentity.orchestrate.ContinueNode
import com.pingidentity.samples.app.Mode
import com.pingidentity.samples.app.User
import com.pingidentity.samples.app.env.daVinci
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Use DataStore to store the AccessToken
//val Context.dataStore: androidx.datastore.core.DataStore<AccessToken?> by dataStore("test", DataStoreSerializer())
//val dataStore = DataStoreStorage(ContextProvider.context.dataStore)

class DaVinciViewModel : ViewModel() {
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
                it.copy(prev = current, node = next)
            }
            loading.update {
                false
            }
        }
    }

    fun start() {
        loading.update {
            true
        }
        viewModelScope.launch {
            User.current(Mode.DAVINCI)

            val next = daVinci.start()

            state.update {
                it.copy(prev = next, node = next)
            }
            loading.update {
                false
            }
        }
    }

    fun refresh() {
        state.update {
            it.copy(prev = it.prev, node = it.node)
        }
    }
}

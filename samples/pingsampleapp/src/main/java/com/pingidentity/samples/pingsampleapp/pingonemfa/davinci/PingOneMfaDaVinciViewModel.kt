/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.samples.pingsampleapp.pingonemfa.davinci

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pingidentity.orchestrate.ContinueNode
import com.pingidentity.samples.pingsampleapp.config.pingOneMfaDaVinci
import com.pingidentity.samples.pingsampleapp.davinci.DaVinciState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch


/**
 * ViewModel that drives the PingOne MFA DaVinci pairing flow.
 *
 * Uses the dedicated [pingOneMfaDaVinci] instance auto-built at startup from the
 * `assets/pingone-mfa.json` file — independent of any DaVinci config the user selects
 * in the Configuration screen.
 */
class PingOneMfaDaVinciViewModel : ViewModel() {

    var state = MutableStateFlow(DaVinciState())
        private set

    var loading = MutableStateFlow(false)
        private set

    /**
     * Becomes true once the pairing result has been submitted to the DaVinci server.
     * Navigation observes this to pop the screen immediately — there is no second step in
     * this flow regardless of what node the server returns.
     */
    var pairingComplete = MutableStateFlow(false)
        private set

    init {
        start()
    }

    fun next(current: ContinueNode) {
        viewModelScope.launch {
            try {
                current.next() // submit pairing result; server response is intentionally ignored
                pairingComplete.update { true }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state.update { it.copy(node = null, error = e.message ?: "An unexpected error occurred") }
            }
        }
    }

    fun start() {
        if (pingOneMfaDaVinci == null) {
            state.update {
                it.copy(node = null, error = "PingOne MFA DaVinci not configured")
            }
            return
        }
        loading.update { true }
        viewModelScope.launch {
            try {
                val next = pingOneMfaDaVinci?.start()
                state.update { it.copy(node = next, counter = it.counter + 1) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state.update { it.copy(node = null, error = e.message ?: "An unexpected error occurred") }
            } finally {
                loading.update { false }
            }
        }
    }

    fun refresh() {
        state.update { it.copy(node = it.node, counter = it.counter + 1) }
    }
}

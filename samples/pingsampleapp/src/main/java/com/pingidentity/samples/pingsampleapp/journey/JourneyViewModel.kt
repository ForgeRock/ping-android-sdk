/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.samples.pingsampleapp.journey

import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pingidentity.journey.start
import com.pingidentity.oidc.module.VERIFICATION_URI_COMPLETE
import com.pingidentity.orchestrate.ContinueNode
import com.pingidentity.samples.pingsampleapp.config.journey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class JourneyViewModel(
    private val journeyName: String? = null,
    private val verificationUri: String? = null,
    private val backChannelAuthorizationUri: String? = null,
) : ViewModel() {
    var state = MutableStateFlow(JourneyState())
        private set

    var loading = MutableStateFlow(false)
        private set

    init {
        start()
    }

    fun start() {
        loading.update { true }
        viewModelScope.launch {
            val next = when {
                !verificationUri.isNullOrBlank() ->
                    journey?.start(requireJourneyName()) {
                        VERIFICATION_URI_COMPLETE to verificationUri.toUri()
                    }

                !backChannelAuthorizationUri.isNullOrBlank() ->
                    journey?.start(backchannelUri = backChannelAuthorizationUri.toUri())

                else -> journey?.start(requireJourneyName())
            }
            state.update { it.copy(node = next) }
            loading.update { false }
        }
    }

    fun next(node: ContinueNode) {
        loading.update {
            true
        }
        viewModelScope.launch {
            val next = node.next()
            state.update {
                it.copy(node = next)
            }
            loading.update {
                false
            }
        }
    }

    private fun requireJourneyName(): String =
        checkNotNull(journeyName) { "journeyName is required for a named journey start" }

    fun refresh() {
        state.update {
            it.copy(node = it.node, counter = it.counter + 1)
        }
    }

    companion object {
        fun factory(journeyName: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    JourneyViewModel(journeyName) as T
            }

        fun factory(journeyName: String, verificationUri: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    JourneyViewModel(journeyName, verificationUri) as T
            }

        fun factoryForBackchannel(backchannelUri: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    JourneyViewModel(
                        backChannelAuthorizationUri = backchannelUri,
                    ) as T
            }
    }
}
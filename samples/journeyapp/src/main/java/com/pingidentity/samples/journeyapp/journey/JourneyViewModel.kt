/*
 * Copyright (c) 2024 Ping Identity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.samples.journeyapp.journey

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pingidentity.journey.Journey
import com.pingidentity.journey.module.Oidc
import com.pingidentity.journey.start
import com.pingidentity.logger.Logger
import com.pingidentity.logger.STANDARD
import com.pingidentity.orchestrate.ContinueNode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

val forgeblock =  Journey {
    logger = Logger.STANDARD

    serverUrl = "https://openam-sdks.forgeblocks.com/am"
    realm = "alpha"
    cookie = "5421aeddf91aa20"
    forceAuth = true
    // Oidc as module
    module(Oidc) {
        clientId = "AndroidTest"
        discoveryEndpoint =
            "https://openam-sdks.forgeblocks.com/am/oauth2/alpha/.well-known/openid-configuration"
        scopes = mutableSetOf("openid", "email", "address", "profile", "phone")
        redirectUri = "org.forgerock.demo:/oauth2redirect"
        //storage = dataStore
    }
}

val localhost =  Journey {
    logger = Logger.STANDARD

    serverUrl = "http://192.168.86.32:8080/openam"
    realm = "root"
    // Oidc as module
    module(Oidc) {
        clientId = "AndroidTest"
        discoveryEndpoint =
            "http://192.168.86.32:8080/openam/oauth2/.well-known/openid-configuration"
        scopes = mutableSetOf("openid", "email", "address", "profile", "phone")
        redirectUri = "org.forgerock.demo:/oauth2redirect"
        //storage = dataStore
    }
}


val journey = forgeblock

class JourneyViewModel(private var journeyName: String) : ViewModel() {

    var state = MutableStateFlow(JourneyState())
        private set

    var loading = MutableStateFlow(false)
        private set

    init {
        start()
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

    fun start() {

        loading.update {
            true
        }
        viewModelScope.launch {
            val next = journey.start(journeyName)

            state.update {
                it.copy(node = next)
            }
            loading.update {
                false
            }
        }
    }

    fun refresh() {
        state.update {
            it.copy(node = it.node)
        }
    }

    companion object {
        fun factory(
            journeyName: String,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return JourneyViewModel(journeyName) as T
            }
        }
    }
}

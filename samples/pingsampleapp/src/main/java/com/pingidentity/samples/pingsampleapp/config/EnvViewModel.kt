/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.samples.pingsampleapp.config

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class EnvViewModel : ViewModel() {
    val configurations: StateFlow<List<Configuration>> = ConfigurationManager.configurations
    val selections: StateFlow<Map<ConfigType, Configuration>> = ConfigurationManager.selections

    fun select(config: Configuration) {
        viewModelScope.launch { ConfigurationManager.select(config) }
    }

    fun add(config: Configuration) {
        viewModelScope.launch { ConfigurationManager.add(config) }
    }

    fun update(oldName: String, config: Configuration) {
        viewModelScope.launch { ConfigurationManager.update(oldName, config) }
    }

    fun delete(config: Configuration) {
        viewModelScope.launch { ConfigurationManager.delete(config) }
    }
}

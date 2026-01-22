/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.samples.pingsampleapp.journey

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class PreferenceViewModel(private val preferenceRepository: PreferenceRepository): ViewModel() {
    fun getLastJourney(): String {
        return preferenceRepository.getLastJourney()
    }

    fun saveJourney(journeyName: String) {
        preferenceRepository.saveJourney(journeyName)
    }

    companion object {
        fun factory(
            context: Context,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val sharedPreferences = context.applicationContext.getSharedPreferences(
                    "JourneyPreferences",
                    Context.MODE_PRIVATE,
                )
                return PreferenceViewModel(
                    PreferenceRepository(sharedPreferences)
                ) as T
            }
        }
    }
}
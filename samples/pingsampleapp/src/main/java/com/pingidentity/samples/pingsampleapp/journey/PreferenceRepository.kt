/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.samples.pingsampleapp.journey

import android.content.SharedPreferences
import androidx.core.content.edit

class PreferenceRepository(private val sharedPreferences: SharedPreferences) {

    fun saveJourney(journeyName: String) {
        sharedPreferences.edit().putString("lastJourney", journeyName).apply()
    }

    fun getLastJourney(): String {
        return sharedPreferences.getString("lastJourney", "Login")!!
    }
}
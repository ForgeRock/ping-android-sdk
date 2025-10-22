/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.binding.migration

import android.content.Context
import androidx.startup.Initializer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Initializer to trigger device binding migration during app startup.
 */
class BindingMigrationInitializer : Initializer<Unit> {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun create(context: Context) {
        // Launch migration in a coroutine scope
        scope.launch {
            try {
                BindingMigration.start(context)
            } catch (e: Exception) {
                //Ignore migration errors
                BindingMigration.logger.e("Migration failed", e)
            }
        }
    }

    override fun dependencies(): List<Class<out Initializer<*>>> {
        // No dependencies
        return emptyList()
    }
}

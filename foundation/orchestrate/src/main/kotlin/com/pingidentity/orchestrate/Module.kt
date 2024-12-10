/*
 * Copyright (c) 2024 Ping Identity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.orchestrate

/**
 * Interface for a Module. A Module represents a unit of functionality in the application.
 * @property config A function that returns the configuration for the module.
 * @property setup A function that sets up the module.
 */
interface Module<ModuleConfig : Any> {
    val config: () -> ModuleConfig
    val setup: Setup<ModuleConfig>.() -> Unit

    companion object {
        /**
         * Constructs a module with no config.
         * @param setup A function that sets up the module.
         * @return A Module with no config.
         */
        fun of(setup: Setup<Unit>.() -> Unit): Module<Unit> {
            return of({}, setup)
        }

        /**
         * Constructs a module with config.
         * @param config A function that returns the configuration for the module.
         * @param setup A function that sets up the module.
         * @return A Module with the provided config.
         */
        fun <T : Any> of(
            config: () -> T,
            setup: Setup<T>.() -> Unit,
        ): Module<T> {
            return object : Module<T> {
                override val config: () -> T = config
                override val setup: Setup<T>.() -> Unit = setup
            }
        }
    }
}

/**
 * Delegate for a module, which allows for the same module to be
 * registered multiple times to the workflow.
 * @property module The module to be delegated.
 */
internal class ModuleDelegate<ModuleConfig : Any>(private val module: Module<ModuleConfig>) :
    Module<ModuleConfig> by module
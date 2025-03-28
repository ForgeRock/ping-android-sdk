/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.orchestrate

import androidx.annotation.VisibleForTesting

/**
 * Class for a ModuleRegistry. A ModuleRegistry represents a registry of modules in the application.
 * @param module The module to be registered.
 * @param priority The priority of the module in the registry.
 * @param config The configuration for the module.
 * @param setup A function that sets up the module.
 */
@VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
class ModuleRegistry<Config : Any> internal constructor(
    val module: Module<Config>,
    val priority: Int,
    val config: Config,
    // Function to setup the module
    private val setup: Setup<Config>.() -> Unit,
) : Comparable<ModuleRegistry<Config>> {
    /**
     * Registers the module to the workflow.
     * @param workflow The workflow to which the module is registered.
     */
    internal fun register(workflow: Workflow) {
        Setup(workflow = workflow, config = config).apply(setup)
    }

    /**
     * Compares this ModuleRegistry with another based on priority.
     * @param other The other ModuleRegistry to compare with.
     * @return A negative integer, zero, or a positive integer as this ModuleRegistry is less than, equal to, or greater than the specified ModuleRegistry.
     */
    override fun compareTo(other: ModuleRegistry<Config>): Int {
        return priority.compareTo(other.priority)
    }
}
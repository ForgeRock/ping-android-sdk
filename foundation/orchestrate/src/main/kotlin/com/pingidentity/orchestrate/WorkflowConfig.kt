/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.orchestrate

import androidx.annotation.VisibleForTesting
import com.pingidentity.logger.Logger
import com.pingidentity.network.HttpClient
import com.pingidentity.network.ktor.HttpClient
import com.pingidentity.utils.PingDsl
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.toDuration

/**
 * Enum class representing the mode of module override.
 */
enum class OverrideMode {
    OVERRIDE, // Override the previous registered module
    APPEND, // Append to the list, and cannot be overridden
    IGNORE // Ignore if the module is already registered
}

/**
 * DSL class for configuring a Workflow.
 */
@PingDsl
open class WorkflowConfig {
    // Use a map instead of a list, so that we can override the registered module
    // But when using a map, what if I want to register a module twice? for example inject customHeader
    @VisibleForTesting
    val modules = mutableListOf<ModuleRegistry<*>>()

    // Timeout for the HTTP client, default is 15 seconds
    var timeout: Long = 15.seconds.inWholeMilliseconds

    // Logger for the log, default is None
    var logger = Logger.logger

    // HTTP client for the engine
    lateinit var httpClient: HttpClient

    /**
     * Register a module.
     *
     * @param module The module to be registered.
     * @param priority The priority of the module in the registry. Default is 10.
     * @param mode The mode of the module registration. Default is OVERRIDE. If the mode is OVERRIDE, the module will be overridden if it is already registered.
     * @param config The configuration for the module.
     */
    fun <Config : Any> module(
        module: Module<Config>,
        priority: Int = 10,
        mode: OverrideMode = OverrideMode.OVERRIDE,
        config: Config.() -> Unit = {},
    ) {

        when (mode) {
            OverrideMode.OVERRIDE -> {
                // For override, we need to replace the module if it is already registered
                // and retain the order and priority of the modules
                modules.indexOfFirst { it.module == module }.let {
                    if (it != -1) {
                        modules[it] = ModuleRegistry(
                            module,
                            modules[it].priority, // Retain the priority
                            module.config().apply(config),
                            module.setup
                        )
                    } else {
                        modules.add(
                            ModuleRegistry(
                                module,
                                priority,
                                module.config().apply(config),
                                module.setup
                            )
                        )
                    }
                }
            }

            OverrideMode.APPEND -> {
                modules.add(
                    ModuleRegistry(
                        ModuleDelegate(module),
                        priority,
                        module.config().apply(config),
                        module.setup
                    )
                )
            }

            OverrideMode.IGNORE -> {
                if (modules.none { it.module == module }) {
                    modules.add(
                        ModuleRegistry(
                            module,
                            priority,
                            module.config().apply(config),
                            module.setup
                        )
                    )
                }
            }
        }
    }

    /**
     * Registers the workflow and initializes the HTTP client if not already initialized.
     *
     * @param workflow The workflow to be registered.
     */
    internal fun register(workflow: Workflow) {
        if (!::httpClient.isInitialized) {
            httpClient = HttpClient {
                timeout = workflow.config.timeout.toDuration(DurationUnit.MILLISECONDS)
                logger = workflow.config.logger
            }
        }
        modules.sortBy { it.priority }
        modules.forEach { it.register(workflow) }
    }
}
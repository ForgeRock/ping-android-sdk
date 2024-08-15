/*
 * Copyright (c) 2024. PingIdentity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci

import com.pingidentity.android.ModuleInitializer
import com.pingidentity.davinci.collector.FlowCollector
import com.pingidentity.davinci.collector.PasswordCollector
import com.pingidentity.davinci.collector.SubmitCollector
import com.pingidentity.davinci.collector.TextCollector
import com.pingidentity.davinci.plugin.CollectorFactory

/**
 * The CollectorRegistry class is responsible for registering collectors in the application.
 * It extends the ModuleInitializer class, which means it is part of the initialization process of the application.
 */
internal class CollectorRegistry : ModuleInitializer() {

    /**
     * This function is called during the initialization process of the application.
     * It registers four types of collectors: TextCollector, PasswordCollector, SubmitCollector, and FlowCollector.
     * Each collector is registered with a string identifier and a creation function.
     */
    override fun initialize() {
        // Register TextCollector with the CollectorFactory
        CollectorFactory.register("TEXT", ::TextCollector)

        // Register PasswordCollector with the CollectorFactory
        CollectorFactory.register("PASSWORD", ::PasswordCollector)

        // Register SubmitCollector with the CollectorFactory
        CollectorFactory.register("SUBMIT_BUTTON", ::SubmitCollector)

        // Register FlowCollector with the CollectorFactory
        CollectorFactory.register("FLOW_BUTTON", ::FlowCollector)
    }
}
/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.root

import com.pingidentity.android.ContextProvider
import com.pingidentity.device.root.detector.BuildTagsDetector
import com.pingidentity.device.root.detector.BusyBoxProgramFileDetector
import com.pingidentity.device.root.detector.DangerousPropertyDetector
import com.pingidentity.device.root.detector.NativeDetector
import com.pingidentity.device.root.detector.PermissionDetector
import com.pingidentity.device.root.detector.RootApkDetector
import com.pingidentity.device.root.detector.RootAppDetector
import com.pingidentity.device.root.detector.RootCloakingAppDetector
import com.pingidentity.device.root.detector.RootDetector
import com.pingidentity.device.root.detector.RootProgramFileDetector
import com.pingidentity.device.root.detector.RootRequiredAppDetector
import com.pingidentity.device.root.detector.SuCommandDetector
import com.pingidentity.utils.PingDsl
import kotlin.math.max

internal fun DefaultRootDetector(): MutableList<RootDetector>.() -> Unit = {
    add(BuildTagsDetector)
    add(BusyBoxProgramFileDetector())
    add(DangerousPropertyDetector())
    add(NativeDetector())
    add(PermissionDetector())
    add(RootApkDetector())
    add(RootAppDetector())
    add(RootRequiredAppDetector())
    add(RootCloakingAppDetector())
    add(RootProgramFileDetector())
    add(SuCommandDetector())
}

@PingDsl
class DeviceRootConfig {
    internal val rootDetectors: MutableList<RootDetector> = mutableListOf()

    fun detector(block: MutableList<RootDetector>.() -> Unit) {
        rootDetectors.apply(block)
    }
}

suspend fun scan(
    block: DeviceRootConfig.() -> Unit = { detector(DefaultRootDetector()) }
): Double {
    val config = DeviceRootConfig().apply(block)
    val detectors = config.rootDetectors
    var max = 0.0
    for (detector in detectors) {
        max = max(max, detector.isRooted(ContextProvider.context))
        if (max >= 1) {
            return max
        }
    }
    return max
}
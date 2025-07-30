/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.root.detector

/**
 * Check the busybox program is installed.
 */
class BusyBoxProgramFileDetector : FileDetector() {

    override fun getFilenames(): List<String> = listOf("busybox")

    companion object {
        private val DETECTOR_NAME_KEY = BusyBoxProgramFileDetector::class.java.simpleName
    }
}
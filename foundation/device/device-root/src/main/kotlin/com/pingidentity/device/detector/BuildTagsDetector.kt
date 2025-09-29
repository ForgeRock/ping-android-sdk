/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.detector

import android.content.Context
import android.os.Build

/**
 * Pre-configured tamper detector that checks Android build tags for signs of tampering.
 *
 * This detector examines the [Build.TAGS] property to determine if the device is running
 * on unofficial or test builds. Official Android releases are signed with release keys,
 * while custom ROMs, developer builds, and other unofficial firmware typically use test keys.
 *
 * The presence of "test-keys" in the build tags indicates:
 * - Custom ROM or modified firmware
 * - Developer/debug builds
 * - Unofficial Android distributions
 * - Potentially compromised system integrity
 *
 * This detector is implemented as a lazy-initialized singleton using the [TamperDetector]
 * factory function for optimal performance and memory usage.
 *
 * Example usage:
 * ```kotlin
 * val isTampered = analyze {
 *     detector {
 *         add(BuildTagsDetector)
 *     }
 * }
 * ```
 *
 * @see Build.TAGS
 * @see TamperDetector
 */
class BuildTagsDetector(
    private val androidBuildTagProvider: AndroidBuildTagProvider = DefaultAndroidBuildTagProvider()
) : TamperDetector {
    override suspend fun analyze(context: Context): Double {
        val buildTags = androidBuildTagProvider.getBuildTags()
        return if (buildTags != null && buildTags.contains(TEST_KEYS)) {
            1.0
        } else {
            0.0
        }
    }
}

/**
 * Constant representing the test key signature found in unofficial Android builds.
 *
 * This string is present in [Build.TAGS] when the Android system has been built
 * using test keys instead of official release keys, indicating a potentially
 * compromised or unofficial firmware.
 */
internal const val TEST_KEYS = "test-keys"

interface AndroidBuildTagProvider {
    fun getBuildTags(): String? = null
}

class DefaultAndroidBuildTagProvider : AndroidBuildTagProvider {
    override fun getBuildTags(): String? = Build.TAGS
}
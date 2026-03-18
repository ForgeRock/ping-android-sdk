/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.samples.pingsampleapp.devtools

import android.content.Context
import android.os.Build
import com.pingidentity.device.root.detector.TamperDetector
import com.pingidentity.logger.Logger
import com.pingidentity.logger.WARN

class CustomEmulatorDetector(
    override var logger: Logger = Logger.WARN
) : TamperDetector {

    override suspend fun analyze(context: Context): Double {
        // 1) Simple Build.* heuristics (fast + readable)
        val fingerprint = Build.FINGERPRINT.orEmpty()
        val model = Build.MODEL.orEmpty()
        val manufacturer = Build.MANUFACTURER.orEmpty()
        val hardware = Build.HARDWARE.orEmpty()
        val product = Build.PRODUCT.orEmpty()

        val buildLooksLikeEmulator =
            fingerprint.startsWith("generic", ignoreCase = true) ||
                    fingerprint.contains("unknown", ignoreCase = true) ||
                    model.contains("google_sdk", ignoreCase = true) ||
                    model.contains("emulator", ignoreCase = true) ||
                    model.contains("Android SDK built for", ignoreCase = true) ||
                    manufacturer.contains("Genymotion", ignoreCase = true) ||
                    hardware.contains("goldfish", ignoreCase = true) ||
                    hardware.contains("ranchu", ignoreCase = true) ||
                    product.contains("sdk", ignoreCase = true) ||
                    product.contains("emulator", ignoreCase = true)

        // 2) Optional: one strong “this is QEMU” signal
        // (If getprop fails, we just treat it as false.)
        val qemu = getProp("ro.kernel.qemu") == "1"

        val isEmulator = buildLooksLikeEmulator || qemu

        if (isEmulator) {
            logger.w(
                "Emulator suspected (build=$buildLooksLikeEmulator, qemu=$qemu) " +
                        "fp=$fingerprint model=$model manuf=$manufacturer hw=$hardware product=$product"
            )
        } else {
            logger.d("Emulator not detected")
        }

        // For testing: return a fixed score when detected
        return if (isEmulator) 1.0 else 0.0
    }

    private fun getProp(key: String): String? =
        runCatching {
            val p = Runtime.getRuntime().exec(arrayOf("getprop", key))
            p.inputStream.bufferedReader().readLine()?.trim()
        }.getOrNull()
}
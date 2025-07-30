package com.pingidentity.device.root

import android.content.Context

interface BaseRootDetector {

    /**
     * Detect the device is rooted.
     *
     * @param context The application context
     * @return 0 - 1 How likely the device is rooted, 0 - not likely, 0.5 - likely, 1 - Very likely
     */
    fun isRooted(context: Context): Double
}
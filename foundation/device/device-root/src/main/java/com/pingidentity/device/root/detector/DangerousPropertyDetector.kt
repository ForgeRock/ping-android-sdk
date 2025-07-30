package com.pingidentity.device.root.detector

/**
 * Check System properties
 */
class DangerousPropertyDetector : SystemPropertyDetector() {
    override fun getProperties(): Map<String, String> {
        return mapOf(
            "ro.debuggable" to "1",
            "ro.secure" to "0",
        )
    }
}
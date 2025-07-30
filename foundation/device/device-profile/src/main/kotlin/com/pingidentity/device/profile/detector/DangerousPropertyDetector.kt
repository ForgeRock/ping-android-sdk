package com.pingidentity.device.profile.detector

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
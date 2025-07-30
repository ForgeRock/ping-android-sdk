package com.pingidentity.device.profile.detector

/**
 * Check common root program exist
 */
class RootProgramFileDetector : FileDetector() {
    override val key: String
        get() = RootProgramFileDetector::class.java.simpleName

    override fun getFilenames(): List<String> = listOf(
        "su",
        "magisk",
    )
}
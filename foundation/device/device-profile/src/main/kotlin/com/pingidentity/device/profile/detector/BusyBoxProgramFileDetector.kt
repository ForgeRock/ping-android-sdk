package com.pingidentity.device.profile.detector

/**
 * Check the busybox program is installed.
 */
class BusyBoxProgramFileDetector : FileDetector() {
    override val key: String
        get() = DETECTOR_NAME_KEY

    override fun getFilenames(): List<String> = listOf("busybox")

    companion object {
        private val DETECTOR_NAME_KEY = BusyBoxProgramFileDetector::class.java.simpleName
    }
}
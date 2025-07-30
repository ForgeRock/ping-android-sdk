package com.pingidentity.device.profile.detector

/**
 * Check su command exists
 */
class SuCommandDetector : CommandDetector() {
    override fun getCommands(): Array<String> {
        return arrayOf("su")
    }

    override val key: String
        get() = SuCommandDetector::class.java.simpleName
}
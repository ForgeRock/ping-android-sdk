package com.pingidentity.device.profile.detector

/**
 * Check if there are well-known root App are installed.
 */
class RootAppDetector : PackageDetector() {
    override val key: String
        get() = RootAppDetector::class.java.simpleName

    override fun getPackages(): List<String> = CURRENT_KNOWN_ROOT_APPS

    companion object {
        private val CURRENT_KNOWN_ROOT_APPS = listOf<String>(
            "com.noshufou.android.su",
            "com.noshufou.android.su.elite",
            "eu.chainfire.supersu",
            "com.koushikdutta.superuser",
            "com.thirdparty.superuser",
            "com.yellowes.su",
            "com.topjohnwu.magisk",
            "com.kingroot.kinguser",
            "com.kingo.root",
            "com.smedialink.oneclickroot",
            "com.zhiqupk.root.global",
            "com.alephzain.framaroot",
        )
    }
}
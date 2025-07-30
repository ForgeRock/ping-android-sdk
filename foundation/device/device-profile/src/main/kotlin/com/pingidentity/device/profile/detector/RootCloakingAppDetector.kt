package com.pingidentity.device.profile.detector

/**
 * Check if there are well-known root cloaking App installed.
 */
class RootCloakingAppDetector : PackageDetector() {
    override val key: String
        get() = RootCloakingAppDetector::class.java.simpleName

    override fun getPackages(): List<String> = CURRENT_ROOT_CLOAKING_APPS

    companion object {
        private val CURRENT_ROOT_CLOAKING_APPS = listOf<String>(
            "com.devadvance.rootcloak",
            "com.devadvance.rootcloakplus",
            "de.robv.android.xposed.installer",
            "com.saurik.substrate",
            "com.zachspong.temprootremovejb",
            "com.amphoras.hidemyroot",
            "com.amphoras.hidemyrootadfree",
            "com.formyhm.hiderootPremium",
            "com.formyhm.hideroot",
        )
    }
}
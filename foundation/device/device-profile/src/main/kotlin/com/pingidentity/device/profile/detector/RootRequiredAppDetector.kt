package com.pingidentity.device.profile.detector

import android.content.Context

/**
 * Check if root required App are installed
 */
class RootRequiredAppDetector : PackageDetector() {
    override val key: String
        get() = RootRequiredAppDetector::class.java.simpleName

    override fun getPackages(): List<String> = CURRENT_KNOWN_APPS_REQUIRE_ROOT

    override fun isRooted(context: Context): Double {
        return if (super.isRooted(context) > 0) {
            0.5
        } else {
            0.0
        }
    }

    companion object {
        private val CURRENT_KNOWN_APPS_REQUIRE_ROOT = listOf<String>(
            "com.koushikdutta.rommanager",
            "com.koushikdutta.rommanager.license",
            "com.dimonvideo.luckypatcher",
            "com.chelpus.lackypatch",
            "com.ramdroid.appquarantine",
            "com.ramdroid.appquarantinepro",
            "com.android.vending.billing.InAppBillingService.COIN",
            "com.android.vending.billing.InAppBillingService.LUCK",
            "com.chelpus.luckypatcher",
            "com.blackmartalpha",
            "org.blackmart.market",
            "com.allinone.free",
            "com.repodroid.app",
            "org.creeplays.hack",
            "com.baseappfull.fwd",
            "com.zmapp",
            "com.dv.marketmod.installer",
            "org.mobilism.android",
            "com.android.wp.net.log",
            "com.android.camera.update",
            "cc.madkite.freedom",
            "com.solohsu.android.edxp.manager",
            "org.meowcat.edxposed.manager",
            "com.xmodgame",
            "com.cih.game_cih",
            "com.charles.lpoqasert",
            "catch_.me_.if_.you_.can_",
        )
    }
}
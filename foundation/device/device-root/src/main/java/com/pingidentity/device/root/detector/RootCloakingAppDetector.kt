/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.root.detector

/**
 * Check if there are well-known root cloaking App installed.
 */
class RootCloakingAppDetector : PackageDetector() {

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
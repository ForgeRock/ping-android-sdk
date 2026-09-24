/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci

import com.pingidentity.android.ContextProvider
import java.io.IOException
import java.util.Properties

object DaVinciTestConfig {
    private const val CONFIG_FILE_NAME = "davinci_test_config.properties"
    private val properties = Properties()

    // Shared
    var davinciRedirectUri: String = ""
        private set

    // DaVinci E2E (DavinciAndroidTest, FormMFADevicesTest, DavinciProtectTest)
    var davinciClientId: String = ""
        private set
    var davinciDiscoveryEndpoint: String = ""
        private set
    var davinciAcrValues: String = ""
        private set
    var davinciProtectAcrValues: String = ""
        private set
    var davinciMfaDeviceAcrValues: String = ""
        private set

    var davinciFormFieldsAcrValues: String = ""
        private set
    var davinciPollingAcrValues: String = ""
        private set

    var davinciMetadataAcrValues: String = ""
        private set

    var davinciImageAcrValues: String = ""
        private set

    // Credentials
    var davinciUsername: String = ""
        private set
    var davinciPassword: String = ""
        private set
    var davinciVerificationCode: String = ""
        private set

    // PAR (PARDaVinciE2ETest, PARCentralizedLoginDaVinciE2ETest)
    var parClientId: String = ""
        private set
    var parDiscoveryEndpoint: String = ""
        private set
    var parRedirectUri: String = ""
        private set
    var parAcrValues: String = ""
        private set

    // Device Authorization Grant
    var deviceClientId: String = ""
        private set
    var deviceDiscoveryEndpoint: String = ""
        private set
    var deviceApproverClientId: String = ""
        private set
    var deviceApproverDiscoveryEndpoint: String = ""
        private set
    var deviceApproverRedirectUri: String = ""
        private set
    var deviceApproverAcrValues: String = ""
        private set
    var deviceUsername: String = ""
        private set
    var devicePassword: String = ""
        private set

    init {
        try {
            val inputStream = ContextProvider.context.assets.open(CONFIG_FILE_NAME)
            properties.load(inputStream)
            inputStream.close()

            davinciRedirectUri = properties.getProperty("DAVINCI_REDIRECT_URI", "")

            davinciClientId = properties.getProperty("DAVINCI_CLIENT_ID", "")
            davinciDiscoveryEndpoint = properties.getProperty("DAVINCI_DISCOVERY_ENDPOINT", "")
            davinciAcrValues = properties.getProperty("DAVINCI_ACR_VALUES", "")
            davinciProtectAcrValues = properties.getProperty("DAVINCI_PROTECT_ACR_VALUES", "")
            davinciMfaDeviceAcrValues = properties.getProperty("DAVINCI_MFA_DEVICE_ACR_VALUES", "")

            davinciFormFieldsAcrValues = properties.getProperty("DAVINCI_FORM_FIELDS_ACR_VALUES", "")
            davinciPollingAcrValues = properties.getProperty("DAVINCI_POLLING_ACR_VALUES", "")
            davinciMetadataAcrValues = properties.getProperty("DAVINCI_METADATA_ACR_VALUES", "")
            davinciImageAcrValues = properties.getProperty("DAVINCI_IMAGE_ACR_VALUES", "")

            davinciUsername = properties.getProperty("DAVINCI_USERNAME", "")
            davinciPassword = properties.getProperty("DAVINCI_PASSWORD", "")
            davinciVerificationCode = properties.getProperty("DAVINCI_VERIFICATION_CODE", "")

            parClientId = properties.getProperty("PAR_CLIENT_ID", "")
            parDiscoveryEndpoint = properties.getProperty("PAR_DISCOVERY_ENDPOINT", "")
            parRedirectUri = properties.getProperty("PAR_REDIRECT_URI", "")
            parAcrValues = properties.getProperty("PAR_ACR_VALUES", "")

            deviceClientId = properties.getProperty("DEVICE_CLIENT_ID", "")
            deviceDiscoveryEndpoint = properties.getProperty("DEVICE_DISCOVERY_ENDPOINT", "")
            deviceApproverClientId = properties.getProperty("DEVICE_APPROVER_CLIENT_ID", "")
            deviceApproverDiscoveryEndpoint = properties.getProperty("DEVICE_APPROVER_DISCOVERY_ENDPOINT", "")
            deviceApproverRedirectUri = properties.getProperty("DEVICE_APPROVER_REDIRECT_URI", "")
            deviceApproverAcrValues = properties.getProperty("DEVICE_APPROVER_ACR_VALUES", "")
            deviceUsername = properties.getProperty("DEVICE_USERNAME", "")
            devicePassword = properties.getProperty("DEVICE_PASSWORD", "")
        } catch (e: IOException) {
            android.util.Log.e("DaVinciTestConfig", "Error loading properties file: ${e.message}")
        }
    }
}

/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.journey.callback.recaptchaEnterprise

import com.pingidentity.journey.BaseJourneyTest
import com.pingidentity.journey.Journey
import com.pingidentity.logger.Logger
import com.pingidentity.logger.STANDARD
import org.junit.Before

abstract class BaseRecaptchaEnterpriseCallbackTest : BaseJourneyTest() {
    lateinit var recaptchaJourney: Journey

    /**
     * Sets up the journey tree configuration before each test.
     */
    @Before
    fun setupTree() {
        tree = "TEST-e2e-recaptcha-enterprise"
        recaptchaJourney = Journey {
            logger = Logger.STANDARD
            serverUrl = "https://openam-sdks2.forgeblocks.com/am"
            realm = REALM
            cookie = COOKIE
        }
    }
}
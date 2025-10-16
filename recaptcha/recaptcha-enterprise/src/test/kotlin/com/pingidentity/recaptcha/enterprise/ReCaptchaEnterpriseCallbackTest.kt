/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.recaptcha.enterprise

import android.app.Application
import com.google.android.recaptcha.Recaptcha
import com.google.android.recaptcha.RecaptchaAction
import com.pingidentity.android.ContextProvider
import io.mockk.*
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.*

/**
 * Unit tests for [ReCaptchaEnterpriseCallback].
 *
 * This class verifies the correct initialization, configuration, and execution
 * of the ReCaptcha verification process.
 */
class ReCaptchaEnterpriseCallbackTest {

    private val mockApplication = mockk<Application>()
    private val siteKey = "test-site-key"

    @BeforeTest
    fun setup() {
        // Mock Android context and ReCaptcha static methods
        every { mockApplication.applicationContext } returns mockApplication
        ContextProvider.init(mockApplication)
        mockkStatic(Recaptcha::class)
    }

    @AfterTest
    fun tearDown() {
        unmockkAll()
    }

    /**
     * Test that the callback correctly sets the site key when initialized.
     */
    @Test
    fun `init sets reCaptchaSiteKey when property name matches`() {
        val callback = ReCaptchaEnterpriseCallback()
        callback.initForTest("recaptchaSiteKey", JsonPrimitive(siteKey))
        assertEquals(siteKey, callback.reCaptchaSiteKey)
    }

    /**
     * Test that the callback ignores unrelated property names.
     */
    @Test
    fun `init ignores unrelated property names`() {
        val callback = ReCaptchaEnterpriseCallback()
        val originalSiteKey = callback.reCaptchaSiteKey
        callback.initForTest("otherProperty", JsonPrimitive("value"))
        assertEquals(originalSiteKey, callback.reCaptchaSiteKey)
    }

    /**
     * Test default values of ReCaptchaEnterpriseConfig.
     */
    @Test
    fun `ReCaptchaEnterpriseConfig has correct defaults`() {
        val config = ReCaptchaEnterpriseConfig()
        assertEquals(RecaptchaAction.LOGIN, config.recaptchaAction)
        assertEquals(10000L, config.timeoutInMills)
    }
}
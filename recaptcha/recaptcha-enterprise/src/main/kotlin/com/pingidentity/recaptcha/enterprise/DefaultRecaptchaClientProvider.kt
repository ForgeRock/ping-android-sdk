/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.recaptcha.enterprise

import android.app.Application
import com.google.android.recaptcha.Recaptcha
import com.google.android.recaptcha.RecaptchaAction
import com.google.android.recaptcha.RecaptchaClient

/**
 * Default implementation of RecaptchaClientProvider that uses Google's ReCaptcha Enterprise SDK.
 *
 * This provider handles the creation and management of RecaptchaClient instances for
 * ReCaptcha Enterprise verification. It provides standard implementations for fetching
 * clients and executing ReCaptcha actions with the configured parameters.
 *
 * This implementation directly uses Google's ReCaptcha Enterprise API without any
 * additional customizations or modifications. It's suitable for most standard use cases
 * where default ReCaptcha behavior is desired.
 *
 * Example usage:
 * ```kotlin
 * val provider = DefaultRecaptchaClientProvider()
 * val client = provider.fetchClient(application, "your-site-key")
 * val token = provider.execute(client, RecaptchaAction.LOGIN, 10000L)
 * ```
 */
class DefaultRecaptchaClientProvider : RecaptchaClientProvider {

    /**
     * Fetches a RecaptchaClient instance for the specified site key.
     *
     * @param application The Application context
     * @param siteKey The ReCaptcha site key
     * @return The RecaptchaClient instance
     * @throws Exception if client creation fails
     */
    override suspend fun fetchClient(application: Application, siteKey: String): RecaptchaClient {
        return Recaptcha.fetchClient(application, siteKey)
    }

    /**
     * Executes a ReCaptcha action with the specified parameters.
     *
     * @param client The RecaptchaClient to use
     * @param action The ReCaptcha action to execute
     * @param timeoutInMillis The timeout for the action in milliseconds
     * @return The verification token or null if execution failed
     */
    override suspend fun execute(client: RecaptchaClient, action: RecaptchaAction, timeoutInMillis: Long): String? {
        return client.execute(action, timeoutInMillis).getOrNull()
    }
}
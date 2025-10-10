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
 * Provider interface for managing ReCaptcha Enterprise client operations.
 *
 * This interface abstracts the creation and management of RecaptchaClient instances
 * and the execution of ReCaptcha actions. It allows for custom implementations
 * to provide different ReCaptcha handling strategies, testing mocks, or enhanced
 * functionality while maintaining a consistent API.
 *
 * Implementations should handle:
 * - Client creation and configuration
 * - Action execution with proper timeout handling
 * - Error handling and recovery
 * - Resource cleanup when necessary
 */
interface RecaptchaClientProvider {
    /**
     * Fetches the RecaptchaClient for the specified site key.
     *
     * Creates or retrieves a RecaptchaClient instance configured for the given
     * site key. The client is used to execute ReCaptcha actions and obtain
     * verification tokens.
     *
     * @param application The Application context required for ReCaptcha initialization
     * @param siteKey The ReCaptcha Enterprise site key configured in Google Cloud Console
     * @return The RecaptchaClient instance ready for action execution
     * @throws Exception if client creation fails due to network issues, invalid site key, or configuration errors
     */
    suspend fun fetchClient(application: Application, siteKey: String): RecaptchaClient

    /**
     * Executes the specified ReCaptcha action using the provided client.
     *
     * Performs a ReCaptcha verification by executing the given action and waiting
     * for the result within the specified timeout period. The action represents
     * the type of user interaction being verified (e.g., LOGIN, SIGNUP, etc.).
     *
     * @param client The RecaptchaClient to use for action execution
     * @param action The RecaptchaAction representing the user interaction type
     * @param timeoutInMillis The maximum time to wait for action completion in milliseconds
     * @return The verification token string if successful, null if execution fails or times out
     * @throws Exception if action execution encounters critical errors
     */
    suspend fun execute(client: RecaptchaClient, action: RecaptchaAction, timeoutInMillis: Long): String?
}
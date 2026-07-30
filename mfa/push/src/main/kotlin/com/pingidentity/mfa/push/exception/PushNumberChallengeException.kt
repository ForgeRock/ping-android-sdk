/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.push.exception

import com.pingidentity.exception.ApiException

/**
 * Exception thrown when a push number challenge fails.
 *
 * This exception is thrown when the server responds with an error during a push number challenge operation.
 * It contains the status code and the error message from the server response.
 *
 * @param status The status code of the API response.
 * @param message The error message from the API response.
 */
class PushNumberChallengeException(
    status: Int,
    message: String,
) : ApiException(content = message, status = status)
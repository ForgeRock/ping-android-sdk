/*
 * Copyright (c) 2024 Ping Identity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.exception

/**
 * Class representing an API exception.
 *
 * This class extends the Exception class and is used to represent exceptions that occur when interacting with an API.
 * It contains a status code and a content message.
 *
 * @constructor Creates a new ApiException with the given status and content.
 * @param status The status code of the API response.
 * @param content The content message of the API response.
 */
class ApiException(val status: Int, val content: String) : Exception(content) {

    /**
     * Overrides the toString function from the Exception class.
     *
     * This method is used to provide a string representation of the ApiException.
     * It includes the status code and the content message.
     *
     * @return A string representation of the ApiException.
     */
    override fun toString(): String {
        return "ApiException(status=$status, content='$content')"
    }
}
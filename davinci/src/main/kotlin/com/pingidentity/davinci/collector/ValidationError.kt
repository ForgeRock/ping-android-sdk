/*
 * Copyright (c) 2024 Ping Identity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci.collector

/**
 * Class representing a validation error.
 */
sealed class ValidationError

/**
 * Class representing a required field error.
 */
data object Required : ValidationError()

/**
 * Class representing a regex error.
 * @property message The error message.
 */
data class RegexError(val message: String) : ValidationError()



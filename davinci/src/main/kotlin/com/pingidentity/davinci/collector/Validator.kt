/*
 * Copyright (c) 2025 Ping Identity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci.collector

/**
 * Interface representing a validator.
 */
interface Validator {
    /**
     * Function to validate the field collector.
     * @return The list of validation errors.
     */
    fun validate(): List<ValidationError>
}
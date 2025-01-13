/*
 * Copyright (c) 2024 Ping Identity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci.collector

import com.pingidentity.orchestrate.Closeable

/**
 * Class representing a PASSWORD Type.
 *
 * This class inherits from the FieldCollector class and implements the Closeable and Collector interfaces.
 * It is used to collect password data.
 *
 * @constructor Creates a new PasswordCollector with the given input.
 */
class PasswordCollector : FieldCollector(), Closeable {

    // A flag to determine whether to clear the password or not after submission.
    var clearPassword = true

    /**
     * Overrides the close function from the Closeable interface.
     * It is used to clear the value of the password field when the collector is closed.
     * If the clearPassword flag is set to true, the value of the password field will be cleared.
     */
    override fun close() {
        if (clearPassword) value = ""
    }
}
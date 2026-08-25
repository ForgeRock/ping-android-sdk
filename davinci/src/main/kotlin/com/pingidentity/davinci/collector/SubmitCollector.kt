/*
 * Copyright (c) 2024 - 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci.collector

import com.pingidentity.davinci.plugin.ActionKeyProvider
import com.pingidentity.davinci.plugin.Submittable
import com.pingidentity.orchestrate.Closeable

private const val SUBMIT = "submit"

/**
 * Class representing a SUBMIT_BUTTON Type.
 *
 * This class extends from the [FieldCollector] class. It is used to collect data
 * when a form is submitted.
 *
 * @constructor Creates a new SubmitCollector.
 */
class SubmitCollector : SingleValueCollector(), Submittable, ActionKeyProvider, Closeable {
    override fun eventType(): String = SUBMIT

    override val actionKey: String? get() = if (value.isEmpty()) null else id()

    override fun close() {
        value = ""
    }
}
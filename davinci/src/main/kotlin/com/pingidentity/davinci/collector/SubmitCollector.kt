/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci.collector

import com.pingidentity.davinci.plugin.Submittable

/**
 * Class representing a SUBMIT_BUTTON Type.
 *
 * This class extends from the [FieldCollector] class. It is used to collect data
 * when a form is submitted.
 *
 * @constructor Creates a new SubmitCollector.
 */
class SubmitCollector : SingleValueCollector(), Submittable {
    override fun eventType(): String = "submit"
}
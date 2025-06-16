/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.journey.callback

/**
 * A callback for suspending the output of text.
 *
 * This callback is used to indicate that the output of text should be suspended.
 * It is typically used in conjunction with a [TextOutputCallback] to control
 * the flow of text output in a user interface.
 *
 * @see TextOutputCallback
 */
class SuspendedTextOutputCallback : TextOutputCallback()
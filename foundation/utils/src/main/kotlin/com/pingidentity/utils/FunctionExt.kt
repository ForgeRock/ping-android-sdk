/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.utils

inline fun <T> (T.() -> Unit).andThen(crossinline next: T.() -> Unit): T.() -> Unit = {
    this@andThen()
    next()
}
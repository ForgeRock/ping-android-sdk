/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.fido2

import android.util.Base64
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.io.encoding.Base64.Default.UrlSafe
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class Test {

    @Test
    fun name() {
        val string = "wh+WU7Mp3AG27wGmrFW+w/bKqxFfUycZFs9nMUyHEEM="
        val result = Base64.decode(string, Base64.DEFAULT)
        println("result: $result")
        val encoded = Base64.encodeToString(result, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        println("encoded: $encoded")
    }

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun name2() {
        val string = "wh+WU7Mp3AG27wGmrFW+w/bKqxFfUycZFs9nMUyHEEM="
        val result = kotlin.io.encoding.Base64.decode(string)
        val encoded = UrlSafe.encode(result).trimEnd('=') // remove padding as per https://tools.ietf.org/html/rfc7636#section-4.1
        println("encoded: $encoded")

        val result2 = Base64.decode(string, Base64.DEFAULT)
        val encoded2 = Base64.encodeToString(result, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        println("encoded2: $encoded")

        assertEquals(encoded, encoded2)
    }
}
/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.utils

import android.os.LocaleList

/**
 * Function to convert a LocaleList to an Accept-Language header value.
 */
fun LocaleList.toAcceptLanguage(): String {
    if (isEmpty) return ""

    val languageTags = mutableListOf<String>()
    var currentQValue = 0.9

    (0 until size()).forEach { index ->
        val locale = this[index]

        // Add toLanguageTag version first
        if (index == 0) {
            languageTags.add(locale.toLanguageTag())
            currentQValue = 0.9
        } else {
            languageTags.add("${locale.toLanguageTag()};q=%.1f".format(currentQValue))
            currentQValue -= 0.1
        }

        // Add language version with next q-value
        if (locale.toLanguageTag() != locale.language) {
            languageTags.add("${locale.language};q=%.1f".format(currentQValue))
            currentQValue -= 0.1
        }
    }

    return languageTags.joinToString(", ")
}
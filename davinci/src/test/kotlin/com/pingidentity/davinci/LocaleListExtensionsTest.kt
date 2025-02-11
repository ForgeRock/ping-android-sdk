/*
 * Copyright (c) 2025 Ping Identity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci

import android.os.LocaleList
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class LocaleListExtensionsTest {

    @Test
    fun `empty locale list returns empty string`() {
        val emptyLocaleList = LocaleList()
        assertEquals("", emptyLocaleList.toAcceptLanguage())
    }

    @Test
    fun `single locale without script returns language tag`() {
        val localeList = LocaleList(Locale("en", "US"))
        assertEquals("en-US, en;q=0.9", localeList.toAcceptLanguage())
    }

    @Test
    fun `single locale with different language tag adds both versions`() {
        val localeList = LocaleList(Locale.forLanguageTag("zh-Hant-TW"))
        assertEquals("zh-Hant-TW, zh;q=0.9", localeList.toAcceptLanguage())
    }

    @Test
    fun `multiple locales are ordered with decreasing q values`() {
        val localeList = LocaleList(
            Locale("en", "US"),
            Locale("es", "ES"),
            Locale("fr", "FR")
        )
        assertEquals(
            "en-US, en;q=0.9, es-ES;q=0.8, es;q=0.7, fr-FR;q=0.6, fr;q=0.5",
            localeList.toAcceptLanguage()
        )
    }

    @Test
    fun `complex locale list with scripts handled correctly`() {
        val localeList = LocaleList(
            Locale.forLanguageTag("zh-Hant-TW"),
            Locale("en", "US"),
            Locale.forLanguageTag("zh-Hans-CN")
        )
        assertEquals(
            "zh-Hant-TW, zh;q=0.9, en-US;q=0.8, en;q=0.7, zh-Hans-CN;q=0.6, zh;q=0.5",
            localeList.toAcceptLanguage()
        )
    }

    @Test
    fun `q values decrease correctly for long lists`() {
        val localeList = LocaleList(
            Locale("en", "US"),
            Locale("fr", "FR"),
            Locale("de", "DE"),
            Locale("it", "IT"),
            Locale("es", "ES")
        )
        assertEquals(
            "en-US, en;q=0.9, fr-FR;q=0.8, fr;q=0.7, de-DE;q=0.6, de;q=0.5, " +
                    "it-IT;q=0.4, it;q=0.3, es-ES;q=0.2, es;q=0.1",
            localeList.toAcceptLanguage()
        )
    }
}
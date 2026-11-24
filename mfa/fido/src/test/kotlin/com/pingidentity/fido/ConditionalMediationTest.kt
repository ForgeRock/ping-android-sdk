/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.fido

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * SDKS-4574 Phase 2 tests: verifies the [isConditionalMediationSupported] OS gate against the
 * truth table implied by the androidx `CredentialManagerViewHandler` gate
 * (`SDK_INT >= 35 || (SDK_INT == 34 && PREVIEW_SDK_INT > 0)`).
 *
 * The `SDK_INT == 34 && PREVIEW_SDK_INT > 0` branch is not exercised here: Robolectric's
 * `@Config(sdk = [...])` can pin `SDK_INT` but cannot set `PREVIEW_SDK_INT`, so the
 * API-34-preview state is untestable in a JVM test. The branch mirrors the androidx
 * library's own gate (`CredentialManagerViewHandler.Api35Impl`), which carries the equivalent
 * behaviour, and is covered there upstream.
 */
@RunWith(RobolectricTestRunner::class) // Build.VERSION uses the Android API
class ConditionalMediationTest {

    @Test
    @Config(sdk = [33])
    fun `API 33 does not support conditional mediation`() {
        assertEquals(false, isConditionalMediationSupported)
    }

    @Test
    @Config(sdk = [34])
    fun `API 34 does not support conditional mediation without a preview SDK`() {
        assertEquals(false, isConditionalMediationSupported)
    }

    @Test
    @Config(sdk = [35])
    fun `API 35 supports conditional mediation`() {
        assertEquals(true, isConditionalMediationSupported)
    }

    @Test
    @Config(sdk = [36])
    fun `API 36 supports conditional mediation`() {
        assertEquals(true, isConditionalMediationSupported)
    }
}

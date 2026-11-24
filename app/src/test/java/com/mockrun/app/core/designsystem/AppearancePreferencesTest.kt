package com.mockrun.app.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppearancePreferencesTest {

    @Test
    fun `accent hex accepts six digits with or without hash`() {
        assertEquals(
            0xFF2F7BFF.toInt(),
            AppAppearancePreferences.parseAccentColor("#2F7BFF")
        )
        assertEquals(
            0xFF2E9E6B.toInt(),
            AppAppearancePreferences.parseAccentColor("2e9e6b")
        )
    }

    @Test
    fun `accent hex rejects invalid values`() {
        assertNull(AppAppearancePreferences.parseAccentColor("#12345"))
        assertNull(AppAppearancePreferences.parseAccentColor("#GG0000"))
        assertNull(AppAppearancePreferences.parseAccentColor(""))
    }
}

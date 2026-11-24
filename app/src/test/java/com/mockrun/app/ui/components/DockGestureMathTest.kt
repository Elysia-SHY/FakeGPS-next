package com.mockrun.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class DockGestureMathTest {

    @Test
    fun `touch position maps to an indicator centered under the finger`() {
        val tabWidth = 200f

        assertEquals(
            1f,
            indicatorPositionForTouch(xPx = 300f, tabWidthPx = tabWidth, tabCount = 4),
            0.0001f
        )
        assertEquals(
            3f,
            indicatorPositionForTouch(xPx = 700f, tabWidthPx = tabWidth, tabCount = 4),
            0.0001f
        )
    }

    @Test
    fun `indicator travel stays between the first and last tab centers`() {
        val tabWidth = 200f

        assertEquals(
            0f,
            indicatorPositionForTouch(xPx = 10f, tabWidthPx = tabWidth, tabCount = 4),
            0.0001f
        )
        assertEquals(
            3f,
            indicatorPositionForTouch(xPx = 790f, tabWidthPx = tabWidth, tabCount = 4),
            0.0001f
        )
    }

    @Test
    fun `release position resolves to the nearest tab slot`() {
        assertEquals(0, targetIndexForIndicatorPosition(0.49f, tabCount = 4))
        assertEquals(1, targetIndexForIndicatorPosition(0.50f, tabCount = 4))
        assertEquals(2, targetIndexForIndicatorPosition(1.50f, tabCount = 4))
        assertEquals(3, targetIndexForIndicatorPosition(2.90f, tabCount = 4))
    }

    @Test
    fun `single tab and zero width remain safe`() {
        assertEquals(
            0f,
            indicatorPositionForTouch(xPx = 100f, tabWidthPx = 0f, tabCount = 1),
            0.0001f
        )
        assertEquals(
            0f,
            indicatorPositionForTouch(xPx = 100f, tabWidthPx = 0f, tabCount = 4),
            0.0001f
        )
        assertEquals(0, targetIndexForIndicatorPosition(3f, tabCount = 1))
    }
}

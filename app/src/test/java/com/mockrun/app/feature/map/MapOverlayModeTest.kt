package com.mockrun.app.feature.map

import org.junit.Assert.assertEquals
import org.junit.Test

class MapOverlayModeTest {

    @Test
    fun `location tab owns only single location overlays`() {
        assertEquals(MapOverlayMode.SINGLE_LOCATION, overlayModeFor(MapTab.LOCATION))
    }

    @Test
    fun `route tab owns only route overlays`() {
        assertEquals(MapOverlayMode.ROUTE, overlayModeFor(MapTab.ROUTE))
    }
}

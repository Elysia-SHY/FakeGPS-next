package com.mockrun.app.feature.map

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreeStageMapDrawerTest {

    @Test
    fun `measured content produces three ordered anchors on compact phones`() {
        val cases = listOf(
            ViewportCase(568.dp, 112.dp, 92.dp),
            ViewportCase(640.dp, 118.dp, 104.dp),
            ViewportCase(851.dp, 132.dp, 112.dp)
        )

        cases.forEach { case ->
            val anchors = calculateThreeStageDrawerAnchors(
                viewportHeight = case.height,
                topChromeHeight = case.topChrome,
                bottomReservation = case.bottomInset,
                contentHeights = DrawerContentHeights(
                    collapsed = 60.dp,
                    halfExpanded = 188.dp,
                    expanded = 356.dp
                )
            )

            assertOrderedAndOnScreen(anchors, case.height)
        }
    }

    @Test
    fun `expanded anchor wraps content instead of filling the viewport`() {
        val anchors = calculateThreeStageDrawerAnchors(
            viewportHeight = 900.dp,
            topChromeHeight = 80.dp,
            bottomReservation = 100.dp,
            contentHeights = DrawerContentHeights(
                collapsed = 60.dp,
                halfExpanded = 220.dp,
                expanded = 410.dp
            )
        )

        assertEquals(100.dp + 32.dp + 410.dp, anchors.expanded)
        assertTrue(anchors.expanded < 900.dp - 80.dp - 24.dp)
    }

    @Test
    fun `oversized expanded content is capped below top chrome`() {
        val anchors = calculateThreeStageDrawerAnchors(
            viewportHeight = 640.dp,
            topChromeHeight = 120.dp,
            bottomReservation = 96.dp,
            contentHeights = DrawerContentHeights(
                collapsed = 80.dp,
                halfExpanded = 300.dp,
                expanded = 1_000.dp
            )
        )

        assertEquals(640.dp - 120.dp - 24.dp, anchors.expanded)
        assertOrderedAndOnScreen(anchors, 640.dp)
    }

    @Test
    fun `route half keeps at least fifty five percent usable map`() {
        val cases = listOf(
            ViewportCase(640.dp, 64.dp, 96.dp),
            ViewportCase(800.dp, 72.dp, 104.dp),
            ViewportCase(915.dp, 88.dp, 116.dp)
        )

        cases.forEach { case ->
            val anchors = calculateThreeStageDrawerAnchors(
                viewportHeight = case.height,
                topChromeHeight = case.topChrome,
                bottomReservation = case.bottomInset,
                contentHeights = DrawerContentHeights(
                    collapsed = 48.dp,
                    halfExpanded = 280.dp,
                    expanded = 520.dp
                ),
                minMapVisibilityFraction = 0.55f
            )
            val visibleMapFraction = (case.height - anchors.halfExpanded - case.topChrome) /
                case.height

            assertOrderedAndOnScreen(anchors, case.height)
            assertTrue(
                "Expected >=55% map at $case, got $visibleMapFraction",
                visibleMapFraction >= 0.55f - 0.001f
            )
        }
    }

    @Test
    fun `landscape and tablet dimensions remain reachable`() {
        val cases = listOf(
            ViewportCase(360.dp, 56.dp, 88.dp),
            ViewportCase(800.dp, 144.dp, 160.dp),
            ViewportCase(1280.dp, 172.dp, 128.dp)
        )

        cases.forEach { case ->
            val anchors = calculateThreeStageDrawerAnchors(
                viewportHeight = case.height,
                topChromeHeight = case.topChrome,
                bottomReservation = case.bottomInset,
                contentHeights = DrawerContentHeights(
                    collapsed = 60.dp,
                    halfExpanded = 240.dp,
                    expanded = 520.dp
                ),
                minMapVisibilityFraction = 0.55f
            )

            assertOrderedAndOnScreen(anchors, case.height)
            assertTrue(anchors.expanded <= case.height - case.topChrome - 24.dp)
        }
    }

    @Test
    fun `moderate upward pull from collapsed lands on half expanded`() {
        val anchors = testAnchors()
        val collapsed = anchors.collapsed.value
        val half = anchors.halfExpanded.value

        val target = resolveDrawerStageAfterDrag(
            currentStage = MapDrawerStage.COLLAPSED,
            startHeight = collapsed,
            releasedHeight = collapsed + (half - collapsed) * 0.55f,
            velocityY = 0f,
            anchors = anchors
        )

        assertEquals(MapDrawerStage.HALF_EXPANDED, target)
    }

    @Test
    fun `small pull settles back without jumping`() {
        val anchors = testAnchors()
        val half = anchors.halfExpanded.value

        val target = resolveDrawerStageAfterDrag(
            currentStage = MapDrawerStage.HALF_EXPANDED,
            startHeight = half,
            releasedHeight = half + 18f,
            velocityY = 120f,
            anchors = anchors
        )

        assertEquals(MapDrawerStage.HALF_EXPANDED, target)
    }

    @Test
    fun `deep pull still settles on the adjacent stop`() {
        val anchors = testAnchors()
        val collapsed = anchors.collapsed.value
        val half = anchors.halfExpanded.value
        val expanded = anchors.expanded.value

        val target = resolveDrawerStageAfterDrag(
            currentStage = MapDrawerStage.COLLAPSED,
            startHeight = collapsed,
            releasedHeight = half + (expanded - half) * 0.7f,
            velocityY = 0f,
            anchors = anchors
        )

        assertEquals(MapDrawerStage.HALF_EXPANDED, target)
    }

    @Test
    fun `threshold below forty eight percent returns to origin`() {
        val anchors = testAnchors()
        val collapsed = anchors.collapsed.value
        val half = anchors.halfExpanded.value

        val target = resolveDrawerStageAfterDrag(
            currentStage = MapDrawerStage.COLLAPSED,
            startHeight = collapsed,
            releasedHeight = collapsed + (half - collapsed) * 0.47f,
            velocityY = 0f,
            anchors = anchors,
            velocityThreshold = 2_000f
        )

        assertEquals(MapDrawerStage.COLLAPSED, target)
    }

    @Test
    fun `rubber band compresses overdrag asymptotically`() {
        val minimum = 100f
        val maximum = 500f
        val stretch = 20f

        assertEquals(150f, applyDrawerRubberBand(150f, minimum, maximum, stretch), 0.001f)
        val lower = applyDrawerRubberBand(-100f, minimum, maximum, stretch)
        val upper = applyDrawerRubberBand(700f, minimum, maximum, stretch)
        assertTrue(lower in (minimum - stretch)..minimum)
        assertTrue(upper in maximum..(maximum + stretch))
        assertTrue(lower > minimum - stretch)
        assertTrue(upper < maximum + stretch)
    }

    @Test
    fun `crosshair fades only when drawer covers centre and never changes position`() {
        assertEquals(1f, crosshairAlphaForDrawer(2_000, 600, 48), 0.001f)
        assertEquals(0.5f, crosshairAlphaForDrawer(2_000, 1_000, 48), 0.001f)
        assertEquals(0f, crosshairAlphaForDrawer(2_000, 1_200, 48), 0.001f)
    }

    private fun testAnchors(): ThreeStageDrawerAnchors {
        return calculateThreeStageDrawerAnchors(
            viewportHeight = 800.dp,
            topChromeHeight = 72.dp,
            bottomReservation = 104.dp,
            contentHeights = DrawerContentHeights(
                collapsed = 48.dp,
                halfExpanded = 260.dp,
                expanded = 460.dp
            )
        )
    }

    private fun assertOrderedAndOnScreen(
        anchors: ThreeStageDrawerAnchors,
        viewportHeight: androidx.compose.ui.unit.Dp
    ) {
        assertTrue(
            "collapsed must be below half: $anchors",
            anchors.collapsed < anchors.halfExpanded
        )
        assertTrue(
            "half must be below expanded: $anchors",
            anchors.halfExpanded < anchors.expanded
        )
        assertTrue("drawer must include the Dock reservation", anchors.collapsed > anchors.bottomReservation)
        assertTrue("drawer must stay on screen", anchors.expanded <= viewportHeight)
    }

    private data class ViewportCase(
        val height: androidx.compose.ui.unit.Dp,
        val topChrome: androidx.compose.ui.unit.Dp,
        val bottomInset: androidx.compose.ui.unit.Dp
    )
}

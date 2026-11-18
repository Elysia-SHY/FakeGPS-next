package com.mockrun.app.core.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mockrun.app.domain.model.Route
import com.mockrun.app.domain.model.RouteMode
import com.mockrun.app.domain.model.WayPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LastMockSelectionStoreTest {
    private lateinit var context: Context
    private lateinit var store: LastMockSelectionStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        store = LastMockSelectionStore(context)
        store.clear()
    }

    @Test
    fun `global point and target key survive store recreation`() {
        store.saveTargetKey("com.autonavi.minimap_0")
        store.saveLocation(31.230416, 121.473701)

        val restored = LastMockSelectionStore(context).load()

        assertEquals("com.autonavi.minimap_0", restored.targetKey)
        assertEquals(31.230416, restored.location!!.latitude, 0.0)
        assertEquals(121.473701, restored.location!!.longitude, 0.0)
    }

    @Test
    fun `switching back to global clears only target key`() {
        store.saveTargetKey("com.example.app_0")
        store.saveLocation(22.543096, 114.057865)

        store.saveTargetKey(null)
        val restored = LastMockSelectionStore(context).load()

        assertNull(restored.targetKey)
        assertEquals(22.543096, restored.location!!.latitude, 0.0)
        assertEquals(114.057865, restored.location!!.longitude, 0.0)
    }

    @Test
    fun `unsaved route snapshot preserves mode and every waypoint`() {
        val route = Route(
            id = 0L,
            name = "未收藏的手绘路线",
            waypoints = listOf(
                WayPoint(39.9042, 116.4074, 45.0, "起点"),
                WayPoint(31.230416, 121.473701, 12.0, "终点")
            ),
            createdAt = 123456789L,
            mode = RouteMode.PING_PONG
        )

        store.saveRoute(route)
        val restored = LastMockSelectionStore(context).loadRoute()

        assertEquals(route, restored)
    }

    @Test
    fun `clearing route keeps last point and target selection`() {
        store.saveTargetKey("com.example.app_0")
        store.saveLocation(30.274084, 120.15507)
        store.saveRoute(
            Route(
                name = "测试路线",
                waypoints = listOf(WayPoint(30.0, 120.0), WayPoint(31.0, 121.0))
            )
        )

        store.saveRoute(null)

        assertNull(store.loadRoute())
        assertEquals("com.example.app_0", store.load().targetKey)
        assertEquals(30.274084, store.load().location!!.latitude, 0.0)
    }

    @Test
    fun `invalid coordinates are never persisted`() {
        store.saveLocation(Double.NaN, 120.0)
        store.saveLocation(91.0, 120.0)
        assertNull(store.load().location)

        store.saveLocation(30.0, 120.0)
        store.saveLocation(-91.0, 120.0)
        assertTrue(store.load().location!!.latitude == 30.0)
    }
}

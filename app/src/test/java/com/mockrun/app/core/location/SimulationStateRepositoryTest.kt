package com.mockrun.app.core.location

import com.mockrun.app.domain.model.MockSimulationMode
import com.mockrun.app.domain.model.Route
import com.mockrun.app.domain.model.SimulationStatus
import com.mockrun.app.domain.model.WayPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SimulationStateRepositoryTest {

    private val route = Route(
        name = "test route",
        waypoints = listOf(
            WayPoint(31.2304, 121.4737),
            WayPoint(31.2314, 121.4747)
        )
    )

    @Test
    fun `starting point mock clears paused route state atomically`() {
        val repository = SimulationStateRepository()
        repository.onSimulationStarted(route, 8f)
        repository.onPaused(0.4f)

        repository.setPointMock(true, WayPoint(30.0, 120.0))

        assertEquals(MockSimulationMode.SINGLE_POINT, repository.activeMode.value)
        assertTrue(repository.isPointMockActive.value)
        assertEquals(SimulationStatus.Idle, repository.state.value.status)
        assertNull(repository.state.value.route)
        assertNull(repository.pendingRoute)
    }

    @Test
    fun `starting route clears point and joystick ownership`() {
        val repository = SimulationStateRepository()
        repository.setPointMock(true, WayPoint(30.0, 120.0))
        repository.setJoystickActive(true)

        repository.onSimulationStarted(route, 10f)

        assertEquals(MockSimulationMode.ROUTE, repository.activeMode.value)
        assertFalse(repository.isPointMockActive.value)
        assertFalse(repository.isJoystickActive.value)
        assertNull(repository.pointMockLocation.value)
        assertEquals(route, repository.state.value.route)
        assertEquals(SimulationStatus.Running, repository.state.value.status)
    }

    @Test
    fun `paused route still owns injection mode`() {
        val repository = SimulationStateRepository()
        repository.onSimulationStarted(route, 6f)

        repository.onPaused(0.25f)

        assertEquals(MockSimulationMode.ROUTE, repository.activeMode.value)
        assertEquals(SimulationStatus.Paused, repository.state.value.status)
    }

    @Test
    fun `joystick transition cannot coexist with planned route`() {
        val repository = SimulationStateRepository()
        repository.onSimulationStarted(route, 6f)

        repository.setJoystickActive(true)

        assertEquals(MockSimulationMode.JOYSTICK, repository.activeMode.value)
        assertEquals(SimulationStatus.Idle, repository.state.value.status)
        assertNull(repository.state.value.route)
    }

    @Test
    fun `stopping point mock releases mode without clearing a planned overlay route`() {
        val repository = SimulationStateRepository()
        repository.setPointMock(true, WayPoint(30.0, 120.0))

        repository.setPointMock(false)

        assertEquals(MockSimulationMode.NONE, repository.activeMode.value)
        assertFalse(repository.isPointMockActive.value)
        assertNull(repository.pointMockLocation.value)
    }
}

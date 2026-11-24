package com.mockrun.app.ui.viewmodel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchRequestGateTest {

    @Test
    fun `only the newest search request may publish results`() {
        val gate = MapViewModel.SearchRequestGate()
        val first = gate.next()
        val second = gate.next()

        assertFalse(gate.isLatest(first))
        assertTrue(gate.isLatest(second))
    }

    @Test
    fun `clearing search invalidates the in flight request`() {
        val gate = MapViewModel.SearchRequestGate()
        val request = gate.next()

        gate.invalidate()

        assertFalse(gate.isLatest(request))
    }

    @Test
    fun `a new search after clear is current`() {
        val gate = MapViewModel.SearchRequestGate()
        val clearedRequest = gate.next()
        gate.invalidate()

        val newRequest = gate.next()

        assertFalse(gate.isLatest(clearedRequest))
        assertTrue(gate.isLatest(newRequest))
    }
}

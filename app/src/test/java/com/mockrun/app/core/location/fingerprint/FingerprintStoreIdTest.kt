package com.mockrun.app.core.location.fingerprint

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FingerprintStoreIdTest {

    @Test
    fun `shell and path metacharacters are neutralized`() {
        val id = FingerprintStore.sanitizeId("a';id;/../x")
        assertFalse(id.contains("'"))
        assertFalse(id.contains(";"))
        assertFalse(id.contains("/"))
        assertTrue(id.length <= 64)
        assertTrue(id.isNotBlank())
    }

    @Test
    fun `blank id gets safe fallback`() {
        assertEquals("fingerprint", FingerprintStore.sanitizeId("   "))
        assertEquals("fingerprint", FingerprintStore.sanitizeId("../"))
    }

    @Test
    fun `length is bounded`() {
        assertEquals(64, FingerprintStore.sanitizeId("a".repeat(200)).length)
    }
}

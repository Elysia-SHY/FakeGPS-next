package com.mockrun.app.hook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class WeakListenerProxyRegistryTest {
    private data class Listener(val name: String)
    private data class Proxy(val delegate: Listener)

    @Test
    fun `registered listener reuses one live proxy`() {
        val registry = WeakListenerProxyRegistry<Listener, Proxy>()
        val listener = Listener("light")
        var allocations = 0

        val first = registry.getOrCreate(listener) { Proxy(listener).also { allocations++ } }
        val second = registry.getOrCreate(listener) { Proxy(listener).also { allocations++ } }

        assertSame(first, second)
        assertEquals(1, allocations)
        assertSame(first, registry[listener])
    }

    @Test
    fun `full unregister removes mapping atomically`() {
        val registry = WeakListenerProxyRegistry<Listener, Proxy>()
        val listener = Listener("light")
        val proxy = registry.getOrCreate(listener) { Proxy(listener) }

        assertSame(proxy, registry.remove(listener))
        assertNull(registry[listener])
        assertNull(registry.remove(listener))
    }

    @Test
    fun `different listeners never share proxies`() {
        val registry = WeakListenerProxyRegistry<Listener, Proxy>()
        val first = Listener("first")
        val second = Listener("second")

        val firstProxy = registry.getOrCreate(first) { Proxy(first) }
        val secondProxy = registry.getOrCreate(second) { Proxy(second) }

        assertSame(first, firstProxy.delegate)
        assertSame(second, secondProxy.delegate)
        assertSame(firstProxy, registry[first])
        assertSame(secondProxy, registry[second])
    }

    @Test
    fun `256 register and unregister cycles leave no mapping`() {
        val registry = WeakListenerProxyRegistry<Listener, Proxy>()

        repeat(256) { index ->
            val listener = Listener("listener-$index")
            val proxy = registry.getOrCreate(listener) { Proxy(listener) }
            assertSame(proxy, registry.remove(listener))
            assertNull(registry[listener])
        }
    }
}

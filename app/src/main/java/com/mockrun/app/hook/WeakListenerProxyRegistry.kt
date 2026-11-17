package com.mockrun.app.hook

import java.lang.ref.WeakReference
import java.util.WeakHashMap

/**
 * 监听器代理表：键和值都不由表强持有。
 *
 * 代理通常强引用原始监听器；如果表强持有代理，会形成 key → proxy → key 的环，
 * WeakHashMap 的弱键因此永远失效。系统注册期间会强持有代理，注销后则允许整条链回收。
 */
internal class WeakListenerProxyRegistry<L : Any, P : Any> {
    private val proxies = WeakHashMap<L, WeakReference<P>>()

    @Synchronized
    fun getOrCreate(listener: L, factory: () -> P): P =
        proxies[listener]?.get() ?: factory().also { proxies[listener] = WeakReference(it) }

    @Synchronized
    operator fun get(listener: L): P? = proxies[listener]?.get()

    @Synchronized
    fun remove(listener: L): P? = proxies.remove(listener)?.get()
}

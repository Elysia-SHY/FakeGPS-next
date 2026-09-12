package com.mockrun.app.util

import android.util.Log
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Unified diagnostic sink for both halves of this app.
 *
 * ## The problem this solves
 *
 * This project runs in two very different process shapes:
 *
 *  1. **The app process** — normal Android, `android.util.Log` works.
 *  2. **Injected processes** — `XposedLocationHook` runs inside `system_server` and inside
 *     every hooked third-party app. `de.robv.android.xposed:api` is declared `compileOnly`,
 *     so `XposedBridge` is **not on the classpath in the app process** and referencing it
 *     directly would throw `NoClassDefFoundError` there.
 *
 * Historically the codebase had 154 `runCatching` sites and only 16 log statements, so
 * almost every failure path was silent. The hook — 96 of those sites — had just two log
 * calls. That made any misbehaviour effectively undiagnosable.
 *
 * ## Design constraints
 *
 * - **Must never throw.** Diagnostics are a side channel; if they fail they must not take
 *   down `system_server`. Everything is wrapped.
 * - **Must not flood.** Most call sites sit on hot paths (every location dispatch). A naive
 *   `Log.w` there would flood logcat and cost real CPU in `system_server`. Every emit goes
 *   through a per-tag rate limiter.
 * - **Must work without Xposed.** `XposedBridge` is resolved once via reflection and simply
 *   skipped when absent.
 *
 * Usage:
 * ```
 * Diag.w("LocationHook", "dispatch failed", t)
 * val v = runCatching { risky() }.logFailure("LocationHook", "read spoof config").getOrNull()
 * ```
 */
object Diag {

    /** Shared logcat tag, so `adb logcat -s FakeGPS` shows everything. */
    const val LOGCAT_TAG = "FakeGPS"

    enum class Level { DEBUG, INFO, WARN, ERROR }

    /** Messages below this level are dropped entirely. */
    @Volatile
    var minLevel: Level = Level.DEBUG

    // ---------------------------------------------------------------------------------
    // Rate limiting
    // ---------------------------------------------------------------------------------

    /**
     * At most [MAX_PER_WINDOW] messages per tag per [WINDOW_MS]. Deliberately conservative:
     * these call sites fire on every location dispatch, and this code runs inside
     * `system_server`.
     */
    private const val WINDOW_MS = 10_000L
    private const val MAX_PER_WINDOW = 5

    private class Bucket {
        val windowStart = AtomicLong(0L)
        val count = AtomicLong(0L)

        /** True if this emit is allowed; also tells the caller whether it just got truncated. */
        fun tryAcquire(now: Long): Boolean {
            val start = windowStart.get()
            if (now - start >= WINDOW_MS) {
                // New window. Racing writers may both reset; harmless for a log limiter.
                windowStart.set(now)
                count.set(1L)
                return true
            }
            return count.incrementAndGet() <= MAX_PER_WINDOW
        }
    }

    private val buckets = ConcurrentHashMap<String, Bucket>()

    // ---------------------------------------------------------------------------------
    // Xposed bridge, resolved lazily and tolerated when absent
    // ---------------------------------------------------------------------------------

    private val xposedLogMethod: Method? by lazy {
        runCatching {
            Class.forName("de.robv.android.xposed.XposedBridge")
                .getMethod("log", String::class.java)
        }.getOrNull()
    }

    fun isRunningUnderXposed(): Boolean = xposedLogMethod != null

    // ---------------------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------------------

    fun d(tag: String, msg: String) = emit(Level.DEBUG, tag, msg, null)
    fun i(tag: String, msg: String) = emit(Level.INFO, tag, msg, null)
    fun w(tag: String, msg: String, t: Throwable? = null) = emit(Level.WARN, tag, msg, t)
    fun e(tag: String, msg: String, t: Throwable? = null) = emit(Level.ERROR, tag, msg, t)

    private fun emit(level: Level, tag: String, msg: String, t: Throwable?) {
        try {
            if (level.ordinal < minLevel.ordinal) return
            if (!bucketFor(tag).tryAcquire(System.currentTimeMillis())) return

            val line = buildString {
                append('[').append(tag).append("] ").append(msg)
                if (t != null) {
                    append(" :: ").append(t.javaClass.simpleName)
                    t.message?.let { append(": ").append(it) }
                }
            }

            when (level) {
                Level.DEBUG -> Log.d(LOGCAT_TAG, line)
                Level.INFO -> Log.i(LOGCAT_TAG, line)
                Level.WARN -> Log.w(LOGCAT_TAG, line)
                // Throwable overload keeps the stack trace where logcat can render it.
                Level.ERROR -> Log.e(LOGCAT_TAG, line, t)
            }

            // Mirror into the Xposed log when the framework is present, so hook-side
            // failures are visible in LSPosed's log viewer too.
            xposedLogMethod?.let { m ->
                runCatching { m.invoke(null, "[$LOGCAT_TAG] $line") }
            }
        } catch (_: Throwable) {
            // A diagnostic must never propagate into the host process.
        }
    }

    private fun bucketFor(tag: String): Bucket =
        buckets.getOrPut(tag) { Bucket() }

    /**
     * Clears rate-limit state. Useful when a state change makes previously-suppressed
     * messages interesting again (e.g. simulation just started).
     */
    fun resetThrottle() {
        buckets.clear()
    }
}

/**
 * Terminal operator for a [Result] chain that records the failure instead of dropping it.
 *
 * This exists because the codebase has ~154 `runCatching` call sites and previously only
 * four of them handled the failure branch. Attaching `.logFailure(...)` is a **pure
 * addition** to an existing chain: it does not change control flow, it only makes the
 * failure observable.
 *
 * ```
 * // before — failure vanishes without a trace
 * val rules = runCatching { readRules() }.getOrNull()
 *
 * // after — same behaviour, but the failure leaves a record
 * val rules = runCatching { readRules() }
 *     .logFailure("LocationHook", "read multi-target rules")
 *     .getOrNull()
 * ```
 */
fun <T> Result<T>.logFailure(tag: String, what: String, level: Diag.Level = Diag.Level.WARN): Result<T> {    onFailure { t ->
        when (level) {
            Diag.Level.DEBUG -> Diag.d(tag, "$what failed")
            Diag.Level.INFO -> Diag.i(tag, "$what failed")
            Diag.Level.WARN -> Diag.w(tag, "$what failed", t)
            Diag.Level.ERROR -> Diag.e(tag, "$what failed", t)
        }
    }
    return this
}

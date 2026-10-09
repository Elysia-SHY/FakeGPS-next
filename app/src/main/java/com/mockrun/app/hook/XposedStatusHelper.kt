package com.mockrun.app.hook

import android.app.AppOpsManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Process

object XposedStatusHelper {

    /**
     * Hooked by LSPosed / Xposed when this module is enabled.
     * Returns false by default in plain Android runtime.
     * If LSPosed has active hooks on this app, this method is replaced with return true.
     */
    @JvmStatic
    fun isModuleActive(): Boolean = false

    /**
     * Accurately check if Fake GPS is chosen as the Mock Location App in Developer Options.
     * Uses AppOpsManager.OPSTR_MOCK_LOCATION ("android:mock_location").
     */
    fun isMockLocationAppSelected(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        return try {
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_MOCK_LOCATION,
                    Process.myUid(),
                    context.packageName
                )
            } else {
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_MOCK_LOCATION,
                    Process.myUid(),
                    context.packageName
                )
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Accurately check if LSPosed / Xposed is truly hooked and functioning.
     * Returns true only if either:
     * 1. The module method is hooked (isModuleActive() == true)
     * 2. The system_server hook has successfully sent an IPC heartbeat to HookConfigProvider
     */
    fun isLsposedHookReallyActive(context: Context): Boolean {
        if (isModuleActive()) return true
        if (HookStateBridge.isSystemHookAlive()) return true
        return try {
            val bundle = context.contentResolver.call(
                // 必须是 manifest 里声明的 authority：com.mockrun.app.hook.provider。
                // 原值 com.mockrun.app.provider 从未注册，每次调用都抛
                // IllegalArgumentException 并被下面 catch 吞掉，该回退分支永远返回 false ——
                // 结果是模块其实已激活、只是心跳还没写入时，UI 误报「LSPosed 未激活」。
                Uri.parse("content://com.mockrun.app.hook.provider"),
                "isHookActive",
                null,
                null
            )
            bundle?.getBoolean("is_active", false) == true
        } catch (e: Exception) {
            false
        }
    }
}

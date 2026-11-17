package com.mockrun.app.hook

/** 客户端 Hook 的进程边界；保持纯 Kotlin，便于回归测试。 */
internal object ClientHookPolicy {
    private const val FIRST_APPLICATION_UID = 10_000
    private const val MIUI_CONTENT_CATCHER = "com.miui.contentcatcher"

    /** MIUI 会在目标应用进程中再次以该包名回调 LoadPackage，不能重复安装整套客户端 Hook。 */
    fun isAuxiliaryPackage(packageName: String): Boolean = packageName == MIUI_CONTENT_CATCHER

    /** 系统 UID 或 system 进程只允许走系统层 Hook，绝不能包装系统传感器监听器。 */
    fun shouldInstall(processName: String?, uid: Int): Boolean =
        processName != "system" && uid >= FIRST_APPLICATION_UID
}

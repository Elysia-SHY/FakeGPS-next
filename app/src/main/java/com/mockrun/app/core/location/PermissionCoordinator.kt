package com.mockrun.app.core.location

import android.content.Context
import com.mockrun.app.util.Diag
import com.mockrun.app.util.InjectionModePrefs
import com.mockrun.app.util.PermissionHelper
import com.mockrun.app.util.PermissionIssueType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 权限与注入环境统一协调器 (PermissionCoordinator)
 *
 * 统一所有模块（ViewModel、MapScreen、LocationMockScreen 等）进入模拟/注入前的权限判定与自动授权流程。
 *
 * 核心机制：
 * 1. 优先校验物理系统基础定位权限（ACCESS_FINE_LOCATION / ACCESS_COARSE_LOCATION），缺失则拦截；
 * 2. Root 注入模式：
 *    - 优先通过 RootSuBridge 执行 appops 授权；
 *    - 若 Root 授权命令执行成功，直接放行，避免 AppOpsManager 进程内读取外部 su 写入时存在缓存延迟导致误弹；
 *    - 真正的系统层拦截由 MockLocationService.register (addTestProvider) 在注入时兜底把关；
 * 3. 免 Root 模式：
 *    - 绝对不调用任何 su 命令；
 *    - 严格校验开发者选项中的模拟位置信息应用是否已勾选本应用。
 */
@Singleton
class PermissionCoordinator @Inject constructor(
    private val rootBridge: RootSuBridge
) {
    companion object {
        private const val TAG = "PermissionCoordinator"
    }

    suspend fun resolvePermission(context: Context): PermissionIssueType {
        // 1. 基础系统定位权限检查
        if (!PermissionHelper.hasLocationPermission(context)) {
            Diag.w(TAG, "Missing base location permission")
            return PermissionIssueType.LOCATION_PERMISSION_MISSING
        }

        // 2. Root 模式自动授权与放行
        val isRootMode = InjectionModePrefs.isRootMode(context)
        if (isRootMode && rootBridge.isRootAvailable()) {
            val granted = rootBridge.grantMockLocation(context.packageName)
            if (granted) {
                Diag.d(TAG, "Root grantMockLocation succeeded, granting permission directly")
                return PermissionIssueType.NONE
            } else {
                Diag.w(TAG, "Root grantMockLocation command failed, falling back to standard check")
            }
        }

        // 3. 免 Root 或 Root 降级：走常规 Android 原生权限与开发者选项勾选探测
        return PermissionHelper.checkPrimaryPermissions(context)
    }
}

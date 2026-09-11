# 更新日志 (Changelog)

本项目遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.0.0/) 规范，版本命名采用 [语义化版本 2.0.0](https://semver.org/lang/zh-CN/)。

---

## [1.2.1] - 2026-09-11

### 修复 (Fixed)
- **退出后定位残留问题**：停止模拟后主动向系统请求一次网络与 GPS 真实位置更新，加速刷新 `system_server` 的 `mLastLocation` 缓存；调整硬件扫描设置逻辑，不再篡改系统级辅助扫描开关。
- **Android 12+ 缓存清理适配**：在 `LocationProviderManager.getLastLocation` 中兼容 `LocationResult` 包装对象，停止模拟时置空底层分发的假坐标缓存。
- **MapView 内存泄漏**：在 Compose `AndroidView` 中补齐 `onRelease` 回调，并在离开组合与前后台切换时正确绑定 `onResume` / `onPause` / `onDetach`，清理地图图层与监听器。
- **大路线跨进程传输异常**：由单例状态仓库维护路由对象，避免长航点路线经由 `Intent` 序列化引发 `TransactionTooLargeException`。
- **硬件定位监听器超时清理**：为单次真实位置请求引入 15 秒超时机制，避免在弱信号或无定位环境下系统监听器长期残留。
- **悬浮摇杆与传感器状态同步**：摇杆归中静止时同步发送速度为 0 的传感器心跳，并在服务销毁时重置计步仿真状态。

### 优化 (Changed)
- **构建体积精简**：配置生产级 R8 代码混淆与资源缩减（`isMinifyEnabled` 与 `isShrinkResources`），安装包体积由 56.2 MB 降至 3.53 MB（缩减约 93.7%）。
- **反射调用开销优化**：对 `XposedLocationHook` 中的 `LocationResult` 构造方法引入方法缓存，减少系统进程内的高频反射开销。
- **界面文本规范化**：规范化应用内各模块提示与排查指南文案，去除夸大表述，提升表述准确度。

---

## [1.2.0] - 2026-09-10

### 重点突破 (Highlights)
解决开启单点模拟时主界面卡死停滞、未停止或异常退出导致必须重启手机方可恢复 GPS 的顽固问题。

### 修复 (Fixed)
- **UI 主线程阻塞假死**：将 HookStateBridge 中所有 su 提权命令执行与跨进程文件写入移入 Dispatchers.IO 后台异步线程，杜绝主线程与 su 守护进程建立 IPC 握手时的阻塞与 ANR 风险。
- **启动交互阻断**：移除 startPointMock 中每次开启无条件触发的系统电池优化弹窗打断，点击即时响应。
- **物理真机位置误保存**：在 saveRealLocation 中增加 isHookActive 守卫，模拟生效时严禁将虚假坐标误存为真机物理偏好；增加 clearSavedRealLocation 彻底清理历史脏数据。

### 新增 (Added)
- **20 秒动态心跳超时 (TTL) 机制**：在 SystemProperties (debug.fakegps.time)、/data/system/fake_gps_hook.json、Settings.Global 及 XSharedPreferences 中全量引入时间戳检测；超过 20 秒无心跳自动判定模拟失效并交还真实硬件控制权，告别重启手机。
- **注销测试 Provider**：新增 MockLocationEngine.forceCleanAllTestProviders，调用 removeTestProvider 清理 gps、network、passive、fused，避免残留禁用标志。

---

## [1.1.0] - 2026-09-09

### 新增 (Added)
- **Apple HIG 动态色彩体系与全暗黑模式 (OLED Dark Mode)**：构建全局 IosColorPalette、LightIosColorPalette 与 DarkIosColorPalette，支持浅色纯白与深色纯黑自适应切换。
- **高德地图深色夜间滤镜 (Apple Maps Night Matrix)**：基于高精度 ColorMatrix 色阶矩阵，将纯白路网转换为深邃 #141416 底图，保持高对比度清晰道路与地标。
- **平板端 (Pad / Tablet) 横屏适配**：新增响应式横屏自适应，宽屏下操作控制板与全景地图左右分屏并列展示，彻底解决侧边栏与地图的遮挡冲突。
- **全界面弹窗深色磨砂化**：地点检索弹窗、道路规划弹窗、路线保存库、微信防检测向导全面升级为深色毛玻璃材质。

### 优化 (Changed)
- 地图顶部悬浮地址胶囊适配暗黑高反差文本；
- 优化分段选择器 IosSegmentedControl 与开关 IosSwitch 的微动动效与暗黑底色。

---

## [1.0.0] - 2026-09-09

### 新增 (Added)
- **正式版首发 (Initial Release)**：
  - 支持免 Root 模式、Root 模式与 LSPosed 系统内核级 Hook 三大工作模式；
  - 道路拓扑路线巡航引擎（驾车、骑行、步行三种速度拓扑与 GPX 轨迹导入）；
  - 全局桌面触控悬浮摇杆（80dp ~ 220dp 实时动态阻尼缩放与方向锁定）；
  - 步频生物力学与计步传感器拟真引擎（自适应配速换算与加速度抖动）；
  - 防闪退与防漏点权限引导弹窗，自动检测精确定位权限与开发者选项配置。

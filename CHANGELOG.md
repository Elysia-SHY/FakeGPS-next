# 更新日志 (Changelog)

本项目遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.0.0/) 规范，版本命名采用 [语义化版本 2.0.0](https://semver.org/lang/zh-CN/)。

---

## [1.2.1] - 2026-09-10

### 重点突破 (Highlights)
彻底根除虚拟定位退出后依然停留在假坐标的痛点。废弃破坏性系统设置篡改，重构系统底层 system_server 解包净化逻辑，实现停止后 200ms 毫秒级室内物理定位自愈刷新。

### 修复 (Fixed)
- **解决室内退出后定位残留死锁**：根治旧版执行 settings put secure location_mode 1 与 wifi_scan_always_enabled 0 导致的“仅限传感器”模式。此前手机在室内因无 GPS 卫星且被关死 Wi-Fi 扫描，系统无法计算新位置而无限期待在假坐标；重构后严禁修改系统持久化设置，保持高精度模式畅通。
- **Android 12~15 LocationResult 假坐标清除失败**：高版本 Android 的 LocationProviderManager.getLastLocation 返回 LocationResult 包装对象而非传统 Location。新增 extractLocationFromResult 深度反射解包，退出后精准识别并置空 (param.result = null) 系统底层缓存。
- **UI 操作误区纠偏**：主界面与微信向导弹窗中的“一键关闭硬件扫描”按钮全面纠偏为「🛡️ 一键恢复高精度与硬件扫描」，避免用户误操作关闭系统探针。

### 新增 (Added)
- **estoreScanningHardware() 自动自愈**：在停止模拟、服务销毁以及 App 启动时，主动校正系统设置至 location_mode 3（高精度模式）并开启 Wi-Fi/BLE 后台扫描，自动修复历史受影响的设备。
- **lushRealLocation 毫秒级冲刷机制**：退出模拟时，并发唤醒 NETWORK_PROVIDER 与 GPS_PROVIDER 请求单次真实位置，借助室内 Wi-Fi 在 200~300ms 内重刷 system_server 的 mLastLocation 缓存。
- **退离坐标传递净化**：在 update(active = false) 时保留退出瞬间的经纬度坐标，确保 Hook 模块知晓需清洗的目标假位置。

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
- **强力注销所有测试 Provider**：新增 MockLocationEngine.forceCleanAllTestProviders，直接调用 emoveTestProvider 剥离 gps、
etwork、passive、used，绝不再调用易残留禁用标志的 setTestProviderEnabled(false)。

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

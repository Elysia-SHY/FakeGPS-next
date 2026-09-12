# 更新日志 (Changelog)

本项目遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.0.0/) 规范，版本命名采用 [语义化版本 2.0.0](https://semver.org/lang/zh-CN/)。

## [1.4.0] - 2026-09-12

> 本版为**可观测性专项**：不改变任何业务逻辑分支，不新增特权能力，只让原本静默失败的路径留下记录。

### 新增 (Added)

- **统一诊断出口 `Diag`**（`com.mockrun.app.util`）：
  - 同时适配两种运行形态 —— App 进程用 `android.util.Log`，被注入进程（`system_server` 与各被 Hook 应用）额外镜像到 `XposedBridge` 日志；
  - 因 Xposed API 声明为 `compileOnly`，`XposedBridge` 在 App 进程不在 classpath，故通过**反射惰性解析**，缺失时静默降级；
  - **内置按标签限流**（10 秒窗口 / 5 条）：这些站点大多位于「每次位置分发」的高频路径且运行在 `system_server`，不限流会刷爆日志并实际消耗 CPU；
  - 全程异常包裹，诊断失败绝不向宿主进程传播。
- **`Result<T>.logFailure(tag, what, level)` 扩展**：以**纯增量**方式接入既有 `runCatching` 调用链，不改变控制流。

### 修复 (Fixed)

- **在线更新误报「有新版本」**：`VersionSyncManager.extractVersionCode` 原先在 Release 说明中找不到 `versionCode:` 时，会退化成「把版本标签按十进制拼接」——`v1.3.9` 被算成 `139`，再与 `versionCode`(15) 直接比较，`139 > 15` 恒为真，导致**每次启动都弹出更新提示**。现改为返回未知哨兵并由语义化版本比较兜底。
- **更新包完整性缺失**：下载的 APK 此前仅校验「文件大于 1MB」就交给系统安装器，而下载源包含第三方镜像。现新增签名证书比对，与已安装应用签名不一致即拒绝安装（fail-closed）。

### 优化 (Changed)

- **静默失败路径系统性收口**：`XposedLocationHook` 中 94 个 `runCatching` 站点已有 63 个携带失败记录，其余 31 处为**刻意保留的静默**（有合理默认值、或属可选能力的探测），逐条理由见提交说明。重点覆盖：
  - `createLocationResult` —— 坐标包装失败会让伪造坐标静默不生效，现在会打出具体类名；
  - `getGlobalActiveLocation` —— 6 个配置通道全部失败时此前无任何输出，设备只是安静地继续上报真实位置，现已在终点告警；
  - Hook 安装路径 —— 新增「静默零」守卫，当全部候选类名都未命中时显式告警，不再让「ROM 改了类名」与「无事可做」无法区分。
- **仓库整理**：取消跟踪根目录 `FakeGPS-next-v1.3.7-release.apk`（本地文件保留），并将 `.workbuddy/` 加入忽略列表。

### 说明 (Notes)

- **本版未经真机验证**：改动均为纯增量的日志与异常记录，编译验证通过，但效果需在真机上确认。
- 已知未处理项：`HookConfigProvider` 与 `AdbCommandReceiver` 仍为 `exported=true` 且无权限保护；`HookStateBridge` 中的 `chmod 666` 未收敛。这两项需先做 IPC 设计确认，未纳入本版。

---

## [1.3.7] - 2026-09-12

### 优化与突破 (Highlights)
- **极致通透液态毛玻璃（Liquid Glass Refraction）**：
  - 彻底重构玻璃拟态着色管线，摒弃扁平单调半透明遮罩；
  - 引入高透光率多阶微偏光晶体渐变底衬（32%~72% 透光率），底层地图道路、地标及图钉清晰穿透；
  - 135° 菲涅尔全反射微棱镜双光边框（98% 纯净高光耀斑与微色彩折射边缘）；
  - 沿圆角内壁绘制 1.2dp 微倒角立体高光内沿，模拟 3mm 物理晶体厚度感；
  - 顶部镜面掠射弧光与底部漫反射环境反光，质感晶莹流光溢彩。
- **纯净前景色渲染保护**：
  - 将折射高光与棱镜光影下沉至 `drawBehind` 专用底层管道，100% 保护前景文字、图标与交互控件的原生锐利度与高对比度，彻底根除白屏或文字隐形隐患。
- **更新弹窗容器通透化**：
  - 修正更新弹窗背景色覆盖问题，使弹窗在开启毛玻璃时完美呈现全透晶莹质感。

---

## [1.3.6] - 2026-09-12

### 新增 (Added)
- **全新 GitHub Release + jsDelivr CDN 在线更新机制**：官方 Release API 智能判定，jsDelivr 全球加速分发与多节点容灾，内置流式下载面板与 Android 8~15 原生安装器全自动化唤起。
- **开源生态致谢专栏**：关于页面新增开源致谢专栏，完整列出 Chris Banes Haze、OSMDroid、LSPosed、Jetpack Compose 等开源项目与 GitHub 直达链接。

---

## [1.3.2] - 2026-09-12

### 新增 (Added)
- **云端版本自动同步服务 (VersionSyncManager)**：引入三级容灾架构（jsdelivr CDN、raw.githubusercontent 及 GitHub Releases API），实现全天候无阻碍版本检测与一键热同步。
- **介绍页全景重构与美化 (AboutScreen Revamp)**：
  - 引入版本对比矩阵与状态胶囊，实时显示本地版本、云端最新版本及最后同步时间；
  - 核心功能技术微标签矩阵（0 注入特征、AOSP 8~15 全兼容、向心减速、动态信噪比等）；
  - 新增设备环境与运行诊断卡片，实时呈现设备型号、Android 版本（API 级别）、CPU 架构与构建模式。
- **新版本更新说明速览弹窗**：支持在应用内直接查看云端发布的更新日志，并提供一键前往 GitHub Release / 下载 APK 快捷通道。

### 优化 (Changed)
- **清理清单文件冗余**：去重 `AndroidManifest.xml` 中重复声明的 `HookConfigProvider`。
- **根目录版本元数据维护**：在项目根目录新增 `version.json`，提升版本同步吞吐率与抗限流能力。

---

## [1.3.1] - 2026-09-12

### 修复 (Fixed)
- **系统框架级独立分流闭环**：在清单中注册并导出 `HookConfigProvider`，动态拦截 `LocationProviderManager$Registration` 全量派生类，提取真实 `CallerIdentity` 消除派发 UID 假冒问题。
- **防位移丢包机制**：在定点驻留模式下抑制 AOSP `minUpdateDistanceMeters` 阈值，注入递增单调时钟避免无位移被底层过滤。
- **文案与排版修复**：消除首页排查指南中的历史遗留乱码符号，规范使用指引。

---

## [1.3.0] - 2026-09-11

### 新增 (Added)
- **多应用独立分流虚拟定位 (Multi-Instance Routing)**：突破单一全局坐标，基于调用方应用 UID 与 User ID（支持应用双开/多开空间）实现独立分流。不同应用可驻留不同虚拟坐标，未配置应用与系统地图可直接使用真实物理定位。
- **地图多目标胶囊选择与多色 Pin 联动**：地图顶部新增横向应用胶囊选择器，直观切换当前操作目标；在底图图层中为各分流应用赋予独立高对比度色彩的定位标点（Pin），并支持点击图钉直接聚焦。
- **离线物理运动动力学引擎 (Kinematics Engine)**：
  - 基于三点外接圆几何算法实时评估道路曲率，提供向心加速度弯道减速约束（\(v \le \sqrt{a_{\max} \cdot R}\)），避免直角弯道或掉头时的突兀瞬移。
  - 基于 Ornstein-Uhlenbeck 均值回归随机过程生成平滑道路微起伏高程，避免恒定 20.0m 海拔特征。
- **动态 GNSS 卫星星座仿真 (Synthetic GNSS)**：底层合成包含北斗（BDS）、GPS 及 GLONASS 的多星座卫星数据，动态模拟天顶角、方位角与微动载噪比（\(C/N_0\)），辅助应对无卫星检测异常。
- **应用分流规则管理**：首页新增分流规则卡片列表与已安装应用快速选择抽屉，支持随时切换定点驻留、路线巡航与物理透传模式。

### 优化 (Changed)
- **系统设置解耦**：非 Root 环境下避免修改系统全局持久化定位开关，保持系统设置原始状态。
- **高频跨进程路由缓存**：在系统进程维护并发规则映射表，降低分流判定开销。

---

## [1.2.2] - 2026-09-11

### 修复 (Fixed)
- **MapView 内存泄漏**：在 Compose `AndroidView` 中补齐 `onRelease` 回调，并在离开组合与前后台切换时正确绑定 `onResume` / `onPause` / `onDetach`，彻底清理地图图层与渲染监听器。
- **大路线跨进程传输异常**：由单例状态仓库维护路由对象，避免长航点路线经由 `Intent` 序列化引发 `TransactionTooLargeException` 崩溃。
- **硬件定位监听器超时清理**：为单次真实物理位置请求引入 15 秒超时自动清理机制，避免在弱信号或无定位环境下系统监听器长期残留。
- **悬浮摇杆与传感器状态同步**：摇杆归中静止时同步发送速度为 0 的传感器心跳，并在服务销毁时重置计步仿真引擎。

### 优化 (Changed)
- **构建体积精简**：在 Release 构建中配置生产级 R8 代码混淆与资源缩减（`isMinifyEnabled` 与 `isShrinkResources`），安装包体积由 56.2 MB 降至 **3.53 MB**（缩减约 93.7%）。
- **反射调用开销优化**：对 `XposedLocationHook` 中的 `LocationResult` 构造方法引入方法缓存，减少系统进程内的高频反射开销。

---

## [1.2.1] - 2026-09-10

### 修复 (Fixed)
- **退出后定位残留问题**：停止模拟后主动向系统请求一次网络与 GPS 真实位置更新，加速刷新 `system_server` 的 `mLastLocation` 缓存；调整硬件扫描设置逻辑，不再篡改系统级辅助扫描开关。
- **Android 12+ 缓存清理适配**：在 `LocationProviderManager.getLastLocation` 中兼容 `LocationResult` 包装对象，停止模拟时置空底层分发的假坐标缓存。

### 优化 (Changed)
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

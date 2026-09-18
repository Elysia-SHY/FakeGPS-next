# Version Tracker - FakeGPS-next

> **版本真源**：`app/build.gradle.kts` 的 `versionCode` / `versionName`。`version.json`、`package.json`、README 与 CHANGELOG 均以其为准同步。

## 当前状态 (Current State)，更新于 2026-09-18

| 项目 | 值 |
| :--- | :--- |
| **最新已发布版本** | `v1.4.4`（由 `d294304` 上的 Hook 改动触发构建） |
| **Android 内部版本** | `versionCode = 21` |
| **Android 显示版本** | `versionName = "v1.4.4"` |
| **本版产物提交** | `0f52f6b`（= tag `v1.4.4`，CI 提交构建产物） |
| **相对 v1.4.3 差异** | 只改系统层 Hook：Provider 状态查询、注册登记、硬件静默时主动喂点、伪造点零值补齐 |
| **上一条已发布版本** | `v1.4.3`（tag `v1.4.3` → `cbb1edd`，versionCode = 20） |
| **交付存放目录** | `D:\Desktop\fake gps\` |
| **最新安装包路径** | `D:\Desktop\fake gps\FakeGPS-next-v1.4.4-release.apk` |
| **仓库内 Release 产物** | `FakeGPS-next-v1.4.4-release.apk`（已被 git 跟踪，jsDelivr CDN 依赖此路径） |
| **发布方式** | GitHub Actions [`.github/workflows/release.yml`](.github/workflows/release.yml)（`workflow_dispatch` 或 push tag `v*`） |
| **签名证书 SHA-256** | `0f8d4bea2db592a239dbc2eab8de441ff26dd6f1e244bfe4dbff099c1072761e`（`CN=FakeGPS-next`，CI 固定密钥，v1.4.2 起未变） |

### v1.4.4 构建信息

- **构建时间**：2026-09-18（由 CI 产出，run `35319615574`）
- **构建环境**：GitHub Actions `ubuntu-latest`，JDK 17（temurin），Android SDK `platforms;android-34` + `build-tools;34.0.0`
- **构建命令**：`sh ./gradlew :app:assembleRelease --no-daemon --stacktrace`
- **产物绝对路径**：`app/build/outputs/apk/release/app-release.apk`
- **APK 大小**：3,906,652 bytes
- **APK SHA-256**：`03a4c3489967a05131b5b8863440f0f7ec5f1ae55fcf51f1a8fa163c3bac82cb`
- **Release**：https://github.com/Elysia-SHY/FakeGPS-next/releases/tag/v1.4.4
- **签名证书**：`CN=FakeGPS-next, OU=CI Release, O=Elysia-SHY, C=CN`（与 v1.4.2 同一张证书，可直接覆盖安装）
- **编译验证**：`./gradlew :app:compileDebugKotlin` 通过；**未做真机验证**

### v1.4.3 构建信息

- **构建时间**：2026-09-15 17:34 UTC（由 CI 产出）
- **构建环境**：GitHub Actions `ubuntu-latest`，JDK 17（temurin），Android SDK `platforms;android-34` + `build-tools;34.0.0`
- **构建命令**：`sh ./gradlew :app:assembleRelease --no-daemon --stacktrace`
- **产物绝对路径**：`app/build/outputs/apk/release/app-release.apk`
- **APK SHA-256**：`1d79a9e1918cfe3bc79cea0e6b38f620cc27aaf1852a54493e815f04c3db99f3`
- **签名证书**：`CN=FakeGPS-next, OU=CI Release, O=Elysia-SHY, C=CN`（与 v1.4.2 同一张证书，可直接覆盖安装）
- **下载路径验证**：Release 资产、jsDelivr `@v1.4.3`、raw `@v1.4.3` 三路均已实测可解析

### 已知版本治理问题（待处理）

1. **v1.3.8 与 v1.3.9 从未打 tag、未发 Release**，仅存在于提交历史（`96e4004` / `1ac36a2`）。
2. **versionCode 时序错乱**：`v1.3.7=16` > `v1.3.9=15` > `v1.3.8=14`。v1.3.7 实际是 v1.3.9 之后的回退重发基线。
3. **签名断点位于 v1.4.2**：该版起 CI 改用固定密钥 `keystore/fakegps-signing.jks`，与 v1.4.1 及更早版本的 debug 签名（证书 SHA-256 `f4d59c6d…`）不同，**从 v1.4.1 升级到 v1.4.2/1.4.3 需先卸载**——会清除本机收藏与分流配置；v1.4.2 → v1.4.3 及之后的版本之间签名一致，可直接覆盖。若要恢复与旧版的签名连续，把项目一直使用的 `~/.android/debug.keystore` 以 base64 填入仓库 Secret `ANDROID_KEYSTORE_BASE64`（配套 `ANDROID_KEYSTORE_PASSWORD` / `ANDROID_KEY_ALIAS` / `ANDROID_KEY_PASSWORD`）后重跑一次 workflow。
4. **`gradlew` 可执行位**：仓库长期以 `100644` 跟踪该脚本（Windows 提交时丢失权限位），已在 v1.4.2 修正为 `100755`，workflow 同时改用 `sh ./gradlew` 以免再受权限位影响。

> **头部维护说明**：此前头部长期停留在 `1.2.1 / versionCode = 4`，与实际严重脱节。已按 `app/build.gradle.kts`（Android 版本的唯一真源）重建。后续请以 `build.gradle.kts` 为准同步此处。

---

## 版本演进与变更日志 (Version History)

### [1.4.3] - 2026-09-16

- **新增右上角收藏夹入口**：定位标签与路线标签的右上角工具胶囊新增收藏夹按钮（紧邻底图切换）。定位标签列出单点收藏的地点（点按跳转定位、设为当前目标，不写入绘制航点与已选路线）；路线标签列出航线（点按载入为当前路线，标注当前已载入项）。
- **数据分流**：`MapViewModel` 新增 `bookmarkedLocations`（`waypoints.size <= 1`）与 `savedTracks`（`waypoints.size >= 2`）两个派生 `StateFlow`，按航点数区分两类收藏，无需 DB 迁移。
- **新增组件** `ui/components/BookmarkBottomSheet.kt`（ModalBottomSheet 风格，含空态引导与二次确认删除）。
- 详见 [CHANGELOG.md](CHANGELOG.md)。

### [1.4.2] - 2026-09-15

- **修复首页「收藏此点」失效**：`saveCurrentRoute()` 读取手绘航点并要求 >= 2 个点，而定位标签下该列表恒为空，收藏静默返回却仍提示成功。新增 `MapViewModel.saveLocationPoint()` 以单点构造 `Route` 入库，并按实际结果反馈。
- **修复 release 构建下「收藏路线」必闪退**：`RouteRepository.toDomain()` 的 `object : TypeToken<List<WayPoint>>() {}` 在 R8 下丢失泛型签名，Gson 抛 `IllegalStateException`，且该语句位于 `runCatching` 之外，异常直接终止进程。改用 `TypeToken.getParameterized()`；`MultiTargetRepository` 的同类写法一并修正（此前不崩溃，但分流规则整批读不出来）；`proguard-rules.pro` 补入 Gson 的 TypeToken 保留规则。
- **发布流程迁移到 GitHub Actions**：新增 `.github/workflows/release.yml`，一次触发完成构建 → 提交 APK 进仓库树 → 打 tag → 创建 Release；签名切换为 CI 固定密钥。
- **回滚至 v1.3.7 UI 基线**（承自 `3b7463e`）：撤销 `c9ca650` 至 `8c7bc10` 的液态玻璃改动，`LiquidGlassModifier.kt` 与 `RouteLibraryScreen.kt` 恢复至 `0a1eeb0`；`LiquidGlassShader.kt` 删除。原因：多轮 iOS 26 风格玻璃效果迭代后，真机背景模糊表现仍未达预期，项目所有者要求停止迭代。
- 详见 [CHANGELOG.md](CHANGELOG.md)。

### [1.3.9] - 2026-09-12

- **构建状态**：未打 tag、未发 Release（提交 `1ac36a2`，`versionCode = 15`）。
- **核心变更**：修复滑动时卡片模糊画面偏移，精简自定义壁纸，上移浮动按钮避让，移除地图模糊残影。

### [1.3.8] - 2026-09-12

- **构建状态**：未打 tag、未发 Release（提交 `96e4004`，`versionCode = 14`）。
- **核心变更**：卡片毛玻璃改为全局通用（双 `HazeState`），关于页支持背景自定义，底栏拖动跟随联动。

### [1.4.1] - 2026-09-12

- **构建状态**：`assembleRelease` 成功，产物 3,884,308 bytes（3.7 MB），路径 `app/build/outputs/apk/release/app-release.apk`。
- **Android 配置**：`versionCode = 18`，`versionName = "v1.4.1"`。
- **本版定位**：安全加固，不新增功能，不改动业务逻辑分支。
- **核心变更**：
  1. **`HookConfigProvider` 按 uid 收闸**：该 Provider 必须保持 `exported` 才能服务 `system_server`（signature 权限会挡掉系统框架），但此前任意第三方应用都可调用 `getLocation` 读到当前伪造坐标，并探测应用是否激活。现只放行自身、system、root 与 shell。
  2. **配置文件 0666 → 0644**：世界可读是 hook 跨进程读取的设计必需，但世界可写没有必要。原权限下任何应用都能改写伪造坐标。现保留只读、去掉篡改通道。涉及 `/data/system/fake_gps_hook.json`、`/data/local/tmp/fake_gps_hook.json`、`shared_prefs/hook_config.xml`。
  3. **`AdbCommandReceiver` 加 `WRITE_SECURE_SETTINGS` 权限**：adb shell 默认持有、普通应用没有，既保留 adb 用法，也阻断任意应用驱动模拟。已确认应用自身不发这些广播（`SimulationViewModel` 直接 `startService`）。
  4. 修复 `android.os.Process` 与 `java.lang.Process` 导入冲突导致的编译失败。
- **验证状态**：未经真机验证。产物权限已用 `aapt2 dump xmltree` 反查确认写入 APK manifest。
- **已知取舍**：hook 运行于第三方应用进程时，其 ContentProvider 兜底通道失效（该通道本就是末位兜底，且现会留痕）。

### [1.4.0] - 2026-09-12

- **构建状态**：`assembleRelease` 成功，R8 混淆与资源压缩通过，产物 3,883,552 bytes（3.7 MB），路径 `app/build/outputs/apk/release/app-release.apk`。
- **Android 配置**：`versionCode = 17`，`versionName = "v1.4.0"`。
- **本版定位**：可观测性专项。不改动业务逻辑分支，不新增特权能力，只让原本静默失败的路径留下记录。
- **核心变更**：
  1. **新增统一诊断出口 `Diag`**：同时适配 App 进程（`android.util.Log`）与被注入进程（额外镜像到 `XposedBridge`）；因 Xposed API 为 `compileOnly`，`XposedBridge` 用反射惰性解析，缺失即静默降级；内置按标签限流（10 秒 5 条），避免在 `system_server` 高频路径刷屏；全程异常包裹，诊断失败不向宿主传播。
  2. **新增 `Result<T>.logFailure()` 扩展**：以纯增量方式接入既有 `runCatching` 链，不改变控制流。
  3. **静默失败收口**：`XposedLocationHook` 的 94 个 `runCatching` 站点中，63 个已携带失败记录（覆盖率经括号配平与链式行走扫描核验，非 grep 估算），其余 31 处为刻意保留的静默并逐条注明理由。重点覆盖 `createLocationResult`、`getGlobalActiveLocation`、Hook 安装路径，并新增 4 处「静默零」守卫。
  4. **修复在线更新误报**：`extractVersionCode` 不再把版本标签按十进制拼接（`v1.3.9` 曾被算成 `139`，与 `versionCode` 比较后导致每次启动都误报更新）。
  5. **新增更新包签名校验**：下载的 APK 须与已安装应用签名证书一致，否则拒绝安装。
  6. **仓库整理**：取消跟踪根目录 APK 产物（本地保留），`.workbuddy/` 加入忽略列表，`package.json` 由 `1.1.0` 校正为 `1.4.0`。
- **验证状态**：未经真机验证。改动均为纯增量日志与异常记录，编译验证通过，效果待真机确认。
- **已知未处理项**：`HookConfigProvider` 与 `AdbCommandReceiver` 仍 `exported=true` 且无权限保护；`HookStateBridge` 的 `chmod 666` 未收敛。两项均需先做 IPC 设计确认。

### [1.3.7] - 2026-09-12

- **构建状态**：已发布 GitHub Release（`versionCode = 16`，`versionName = "v1.3.7"`），产物 3.87 MB。
- **核心变更**：移除实时模糊与 Haze 渲染，改用静态晶体微光材质；解决滑动时卡片画面漂移与图层冲突；合并单例 MapView；底栏手势平移与防遮挡布局。

### [1.2.1] - 2026-09-10

- **构建状态**：解决退出后定位残留，修复因篡改系统扫描配置导致室内无法定位的问题（产物 `outputs/app-debug.apk`）。
- **Android 配置**：`versionCode = 4`，`versionName = "v1.2.1"`。
- **核心修复与特性**：
  1. **修复系统级硬件扫描设置被篡改、室内定位瘫痪的问题**：
     - 废除 `disableScanningHardware()`（原命令强设 `location_mode 1` 并关闭 `wifi_scan_always_enabled`，导致室内无 GPS 信号时也被剥夺基站与 Wi-Fi 定位能力）；
     - 实现 `restoreScanningHardware()`，在停止模拟、服务销毁以及 App 启动时主动执行 `settings put secure location_mode 3` 并重开 Wi-Fi 与 BLE 扫描，自愈修复受影响的环境；
     - 遵循成熟开源项目的做法：Wi-Fi 与基站探针屏蔽交由 Xposed 在内存级动态拦截，不修改 Android 全局系统设置。
  2. **Android 12 至 15 的 `LocationResult` 解包与假坐标清洗**：
     - 新增 `extractLocationFromResult`，兼容系统高版本的 `LocationResult` 与经典 `Location` 类型；
     - 当 Hook 处于非激活状态时，比对提取出的经纬度与停止前模拟的坐标，若吻合则将系统 `mLastLocation` 判定结果置为 `null`，促使系统与微信、高德等客户端重新调用底层硬件。
  3. **主动硬件定位冲刷 (`flushRealLocation`)**：
     - 停止虚拟定位时，同时向 `NETWORK_PROVIDER` 与 `GPS_PROVIDER` 发起即时单次定位请求；
     - 室内通常仅需 200 至 300 毫秒即可借助周围 Wi-Fi 路由器取得真实位置，更新系统全局定位缓存。
  4. **UI 文案纠偏**：
     - 主界面与微信向导弹窗统一改为「一键恢复高精度与硬件扫描」，避免用户误操作关闭硬件探针。

### [1.2.0] - 2026-09-10

- **构建状态**：解决开启定位后主界面卡死停滞，以及关闭定位后残留虚假坐标、需重启手机才能复原的问题（产物 `outputs/app-debug.apk`）。
- **Android 配置**：`versionCode = 3`，`versionName = "v1.2.0"`。
- **核心修复与特性**：
  1. **解决开启定位后主界面卡顿与停滞**：
     - 将 `HookStateBridge` 中的 Root 命令执行（`Runtime.exec("su")`）及跨进程文件写入移入 `Dispatchers.IO` 后台线程，避免主线程与 root 守护进程建立 IPC 握手时阻塞造成 ANR；
     - 移除 `startPointMock` 中每次开启都无条件触发的系统电池优化弹窗，按钮点击即时反馈。
  2. **引入 20 秒心跳超时 (TTL) 机制**：
     - 在 SystemProperties (`debug.fakegps.time`)、`/data/system/fake_gps_hook.json`、`Settings.Global` 及 XSharedPreferences 中引入时间戳检测；
     - 若 App 崩溃、被清理杀后台或服务停止后超过 20 秒无心跳，系统级 Hook 自动失效归位并返回真实定位。
  3. **清理系统级 mLastLocation 假坐标**：
     - 检测到模拟停止时，若系统框架 `LocationProviderManager` 或 `LocationManagerService` 的 `getLastLocation` 仍返回此前注入的坐标，Hook 将其置空（`param.result = null`），促使系统与各 App 重新向物理硬件申请定位。
  4. **注销 Mock Test Provider 与真机缓存保护**：
     - 新增 `MockLocationEngine.forceCleanAllTestProviders` 静态方法，移除 `gps`、`network`、`passive`、`fused` 等测试 Provider，且不在移除前调用可能残留禁用标记的 `setTestProviderEnabled(false)`；
     - 新增 `CoordinateConverter.clearSavedRealLocation`，并在 `saveRealLocation` 中加入 `HookStateBridge.isHookActive` 守卫，避免开启模拟时把虚假坐标误存为真机物理位置。

### [1.1.0] - 2026-09-09

- **构建状态**：完成全功能模块的暗黑模式适配、高德地图夜间色阶矩阵滤镜重构与 Apple HIG 动态色彩系统升级（产物 `outputs/app-debug.apk`）。
- **Android 配置**：`versionCode = 2`，`versionName = "v1.1.0"`。
- **核心特性与修复**：
  1. **高德地图深色夜间模式 (Apple Maps Style Night Matrix)**：
     - 地图图层菜单新增「高德路网 (跟随系统)」与「高德路网 (深色夜间)」选项；
     - 基于 ColorMatrix 的夜间滤镜，将白天纯白路网转换为深色底图 `#141416`，同时保持路网与地标的对比度；
     - 修复地图顶部地址胶囊在深色模式下的文字对比度问题，自适应为白色高亮文字。
  2. **Apple HIG 动态语义色彩系统 (Dynamic IosColorPalette)**：
     - 构建 `IosColorPalette`、`LightIosColorPalette` 与 `DarkIosColorPalette`，通过 `LocalIosColors` 全局注入；
     - 基础组件（`IosColors.Label`、`IosColors.SecondaryLabel`、`IosColors.SystemGroupedBackground`、`IosColors.SecondaryGroupedBackground`、`IosColors.TertiarySystemFill`、`IosFrostedCapsule`、`IosHairlineBorder`）自适应明暗切换；
     - `IosSwitch` 增加暗黑版深灰轨道底色 (`#39393D`)，`IosSegmentedControl` 适配深灰选中胶囊 (`#636366`)。
  3. **功能界面与弹窗暗黑配色适配**：
     - 修复主控状态与虚拟定位界面 (`LocationMockScreen`)、路线模拟界面 (`RouteSimulationScreen`)、路线库界面中的原生白色弹窗；
     - 地点检索弹窗 (`SearchLocationDialog`)、道路规划弹窗 (`RoadRouteDialog`)、路线保存弹窗、微信检测向导、防杀后台保活弹窗统一适配深色磨砂材质与高对比度文本。

### [2.9.0] - 2026-09-09

- **构建状态**：非 Root 环境下隐藏高阶仿真功能，摇杆下拉控制面板重构为 iOS 极简卡片，GitHub 文案统一规范（产物 `outputs/app-debug.apk`）。
- **Android 配置**：`versionCode = 209`，`versionName = "v2.9"`。
- **交付与目录规整**：
  1. 所有构建产物统一置于 `d:\Desktop\fake gps\` 文件夹；
  2. 交付产物：`FakeGPS-v2.9-LATEST.apk`、`FakeGPS-v2.9.apk`，清理过时旧版安装包，仅保留当前版本与原始参考基准；
  3. GitHub 仓库同步最新源码与 Release。
- **核心功能与交互演进**：
  1. **Root 功能自适应隐藏**：
     - 「路线模拟」页中的「运动步频与计步仿真」与「GPS 底层仿真与抗检测」受 `isRootActive` 状态守卫保护；非 Root 手机或未授权环境下自动隐藏，有 Root 环境时高亮展示；
     - 明确软件的三档工作模式：免 Root 模式（开发者选项模拟位置）、Root 模式（解锁传感器仿真）、LSPosed 模块（可选的系统级增强）。
  2. **摇杆下拉控制面板重构 (iOS Translucent Options Card)**：
     - 重构原先青绿色（Teal）下拉面板；
     - 切换胶囊改为 iOS 风格圆角胶囊 `[ 控制 ]` / `[ 收起 ]`；
     - 控制面板改为 32px 圆角磨砂卡片，采用 Apple Blue (`#007AFF`)、Apple Green (`#34C759`) 与红色标签 (`#FF3B30`)；
     - 巡航速度、尺寸预设、方向模式与八方向锁定采用 iOS 分段胶囊排布；
     - 非 Root 设备自动剔除「步频仿真」行。

### [2.8.0] - 2026-09-09

- **构建状态**：修复切换步频模式时预估步幅无响应，以及滑动配速条导致累计步数异常上涨的逻辑漏洞（产物 `outputs/app-debug.apk`）。
- **Android 配置**：`versionCode = 208`，`versionName = "v2.8"`。
- **交付与目录规整**：
  1. 所有构建产物统一置于 `d:\Desktop\fake gps\` 文件夹；
  2. 交付产物：`FakeGPS-v2.8-LATEST.apk`、`FakeGPS-v2.8.apk`。
- **核心修复与交互重构**：
  1. **修复滑动配速条导致步数暴涨的逻辑 Bug (Phantom Step Accumulation Fix)**：
     - 原因定位：滑动配速滑块时，每帧触发的高频 `applySpeed(speed)` 中错误调用了 `sensorEngine.updateTick(speed, 1f)`，导致每次微调配速都向累计步数强行增加步数；
     - 修复方案：移除配速滑动对 `updateTick` 的误调，步数仅在路线模拟真正运行（Service 后台驱动）时按物理周期增长；
     - 引入小数级步数累加器 (`fractionalSteps`)，按毫秒级周期统计步数，避免提前舍入造成步数虚高。
  2. **切换步频模式与配速时预估步幅实时响应**：
     - 原因定位：原先预估步幅只在运行中的固定 tick 回调里更新，未运行时步频固定显示 `-- SPM`、步幅固定为 `0.75m`，导致点击预设步频（健步 110、慢跑 160、跑马 180、自定义、自适应）时面板没有反应；
     - 修复方案：
       - 基于运动生物力学公式 `步幅 = 速度 (m/s) / (步频 / 60)`，在 UI 渲染层与引擎层实现双向响应；
       - 切换步频或滑动配速条时，预估步幅与步频看板实时刷新；
       - 例如 8 km/h 配速下：点击「健步 110」步幅变为 **1.21 m**，点击「慢跑 160」变为 **0.83 m**，点击「跑马 180」变为 **0.74 m**；滑动配速至 12 km/h，步幅平滑延展至 **1.11 m**。

### [2.7.0] - 2026-09-09

- **构建状态**：修复「重置位置」被虚假 Mock 缓存劫持的问题，重构地图底部 UI 层级布局以消除重叠冲突（产物 `outputs/app-debug.apk`）。
- **Android 配置**：`versionCode = 207`，`versionName = "v2.7"`。
- **交付与目录规整**：
  1. 所有构建产物统一置于 `d:\Desktop\fake gps\` 文件夹；
  2. 交付产物：`FakeGPS-v2.7-LATEST.apk`、`FakeGPS-v2.7.apk`。
- **核心修复与交互优化**：
  1. **修复「重置位置」显示虚拟定位的问题 (Strict Real GPS Recovery)**：
     - 原因定位：开启虚拟定位时，系统 `LocationManager.getLastKnownLocation()` 会被 Mock Test Provider 注入的虚假位置污染，复位时读到的仍是虚假位置；
     - 处理方式：
       - `CoordinateConverter.isMockLocation` 过滤 `loc.isMock` (Android 12+) 与 `loc.isFromMockProvider`，排除 Mock 数据；
       - 本地持久化保存通过硬件传感器实际取得的真实 GPS 坐标；
       - 点击「重置位置」时立即调用 `stopPointMock` / `stopSimulation`，注销并清理底层 Test Provider (`removeTestProvider`)，让系统硬件定位驱动收回主控权；
       - 主动请求单次硬件 GPS 定位，并将地图平滑聚焦至 Apple Maps 风格的发光蓝环真机位置。
  2. **重构地图底部 UI 布局，解决组件重叠与折行遮挡**：
     - **目标点信息卡片改为双行结构**：原先 6 个横向按钮挤在一行，现第一行展示状态标签、经纬度、地理地址与关闭按钮，第二行用弹性权重 (`Modifier.weight`) 均匀排布「恢复真实 / 开启定位」、「划线」、「起」、「终」按钮，在 360 至 390dp 窄屏上不折行重叠；
     - **悬浮层级高度锚定**：底栏卡片与连续划线状态条统一定位在 `bottom = 86.dp`（高出 70dp 悬浮底栏 16dp）；
     - **右侧浮动按钮动态避让**：右侧按钮改为 46dp 圆形玻璃质感悬浮钮（划线模式钮与重置位置钮）；底部卡片展开或处于划线模式时升至 `bottom = 205.dp`，浏览模式下降至 `bottom = 145.dp`，保持 25dp 以上的可视间距。

### [2.6.0] - 2026-09-09

- **构建状态**：上线地图选点「重置位置」即时呈现真实物理位置功能，新增发光蓝环光标与定位协同交互（产物 `outputs/app-debug.apk`）。
- **Android 配置**：`versionCode = 206`，`versionName = "v2.6"`。
- **交付与目录规整**：
  1. 所有构建与基准 APK 统一规整至 `d:\Desktop\fake gps\` 文件夹；
  2. 交付产物：`FakeGPS-v2.6-LATEST.apk`、`FakeGPS-v2.6.apk`。
- **地图选点「重置位置」与真实位置呈现**：
  1. **真实物理位置光标 (Real Location Puck)**：
     - 在底图图层中常驻绘制发光蓝环光标（蓝色实体核心、白色外框、半透明光晕）；
     - 硬件 GPS 或网络提供商捕获到真实定位时，地图上实时呈现物理位置，点击该光标可查看或设为目标点；
  2. **「重置位置」悬浮胶囊按钮**：
     - 地图右下角的图标升级为悬浮胶囊 `[ 重置位置 ]`；
     - 点击后主动向硬件 GPS 发起即时定位拉取，并将地图平滑聚焦至真实物理位置；
     - 自动标定当前真实位置为选中目标点，并在底部弹出「当前真实物理位置」卡片，包含经纬度与逆地理编码地址；
  3. **恢复真实位置**：
     - 若当前已开启虚拟定位，点击「重置位置」后底部卡片提供「恢复真实」按钮，点击即停止模拟、恢复硬件 GPS；
  4. **跨页面联动**：
     - 在「虚拟定位」页点击「重置为真机物理位置」，同样会停止模拟、复位坐标并跳转到「地图选点」页面。

### [2.5.0] - 2026-09-09

- **构建状态**：加固系统后台保活与防杀机制，集成路线模拟中的步频与 GPS 底层仿真（产物 `outputs/app-debug.apk`）。
- **Android 配置**：`versionCode = 205`，`versionName = "v2.5"`。
- **交付与目录规整**：
  1. 所有构建与基准 APK 统一规整至 `d:\Desktop\fake gps\` 文件夹；
  2. 交付产物：`FakeGPS-v2.5-LATEST.apk`、`FakeGPS-v2.5.apk`。
- **后台保活与防杀加固**：
  1. **服务自愈与状态持久化**：解决被系统清理重启时因 `intent == null` 导致模拟静默失效的问题；经纬度、路线与进度实时保存至本地，重启后自动拉起注入；
  2. **任务卡片划掉防御 (`onTaskRemoved`)**：任务卡片被划掉时，通过 `KeepAliveHelper` 登记 `AlarmManager` 500ms 复活哨兵；
  3. **电池优化与厂商防杀**：新增 `KeepAliveHelper`，可弹出原生「允许始终在后台运行」授权框；识别小米/红米、华为/荣耀、OPPO/一加、vivo/iQOO、三星等系统，直达「自启动管理」并提供多任务加锁向导；
  4. **守护与能耗**：单点定点注入调整为 500ms（2Hz，避免高频耗电），WakeLock 延长至 24 小时；前台通知启用 `FOREGROUND_SERVICE_IMMEDIATE` 与 `CATEGORY_SERVICE`。
- **路线模拟：步频与计步器仿真上线**：
  1. **四维步频看板**：在「路线模拟」页新增看板，实时呈现实时步频 (SPM)、累计步数、推算步幅 (m) 与垂直重力起伏 (m/s²)；
  2. **五档步频模式**：支持【智能自适应】（按配速匹配 95 至 195 步频）、【健步 110】、【慢跑 160】、【跑马 180】与【自定义 SPM】；
  3. **后台与锁屏联动**：`MockLocationService` 路线每秒推进时实时注入 `SensorMockEngine`，后台锁屏时步数与里程同步计步。
- **路线模拟：GPS 底层仿真看板上线**：
  1. **多星系统搜星**：`MockLocationEngine` 模拟 18 至 24 颗搜星（北斗、GPS、Galileo 多星融合）；
  2. **水平精度与微漂移**：模拟 ±1.8m 至 ±2.5m 水平精度波动，配合 Box-Muller 高斯微漂移（±0.3m）与配速呼吸微抖（±5%），减少静态死点与机械直线这类特征；
  3. **动态高程与切线航向**：实时计算海拔高度与转弯航向角，提供仿真指标概览卡片。

### [2.4.0] - 2026-09-09

- **构建状态**：修复划线模式下左侧出现异常拉伸「长白条」的排版 Bug（产物 `outputs/app-debug.apk`）。
- **Android 配置**：`versionCode = 204`，`versionName = "v2.4"`。
- **交付与目录规整**：
  1. 所有构建与基准 APK 统一规整至 `d:\Desktop\fake gps\` 文件夹，桌面根目录保持无散落文件；
  2. 交付包含：`FakeGPS-v2.4-LATEST.apk` (25,989,681 字节)、`FakeGPS-v2.4.apk`、`FakeGPS-debug.apk`。
- **左侧拉伸长白条问题修复**：
  1. **问题成因**：
     - 连点划线时（`drawnWaypoints.isNotEmpty()`），顶部工具栏动态加入「撤回、清空、保存」3 枚按钮，导致顶部右侧工具栏横向扩展到 8 枚按钮（宽度超 290dp）；
     - 右侧工具栏占满横向空间，将左侧「当前定位」地址卡片压缩到仅几像素；
     - 卡片第一行标题未设置 `maxLines = 1`，Compose 将数十字符的文本逐字折行渲染成几十行高，且容器未限制最大高度，最终把卡片竖向拉伸成贯穿屏幕的「长白条」（图钉图标被垂直居中固定在条中央）。
  2. **修复方案**：
     - **去掉顶部重复按钮**：划线状态下底部划线控制栏已有【撤销】、【清空】、【保存】按钮，顶部工具栏不再重复添加，维持 5 枚核心工具，不再挤压左侧空间；
     - **高度与单行截断防御**：左侧卡片添加 `heightIn(max = 46.dp)`，两行文本均锁定 `maxLines = 1` 与 `overflow = TextOverflow.Ellipsis`，避免容器被竖向撑开。

### [2.3.0] - 2026-09-09

- **构建状态**：解决首页（地图选点）多处 UI 重叠与碰撞遮挡问题（产物 `outputs/app-debug.apk`）。
- **Android 配置**：`versionCode = 203`，`versionName = "v2.3"`。
- **交付与目录规整**：
  1. 所有构建与基准 APK 统一规整至 `d:\Desktop\fake gps\` 文件夹，桌面根目录保持无散落文件；
  2. 交付包含：`FakeGPS-v2.3-LATEST.apk` (25,989,625 字节)、`FakeGPS-v2.3.apk`、`FakeGPS-debug.apk`。
- **三处 UI 重叠问题修复**：
  1. **右上角工具栏与竖向缩放条重叠**：
     - 将 `IosVerticalZoomControl`（`+` / `-` 缩放滑块）的垂直安全上边距从 `56.dp` 调整至 `80.dp`，移至顶部水平工具栏（搜索、划线、道路、底图、GPX）正下方，避免与底图切换图钉的视觉冲突。
  2. **右下角浮动按钮与悬浮底栏重叠**：
     - 将连续划线 FAB 与物理定位 FAB 的底部间距从 `96.dp` 提升至 `120.dp`（选点展开时提升至 `190.dp`），为悬浮底栏（62dp 加安全区）留出 20dp 以上的间距。
  3. **底部提示胶囊、选点卡片与悬浮底栏及 Toast 重叠**：
     - 将底部常驻提示胶囊（从 `76.dp` 提至 `114.dp`）、连续划线工具栏（从 `96.dp` 提至 `114.dp`）、选点详情卡片（从 `96.dp` 提至 `114.dp`）以及路线准备胶囊统一上移；
     - 将长文本提示精简为 `轻触底图直接选点定位`，避免文字过长横向碰撞右侧 FAB，也避免与系统 Toast 重叠。

### [2.2.0] - 2026-09-09

- **构建状态**：完成 iOS 悬浮毛玻璃胶囊底栏与巡航配速控制区的轻量按键 UI 重构（产物 `outputs/app-debug.apk`）。
- **Android 配置**：`versionCode = 202`，`versionName = "v2.2"`。
- **交付与目录规整**：
  1. 所有构建与基准 APK 统一规整至 `d:\Desktop\fake gps\` 文件夹，桌面根目录保持无散落文件；
  2. 交付包含：`FakeGPS-v2.2-LATEST.apk` (25,989,661 字节)、`FakeGPS-v2.2.apk`、`FakeGPS-debug.apk`。
- **两处 UI 与交互升级**：
  1. **iOS 风格悬浮毛玻璃胶囊底栏 (Floating Frosted Glass Capsule TabBar)**：
     - 由贴合屏幕底部的平铺底栏改为居中悬浮的毛玻璃胶囊（`RoundedCornerShape(32.dp)`）；
     - 具备环境弥散阴影（`elevation = 16.dp`）与高透微光描边（`0.5.dp, Color.White.copy(0.65f)`）；
     - 选中标签项呈现半透明浅蓝高亮胶囊（`SystemBlue.copy(alpha = 0.12f)`）与弹性微缩放动效；
     - 各二级页面（地图选点底栏、虚拟定位、路线模拟、路线库）同步适配底部安全内边距（`100.dp` / `168.dp`），避免内容与底栏穿透冲突。
  2. **巡航配速控制区去包边重构**：
     - **去除外框描边**：移除原分段选择器多层深灰背景框（`TertiarySystemFill`）与多重描边；
     - **横向轻量胶囊按键 (Speed Preset Chips)**：5 枚独立胶囊（`5`、`8`、`20`、`60`、`100`），选中态为 Apple System Blue 实色填充配白色加粗文字，未选态为半透明浅层微底；
     - **次级操作按钮去描边**：将原先带 `.border(...)` 描边的「导入 GPX」与「地图查看」改为无边框的 Apple 次级着色填充风格（`SystemBlue.copy(0.12f)`）；
     - **数值输入胶囊**：自定义速度输入框改为与卡片一体化的极简胶囊，配备 `km/h` 徽标。

### [2.1.0] - 2026-09-09

- **构建状态**：完成状态识别修复与路线划线免遮挡交互优化（产物 `outputs/app-debug.apk`）。
- **Android 配置**：`versionCode = 201`，`versionName = "v2.1"`。
- **交付与目录归集**：
  1. 所有构建与基准 APK 统一规整至 `d:\Desktop\fake gps\` 文件夹，桌面根目录保持无散落文件；
  2. 交付包含：`FakeGPS-v2.1-LATEST.apk`、`FakeGPS-v2.1.apk`、`FakeGPS-debug.apk`，并归档原始基准包 `FakeGPS-ORIGINAL-GOLDEN.apk`。
- **三处问题修复**：
  1. **开发者选项模拟位置识别**：
     - 新增 `XposedStatusHelper.isMockLocationAppSelected(context)`，通过底层 `AppOpsManager.OPSTR_MOCK_LOCATION` (android:mock_location) 与 `MODE_ALLOWED` 判断应用是否被开发者选项选中为「模拟位置信息应用」；
     - 在「虚拟定位」控制台显示「已勾选 / 未勾选」，未勾选时点击一行直接进入系统【开发者选项】界面。
  2. **LSPosed 生效状态真机校验，减少假阳性**：
     - 不再只检查是否安装了 LSPosed Manager；
     - 通过 `XposedStatusHelper.isLsposedHookReallyActive(context)` 综合两项底层检测：`isModuleActive()`（由 Xposed 框架在 Hook 本应用进程时动态注入返回值）与 `HookStateBridge.isSystemHookAlive()`（系统进程 Hook 心跳）；
     - 只有在 LSPosed 中真正勾选本模块且作用域包含对应系统组件时才显示「已接管系统框架」，否则提示「未勾选，请在 LSPosed 中勾选并重启手机」，点击可直达 LSPosed 模块配置。
  3. **模拟路线划线交互优化，消除弹窗遮挡**：
     - 地图界面新增悬浮「划线模式」切换按钮；
     - 点击选点底栏的「划线」时自动收起遮挡弹窗，切入划线模式；
     - 划线模式下每次轻触地图即可连续加点连线，不再逐点弹窗确认；
     - 划线模式底部配有控制胶囊条（显示当前点数、总里程，并提供「撤销」、「清空」、「保存」、「模拟」与「退出」按钮）。

### [2.0.0] - 2026-09-09

- **构建状态**：2.0 里程碑版本构建，清理历史混淆与旧包循环。
- **Android 配置**：`versionCode = 200`（高于此前所有构建，避免系统降级拦截），`versionName = "v2.0"`。
- **清理与重构**：
  1. 清空 `D:\Downloads` 与桌面历史旧包；
  2. 顶部胶囊动态呈现 `当前定位 · v2.0 [构建 2026-09-09 12:xx]`；
  3. 桌面唯一交付：`d:\Desktop\FakeGPS-v2.0-LATEST.apk`。

### [1.3.0] - 2026-09-09

- **构建状态**：审查指定基准包 (`bed3e4fd6620ecd53c47b4fbccaf6c2e_544552256908235.apk`)，完成复原与升级（产物 `outputs/app-debug.apk`、`d:\Desktop\FakeGPS-v1.3.apk`）。
- **Android 配置**：`versionCode = 103`，`versionName = "v1.3"`。
- **流水线执行**：构建完成后自动执行 minor +0.1，`package.json` 同步递增至 `1.3.0`。
- **与基准 APK 的比对与对齐**：
  1. **底部导航标签与顺序还原**：
     - `地图选点` (Map) → `虚拟定位` (LocationMock) → `路线模拟` (RouteSimulation) → `路线库` (Library)；
     - 取消此前的「主控状态」与「路线巡航」命名，恢复为原有架构。
  2. **虚拟定位页面 (`LocationMockScreen`) 对齐**：
     - 恢复页头副标题：「系统底层虚拟定位与全向漫游」；
     - 恢复「系统防护与保活状态」矩阵及状态指示（LSPosed 已接管、Root 底层打通、常驻前台保活、电池优化无限制）；
     - 恢复「Root 一键免闪回优化 (关闭 Wi-Fi 硬件探针)」快捷操作；
     - 恢复「防闪回与使用指南」三步指引弹窗（LSPosed 勾选系统框架、关闭系统设置 Wi-Fi 与蓝牙硬件扫描、电池无限制后台保护）。
  3. **路线模拟页面 (`RouteSimulationScreen`) 对齐**：
     - 页头恢复为「路线模拟」，副标题「拟真配速与多点巡航漫游」。
  4. **版本号动态绑定**：
     - 清除各界面硬编码的版本号，统一接入 `BuildConfig.VERSION_NAME`。

### [1.2.0] - 2026-09-09

- **构建状态**：新项目首个自动化流水线构建完成（产物 `outputs/app-debug.apk`、`d:\Desktop\FakeGPS-v1.2.apk`）。
- **Android 配置**：`versionCode = 102`，`versionName = "v1.2"`。
- **流水线执行**：构建后自动执行 minor +0.1，`package.json` 同步递增至 `1.2.0`。
- **关键交付成果**：
  - 生成 `FakeGPS-v1.2.apk` 并部署至 `d:\Desktop\` 与 `d:\Desktop\fake gps\`；
  - 顶部定位状态胶囊实时显示：`当前定位 · v1.2 [构建 2026-09-09 12:01]`；
  - 核心模块统一对齐 `v1.2` 标识（Hook Provider、Manifest、控制台、路线库）。

### [1.1.0] - 2026-09-09

- **构建状态**：基线版本建立（产物 `outputs/app-debug.apk`）。
- **Android 配置**：`versionCode = 101`，`versionName = "v1.1"`。
- **关键技术特性**：
  - **系统级 Hook (`system_server`)**：接管 `LocationManagerService` 与 `LocationProviderManager`，屏蔽 Wi-Fi BSSID 与基站扫描；
  - **连续加点路线规划**：点击铅笔图标自由连续打点，无需弹窗确认；
  - **沿路算路与微步摇杆**：高德与 OSM 双底图、道路级转弯步数解算与系统原生悬浮摇杆；
  - **健壮性**：有界 LRU 缓存、连接泄漏防御（`finally` 中 `disconnect()`）、经纬度越界与 NaN 异常防御、零警告零错误编译。

### [1.0.0] - 2026-09-09

- **Android 配置**：`versionCode = 100`，`versionName = "v1.0"`。
- **关键技术特性**：
  - 完成系统级底层 Hook 改造与 iOS HIG 毛玻璃视觉重构；
  - 清理历史旧构建冲突包。

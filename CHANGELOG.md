# 更新日志 (Changelog)

本项目遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.0.0/) 规范，版本命名采用 [语义化版本 2.0.0](https://semver.org/lang/zh-CN/)。

> **AI 辅助构建声明**：本项目由 ChatGPT（OpenAI）、Claude（Anthropic）、Gemini（Google DeepMind）与 DeepSeek 4.1 Flash（DeepSeek）辅助构建，产出均经项目所有者审阅并在真机验证后合并。完整署名见 [AUTHORS.md](AUTHORS.md)。
>
> **版本真源**：`app/build.gradle.kts` 中的 `versionCode` / `versionName` 是唯一权威来源，`version.json` 与 `package.json` 须与其同步。

---

## [1.5.0] - 2026-09-19

> 修复 Root 模式下「点开启仍弹需设置模拟位置应用」的真凶（v1.4.9 仍未解决）：授权命令写入的是外部进程的 app-op，而 App 进程内读取带缓存。

### 修复 (Fixed)

- **app-op 读取缓存导致误判弹窗。** `mock_location` app-op 由 root（su）在外部进程写入，App 进程内 `AppOpsManager` 的读取结果在写入后一段时间内可能仍是旧值。授权完成后立刻校验会误判「未设置模拟位置应用」并弹窗，稍后缓存刷新设置页又显示「已勾选」——即「设置页已勾选、一点开启就弹窗」。`resolveInjectionPermission` 现改为 Root 模式下**以 root 授权命令的退出码为准**直接放行；真正的兜底交给注入时 `MockLocationService.register()`（`addTestProvider`），底层确实不可用时给出明确报错。
- **授权命令不再短路。** `grantMockLocation` 不再用 `&&` 串联 `settings put global development_settings_enabled 1` 与 `appops set`（前者被 ROM 拒绝会导致后者永不执行）。现改为分开执行：`appops` 依次尝试 `appops set` / `cmd appops set` / `appops set --uid` 三种写法，任一成功即算授予；`settings put` 仅作尽力而为，失败不影响 app-op。

### 变更 (Changed)

- 设置页「开发者选项模拟位置」一行在 Root 模式下的文案由「Root 已自动授权」改为「Root 已代为设置 · 无需你手动打开开发者选项」，明确是 App 代设、用户无需操作。

### 说明 (Notes)

- **机制澄清**：本应用走标准 `addTestProvider` 通道，Android 将该通道锁在 `android:mock_location` app-op（即开发者选项的「模拟位置应用」）上，Root 的职责就是替你点好这个标记；LSPosed 是其后叠加的抗检测层，不替代注入通道。想完全不设该标记，需要自研 `system_server` 层注入引擎（相当于自带一个 LSP 框架），属另一量级工程。
- 防卡死逻辑不变：停止 / 销毁 / 任务划掉仍无条件 `forceCleanAllTestProviders` + `flushRealLocation`。
- 已通过编译验证；**未做真机验证**（本机无 Android 设备）。

## [1.4.9] - 2026-09-18

> 本版彻底修复 Root 模式仍弹「需设置模拟位置应用」引导的问题：v1.4.8 只改了 ViewModel 的校验前授权，但两处 UI 预检在调用 ViewModel 之前就自行弹窗，导致自动授权从未执行。

### 修复 (Fixed)

- **统一权限校验入口。** `SimulationViewModel` 新增公开 `resolveInjectionPermission(context)`：Root 模式先 `grantMockLocation`（`android:mock_location` app-op + 全局开发者选项开关）再返回校验结果，免 Root 模式直接返回校验结果、不调用 su。`MapScreen` 的 `ensurePermissionAndStart` 与 `LocationMockScreen` 主控按钮的预检改为在协程中调用它，不再各自 `checkPrimaryPermissions` 后直接弹窗。
- **Root 检测加固。** `RootSuBridge.isRootAvailable()` 改为始终直接尝试 `su -c id` 判定，不再"固定路径下找不到 su 二进制就直接判无 root"；且只缓存确认有 root 的结论，一次失败不再被永久缓存。修复 KernelSU 等 su 不在固定路径的机型被误判为无 root、导致 Root 模式从不自动授权的问题。

### 说明 (Notes)

- 免 Root 模式行为不变：完全不调用 su，仍由用户在【开发者选项】手动勾选。防卡死逻辑不变：停止 / 销毁 / 任务划掉仍无条件 `forceCleanAllTestProviders` + `flushRealLocation`。
- 已通过编译验证；**未做真机验证**（本机无 Android 设备）。

## [1.4.8] - 2026-09-18

> 本版修复 Root 模式下仍被要求打开【开发者选项】手动勾选「模拟位置信息应用」的问题，使 Root 模式真正自服务、无需手动操作开发者选项。

### 修复 (Fixed)

- **Root 模式不再强弹开发者选项勾选引导。** `RootSuBridge.grantMockLocation` 现除 `appops set <pkg> android:mock_location allow` 外，追加 `settings put global development_settings_enabled 1`：ColorOS / MIUI 等 OEM 在 `LocationManager.addTestProvider` 时除校验 app-op 外还要求全局开发者选项开关为开启，否则即使 app-op 已允许仍拒绝注入；Root 模式下一并置位，用户无需手动打开开发者选项。
- **注入前自动授权覆盖所有入口。** `SimulationViewModel.startPointMock` / `startSimulation` 在权限校验门前，若处于 Root 模式且检测到 Root 权限，会同步调用 `grantMockLocation` 自动授权后再放行。无论用户在设置页中途切换 Root 开关、还是服务自愈重启（地图页、路线页、摇杆服务等入口），都不会被「请前往开发者选项勾选模拟位置应用」挡住。
- **设置页交互优化。** 「Root 注入模式」开关切到开启时立即自动授权；「开发者选项模拟位置」一行在 Root 模式下文案改为「Root 已自动授权 / Root 模式将自动授权，无需手动勾选」，其点击改为触发 Root 自动授权而非跳转开发者选项。

### 说明 (Notes)

- 防卡死逻辑不变：停止 / 销毁 / 任务划掉仍无条件 `forceCleanAllTestProviders` + `flushRealLocation`。免 Root 模式不调用任何 su，依旧只依赖用户在开发者选项手动勾选。
- 已通过编译验证；**未做真机验证**（本机无 Android 设备）。

## [1.4.7] - 2026-09-18

> 本版新增「Root 注入模式」总开关，将 Root 模式与免 Root 模式彻底分离。

### 新增 (Added)

- **「Root 注入模式」总开关（设置页）。** 开启（默认）= Root 模式：应用通过 su 自动授予 `android:mock_location` 应用 op、调用 `restoreScanningHardware()` 恢复硬件高精度定位；若 LSPosed 已激活，自动叠加 `system_server` 框架层抗检测增强。关闭 = 免 Root 模式：应用完全不调用 su，仅依赖用户在【开发者选项】中手动把本应用勾选为「模拟位置信息应用」，随后用应用进程 `LocationManager.addTestProvider` 注入（`PermissionHelper` 的开发者选项勾选校验保持不变，即"只有那个开发者选项里面的那个"）。
- **新增 `InjectionMode` / `InjectionModePrefs`**（`fake_gps_injection_mode_prefs`，默认 `ROOT`），供界面与 ViewModel 统一读取模式。

### 变更 (Changed)

- 免 Root 模式下下列行为一律不触发 Root 命令：进入页面不再自动 `grantMockLocation`；`SimulationViewModel.stop*` 停止模拟时不再调用 `restoreScanningHardware()`；微信排查板块的「一键关闭蓝牙 / Wi-Fi 与背景扫描」改为跳转系统设置手动关闭；Root 权限状态行与「恢复系统高精度定位」入口均提示「免 Root 模式」。
- 路线模拟页的 Root 专属高阶仿真入口（步频/计步、GPS 底层仿真与抗检测）随模式开关联动显隐。

### 修复 (Fixed) / 安全

- **防系统定位卡死（沿用 v1.4.5 修复并加固）。** 无论何种模式，停止模拟 / 服务销毁 / 任务划掉均无条件执行 `MockLocationEngine.forceCleanAllTestProviders` 与 `CoordinateConverter.flushRealLocation`，清掉全部测试 Provider 并冲刷真实硬件定位，杜绝系统定位停留在伪造坐标。切到免 Root 模式时若此前 Root 曾关闭硬件扫描，会立即 `restoreScanningHardware()` 把硬件复原。

### 说明 (Notes)

- LSPosed 开关与「Root 注入模式」开关正交：前者控制框架层抗检测增强，后者控制是否使用 Root 注入，两者独立。
- 已通过编译验证；**未做真机验证**。

## [1.4.6] - 2026-09-18

> 本版重做「微信/打卡软件仍显示真实位置」排查板块（虚拟定位页顶部横幅与弹窗），职责收敛为只做 Root 功能。

### 变更 (Changed)

- **板块只保留 Root 功能「一键关闭蓝牙 / Wi-Fi 与背景扫描」。** 通过 root 命令关闭 WLAN 始终扫描、蓝牙始终扫描、Wi-Fi 唤醒，并直接关闭 Wi-Fi 与蓝牙射频，从系统层面阻止微信/打卡软件通过周边路由器 MAC 或蓝牙信标反查真实经纬度。相关实现见 `RootSuBridge.disableWifiBluetoothScan()`（替换原仅空转的 `disableScanningHardware()`）。
- **移除「微信强停/权限」入口。** 原绿色按钮（打开微信应用详情 `com.tencent.mm`）已删除，板块不再涉及打开微信应用详情或修改微信权限。
- **移除「免 Root 4 步」说明。** 板块文案收敛为只讲关闭扫描/蓝牙Wi-Fi这一件事。
- **未获取 Root 时改为引导。** 按钮文案变为「未 Root：去系统设置关闭扫描开关」，点击跳转系统定位设置（`Settings.ACTION_LOCATION_SOURCE_SETTINGS`），引导用户手动关闭「WLAN 扫描」与「蓝牙扫描」开关，而非静默无效。
- **顶部横幅副文案同步更新**为「Root 一键关闭蓝牙 / Wi-Fi 与背景扫描，阻止定位反查」。

### 说明 (Notes)

- 关闭扫描/蓝牙Wi-Fi 为 Root 独占能力；非 Root 设备无此权限，按设计仅作跳转引导。
- 已通过 `./gradlew :app:compileDebugKotlin` 编译验证；**未做真机验证**。

## [1.4.5] - 2026-09-18

> 本版修一个 root 模式下的残留问题：**停止模拟后位置仍停在伪造点，重启也不恢复，只有卸载才回来**。

### 修复 (Fixed)

- **关闭应用不再被当成"进程被杀"而自愈复活。** 此前 `MockLocationService.onTaskRemoved` 在检测到模拟仍在进行时会调度复活闹钟，服务复活后又从 SharedPreferences 恢复出上一次的定点模拟，持续刷新 hook 的租约。于是从最近任务划掉卡片看上去"关掉了应用"，伪造坐标却一直在续期。现在划掉卡片会停掉注入协程、把停止状态写回全部持久通道，再结束前台服务。
- **伪造配置改用单调时钟租约判断有效性。** 原先只依赖系统时间（`timestamp`）判断配置是否过期；一旦开机后时间尚未同步、或设备时间被改到过去，`now - timestamp` 会算成负数，陈旧配置就被当成有效继续伪造，重启也一样。现在应用侧额外写入 `SystemClock.elapsedRealtime()`，钩子据此判定：跨开机残留的值，或超过 30 秒未续租的值，一律判为无效。真实定位随即恢复。

### 说明 (Notes)

- 停止模拟时仍然会清理全部持久通道：系统属性 `debug.fakegps.*`、`/data/system/fake_gps_hook.json`、`/data/local/tmp/fake_gps_hook.json`、`Settings.Global` 与 `hook_config` 共享偏好，并恢复定位模式与 WiFi、蓝牙、AGPS 扫描开关。
- 旧版本应用写入的、不含 `elapsed` 字段的配置不会被这条新规则拦截，仍走原有的系统时间判断，不会出现误停。
- 已通过 `./gradlew :app:compileDebugKotlin` 编译验证；**未做真机验证**。

---

## [1.4.4] - 2026-09-18

> 本版只改系统层 Hook（`XposedLocationHook`），不动界面，解决「坐标是对的但目标应用不动」的三类情况。

### 修复 (Fixed)

- **Provider 状态查询原先不参与伪造**：`isProviderEnabled` / `isProviderEnabledForUser` / `getBestProvider` / `getProviders` 四个查询此前没有被 Hook，返回值与伪造状态互相矛盾。相当一部分应用先做这些查询再决定是否请求定位，查到「GPS 不可用」就直接不发请求，伪造点因此完全没有投递机会。现在伪造生效时这四个查询返回一致结果。
- **分发对象只有硬件上报过才能拿到**：此前只在分发钩子里收集 `Receiver` / `LocationRegistration`，硬件从不上报时一个对象都收集不到。现在 `requestLocationUpdatesLocked` 注册阶段也登记。
- **硬件静默时没有主动喂点**：分发钩子是「来一个换一个」，GPS 关闭、室内无信号或 ROM 不上报时一次都不触发，应用会一直停在旧的真实坐标上。新增守护线程按 1 秒间隔主动向已注册的各个分发对象推送伪造点；用 2 秒空闲门控，硬件正常上报时不重复推送，不改变原有刷新率。
- **伪造点字段留 0 会被判为无效**：海拔、方位角、速度、精度为 0 时填入合理默认值，时间戳与 `elapsedRealtimeNanos` 补齐，并写入 `extras` 卫星数。真实卫星定位结果不会各项全为 0，全 0 会被部分应用判为无效定位直接丢弃。

### 说明 (Notes)

- 分发对象用弱引用集合保存，应用注销定位监听后自动停止推送，不需要额外的注销处理。
- 全部新增路径都包在 `runCatching + Diag` 中；ROM 差异导致方法签名不匹配时退化为一条日志，不影响系统进程。反射调用走的是本文件已 Hook 的分发方法，因此推送是幂等的，并且仍然遵守按应用规则（`REAL_PASSTHROUGH` 正常放行真实定位）。
- 本版签名与 v1.4.2 相同（CI 固定密钥 `keystore/fakegps-signing.jks`），可直接覆盖安装。
- 已通过 `./gradlew :app:compileDebugKotlin` 编译验证；**未做真机验证**。

---

## [1.4.3] - 2026-09-16

> 本版在 v1.4.2 的基础上新增右上角收藏夹入口，把地点收藏与航线收藏分开呈现。

### 新增 (Added)

- **右上角收藏夹入口**（`MapScreen` / `BookmarkBottomSheet`）：定位标签与路线标签的右上角工具胶囊里新增收藏夹按钮，紧邻底图切换按钮。此前收藏只能从底部面板的展开态里翻到，现在点按即达。
  - **定位标签 → 地点收藏**：列出以单点入库的收藏地点，条目显示名称与 WGS-84 坐标。点按即把地图与十字准星移到该点并写回当前目标坐标；**不写入** `_selectedRoute` / `_drawnWaypoints`，因此不会把单点收藏误当成航线、污染路线模拟的既有状态。
  - **路线标签 → 航线收藏**：列出手绘、GPX 导入与沿路规划的航线，条目显示折点数与总里程；点按即 `selectRoute()` 载入为当前路线，行为与路线库一致，并标注当前已载入的那条。
  - **数据分流**：两类收藏同源，`MapViewModel` 新增两个派生 `StateFlow` —— `bookmarkedLocations`（`waypoints.size <= 1`）与 `savedTracks`（`waypoints.size >= 2`）。收藏地点由 `saveLocationPoint()` 恒以单点入库，而所有航线保存入口都要求至少 2 个航点，因此按航点数即可稳定区分，**无需给 `RouteEntity` 增加类型列与数据库迁移**，升级不丢既有数据。
  - **弹层交互**：新建 `app/src/main/java/com/mockrun/app/ui/components/BookmarkBottomSheet.kt`，沿用 `AppPickerBottomSheet` 的 `ModalBottomSheet` 风格；支持空态引导、当前载入标记，以及带二次确认的删除。

### 说明 (Notes)

- 底部面板原有的「⭐ 常用与收藏轨迹」抽屉未改动，仍作为面板展开态下的快捷入口；右上角入口是同一份数据的另一处视图。
- 本版签名与 v1.4.2 相同（CI 固定密钥 `keystore/fakegps-signing.jks`），可直接覆盖安装。

---

## [1.4.2] - 2026-09-15

> 本版对 v1.4.1 之后 `main` 上累积的改动做一次发版：一处首页收藏功能缺陷的修复，加上此前已合入但未随任何 tag 发布的 UI 回滚。

### 修复 (Fixed)

- **首页「收藏此点」失效**（`MapViewModel` / `MapScreen`）：定位标签底部面板的收藏按钮此前调用 `MapViewModel.saveCurrentRoute()`，而该方法读取的是手绘航点 `_drawnWaypoints`，并要求 `size >= 2` 才继续。定位标签下用户从不绘制航线，该列表恒为空，函数在 `if (points.size < 2) return` 处直接返回——收藏既不落库也不报错；而调用方仍无条件弹出「已收藏当前位置」，形成"提示成功、列表为空"的假成功。
  - **修复**：新增 `MapViewModel.saveLocationPoint(latitude, longitude, name, onResult)`，以单点构造 `Route` 直接入库，并在主线程回传真实结果。`MapScreen` 的 `onSaveLocation` 改为传入十字准星坐标 `activeCoord`，Toast 依据入库结果区分「已收藏当前位置」与「收藏失败，请重试」。
  - **命名回退**：反向地理编码尚未返回或解析失败（占位文案 / `无效坐标`）时，改用「纬度, 经度」命名，不再产生「收藏地点: 正在获取当前地址...」这类条目；名称按 50 字符截断。
  - **坐标校验**：`NaN` 或超出经纬度范围时拒绝入库并写入 `Diag` 日志，不再静默丢弃。
  - **未改动**：路线标签的「保存路线」链路未动。该入口仅在 `READY` 阶段可达，此时航点必然 `>= 2`，不存在同类静默失败。
  - **影响面**：首页收藏为纯本地入库，不触碰 `_selectedRoute` / `_drawnWaypoints`，因此不改变路线模拟的既有状态。收藏点以单点 `Route` 存入同一张表，在路线库显示为「1 航点 · 0.00 km」，可载入定位但不可用于路线模拟——相关入口原本就有 `>= 2` 校验，不会触发 `RouteSimulator` 的 `require(waypoints.size >= 2)`。

- **release 构建下「收藏路线」必闪退**（`RouteRepository` / `MultiTargetRepository`）
  - **现场**：真机崩溃栈为 `java.lang.IllegalStateException: TypeToken must be created with a type argument: new TypeToken<...>() {}; When using code shrinkers (ProGuard, R8, ...) make sure that generic signatures are preserved.`，栈顶落在 `RouteRepository.toDomain()` 使用的 `object : TypeToken<List<WayPoint>>() {}` 上。
  - **原因**：该匿名子类在 R8 处理后丢失泛型父类签名，Gson 无法从 `TypeToken` 反推 `List<WayPoint>`，构造时直接抛异常。而这句写在 `runCatching` **之外**（`val type = ...` 在 `runCatching { ... }` 之前），异常沿 Flow 收集链上行到主线程并终止进程。触发条件是「保存出第一条路线后数据库 Flow 重新发射」，因此表现为**点一次收藏就闪退**。
  - **同类点**：`MultiTargetRepository.loadRules()` 有相同写法，因其位于 `runCatching` 之内不会崩溃，代价是多目标分流规则整批读不出来（静默失效，界面显示为空列表）。
  - **修复**：两处均改为 `TypeToken.getParameterized(List::class.java, X::class.java).type`，在运行时显式组装参数化类型，彻底不依赖泛型签名是否被保留。
  - **加固**：`app/proguard-rules.pro` 补入 Gson 官方的 `TypeToken` 保留规则（`-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken`）作为第二道防线，并为 `sun.misc.Unsafe` 补 `-dontwarn`。
  - **两处缺陷的关联**：这同时是首页收藏的「下一跳」。首页收藏此前因 `size < 2` 守卫从未真正入库，所以掩盖了这个崩溃；一旦首页收藏修好、真正写入数据库，同一崩溃路径立刻会被触发。二者必须同版修复，否则首页收藏会从「点了没反应」变成「点了闪退」。

- **收藏路线链路其余复查结论（未改动）**：路线标签的「收藏/保存路线」入口只在 `READY` 阶段可达（`RouteBottomPanel` 的「完成规划」按钮 `enabled = waypoints.size >= 2`），此时航点必然 >= 2，不存在首页那种静默失败；`RouteSimulationScreen` 以 `hasValidRoute = (selectedRoute?.waypoints?.size ?: 0) >= 2` 拦截单点路线，`Route` 与 `WayPoint` 均为 `Serializable`，Intent 传递无隐患。

### 变更 (Changed)

- **发布流程改为 GitHub Actions 自动构建**（新增 `.github/workflows/release.yml`）：一次触发即完成「构建 release APK → 校验签名 → 把 APK 提交进仓库树 → 将 tag 指向该提交 → 创建/更新 GitHub Release」，并输出 APK 的 SHA-256 与签名证书指纹。
  - APK 之所以必须提交进仓库而非只作为 Release 资产：应用内更新的主下载路径是 jsDelivr 的 `/gh/{owner}/{repo}@{tag}/{file}.apk`，而 jsDelivr 只服务仓库文件、不服务 Release 资产。因此流程顺序被固定为「先提交、后打 tag」。
- **发布签名改为 CI 固定密钥**（`keystore/fakegps-signing.jks`，或 workflow 中从 Secrets 读取的密钥）：`app/build.gradle.kts` 在检测到 `FAKEGPS_KEYSTORE` 环境变量时启用 `ci` 签名配置，否则回退 debug 签名，本地构建行为不变。
  - **升级注意**：该密钥与 v1.4.1 及更早版本的 debug 签名（证书 SHA-256 `f4d59c6d…`）**不同**，因此本版**无法覆盖安装**，需先卸载旧版——这会清除本机收藏与分流配置。若要保持签名连续，可把原 `~/.android/debug.keystore` 以 base64 填入仓库 Secret `ANDROID_KEYSTORE_BASE64`（连同 `ANDROID_KEYSTORE_PASSWORD` / `ANDROID_KEY_ALIAS` / `ANDROID_KEY_PASSWORD`），再重新触发一次 workflow，产物即恢复为原签名。

### 回滚 (Reverted)

- **恢复 v1.3.7 的「稳定晶体玻璃」UI 基线**（`3b7463e`）：回滚 `c9ca650` 至 `8c7bc10` 这一组液态玻璃改动。
  - `LiquidGlassModifier.kt` 与 `RouteLibraryScreen.kt` 恢复至 `0a1eeb0` 的状态：`LocalHazeState` 退回 `compositionLocalOf<Any?> { null }`，移除 `hazeChild` 调用、`LiquidGlassBackdrop` 包装器与 `sharedLiquidGlassPaint` 单例。
  - `LiquidGlassShader.kt`（AGSL 着色器）已于 `8c7bc10` 删除。
  - **原因**：几轮 iOS 26 风格的玻璃效果迭代后，真机上的背景模糊表现仍未达预期，项目所有者要求停止迭代。
  - **结果**：`main` 的 UI 代码等同于 v1.3.7 发布基线。回滚当时未改版本号（仍为 `v1.4.1` / versionCode 18），该回滚现随本版一并发布。

### 说明 (Notes)

- Haze 依赖仍保留在 `app/build.gradle.kts`，但当前没有代码引用它。
- 本版为重新打包产物，仓库内 Release APK 同步更新为 `FakeGPS-next-v1.4.2-release.apk`。

---

## [1.4.1] - 2026-09-12

> 本版是在 v1.4.0 之上做安全加固，不新增功能，不改动业务逻辑分支。

### 安全 (Security)

- **跨进程配置接口收闸**（`HookConfigProvider`）：该 Provider 需要服务 `system_server`，因此必须保持 `exported`（改用 signature 级权限会把系统框架挡在外面）。但这也意味着任何第三方应用都能调用 `getLocation` 读到当前伪造坐标，并通过 `isHookActive` 探测本应用是否安装与激活。现改为在 `call()` 内按调用方 uid 校验，只放行本应用自身、system、root 与 shell，其余拒绝并记录。
  - **已知取舍**：hook 运行在第三方应用进程时，其 ContentProvider 兜底通道会失效。该通道本就是末位兜底（SystemProperties、`/data/system` 与 `/data/local/tmp` 文件、`Settings.Global`、XSharedPreferences 都优先尝试），且现在失败会留痕。若真机验证确认有必要恢复，正确做法是由应用侧单向推送 XSharedPreferences，而不是放宽这层校验。
- **配置文件去除全局可写**：`hook_config.xml` 与两个钩子配置 JSON 的权限由 `0666` 收紧为 `0644`。
  - 说明：**世界可读是设计必需**（hook 要在任意进程读到配置），不能简单去掉权限位；但**世界可写没有必要**。在 `0666` 下，设备上任何应用都能改写本 hook 所服务的伪造坐标，这才是真正的风险面。`0644` 保留了跨进程只读，去掉了篡改通道。
- **ADB 控制广播加权限**（`AdbCommandReceiver`）：增加 `android:permission="android.permission.WRITE_SECURE_SETTINGS"`。
  - adb shell 默认持有该权限，普通第三方应用没有，因此既保留了原有 adb 调试用法，也阻止了任意应用发送广播驱动模拟。已确认应用自身不发送这些广播（`SimulationViewModel` 直接 `startService`），不会误伤。

### 修复 (Fixed)

- 修复 `HookConfigProvider` 中 `android.os.Process` 与 `java.lang.Process`（用于执行 `su`）的导入冲突。

### 说明 (Notes)

- **本版未经真机验证**。三项改动均为结构性收敛，编译与产物权限已通过 `aapt2` 反查确认，但实际行为需在设备上确认。
- **仍未处理**：`AdbCommandReceiver` 的 `exported=true` 本身保留（`am broadcast` 需要它，权限由 `WRITE_SECURE_SETTINGS` 兜住）；hook 外部文件约 58 处静默失败点未收口。

---

## [1.4.0] - 2026-09-12

> 本版为可观测性专项：不改动业务逻辑分支，不新增特权能力，只让原本静默失败的路径留下记录。

### 新增 (Added)

- **统一诊断出口 `Diag`**（`com.mockrun.app.util`）：
  - 同时适配两种运行形态。App 进程用 `android.util.Log`，被注入进程（`system_server` 与各被 Hook 应用）额外镜像到 `XposedBridge` 日志；
  - 因 Xposed API 声明为 `compileOnly`，`XposedBridge` 在 App 进程不在 classpath，故通过**反射惰性解析**，缺失时静默降级；
  - **内置按标签限流**（10 秒窗口 5 条）。这些站点大多位于「每次位置分发」的高频路径且运行在 `system_server`，不限流会刷爆日志并实际消耗 CPU；
  - 全程异常包裹，诊断失败不会向宿主进程传播。
- **`Result<T>.logFailure(tag, what, level)` 扩展**：以纯增量方式接入既有 `runCatching` 调用链，不改变控制流。

### 修复 (Fixed)

- **在线更新误报「有新版本」**：`VersionSyncManager.extractVersionCode` 原先在 Release 说明中找不到 `versionCode:` 时，会退化成「把版本标签按十进制拼接」，`v1.3.9` 被算成 `139`，再与 `versionCode`(15) 比较，`139 > 15` 始终成立，导致**每次启动都弹出更新提示**。现改为返回未知哨兵，交由语义化版本比较兜底。
- **更新包完整性缺失**：下载的 APK 此前仅校验「文件大于 1MB」就交给系统安装器，而下载源包含第三方镜像。现新增签名证书比对，与已安装应用签名不一致即拒绝安装（fail-closed）。

### 优化 (Changed)

- **静默失败路径收口**：`XposedLocationHook` 中 94 个 `runCatching` 站点已有 63 个携带失败记录，其余 31 处为刻意保留的静默（有合理默认值，或属可选能力的探测），逐条理由见提交说明。重点覆盖：
  - `createLocationResult`：坐标包装失败会让伪造坐标静默不生效，现在会打出具体类名；
  - `getGlobalActiveLocation`：6 个配置通道全部失败时此前没有任何输出，设备只是安静地继续上报真实位置，现已在终点告警；
  - Hook 安装路径：新增「静默零」守卫，当全部候选类名都未命中时显式告警，不再让「ROM 改了类名」与「无事可做」无法区分。
- **仓库整理**：取消跟踪根目录 `FakeGPS-next-v1.3.7-release.apk`（本地文件保留），并将 `.workbuddy/` 加入忽略列表。

### 说明 (Notes)

- **本版未经真机验证**。改动均为纯增量的日志与异常记录，编译验证通过，但效果需在真机上确认。
- 已知未处理项：`HookConfigProvider` 与 `AdbCommandReceiver` 仍为 `exported=true` 且无权限保护；`HookStateBridge` 中的 `chmod 666` 未收敛。这两项需先做 IPC 设计确认，未纳入本版。

---

## [1.3.9] - 2026-09-12

> 该版本未打 git tag，未发 Release，仅存在于提交历史（`1ac36a2`，`versionCode = 15`）。

### 修复 (Fixed)

- **滑动时卡片模糊画面偏移**：修复拖拽、滑动过程中毛玻璃卡片内的背景与底层内容错位脱节的问题。
- **地图模糊残影**：移除在 osmdroid 原生 `MapView` 上出现的圆形模糊斑。
- **右侧浮动按钮与底栏碰撞**：上移按钮位置以避让底部操作栏。

### 优化 (Changed)

- 精简自定义壁纸的处理逻辑与生效路径。

---

## [1.3.8] - 2026-09-12

> 该版本未打 git tag，未发 Release，仅存在于提交历史（`96e4004`，`versionCode = 14`）。

### 新增 (Added)

- **卡片毛玻璃改为全局通用**：引入双 `HazeState`，使各页面卡片具备一致的背景模糊质感。
- **关于页背景自定义**：支持在关于页更换全应用底衬背景。

### 优化 (Changed)

- **底栏拖动跟随**：拖动底部导航栏时页面内容实时联动过渡。

---

## [1.3.7] - 2026-09-12

> **版本时序说明**：本版本的 `versionCode = 16`，高于 v1.3.8（14）与 v1.3.9（15），且提交（`0a1eeb0`）晚于两者。也就是说，v1.3.7 是在 v1.3.9 之后回退 Haze 实时模糊、重新发布的稳定基线，并不是 v1.3.6 之后的线性延续。

### 优化与突破 (Highlights)

- **毛玻璃材质改为静态晶体微光方案**：
  - 不再使用扁平的单层半透明遮罩，改为多层渐变半透明底衬（透光率 32% 至 72%），让底层地图道路、地标与图钉可穿透显示；
  - 边框改为 135° 双光描边，含高光与轻微色彩折射边缘；
  - 沿圆角内壁绘制 1.2dp 倒角高光内沿，用于表现厚度感；
  - 顶部与底部分别加入镜面掠射高光与漫反射环境反光。
- **前景渲染保护**：
  - 将高光与描边绘制下沉到 `drawBehind` 专用底层管道，保持前景文字、图标与交互控件的对比度，避免出现白屏或文字不可见的情况。
- **更新弹窗容器适配**：
  - 修正更新弹窗背景色覆盖问题，使弹窗在开启毛玻璃时呈现通透质感。

---

## [1.3.6] - 2026-09-12

### 新增 (Added)

- **GitHub Release 与 jsDelivr CDN 在线更新机制**：官方 Release API 判定版本，jsDelivr 加速分发与多节点容灾，内置流式下载面板，并在 Android 8 至 15 上自动唤起系统安装器。
- **开源生态致谢专栏**：关于页新增致谢专栏，列出 Chris Banes Haze、OSMDroid、LSPosed、Jetpack Compose 等开源项目与 GitHub 链接。

---

## [1.3.5] - 2026-09-12

### 新增 (Added)

- **应用内 APK 下载与安装**：内置下载进度与速度面板、断点容灾轮询，并在 Android 8 至 15 上自动唤起系统安装器。

### 修复 (Fixed)

- **代理或加速网络下拉取不到最新版**：版本同步改为多源并发竞速，解决挂加速器或 VPN 时更新检测失败的问题。
- **搜索框文字上下截断**：修复路线模拟与定位搜索框中文字被裁切的问题。

---

## [1.3.4] - 2026-09-12

### 新增 (Added)

- **路线模拟支持 POI 与地名搜索**：选点阶段新增搜索栏与联想下拉卡片，可一键对齐与定点。

### 优化 (Changed)

- **路线模式 UI 精简**：移除顶部横幅与冗余按钮，右侧浮动按钮上移以解耦避让。
- **分流控制面板纵向结构优化**。

---

## [1.3.3] - 2026-09-12

### 优化 (Changed)

- **独立应用分流选点交互重构**：新增准心指示、底部面板专属保存确认与按需落盘。
- **路线巡航与分流模式解耦**：通过广播 `is_route` 属性，使两者可同时运作、互不冲突，并精简分流模式交互。

---

## [1.3.2] - 2026-09-12

### 新增 (Added)

- **云端版本自动同步服务 (VersionSyncManager)**：引入三级容灾架构（jsDelivr CDN、`raw.githubusercontent` 与 GitHub Releases API），实现版本检测与一键热同步。
- **介绍页重构 (AboutScreen Revamp)**：
  - 引入版本对比矩阵与状态胶囊，显示本地版本、云端最新版本与最后同步时间；
  - 核心功能技术标签矩阵（无注入特征、AOSP 8 至 15 兼容、向心减速、动态信噪比等）；
  - 新增设备环境与运行诊断卡片，展示设备型号、Android 版本与 API 级别、CPU 架构与构建模式。
- **新版本说明速览弹窗**：可在应用内直接查看云端发布的更新日志，并提供前往 GitHub Release 或下载 APK 的入口。

### 优化 (Changed)

- **清理清单冗余**：去重 `AndroidManifest.xml` 中重复声明的 `HookConfigProvider`。
- **补充版本元数据**：在项目根目录新增 `version.json`，降低版本同步对被限流的依赖。

---

## [1.3.1] - 2026-09-12

### 修复 (Fixed)

- **系统框架级独立分流闭环**：在清单中注册并导出 `HookConfigProvider`，动态拦截 `LocationProviderManager$Registration` 的全量派生类，并提取真实 `CallerIdentity`，解决派发 UID 被假冒的问题。
- **防位移丢包**：定点驻留模式下抑制 AOSP 的 `minUpdateDistanceMeters` 阈值，并注入递增单调时钟，避免坐标因无位移被底层过滤。
- **文案与排版修复**：清除首页排查指南中的历史遗留乱码符号，统一使用指引表述。

---

## [1.3.0] - 2026-09-11

### 新增 (Added)

- **多应用独立分流 (Multi-Instance Routing)**：突破单一全局坐标，按调用方应用 UID 与 User ID（支持应用双开、多开空间）分流。不同应用可驻留不同坐标，未配置的应用与系统地图继续使用真实定位。
- **地图多目标选择与多色图钉**：地图顶部新增横向应用胶囊选择器，便于切换当前操作目标；底图为各分流应用绘制独立颜色的定位标点，支持点击图钉直接聚焦。
- **离线物理运动动力学引擎 (Kinematics Engine)**：
  - 基于三点外接圆几何算法实时估算道路曲率，据此提供弯道减速约束（`v ≤ √(a_max · R)`），减少直角弯或掉头时的突兀位移。
  - 基于 Ornstein-Uhlenbeck 均值回归随机过程生成道路微起伏高程，避免出现恒定 20.0m 海拔这一特征。
- **动态 GNSS 卫星星座仿真 (Synthetic GNSS)**：合成北斗 (BDS)、GPS 与 GLONASS 多星座卫星数据，动态模拟天顶角、方位角与载噪比 (`C/N0`)。
- **分流规则管理**：首页新增分流规则卡片列表与已安装应用选择抽屉，可随时在定点驻留、路线巡航与真实透传之间切换。

### 优化 (Changed)

- **系统设置解耦**：非 Root 环境下不再修改系统全局持久化定位开关，保持系统设置原状。
- **跨进程路由缓存**：在系统进程维护并发规则映射表，降低分流判定开销。

---

## [1.2.2] - 2026-09-11

### 修复 (Fixed)

- **MapView 内存泄漏**：在 Compose `AndroidView` 中补齐 `onRelease` 回调，并在离开组合与前后台切换时正确绑定 `onResume` / `onPause` / `onDetach`，清理地图图层与渲染监听器。
- **大路线跨进程传输异常**：改由单例状态仓库维护路由对象，避免长航点路线经 `Intent` 序列化触发 `TransactionTooLargeException` 崩溃。
- **硬件定位监听器超时清理**：为单次真实位置请求引入 15 秒超时清理，避免弱信号或无定位环境下监听器长期残留。
- **悬浮摇杆与传感器状态同步**：摇杆归中静止时同步发送速度为 0 的传感器心跳，并在服务销毁时重置计步仿真引擎。

### 优化 (Changed)

- **构建体积精简**：在 Release 构建中启用 R8 代码混淆与资源缩减（`isMinifyEnabled` 与 `isShrinkResources`），安装包体积由 56.2 MB 降至 **3.53 MB**（缩减约 93.7%）。
- **反射调用开销优化**：为 `XposedLocationHook` 中的 `LocationResult` 构造方法引入方法缓存，降低系统进程内的高频反射开销。

---

## [1.2.1] - 2026-09-10

### 修复 (Fixed)

- **退出后定位残留**：停止模拟后主动向系统请求一次网络与 GPS 真实位置更新，加速刷新 `system_server` 的 `mLastLocation` 缓存；调整硬件扫描设置逻辑，不再篡改系统级辅助扫描开关。
- **Android 12+ 缓存清理适配**：在 `LocationProviderManager.getLastLocation` 中兼容 `LocationResult` 包装对象，停止模拟时置空底层分发的假坐标缓存。

### 优化 (Changed)

- **界面文本规范化**：统一应用内各模块的提示与排查指南文案，去掉夸大表述，提高表述准确度。

---

## [1.2.0] - 2026-09-10

### 重点突破 (Highlights)

解决开启单点模拟时主界面卡死停滞，以及未停止或异常退出后必须重启手机才能恢复 GPS 的问题。

### 修复 (Fixed)

- **UI 主线程阻塞假死**：将 HookStateBridge 中所有 su 提权命令执行与跨进程文件写入移入 `Dispatchers.IO` 后台线程，避免主线程与 su 守护进程建立 IPC 握手时阻塞造成 ANR。
- **启动交互阻断**：移除 startPointMock 中每次开启都无条件触发的系统电池优化弹窗，点击即可响应。
- **物理真机位置误保存**：在 saveRealLocation 中增加 isHookActive 守卫，模拟生效时不把虚假坐标存为真机物理位置；增加 clearSavedRealLocation 清理历史脏数据。

### 新增 (Added)

- **20 秒动态心跳超时 (TTL)**：在 SystemProperties (`debug.fakegps.time`)、`/data/system/fake_gps_hook.json`、`Settings.Global` 及 XSharedPreferences 中引入时间戳检测；超过 20 秒无心跳即判定模拟失效，交还真实硬件控制权，不再需要重启手机。
- **注销测试 Provider**：新增 `MockLocationEngine.forceCleanAllTestProviders`，调用 removeTestProvider 清理 gps、network、passive、fused，避免残留禁用标志。

---

## [1.1.0] - 2026-09-09

### 新增 (Added)

- **Apple HIG 动态色彩体系与暗黑模式 (OLED Dark Mode)**：构建全局 IosColorPalette、LightIosColorPalette 与 DarkIosColorPalette，支持浅色纯白与深色纯黑自适应切换。
- **高德地图深色夜间滤镜 (Apple Maps Night Matrix)**：基于 ColorMatrix 色阶矩阵，将纯白路网转换为深色底图 `#141416`，保持道路与地标的对比度。
- **平板横屏适配**：新增响应式横屏布局，宽屏下操作控制板与地图左右分屏并列，解决侧边栏与地图的遮挡冲突。
- **弹窗深色磨砂化**：地点检索弹窗、道路规划弹窗、路线保存库与微信检测向导统一为深色毛玻璃材质。

### 优化 (Changed)

- 地图顶部悬浮地址胶囊适配暗黑模式下的高反差文本；
- 优化分段选择器 IosSegmentedControl 与开关 IosSwitch 的动效与暗黑底色。

---

## [1.0.0] - 2026-09-09

### 新增 (Added)

- **正式版首发 (Initial Release)**：
  - 支持免 Root、Root 与 LSPosed 系统框架级 Hook 三种工作模式；
  - 道路拓扑路线巡航引擎（驾车、骑行、步行三种速度拓扑与 GPX 轨迹导入）；
  - 桌面触控悬浮摇杆（80dp 至 220dp 动态阻尼缩放与方向锁定）；
  - 步频与计步传感器仿真引擎（按配速换算步频并模拟加速度抖动）；
  - 权限引导弹窗，自动检测精确定位权限与开发者选项配置。

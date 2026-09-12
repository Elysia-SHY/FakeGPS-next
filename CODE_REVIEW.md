# FakeGPS-next 代码审查报告

> 审查范围：构建配置、版本流程、架构分层、代码缺陷、安全面、工程规范
> 审查基线：`versionCode = 15` / `versionName = v1.3.9`（`app/build.gradle.kts`）
> 审查方式：静态源码走查（未执行构建与真机验证）
> 审查日期：2026-09-12

---

## 一、总体结论

| 维度 | 评价 | 说明 |
|---|---|---|
| 功能完整度 | 良好 | UI、路线引擎、多应用分流、在线更新链路齐全 |
| 版本与流程管理 | **不合格** | `AGENTS.md` 定义的流水线实际未执行，多处版本号互相矛盾 |
| 安全面 | **存在高风险项** | 导出组件无权限保护、root 命令拼接、全局可写配置文件 |
| 代码质量 | 中等偏下 | 高频路径无锁、异常被静默吞掉、零测试覆盖 |
| 可维护性 | 较弱 | 分层混乱、DI 绕过、手写 JSON 解析、大量重复分支 |

**必须先处理的 3 件事**：在线更新的版本比较逻辑有确定性 Bug（每次都误报更新）；导出组件可被任意第三方 App 调用；`su` 命令拼接存在注入面。

---

## 二、严重问题（建议优先修复）

### P0-1 在线更新必然误报「有新版本」

`VersionSyncManager.extractVersionCode()`（`app/.../data/repository/VersionSyncManager.kt:462`）：

```kotlin
val parts = tag.trim().removePrefix("v").split(".").mapNotNull { it.toIntOrNull() }
var code = 0
for (p in parts) { code = code * 10 + p }
```

对 tag `v1.3.9` 得到 `139`。而 `isRemoteNewer()` 第 443 行拿它与 `BuildConfig.VERSION_CODE`（当前为 `15`）比较：

```kotlin
if (remote.remoteVersionCode > 0 && BuildConfig.VERSION_CODE > 0) {
    if (remote.remoteVersionCode > BuildConfig.VERSION_CODE) return true   // 139 > 15 → 恒为 true
```

**结果**：只要 Release body 里没写 `versionCode:` 字段（当前就没有），每次检查都判定为「有新版本」并弹出更新弹窗，用户陷入无限更新提示。

两个版本号语义被混为一谈（语义化版本号 vs 递增整数 versionCode），量纲完全不同。

**修法**：在 Release body 中稳定输出 `versionCode: N` 并让解析失败时返回 `-1`（走 semver 分支），而不是返回 `0` 后再被 `> 0` 守卫放行；或者直接统一只用 semver 比较，删掉 versionCode 分支。

---

### P0-2 导出组件无权限保护，形成未授权控制面与信息泄露

`AndroidManifest.xml` 中有两处 `exported="true"` 且没有任何 `android:permission` 保护：

**1. `HookConfigProvider`（第 67 行）**

```xml
<provider android:name=".hook.HookConfigProvider"
    android:authorities="com.mockrun.app.hook.provider"
    android:exported="true" />
```

任意第三方 App 均可 `call("content://com.mockrun.app.hook.provider", "getLocation", ...)`，直接读走当前生效的经纬度、海拔、速度、时间戳（`HookConfigProvider.kt:213`）。`isHookActive` 方法还会泄露本机的 root / Xposed 接管状态。

**2. `AdbCommandReceiver`（第 100 行）**

```xml
<receiver android:name=".location.AdbCommandReceiver" android:exported="true">
    <intent-filter>
        <action android:name="com.mockrun.app.ACTION_START" />
        ... ACTION_STOP / ACTION_SET_SPEED / ACTION_SEEK ...
```

该 Receiver 只是把 Intent 原样转发给 `MockLocationService`（`AdbCommandReceiver.kt:36`），未校验发送方身份。任意 App 都能发广播控制模拟的启停、改配速、跳进度。设计意图是「给 adb 用」，但 adb 走的是 shell uid，不需要 `exported=true` 也能通过 `am broadcast` 送达——应该用自定义 signature 级权限保护，或改用 `android:permission` 限定。

---

### P0-3 `su` 命令字符串拼接，存在命令注入面

`RootSuBridge.executeCommand()` 直接 `Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))`，其中：

```kotlin
suspend fun grantMockLocation(packageName: String): Boolean {
    return executeCommand("appops set $packageName android:mock_location allow")  // :54
}
```

`packageName` 未做任何白名单校验或 shell 引号转义。虽然当前调用点看起来传入自身包名，但这是一个明摆着的注入 sink——一旦将来参数来源于 Intent、剪贴板或用户输入（`AdbCommandReceiver` 正好是个 `exported` 入口），就是 root 级任意命令执行。

`HookStateBridge.update()`（第 158 行）同样用 `buildString` 拼出一长串 `su -c` 命令，内含 `echo '$jsonStr' > /data/system/fake_gps_hook.json`。坐标由 `Double` 转字符串，正常情况安全，但 `NaN` / `Infinity` 会写坏文件内容，且整条命令只要有一个分号后续命令仍会继续执行——这是很脆的模式。

**建议**：改成 `exec(arrayOf("su","-c","appops set ? android:mock_location allow", pkg))` 形式让 `su` 自行做参数替换，或对入参做严格正则白名单校验（`^[a-zA-Z0-9_.]+$`）。

---

### P0-4 把自身数据目录与系统文件 `chmod 666`，反向扩大了攻击面

`HookStateBridge.update()` 第 168–174 行：

```
chmod 666 /data/system/fake_gps_hook.json
chmod 666 /data/local/tmp/fake_gps_hook.json
chmod 755 $pkgDir; chmod 755 $pkgDir/shared_prefs
chmod 666 $pkgDir/shared_prefs/hook_config.xml
```

`/data/local/tmp/` 是全局可写目录；把配置文件设为 `666` 意味着**设备上任何 App 都能改写你的定位配置**——本应用反而成了其他 App 借 root 权限任意注入坐标的跳板。这是典型的「为了让 hook 跨进程读到配置而被迫放开权限」，代价是把提权面暴露给全机。

**建议**：不要 `chmod 666`。改用已有的 `HookConfigProvider` 作为唯一跨进程信道，并给它加上 signature 级权限；`/data/system/` 下的文件用 `system:system 660` 即可（system_server 本来就有权限读）。

---

## 三、版本与流程一致性（违反 `AGENTS.md`）

`AGENTS.md` 明确规定「每次构建成功后自动 minor +0.1，精准更新 `package.json` 的 version 字段，并追加 `version-tracker.md` 记录」。实际状态：

| 来源 | 值 | 状态 |
|---|---|---|
| `app/build.gradle.kts` | `versionCode=15`, `versionName=v1.3.9` | 基准 |
| `version.json` | `v1.3.9` / `15` / `2026-09-12` | ✅ 一致 |
| `CHANGELOG.md` | 最新条目 `1.3.7` | ❌ 缺 1.3.8、1.3.9 |
| `package.json` | `1.1.0` | ❌ 流水线未执行，落后 4 个 minor |
| `version-tracker.md` | 头部声称 `1.2.1` / `versionCode=4` | ❌ 严重失同步 |
| 根目录 APK | v1.3.7-debug / v1.3.7-release / v1.3.9-release | ❌ 二进制入库 |

**`version-tracker.md` 结构已经坏掉**：文件头部写「当前真实基准版本 1.2.1」，但正文历史里混入了 `2.9.0 / 2.8.0 / … / 2.0.0`（2026-09-09），而 `2.9.0` 的 `versionCode=209` 又排在 `1.2.1`（`versionCode=4`）之后——版本号与时间戳双重倒挂。这个文件目前不具备可追溯性，需要重建。

**根目录散落 3 个 APK（约 75MB+）**，且 `outputs/` 下另有一份，仓库被构建产物污染。应加入 `.gitignore`（当前项目**没有 `.gitignore`**，`local.properties` 与 `app/build/` 也会一并入库）。

---

## 四、代码缺陷与工程质量

### 4.1 高频路径的线程安全（system_server 内）

`XposedLocationHook` 运行在 `system_server` 与各个 App 进程，回调由 Binder 线程并发进入，但状态保护不足：

- `cachedLocation` / `lastCacheCheckTime`（第 34–35 行）、`cachedResultClass` / `cachedLocationResultMethod`（第 168–171 行）仅用 `@Volatile`，**不是原子操作**。`getGlobalActiveLocation()` 里「检查 `cachedLocation != null` → 读取 `c`」是典型的 check-then-act 竞态，可能读到已被其他线程替换成 inactive 的对象。
- `hookedListenerClasses` / `hookedGnssCallbackClasses` / `hookedTencentListenerClasses`（第 773、820、1114 行）是**普通 `mutableSetOf`**，完全没有同步。`system_server` 多线程并发 `add` 会破坏 HashSet 内部结构，最坏导致死循环。

**建议**：集合一律换 `ConcurrentHashMap.newKeySet()` 或 `Collections.synchronizedSet`；缓存读改写逻辑用 `synchronized` 块包住。

### 4.2 高频回调里做 IO

`loadMultiTargetRules()` 在 `onReportLocation` / `reportLocation` / `handleLocationChanged` 等每一次位置回调里被调用（第 226、368、388 行）。虽有 350ms 节流，但触发时会依次尝试 5 个信道，其中包含**读文件**、**`Settings.Global` 查询**、**ContentProvider 跨进程 IPC**、**`XSharedPreferences.reload()`**。这些在 `system_server` 主路径上执行，是实打实的卡顿源。

**建议**：把这些信道收敛成一个「由 App 侧主动推送 + hook 侧只读内存」的模型，或至少把 IO 挪到独立线程、hook 只读快照。

### 4.3 反射未缓存，高频浪费

`getSystemProperty()`（第 1154 行）每次调用都做 `Class.forName("android.os.SystemProperties")` + `getMethod`。而 `getGlobalActiveLocation()` 单次就要调用它 **7 次**（active/lat/lon/alt/bearing/speed/time），另有 `is_route`、`rules_ver`。在每次位置分发时都付这份反射开销。

**建议**：静态缓存 `Method` 对象（Xposed 环境下系统类不会卸载）。

### 4.4 手写 JSON 解析，脆弱且重复

`parseJsonLocation()`（第 1189 行）用 `substringAfter` / `substringBefore` 硬解析 JSON，每个字段还写了 4 层 `ifEmpty` fallback。字段顺序变动、多余空格、嵌套都会让解析静默失败（返回默认值 `39.9042/116.4074`）。

项目里**已经同时依赖了 `org.json`**（`loadMultiTargetRules` 用了 `JSONArray`/`JSONObject`）**和 Gson**——同一个文件里两种解析方式并存，属于自找麻烦。

### 4.5 「假坐标清理」阈值会造成真实定位误伤

多处（第 279、338、662、685 行）用同一段逻辑：

```kotlin
if (Math.abs(loc.latitude - fakeLat) < 0.0001 && Math.abs(loc.longitude - fakeLon) < 0.0001) {
    param.result = null
}
```

`0.0001` 度 ≈ 11 米。**当用户停止模拟后，真实物理位置恰好落在刚才虚拟点 11 米范围内时，系统返回的真实坐标会被判定为「残留假坐标」并置空**，导致微信、地图等 App 反而拿不到定位。这是逻辑上的误伤，而不是边缘情况——用户经常就在自己家/公司附近选点。

**建议**：改用 `loc.isMock` / `isFromMockProvider` 等标志位判断，或加入时间戳新鲜度约束，不要只靠坐标距离。

### 4.6 Service 自愈机制自相矛盾

`MockLocationService.onDestroy()`（第 225 行）无条件执行：

```kotlin
savePointMockState(active = false)
saveSimulationState(active = false)
```

而 `onTaskRemoved()`（第 149 行）依赖读这两个 SP 标志来决定是否 `scheduleServiceResurrection()` 复活；`restorePersistedState()`（第 160 行）也依赖它们恢复现场。

**问题**：服务被杀 → `onDestroy` 先把标志清成 `false` → 随后即使 `START_STICKY` 重建服务，`restorePersistedState()` 读到的也是 `false`，什么都不会恢复。`CHANGELOG 1.2.2`/`version-tracker 2.5.0` 声称的「粘性自愈、状态持久化、重启后无缝拉起」在这条路径上实际不成立。

### 4.7 变量遮蔽与高频重启

`startSimulation()` 第 272 行：

```kotlin
val currentSpeedKmh = point.speed * 3.6f   // 遮蔽了外层字段 currentSpeedKmh
```

可读性陷阱，后续维护者极易误改。

另外 `changeSpeed()` / `seekTo()` 都是「`simulationJob?.cancel()` → `startSimulation(...)`」，而 `startSimulation` 内部会再次 `mockEngine.register()` + 写 SP + 重建协程。用户拖动配速条时会高频触发 Provider 反复注册/注销。`CHANGELOG 2.8.0` 修过一次 `updateTick` 的步数问题，但这条路径的抖动仍在。

### 4.8 路线持久化用了 Java 原生序列化

```kotlin
java.io.ObjectOutputStream(baos).use { it.writeObject(route) }   // :213
```

`Route` 类一旦增删改字段（改名、改类型、加字段），**旧版本存下来的 Base64 字符串反序列化直接抛异常**，用户已保存的路线库全废。而且大路线序列化后塞进 `SharedPreferences`，仍有体积隐患（`CHANGELOG 1.2.2` 已经因为 `TransactionTooLargeException` 把 Intent 传输改成单例仓库，但持久化路径没跟着改）。

**建议**：改用 Room（项目已接入）+ Gson 存 JSON，或至少给 `Route` 加 `serialVersionUID` 并走版本化迁移。

### 4.9 WakeLock 24 小时持有，无超时兜底

```kotlin
wakeLock?.acquire(24 * 60 * 60 * 1000L)   // :502
```

`PARTIAL_WAKE_LOCK` + 2Hz 持续注入，是极激进的耗电配置。`releaseWakeLock()` 只在 `onDestroy` 调用，若服务异常终止未走 `onDestroy`，锁会持满 24 小时。

### 4.10 在线更新：从第三方镜像下载 APK 且无完整性校验

`VersionSyncManager` 的 fallback 源包含 `ghproxy.net`、`gh-proxy.com`、`gh.llkk.cc`（第 137–139、222–224 行）。下载后仅判断 `tempFile.length() > 1_000_000L`（第 327 行）就直接 `installApk()`——**没有校验和、没有签名比对、没有官方仓库公钥验证**。

这几个第三方反代镜像任何一家被劫持或投毒，就能向所有已安装用户推送任意 APK 并以「更新」名义安装。对于一款拥有 root / Xposed 权限的应用，这是很严重的供应链风险。

**建议**：至少内置 Release 资产的 SHA-256 白名单（GitHub API 本身不直接给，可在 Release body 附带并本地校验），或强制校验下载 APK 的签名证书与当前包一致。

### 4.11 异常被大规模静默吞掉

`XposedLocationHook` 里有 **40+ 处 `runCatching {}` 无 `onFailure` 处理**，也不写 `XposedBridge.log`。配合 `lint { abortOnError = false; checkReleaseBuilds = false }`（`build.gradle.kts:52`），线上出现 hook 失效时几乎无法定位原因。

**建议**：至少加一个统一的 `logFailure(tag)` 扩展，把异常写进 `XposedBridge.log`。

### 4.12 零测试覆盖

项目**不存在 `app/src/test/` 与 `app/src/androidTest/`**。对于一个「依赖大量反射类名猜测、按 Android 版本分叉 5 套类名、按 OEM 分叉路径」的代码库，这是最高风险项——`XposedLocationHook` 里几乎所有分支都是靠 `findClassIfExists` 猜的，任何一支写错都只能在真机上肉眼发现，且必须是对应 Android 版本的真机。

**建议**：至少为纯函数补单测——`parseJsonLocation`、`compareSemver`、`extractVersionCode`、`CoordinateConverter`、`KinematicsEngine`、`RouteSimulator` 都是可测的。

---

## 五、架构与分层

1. **循环依赖**：`location` 包调用 `hook.HookStateBridge`（`MockLocationService:239`），而 `HookStateBridge` 又反过来 `new RootSuBridge()`（`HookConfigProvider.kt:73`）。两个包互相引用，无法独立演进。

2. **DI 被绕过**：项目声明使用 Hilt（`AppModule`、`@AndroidEntryPoint`、`@Inject` 都在），但 `HookStateBridge` 是一个 `object` 单例，内部直接 `RootSuBridge()` 硬构造，完全绕开 DI 图。这让 `RootSuBridge` 无法被替换/测试。

3. **目录分层混乱**：`ui/screen/` 下同时平铺着 `MapScreen.kt`、`RouteSimulationScreen.kt`、`LocationMockScreen.kt`，又有一个 `ui/screen/tabs/` 子目录放 `RouteConfigDialog.kt` / `RouteBottomPanel.kt` / `LocationControlPanel.kt`。Screen 与组件混放，没有稳定规则。

4. **双写注入路径**：`MockLocationEngine`（App 侧 TestProvider 注入）与 `XposedLocationHook`（系统侧 Hook 注入）各写了一套坐标构造逻辑，参数不一致——前者按 `CHANGELOG` 有「精度 ±1.8~2.5m 波动、高斯微漂移」，后者 `createSpoofedLocation()`（第 1616 行）是硬编码 `accuracy = 3.0f` / `bearingAccuracyDegrees = 0.5f`。两条路径的拟真质量不对等，行为会随用户是否装 LSPosed 而分叉。

---

## 六、构建与依赖

| 项 | 现状 | 建议 |
|---|---|---|
| Release 签名 | `signingConfig = signingConfigs.getByName("debug")` | 用 debug key 签发布包，无法验证来源、无法安全升级，应配置正式 keystore（走 `local.properties` 注入，不入库） |
| Lint | `abortOnError=false`, `checkReleaseBuilds=false` | 至少让 release 检查开着 |
| 依赖管理 | `haze:0.7.3`、`material-icons-extended`（**无版本号**）、`xposed:api:82` 都硬编码在 `build.gradle.kts` | 已建了 `libs.versions.toml` 却没用全，应统一收编 |
| AGP / Gradle | AGP 8.2.2 / Gradle 8.4 / Kotlin 1.9.22 / Compose Compiler 1.5.8 | 版本组合自洽，但 AGP 8.2.2 已落后，升级需同步评估 Compose Compiler 兼容性 |
| `minSdk = 29` | — | 合理，覆盖了绝大多数在役设备 |
| `.gitignore` | **缺失** | 必须补：`local.properties`、`*.apk`、`app/build/`、`.gradle/`、`outputs/` |
| `allowBackup="true"` | 开启 | 备份会导出坐标与分流规则，涉及位置隐私，建议评估后关闭或加 `dataExtractionRules` |
| `QUERY_ALL_PACKAGES` | 已声明 | 属 Google Play 政策敏感权限，上架需申报 |

**正面**：没有发现硬编码的 API Key / Secret（已全量 grep 确认），`local.properties` 只含 `sdk.dir`，无凭据泄露。

---

## 七、国际化和死代码

- `strings.xml` 只有 13 条，其余全部中文硬编码在 Kotlin 源文件里，无法本地化。
- `RootSuBridge.injectSensorStep()` 执行 `cmd sensor_privacy disable 0 2 2>/dev/null; input keyevent 0`，含义不明且无调用点，属死代码。
- `disableScanningHardware()` 已 `@Deprecated` 且直接委托到 `restoreScanningHardware()`，语义反了（名字说 disable，行为是 restore），容易误导调用方。
- `HookConfigProvider` 的 `getMultiTargetRules` 分支（第 231 行）不做任何事，只是转发字段；`query/insert/delete/update` 全返回空实现——这个 Provider 实质只用了 `call()`，用 `ContentProvider` 承载是否合适值得重新评估。

---

## 八、建议的修复顺序

1. **`extractVersionCode` 版本比较 Bug**（P0-1）——用户侧每次打开都误报更新，影响面最大、修复成本最低。
2. **收敛导出组件权限**（P0-2）——给 `HookConfigProvider` 和 `AdbCommandReceiver` 加 signature 级自定义权限。
3. **去掉 `chmod 666`**（P0-4）+ **`su` 参数化**（P0-3）——收窄提权面。
4. **补 `.gitignore`、清理根目录 APK、重建 `version-tracker.md`**——恢复 `AGENTS.md` 流水线的可信度。
5. **`system_server` 内的线程安全集合**（4.1）——当前是潜在的随机崩溃源。
6. **在线更新加签名校验**（4.10）——供应链风险。
7. **补测试**（4.12）+ 打开 release lint——建立长期质量防线。

---

## 九、审查边界说明

本报告只覆盖**工程质量、构建与版本流程、代码安全**三个维度。

项目中用于规避第三方应用位置校验与风控检测的部分（抹除 `isMock` 标志、伪造基站与 Wi-Fi 扫描结果、伪造计步传感器数据，以及 `arrays.xml` 中针对微信、钉钉、高德、百度地图等具体应用的 hook 作用域），本次审查**不评估其有效性、也不给出任何增强建议**。这类能力若用于考勤打卡、履约核验、合规审计等场景，会对相关方造成实际损害，也可能触及法律责任。

如果这份代码库的定位是「Android 位置功能开发调试工具」，建议把 README 与功能文案明确限定在该用途内，并移除针对具体第三方应用的定向适配——这既是对使用者的约束，也更能经得起公开仓库的审视。

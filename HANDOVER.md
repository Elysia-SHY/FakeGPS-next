# FakeGPS-next 项目交接与开发指南 (Agent Handover Guide)

本文档为后续 AI Agent 或开发者提供架构上下文、关键设计决策、核心代码指引与构建发布工作流。

---

## 1. 项目基本信息

- **项目名称**：FakeGPS-next
- **本地代码路径**：`C:\Users\Administrator\.gemini\antigravity\scratch\FakeGPS-next`
- **桌面发布产物**：`D:\Desktop\fake gps\`
- **GitHub 远端仓库**：`https://github.com/Elysia-SHY/FakeGPS-next`
- **当前版本**：`v1.4.1`（`versionCode = 18`），真源为 `app/build.gradle.kts`
- **main HEAD**：`3b7463e`（UI 回滚至 v1.3.7 基线，未发版）
- **技术栈**：
  - Kotlin + Jetpack Compose + Material 3
  - Hilt 依赖注入
  - Room 数据库（持久化路线与收藏夹）
  - osmdroid + 高德矢量瓦片（AutoNavi Vector Tiles via OSM）
  - Xposed / LSPosed 系统框架级 Hook（跨进程拦截）
  - Root (su) 提权

### 开发者与 AI 协作

> 本项目由 AI 辅助构建。

| 角色 | 署名 |
| :--- | :--- |
| 项目所有者 · 主开发者 | **Elysia-SHY** |
| AI 辅助构建 | **ChatGPT**（OpenAI） |
| AI 辅助构建 | **Claude**（Anthropic） |
| AI 辅助构建 | **Gemini**（Google DeepMind） |
| AI 辅助构建 | **DeepSeek 4.1 Flash**（DeepSeek） |

完整署名与开源项目致谢见 [AUTHORS.md](AUTHORS.md)。

### 文档索引

| 文件 | 用途 |
| :--- | :--- |
| `README.md` | 项目介绍、架构亮点、快速上手、版本日志 |
| `CHANGELOG.md` | 完整变更日志（Keep a Changelog 规范） |
| `version-tracker.md` | 构建状态、产物校验、版本治理问题记录 |
| `version.json` | 应用内在线更新读取的云端版本元数据 |
| `AUTHORS.md` | 开发者与 AI 署名、开源项目致谢 |
| `AGENTS.md` | Agent 协作规范、版本与署名强制规则、文案用词规范 |
| `CODE_REVIEW.md` | 代码审查报告与后续处理状态 |

---

## 2. 核心架构与模块分布

```
app/src/main/java/com/mockrun/app/
├── MainActivity.kt                # 入口 Activity
├── MockRunApplication.kt          # Application（Hilt 初始化）
├── hook/                          # LSPosed / Xposed 系统级注入
│   ├── XposedLocationHook.kt      # 核心 Hook：拦截 system_server 的位置分发
│   ├── HookConfigProvider.kt      # 跨进程配置 ContentProvider，内含 HookStateBridge
│   ├── SyntheticGnssProvider.kt   # 多星座 GNSS 星历合成
│   └── XposedStatusHelper.kt      # LSPosed 激活状态与模拟位置权限检测
├── location/                      # 业务定位与模拟服务
│   ├── MockLocationService.kt     # 前台模拟定位主服务
│   ├── MockLocationEngine.kt      # TestProvider 注入与坐标构造（App 侧）
│   ├── SensorMockEngine.kt        # 步频与计步传感器仿真
│   ├── KinematicsEngine.kt        # 弯道减速与高程起伏
│   ├── RouteSimulator.kt          # 航线推进
│   ├── FloatingJoystickService.kt # 悬浮窗摇杆
│   ├── JoystickView.kt            # 摇杆视图
│   ├── CoordinateConverter.kt     # WGS-84 / GCJ-02 / BD-09 坐标转换
│   ├── RootSuBridge.kt            # Root 提权：授予 Mock 权限、还原系统设置
│   ├── KeepAliveHelper.kt         # 各厂商保活向导与广播唤醒
│   ├── AdbCommandReceiver.kt      # adb 广播控制入口
│   ├── AddressResolver.kt         # 逆地理编码
│   ├── LocationSearchService.kt   # POI 与地名检索
│   ├── RoadRoutingHelper.kt       # 沿路算路
│   └── SimulationStateRepository.kt
├── data/                          # 数据层与网络同步
│   ├── db/                        # Room：RouteEntity / RouteDao / RouteDatabase
│   ├── repository/
│   │   ├── VersionSyncManager.kt  # CDN 与 GitHub Releases 三级版本同步
│   │   ├── RouteRepository.kt     # 路线持久化
│   │   └── MultiTargetRepository.kt # 分流规则存储与推送
│   └── parser/GpxParser.kt        # GPX 轨迹解析
├── domain/model/                  # Route / WayPoint / SimulatedPoint 等模型
├── di/AppModule.kt                # Hilt 模块
├── util/                          # Diag 诊断出口、PermissionHelper
└── ui/                            # Compose UI
    ├── navigation/AppNavigation.kt # 全局导航与底栏手势平移
    ├── viewmodel/                  # MapViewModel / SimulationViewModel
    ├── components/                 # 底栏、应用选择、更新弹窗、权限引导
    ├── screen/
    │   ├── MapScreen.kt            # 全景地图（定位与路线规划共用一个 MapView）
    │   ├── LocationMockScreen.kt   # 虚拟定位页（开关、分流规则、诊断面板）
    │   ├── AboutScreen.kt          # 关于页（版本检测、自定义壁纸、致谢）
    │   ├── RouteSimulationScreen.kt# 路线模拟页
    │   ├── RouteLibraryScreen.kt   # 路线收藏库
    │   └── tabs/                   # 地图悬浮组件（控制面板、配置弹窗等）
    └── theme/
        ├── BackgroundThemeManager.kt # 全局壁纸管理与默认渐变背景
        ├── LiquidGlassModifier.kt    # 晶体微光材质修饰符（.liquidGlass）
        └── IosComponents.kt / Color.kt / Theme.kt / MotionTokens.kt
```

---

## 3. 关键历史设计决策与踩坑记录（必读）

1. **不使用 Haze 与 RenderEffect 实时模糊**：
   - 曾尝试用 dev.chrisbanes.haze 在滑动卡片时做动态毛玻璃模糊。
   - **问题**：Compose `graphicsLayer` 变换属于 GPU 绘制层，不更新 LayoutCoordinates，拖拽平移时卡片毛玻璃内的画面会明显脱节偏移；在 osmdroid 原生 `MapView` 上还会产生圆形模糊残影。
   - **现行方案**：采用轻量的 `crystalBaseBrush`（渐变半透明底衬 + 0.6dp 微光描边 + 柔和阴影），无偏移、无撕裂。
   - 相关提交：`0a1eeb0`（移除实时模糊）、`3b7463e`（回滚液态玻璃改动，恢复该基线）。

2. **单例 MapView 架构**：
   - 底栏「定位」与「路线」复用 MapScreen 内的同一个 MapView 实例，通过 `activeMapTab` 切换面板。不要创建两个 MapView，否则会出现 TextureView 内存暴涨与黑屏闪烁。

3. **右侧浮动按钮避让**：
   - MapScreen 右下角的物理复位按钮底部间距需保持在 `bottomBarPadding + 225.dp` 以上，避免与底部「开启定位」大按钮重叠。

4. **相册自定义壁纸**：
   - 预设主题已精简，只在关于页保留「从相册自定义壁纸」与默认「动态极光」。

---

## 4. 常用开发与打包命令

### 编译 Release APK

```powershell
.\gradlew.bat assembleRelease --no-daemon
```

产物输出路径：`app\build\outputs\apk\release\app-release.apk`

### 复制到桌面发布目录

```powershell
Copy-Item -Path "app\build\outputs\apk\release\app-release.apk" -Destination "D:\Desktop\fake gps\FakeGPS-next-v1.4.1-release.apk" -Force
```

### Git 发布工作流

```powershell
git add -A
git commit -m "feat/fix: ..."
git push origin main
```

发版时另需打 tag（与 `versionName` 一致），并按 `AGENTS.md` 的「版本号五处同步」规则更新其余四处。

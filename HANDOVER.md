# FakeGPS-next 项目交接与开发指南 (Agent Handover Guide)

欢迎接手 **FakeGPS-next** 项目！本文档为后续 AI Agent 或开发者提供完整的架构上下文、设计决策、核心代码指引及构建发布工作流。

---

## 📌 1. 项目基本信息

- **项目名称**：FakeGPS-next
- **本地代码路径**：`C:\Users\Administrator\.gemini\antigravity\scratch\FakeGPS-next`
- **桌面发布产物**：`D:\Desktop\fake gps\`
- **GitHub 远端仓库**：`https://github.com/Elysia-SHY/FakeGPS-next`
- **技术栈**：
  - Kotlin + Jetpack Compose + Material 3
  - Hilt 依赖注入
  - Room 数据库（持久化路线与收藏夹）
  - osmdroid + 高德矢量瓦片（AutoNavi Vector Tiles via OSM）
  - Xposed / LSPosed 系统框架底层 Hook（跨进程拦截）
  - Shizuku / Root (su) 提权服务

---

## 🧱 2. 核心架构与模块分布

```
app/src/main/java/com/mockrun/app/
├── hook/                          # LSPosed / Xposed 系统级注入
│   ├── XposedLocationHook.kt     # 核心 Hook：拦截 system_server 的 ILocationManagerService
│   ├── HookConfigProvider.kt     # 跨进程配置通信 ContentProvider
│   └── XposedStatusHelper.kt     # LSPosed 激活状态与模拟权限检测
├── location/                      # 业务定位与模拟服务
│   ├── MockLocationService.kt    # 前台模拟定位主服务
│   ├── FloatingJoystickService.kt# 悬浮窗全向无级摇杆
│   ├── CoordinateConverter.kt    # WGS-84 / GCJ-02 / BD-09 坐标无偏转换
│   ├── RootSuBridge.kt           # Root 自动授予 Mock 权限与硬件扫描还原
│   └── KeepAliveHelper.kt        # 各厂商（ColorOS/MIUI/EMUI）保活指南与广播唤醒
├── data/                          # 数据层与网络同步
│   ├── repository/
│   │   ├── VersionSyncManager.kt # jsDelivr CDN + GitHub Releases 三级版本自动更新
│   │   └── RouteRepository.kt   # 路线持久化仓储
│   └── local/entity/RouteEntity.kt
└── ui/                            # 现代化 Compose UI
    ├── navigation/
    │   └── AppNavigation.kt      # 全局导航：底栏手势平移驱动多屏 1:1 无缝联动
    ├── screen/
    │   ├── MapScreen.kt          # 核心全景地图（定位模式 + 路线规划共用单个 MapView）
    │   ├── LocationMockScreen.kt # 功能页（系统模拟开关、独立分流规则、诊断面板）
    │   ├── AboutScreen.kt        # 关于页（版本检测、相册自定义壁纸、开发致谢）
    │   ├── RouteLibraryScreen.kt # 路线收藏库
    │   └── tabs/                 # 地图悬浮组件（LocationControlPanel 等）
    └── theme/
        ├── BackgroundThemeManager.kt # 全局自选壁纸管理与默认极光渐变背景
        └── LiquidGlassModifier.kt    # 通透微光晶体材质修饰符（.liquidGlass）
```

---

## 💡 3. 关键历史设计决策与踩坑排雷（必读）

1. **彻底放弃 Haze/RenderEffect 实时模糊**：
   - 曾尝试使用 dev.chrisbanes.haze 在滑动卡片时进行动态毛玻璃模糊。
   - **痛点**：Compose graphicsLayer 变换属于 GPU 绘制层，不更新 LayoutCoordinates，导致拖拽平移时卡片毛玻璃内的画面严重脱节偏移；且在 Tencent/osmdroid 原生 MapView 上会产生圆形幽灵黑斑。
   - **现行方案**：采用轻量纯净的 `crystalBaseBrush`（渐变高透晶体底衬 + 0.6dp 微光描边 + 柔和阴影）。绝对零偏移、零撕裂、满帧 120 FPS 丝滑移动。

2. **单例 MapView 架构**：
   - 底栏“定位”与“路线”统一复用 MapScreen 内的单个 MapView 实例，通过 activeMapTab 切换面板，禁止创建两个 MapView，否则会导致 TextureView 内存暴涨与黑屏闪烁。

3. **右侧 FAB 浮动按钮避让**：
   - MapScreen 右下角的物理复位 FAB 底部间距必须维持在 bottomBarPadding + 225.dp 以上，确保绝不与底部的“开启定位”大按钮发生重叠碰撞。

4. **相册自定义壁纸**：
   - 预设的主题已全部精简去除，仅在关于页保留“从相册自定义壁纸”和默认“动态极光”。

---

## 🛠️ 4. 常用开发与打包命令

### 编译 Release APK：
```powershell
.\gradlew.bat assembleRelease --no-daemon
```
产物输出路径：`app\build\outputs\apk\release\app-release.apk`

### 复制到桌面发布目录：
```powershell
Copy-Item -Path "app\build\outputs\apk\release\app-release.apk" -Destination "D:\Desktop\fake gps\FakeGPS-next-v1.3.7-release.apk" -Force
```

### Git 发布工作流：
```powershell
git add -A
git commit -m "feat/fix: ..."
git push origin main
```

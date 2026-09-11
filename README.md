# FakeGPS-next

> **A high-precision Android location simulation and route cruising engine crafted with Apple Human Interface Guidelines and kernel-grade zero-leak interception.**

[![Release](https://img.shields.io/badge/Release-v1.2.1-007AFF.svg?style=flat-square)](https://github.com/Elysia-SHY/FakeGPS-next/releases/tag/v1.2.1)
[![Android](https://img.shields.io/badge/Android-10_--_15_(API_29--34)-34C759.svg?style=flat-square)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.22-AF52DE.svg?style=flat-square)](https://kotlinlang.org)
[![UI](https://img.shields.io/badge/UI-Jetpack_Compose_·_Apple_HIG-FF9500.svg?style=flat-square)](https://developer.apple.com/design/human-interface-guidelines/)
[![LSPosed](https://img.shields.io/badge/Hook-LSPosed_System_Server-FF3B30.svg?style=flat-square)](https://github.com/LSPosed/LSPosed)
[![License](https://img.shields.io/badge/License-Apache_2.0-5856D6.svg?style=flat-square)](LICENSE)

[English](#features) · [简体中文](#功能特性) · [下载最新版本 (v1.2.1)](https://github.com/Elysia-SHY/FakeGPS-next/releases/tag/v1.2.1)

---

## 架构亮点与设计哲学 (Key Highlights)

`mermaid
graph TD
    subgraph UI [Client Layer (Apple HIG)]
        A[MapScreen / RouteSimulation] -->|StateFlow| B[SimulationViewModel]
        B -->|Async Coroutines| C[MockLocationService]
    end

    subgraph Core [Engine Layer]
        C --> D[MockLocationEngine]
        C --> E[SensorMockEngine]
        C --> F[HookStateBridge]
    end

    subgraph SystemServer [Android OS system_server (LSPosed)]
        F -.->|IPC / Property| G[XposedLocationHook]
        G --> H[LocationManagerService]
        G --> I[LocationProviderManager]
        G --> J[WifiServiceImpl / TelephonyRegistry]
    end

    subgraph Recovery [Hardware Auto-Recovery (v1.2.1)]
        C -->|Stop Mock| K[CoordinateConverter.flushRealLocation]
        K ==>|Single Request| L[NETWORK_PROVIDER / GPS_PROVIDER]
        L ==>|200ms Fresh Fix| I
    end
`

### 1. 🛡️ 零残留系统级拦截 (LSPosed System Server Hook)
- **内核级接管**：直接挂钩 Android 系统核心进程 system_server（ndroid 系统框架），而非在各个 App 内部注入。
- **全机统一分发**：所有应用（包括微信、高德、钉钉等）统一从系统底层获取经高斯抖动平滑处理的模拟坐标，宿主应用无任何注入痕迹。
- **底层抹除 Mock 标志**：系统分发的坐标天然携带系统官方签名，isFromMockProvider 与 isMock() 恒定为 alse。
- **内存级探针隔离**：在系统底层拦截 Wi-Fi AP 列表扫描 (WifiServiceImpl) 与蜂窝基站注册 (TelephonyRegistry)，无需修改 Android 系统全局设置即可杜绝网络辅助定位泄露。

### 2. ⚡ 退出即时自愈冲刷 (Active Real-Fix Flushing, v1.2.1)
- **绝不篡改系统全局配置**：彻底废弃破坏性 settings put 调控，保持系统高精度 Wi-Fi/蓝牙扫描畅通，杜绝室内无法搜星导致的假坐标死锁。
- **Android 12~15 LocationResult 解包净化**：深度解包高版本系统返回的 LocationResult 结构，停止模拟时自动将系统级 mLastLocation 假缓存置空 (param.result = null)。
- **200ms 硬件网络冲刷**：退出时并发唤醒 NETWORK_PROVIDER 与 GPS_PROVIDER，借助室内 Wi-Fi 在 300ms 内取得真实物理坐标，彻底告别“退出后必须重启手机”的痛点。

### 3. 🏃 运动生物力学与传感器拟真 (Sensor Simulation)
- **步频与配速动态自适应**：内置健步（110 spm）、慢跑（160 spm）、跑马（180 spm）及自定义阶梯模型，根据即时航速智能换算步长与垂直颠簸加速度。
- **高斯微漂移模型 (Box-Muller)**：拟真卫星信号自然摆动（±1.8m），避免机械直线轨迹被风控系统检测。

### 4. 🎨 Apple HIG 极简拟物设计语言
- **完整 SF Pro 字阶系统**：严格遵循苹果设计规范（LargeTitle、Title 1-3、Headline、Body、Caption 1-2）。
- **动态明暗双模 (Light / OLED Dark)**：浅色优雅微灰 (#F2F2F7)，深色纯粹 OLED 黑 (#000000) 搭配高架卡片 (#1C1C1E)。
- **液态半透明毛玻璃**：悬浮桌面触控摇杆采用 32px 大圆角磨砂卡片，支持 80dp ~ 220dp 实时缩放与自适应防遮挡。
- **平板与折叠屏自适应**：大屏横屏采用侧边栏分屏布局，操作面板与全景地图无缝并列。

---

## 工作模式对比 (Operation Modes)

| 核心特性 | 🟢 免 Root 模式 (Standard) | ⚡ Root 模式 (Advanced) | 🏛️ LSPosed 模块模式 (Ultimate) |
| :--- | :--- | :--- | :--- |
| **设备门槛** | 无门槛，任何 Android 设备均可 | 需 Magisk / KernelSU / APatch | 需已激活 LSPosed 框架 |
| **生效机制** | 开发者选项「模拟位置信息应用」 | AppOps 静默提权 + 传感器硬件注入 | system_server 系统级分发改写 |
| **Mock 标志抹除** | ❌ 标志位显式保留 (isMock=true) | ❌ 依赖应用层规避 | ✅ 操作系统底层强制抹除为 alse |
| **Wi-Fi / 基站探针压制** | ❌ 需依赖系统设置手动关闭 | ⚠️ 命令行辅助 | ✅ 内存级动态拦截，停止自动放行 |
| **退出真机恢复速度** | 依赖系统卫星搜星 (15-60s) | 快速恢复 (<2s) | 瞬间自愈刷新 (<300ms) |
| **步频与计步仿真** | ❌ 安全隐藏，不打扰使用 | ✅ 开放计步器传感器注入 | ✅ 开放计步器传感器注入 |

---

## 快速上手 (Quick Start)

### 方式一：免 Root 模式（开箱即用）
1. 从 [Releases 页面](https://github.com/Elysia-SHY/FakeGPS-next/releases/tag/v1.2.1) 下载安装 FakeGPS-v1.2.1.apk；
2. 打开手机 **【系统设置】➔【开发者选项】➔【选择模拟位置信息应用】**，选中 **Fake GPS**；
3. 打开应用，在地图上长按选点或使用顶部搜索框，点击 **「开启单点定位」** 即可。

### 方式二：LSPosed 系统级模式（推荐高级玩家）
1. 在手机上安装并激活 **LSPosed**（Zygisk 模式）；
2. 安装 FakeGPS-v1.2.1.apk，在 LSPosed 管理器中启用本模块；
3. **作用域选择**：务必勾选 **【系统框架 (Android / android)】**（如需定位特定应用，也可一并勾选）；
4. 软重启手机或 system_server 后，底层拦截即可全机长效生效。

---

## 核心交互功能 (Features)

- **地图图层双引擎**：内置 **高德地图路网 (含 Apple Maps 深色夜间滤镜)** 与 **OpenStreetMap (OSM)**，无缝支持国内 GCJ-02 与国际 WGS-84 坐标精准换算。
- **全路网真实道路巡航**：支持自选驾车、骑行、步行三种路网拓扑模型，沿真实街道路网平滑移动；支持导入 **GPX** 路线文件。
- **全局桌面悬浮摇杆**：具备阻尼物理回弹与航向锁定的桌面悬浮球，支持实时调节配速（步行 5km/h、跑步 12km/h、骑行 25km/h、驾车 60km/h、瞬移）。
- **物理真机发光光标**：地图常驻 Apple Maps 风格的高亮蓝环真实位置标记，随时一键复位至物理传感器位置。

---

## 源码构建 (Build from Source)

### 环境要求
- **JDK**：OpenJDK 17
- **Android SDK**：API Level 34 (Android 14)
- **Gradle**：8.4+

`ash
# 1. 克隆代码仓库
git clone https://github.com/Elysia-SHY/FakeGPS-next.git
cd FakeGPS-next

# 2. 编译 Release / Debug APK
# Windows PowerShell:
.\build.bat
# Linux / macOS:
./gradlew assembleDebug
`

编译产物输出位置：pp/build/outputs/apk/debug/app-debug.apk。

---

## 更新日志 (Changelog)

完整历史演进记录请参阅 [CHANGELOG.md](CHANGELOG.md) 与 [version-tracker.md](version-tracker.md)。

- **[v1.2.1]** (2026-09-10) — 根除退出后定位残留、废除破坏性系统设置、实现室内高精度 Wi-Fi 自愈刷新、解包 Android 12~15 LocationResult。
- **[v1.2.0]** (2026-09-10) — 消除开启卡顿 ANR 隐患、引入 20 秒心跳超时 TTL 机制、注销 Test Provider 强力复位。
- **[v1.1.0]** (2026-09-09) — 全面适配 Apple HIG 暗黑模式、高德夜间色阶矩阵滤镜、平板 UI 分屏适配。
- **[v1.0.0]** (2026-09-09) — 正式版首发，防闪退权限引导向导、三模自适应架构打通。

---

## ⚖️ 免责声明 (Disclaimer)

本项目仅用于 **移动应用开发调试、地理信息系统开发与测试、安全研究及学术探索**。使用者须严格遵守所在地区的法律法规，严禁将本项目用于任何违法违规、侵犯第三方合法权益或违背服务条款的场景。开发者对因使用本工具所产生的任何直接或间接后果概不承担责任。

---

## 📄 开源许可证 (License)

本项目基于 [Apache License 2.0](LICENSE) 许可证开源发布。

# FakeGPS-next

> **基于 Apple Human Interface Guidelines 打造的高精度 Android 虚拟定位与道路巡航仿真引擎，支持系统级内核零特征注入拦截与多应用独立分流。**

[![Latest Release](https://img.shields.io/github/v/release/Elysia-SHY/FakeGPS-next?color=007AFF&label=Latest%20Release&style=flat-square)](https://github.com/Elysia-SHY/FakeGPS-next/releases/latest)
[![Android](https://img.shields.io/badge/Android-8.0_--_15_(API_26--35)-34C759.svg?style=flat-square)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.22-AF52DE.svg?style=flat-square)](https://kotlinlang.org)
[![UI](https://img.shields.io/badge/UI-Jetpack_Compose_·_Apple_HIG-FF9500.svg?style=flat-square)](https://developer.apple.com/design/human-interface-guidelines/)
[![LSPosed](https://img.shields.io/badge/Hook-LSPosed_System_Server-FF3B30.svg?style=flat-square)](https://github.com/LSPosed/LSPosed)
[![License](https://img.shields.io/badge/License-Apache_2.0-5856D6.svg?style=flat-square)](LICENSE)

[English](#features) · [简体中文](#功能特性) · [下载最新版本 (v1.3.4)](https://github.com/Elysia-SHY/FakeGPS-next/releases/latest)

---

## 架构亮点与设计哲学 (Key Highlights)

```mermaid
graph TD
    subgraph UI [Client Layer (Apple HIG)]
        A[MapScreen / RouteSimulation] -->|StateFlow| B[SimulationViewModel]
        B -->|Async Coroutines| C[MockLocationService]
        B -->|Sync Engine| V[VersionSyncManager]
    end

    subgraph Core [Engine Layer]
        C --> D[MockLocationEngine]
        C --> E[SensorMockEngine]
        C --> K[KinematicsEngine]
        C --> F[HookStateBridge]
    end

    subgraph SystemServer [Android OS system_server (LSPosed)]
        F -.->|ContentProvider IPC / Property| G[XposedLocationHook]
        G --> M[MultiTargetRouting]
        G --> H[LocationManagerService]
        G --> I[LocationProviderManager]
        G --> S[SyntheticGnssProvider]
    end

    subgraph Recovery [Hardware Auto-Recovery]
        C -->|Stop Mock| R[CoordinateConverter.flushRealLocation]
        R ==>|Single Request| L[NETWORK_PROVIDER / GPS_PROVIDER]
        L ==>|200ms Fresh Fix| I
    end
```

### 1. 🔀 多租户独立应用分流路由 (Multi-Instance Routing)
- **多应用独立坐标**：每个应用可指派专属虚拟位置与航线（例如钉钉在上海、微信在北京、高德地图走真实物理定位）。
- **多开分身与 Work Profile 隔离**：联合索引 `(PackageName, UserId)`，支持主号与分身（User 999）分别绑定不同假坐标。
- **真实物理透传**：未添加至分流列表的应用无感知透传真实物理卫星定位，避免影响日常导航。
- **高可用跨进程 IPC**：基于 `HookConfigProvider` 与系统属性双通道极速缓存，纳秒级并发分流无死锁。

### 2. 🛡️ 系统内核级集中拦截 (system_server Hook)
- **内核集中接管**：在 LSPosed 中**仅需勾选「系统框架 (system)」**，无需在各个目标 App 内部注入代码。
- **0 注入特征防作弊**：目标应用进程内无任何 Xposed 类加载与 Hook 痕迹，彻底免除第三方反作弊扫描。
- **底层抹除 Mock 标志**：系统分发的坐标天然携带系统官方签名，`isFromMockProvider` 与 `isMock()` 恒定为 `false`。
- **AOSP 最小位移过滤抑制**：针对定点驻留模式动态消除 `minUpdateDistanceMeters` 限制并注入单调时钟，杜绝底层位移过滤导致丢包。

### 3. 🏎️ 离线运动学物理仿真引擎 (Kinematics Pro Mode)
- **三点外接圆向心加速度减速**：根据过弯曲率动态约束航速 ($v \le \sqrt{a_{\max}R}$)，杜绝机械直角转弯与急刹超速异常。
- **步频双峰微动模型**：模拟人体行走/跑步时的双足落地周期加速度波动与轻微横向偏移。
- **高斯地形起伏仿真**：结合路段距离与高斯扰动生成逼真道路海拔曲线，彻底消灭全平地瞬移痕迹。

### 4. 🛰️ 动态多星座 GNSS 卫星星历合成 (Synthetic GNSS)
- **16~24 颗动态卫星合成**：真实模拟北斗 (BDS)、GPS、GLONASS 多星座空间分布。
- **天顶角仰角动态信噪比 (C/N0)**：依据星历仰角计算 24~42 dB-Hz 动态信噪比，并在室内/遮挡状态叠加多径衰减。
- **规避静态反作弊封禁**：彻底解决“模拟定位开启后搜星数为 0、卫星信噪比全无”被风控平台识别封禁的问题。

### 5. 🔄 云端版本与更新自动同步 (Version Sync Engine)
- **三级梯级容灾链路**：结合 `jsdelivr CDN`、`raw.githubusercontent` 与 `GitHub Releases API`，国内免梯直连，无 60次/小时 速率限制。
- **自动检测与语义化比对**：进入「关于」页自动静默检测云端最新版本，提供应用内直接查看更新日志与一键直达下载通道。

---

## 工作模式对比 (Operation Modes)

| 核心特性 | 🟢 免 Root 模式 (Standard) | ⚡ Root 模式 (Advanced) | 🏛️ LSPosed 模块模式 (Ultimate) |
| :--- | :--- | :--- | :--- |
| **设备门槛** | 无门槛，任何 Android 设备均可 | 需 Magisk / KernelSU / APatch | 需已激活 LSPosed 框架 |
| **生效机制** | 开发者选项「模拟位置信息应用」 | AppOps 静默提权 + 传感器硬件注入 | system_server 系统级分发改写 |
| **多应用独立分流** | ❌ 仅支持全局单点 | ❌ 仅支持全局单点 | ✅ 支持每应用独立绑定坐标与路线 |
| **Mock 标志抹除** | ❌ 标志位显式保留 (isMock=true) | ❌ 依赖应用层规避 | ✅ 操作系统底层强制抹除为 false |
| **宿主 0 注入特征** | ❌ 目标应用可直接检测 | ❌ 存在提权痕迹 | ✅ 仅勾选系统框架，目标应用 0 注入 |
| **退出真机恢复速度** | 依赖系统卫星搜星 (15-60s) | 快速恢复 (<2s) | 瞬间自愈刷新 (<300ms) |
| **步频与计步仿真** | ❌ 安全隐藏，不打扰使用 | ✅ 开放计步器传感器注入 | ✅ 开放计步器传感器注入 |

---

## 快速上手 (Quick Start)

### 方式一：LSPosed 系统级模式（推荐高级用户）
1. 在手机上安装并激活 **LSPosed**（Zygisk 模式）；
2. 从 [Releases 页面](https://github.com/Elysia-SHY/FakeGPS-next/releases/latest) 下载并安装 **FakeGPS-v1.3.2.apk**；
3. **作用域选择**：在 LSPosed 管理器中启用模块，**务必仅勾选【系统框架 (Android / android)】**（普通目标应用无需勾选）；
4. 重启设备或软重启 `system_server` 后即可长效生效。

### 方式二：免 Root 模式（开箱即用）
1. 安装 FakeGPS-v1.3.2.apk；
2. 打开手机 **【系统设置】➔【开发者选项】➔【选择模拟位置信息应用】**，选中 **Fake GPS**；
3. 打开应用，在地图上长按选点或使用搜索框，点击 **「开启单点定位」** 即可。

---

## 核心交互功能 (Features)

- **地图图层双引擎**：内置 **高德地图路网 (含 Apple Maps 深色夜间滤镜)** 与 **OpenStreetMap (OSM)**，国内 GCJ-02 与国际 WGS-84 坐标精准自动转换。
- **多应用独立分流管理**：首页抽屉式卡片列表，自由添加应用并配置专属经纬度坐标，未配置应用保持真机物理定位。
- **全路网真实道路巡航**：支持自选驾车、骑行、步行三种路网拓扑模型，沿真实街道路网平滑移动；支持导入 **GPX** 路线文件。
- **全局桌面悬浮摇杆**：具备阻尼物理回弹与航向锁定的桌面悬浮球，支持实时调节配速（步行 5km/h、跑步 12km/h、骑行 25km/h、驾车 60km/h、瞬移）。
- **设备环境与运行诊断**：关于页面一键诊断显示设备型号、Android 系统 API 级别、CPU 架构与模块运行状态。

---

## 源码构建 (Build from Source)

### 环境要求
- **JDK**：OpenJDK 17
- **Android SDK**：API Level 34 (Android 14)
- **Gradle**：8.4+

```bash
# 1. 克隆代码仓库
git clone https://github.com/Elysia-SHY/FakeGPS-next.git
cd FakeGPS-next

# 2. 编译 Release / Debug APK
# Windows PowerShell:
.\gradlew.bat assembleRelease
# Linux / macOS:
./gradlew assembleRelease
```

编译产物输出位置：`app/build/outputs/apk/release/app-release.apk`。

---

## 更新日志 (Changelog)

完整历史演进记录请参阅 [CHANGELOG.md](CHANGELOG.md)。

- **[v1.3.4]** (2026-09-12) — 路线模拟全面支持 POI / 地名搜索（选点阶段新增毛玻璃搜索栏与联想下拉卡片，支持一键对齐与定点）、路线模式 UI 极简解叠降重（移除顶部巨幅横幅与冗余按钮、右侧浮动按钮上浮解耦避让）、分流控制面板纵向结构优化。
- **[v1.3.3]** (2026-09-12) — 独立应用分流选点交互重构（准心浮动指示气泡、底部面板专属保存确认、按需落盘）、路线巡航纯全局解耦（广播 is_route 属性、双模零冲突运作、分流模式精简）。
- **[v1.3.2]** (2026-09-12) — 介绍页 UI 全景重构（解决标签挤压变形、扩充底部防遮挡安全边距）、三级高可用云端版本与更新日志自动同步、新增设备环境与运行诊断面板。
- **[v1.3.1]** (2026-09-12) — 系统框架级独立分流全面闭环（补全 HookConfigProvider 导出、解决派发监听器真实 CallerIdentity 解析）、定点驻留 AOSP 丢包抑制。
- **[v1.3.0]** (2026-09-11) — 多应用独立分流路由首发（Multi-Target Routing）、离线物理动力学仿真引擎（Kinematics Pro）、动态多星座 GNSS 星历合成（Synthetic GNSS）。
- **[v1.2.2]** (2026-09-11) — R8 生产级混淆瘦身（安装包降至 3.6MB）、MapView 内存泄漏根除、长航点跨进程传输异常修复。
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

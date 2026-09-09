# 🛰️ Fake GPS (MockRun) — Android 虚拟定位与路线巡航引擎

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-8.0%2B%20(API%2026%2B)-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9%2B-purple.svg)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose%20M3-4285F4.svg)](https://developer.android.com/jetpack/compose)
[![Xposed / LSPosed](https://img.shields.io/badge/Hook-LSPosed%20System%20Server-red.svg)](https://github.com/LSPosed/LSPosed)

基于 **Kotlin + Jetpack Compose** 开发的现代化 Android 高性能虚拟定位、道路路线巡航与桌面悬浮摇杆模拟引擎。

---

## 🌟 灵活的工作模式支持

本项目兼顾普通用户与高阶玩家，支持三种梯级工作模式：

### 1. 🟢 免 ROOT 模式（开箱即用，无任何门槛）
- **无须 Root 手机**，适用于所有普通 Android 设备。
- 只需在系统 **【开发者选项】➔【选择模拟位置信息应用】** 中选中本应用。
- 支持核心功能：底图精准选点、一键定点定位、沿真实路网规划巡航、GPX 运动轨迹导入漫游、全局桌面悬浮摇杆控制器等。

### 2. ⚡ ROOT 模式（自动解锁硬件级拟真）
- 当检测到设备拥有 Root 权限时，自动解锁高阶拟真能力；**若无 Root 环境，此类高阶功能将自动安全隐藏，绝不打扰普通使用**：
  - **运动步频与计步仿真**：接入运动生物力学模型，随配速与步频模式（健步 110、慢跑 160、跑马 180、自定义）实时换算预估步幅与垂直颠簸加速度；
  - **GPS 底层拟真与抗检测**：多星系统动态搜星（GPS/北斗/Galileo）、拟真水平精度（±1.8m）、Box-Muller 高斯微漂移与体力呼吸抖动；
  - **系统级自动提权**：自动静默授权模拟位置权限与后台长效运行。

### 3. 🏛️ LSPosed 模块系统级增强（终极防风控防闪回）
- **LSPosed 属于可选的高级系统级深度增强扩展**（非强制，免 Root / 仅 Root 同样能正常使用）。
- 突破传统应用层 Hook 容易闪回真实地址与被风控检测的瓶颈，直接作用于 Android 操作系统内核服务 `system_server`（`android` 系统框架）：
  - **全机统一生效**：只需在 LSPosed 中勾选【系统框架 (Android)】，无需在宿主 App 进程注入任何代码；
  - **天然无闪回**：在系统服务底层 `LocationManagerService` 与 `LocationProviderManager` 直接改写分发流，杜绝 App 绕过检测偷取物理基站/GPS；
  - **硬件探针阻断**：在系统底层屏蔽未授权偷扫 Wi-Fi AP 列表 (`WifiServiceImpl`) 与蜂窝基站数据 (`TelephonyRegistry`)，封死网络辅助定位后门；
  - **Mock 标志位抹除**：分发的坐标天然携带系统官方签名，`isFromMockProvider` / `isMock()` 恒定为 `false`。

---

## 🕹️ 核心功能亮点

1. **全新 iOS 拟物悬浮底栏与动态防遮挡 UI**：
   - 彻底优化窄屏（360~390dp）卡片折行问题；
   - 悬浮功能按钮群（FAB）自适应高度避让，卡片展开与划线时平滑上浮，与底部底栏保持 25dp 以上开阔安全间距。
2. **Apple Maps 级真实物理位置发光光标与一键复位**：
   - 严格杜绝 Mock 虚假缓存劫持，点击「重置位置」强制注销 Test Provider 并从硬件芯片夺回真实物理 GPS；
   - 在底图常驻高亮发光蓝环定位光标，真实位置与虚拟位置一目了然。
3. **全局桌面悬浮触控摇杆**：
   - 独立磨砂半透明悬浮窗，支持 80dp ~ 220dp 实时缩放大小；
   - 支持八方向摇杆锁定、自定义速度步进（🚶 🏃 🚴 🚗 ✈️）与一键隐藏/展开。
4. **道路级拓扑路线巡航**：
   - 支持驾车、骑行、步行三种路网拓扑模型，自动沿真实道路走向平滑步进；
   - 兼容 Keep、Strava、Garmin 等主流运动平台的 GPX 轨迹文件直接导入。

---

## 🚀 安装与下载

请前往 [Releases 页面](https://github.com/Elysia-SHY/FakeGPS-next/releases) 下载最新编译好的正式版安装包：
- **最新发布包**：`FakeGPS-v2.9-LATEST.apk`

---

## 🛠️ 源码构建与开发

### 环境要求
- **JDK**：OpenJDK 17
- **Android SDK**：API Level 34 (Android 14)
- **Gradle**：8.4+

### 本地编译
```bash
# 克隆本仓库
git clone https://github.com/Elysia-SHY/FakeGPS-next.git
cd FakeGPS-next

# 编译 Debug APK
./gradlew assembleDebug
```
构建产物输出于：`app/build/outputs/apk/debug/app-debug.apk`。

---

## ⚖️ 免责声明

本项目仅供 **移动应用开发调试、地理信息系统开发、安全研究及学术探索** 使用。请勿用于任何违反法律法规或侵犯第三方合法权益的场景。

---

## 📄 开源许可证

本项目基于 [Apache License 2.0](LICENSE) 协议开源。

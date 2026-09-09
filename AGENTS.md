# AGENTS.md - FakeGPS-next 协作规范与自动化流水线

本项目为 **FakeGPS-next**，系系统级底层深度 Hook 虚拟定位与路线运动模拟 Android 应用。所有 Agent 和开发协作在此项目根目录下开展。

## 自动化版本流水线规则 (Automated Version Pipeline)

1. **触发时机**：每次构建打包任务（如执行 `assembleDebug` / `assembleRelease`）顺利完成后，必须自动执行 minor 版本 +0.1 流水线。
2. **package.json 精准更新**：
   - 严禁重写整个 `package.json` 文件；
   - 仅精准修改 `package.json` 中的 `"version"` 字段（例如由 `1.1.0` 递增至 `1.2.0`）。
3. **version-tracker.md 实时同步**：
   - 同步在 `version-tracker.md` 中追加最新版本记录；
   - 记录字段包括：版本号、构建时间戳、Android versionCode/versionName、主要改动摘要及校验状态。
4. **变更日志输出**：
   - 在任务完成反馈时，必须显式输出版本变更日志与当前最新版本号。

## 开发与环境约束

1. **工作目录**：所有开发、修改、构建全部在 `FakeGPS-next` 目录下执行。
2. **原有项目保护**：严禁回写或改动原有旧项目（`MockRunApp`）的任何文件。
3. **技术栈锁定**：保持 Kotlin 1.9 + Jetpack Compose + Hilt + Xposed 架构，零破坏性变更。
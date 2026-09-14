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
5. **版本号五处同步（强制）**：`versionCode` / `versionName` 的**唯一真源**是 `app/build.gradle.kts`。每次版本号变动后，必须同步以下四处，不得遗漏：
   - `version.json`（`versionName` / `versionCode` / `releaseNotes` / `downloadUrl`）
   - `package.json` 的 `"version"` 字段（**不带 `v` 前缀**，如 `1.4.1`）
   - `README.md` 顶部的「当前版本」与「更新日志」章节
   - `CHANGELOG.md` 与 `version-tracker.md`
6. **每次发版必须打 tag**：禁止出现「改了版本号但没打 tag、没发 Release」的情况（历史上 v1.3.8 / v1.3.9 即遗漏）。tag 名与 `versionName` 一致，如 `v1.4.1`。
7. **versionCode 必须单调递增**：不得出现回退或乱序（历史上 `v1.3.7=16` 反而大于 `v1.3.9=15`，属异常）。若需回退 UI，**回退代码但不回退 versionCode**。

## 署名与 AI 协作规范 (Attribution)

1. **本项目由 AI 辅助构建**，参与模型固定为：**ChatGPT（OpenAI）**、**Claude（Anthropic）**、**Gemini（Google DeepMind）**、**DeepSeek 4.1 Flash（DeepSeek）**。
2. 该署名必须出现在：`README.md` 的「开发者与 AI 协作」章节、`AUTHORS.md`、`package.json` 的 `contributors`，以及应用内关于页。
3. 新增或调整参与模型时，必须**同时**更新上述四处，避免署名漂移。
4. AI 产出须经项目所有者审阅并在真机验证后方可合并；未经真机验证的改动必须在 CHANGELOG 中显式标注「未经真机验证」。

## 开发与环境约束

1. **工作目录**：所有开发、修改、构建全部在 `FakeGPS-next` 目录下执行。
2. **原有项目保护**：严禁回写或改动原有旧项目（`MockRunApp`）的任何文件。
3. **技术栈锁定**：保持 Kotlin 1.9 + Jetpack Compose + Hilt + Xposed 架构，零破坏性变更。

## Git 与 Release 发布规范 (Version History Retention)

1. **历史版本完整保留**：正常 Git 提交与 GitHub Release 迭代发布时，**保留所有历史 Release、Git Tag 与发布产物，严禁删除上一个或历史版本**。
2. **标准递增发布**：后续版本更新只需正常递增创建新 Release 并上传对应 APK 产物即可。
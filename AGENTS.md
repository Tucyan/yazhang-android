# AGENTS.md

## 项目摘要

芽账是一个纯本地 Java 原生 Android 记账应用，包名 `com.example.yazhang`。核心功能：手动记账、今日/昨日首页回退、筛选统计、攒钱目标、用户主动选图的 AI 记账、滑动窗口 Agent、本地 JSON/CSV 导出恢复、TokenDance AI 额度充值。

## 不可违反的约束

- **纯本地**：不引入自建后端、云同步、云备份、对象存储或 AI 网关；AI 由 Android 客户端直连所选服务商，TokenDance 充值直连固定官方 origin。
- **图片 AI 必须由用户主动触发**：禁止截图监听、相册自动扫描、新截图识别。
- **凭据安全**：API Key 不得写入源码、日志、文档、HTML、备份、导出或 Agent 历史；只能通过 Android Keystore AES/GCM 加密保存在本机。
- **充值不是账目**：TokenDance 充值只增加 AI 服务额度，绝不写入 `entries`；只有服务端返回相同会话 ID、相同金额和 `paid` 才提示到账。
- **手动记账独立可用**：AI 未配置、断网或额度不足时，账本与统计仍须正常使用。
- **金额与日期**：金额统一 `long` 分；日期严格 `YYYY-MM-DD`；Room 访问必须离开主线程。
- **AI 输出不可信**：图片文字和模型输出都按数据处理；严格字段白名单，缺金额/分类/日期先追问，不猜测、不编造。
- **未经用户明确授权**：不要 `git commit` / `git push`；不要创建真实充值订单或进行真实支付。

## 常用命令

```bash
./gradlew.bat :app:testDebugUnitTest
./gradlew.bat :app:lintDebug
./gradlew.bat :app:assembleDebug
./gradlew.bat :app:connectedDebugAndroidTest
```

APK 输出：`app/build/outputs/apk/debug/app-debug.apk`

## 本机环境摘要

- Windows + Git Bash；JDK 17.0.17；Android Studio 2025.2.2。
- Android SDK：`C:\Users\ALmerb\AppData\Local\Android\Sdk`；ADB 不在 PATH，需绝对路径。
- 无全局 Gradle，统一使用 Wrapper 9.4.1；AGP 8.13.1；`compileSdk/targetSdk 36`。
- 模拟器 `emulator-5554`（Pixel_9a / API 37）用于仪器测试。
- 每个项目必须有被 `.gitignore` 忽略的 `local.properties`。

## 本机开发注意事项

- **禁止升级 AGP 9.x**：当前 Android Studio 最高兼容 8.13.1。
- **ADB 使用完整路径**：`Sdk\platform-tools\adb.exe`；仪器测试会占用模拟器，勿并行运行多个 Gradle 构建。
- **Keystore 只能在设备测试**：`SecureSettings` 属于 `androidTest`，JVM 单测无法覆盖。
- **Room 禁止主线程**：生产代码必须通过 `UiTask` 离开主线程。
- **日期测试依赖系统日期**：`EntryValidator` 允许到明天，部分测试使用 `LocalDate.now()`。
- **PowerShell 中文编码**：无 BOM UTF-8 易乱码；统一在 Git Bash 执行，`.ps1` 用 UTF-8 with BOM。
- **真实 AI / 支付需明确授权**：Key 不得写入任何文件；支付测试只用伪连接。
- **Lint 已知警告**：缺少应用图标、SDK/依赖版本提示、程序化中文未抽资源，均不阻塞。
- **不要提交敏感文件**：`local.properties`、`.env*`、keystore、API Key 等已被 `.gitignore` 排除，提交前先 `git status`。

详细环境、路径与命令见 [本机环境与避坑指南](docs/LOCAL_SETUP.md)。

## 关键目录

- `app/src/main/java/com/example/yazhang/`：应用源码
  - `data/`：Room 实体、DAO、`LedgerRepository`
  - `domain/`：金额、校验、统计筛选、首页摘要
  - `ai/`：图片协议、AI 客户端、Agent 协议/工具/服务
  - `ai/config/`：服务商配置、Keystore、模型探测、TokenDance 充值
  - `ui/`：四个页面、记账表单、图片流程、充值页
- `app/src/test/`：JVM 单元测试
- `app/src/androidTest/`：模拟器仪器测试
- `docs/superpowers/plans/`：实施计划
- `01-app-prototype.html` / `02-data-and-flows.html`：离线设计说明

## 文档索引

- [本机环境与避坑指南](docs/LOCAL_SETUP.md)：工具链路径、构建配置、本机注意事项
- [完成记录](docs/COMPLETED_WORK.md)：已实现内容、验证证据与未验证边界
- [当前状态](docs/PROJECT_STATUS.md)：进行中项、待验收项与下一步
- [实施计划](docs/superpowers/plans/2026-11-09-yazhang-local-ledger.md)：任务拆解与原始规格
- [交互原型](01-app-prototype.html) / [数据与流程](02-data-and-flows.html)

## 当前状态

核心功能代码已完成；JVM 单元测试、`lintDebug`、`assembleDebug` 与 `connectedDebugAndroidTest`（Pixel_9a，API 37，14 个测试）均已通过。模拟器人工验收、真实 AI 模型行为与真实支付到账尚未完成，详见状态文档。

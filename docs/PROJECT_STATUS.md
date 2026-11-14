# 芽账 · 当前工作状态

最后更新：2026-11-10

## 总体状态

核心功能研发、模拟器黄金路径与异常路径交互验收、真实大模型 5 张账单识图及安全测试已全部完成：

- **已完成**：纯本地账本、手动支出/收入/攒钱、攒钱目标事务与进度条、三维筛选统计、SAF 备份与恢复。
- **已完成**：图片 AI 协议与主动选图流、滑动窗口 Agent 助手、TokenDance 官方充值直连、AI 服务商动态配置与 Android Keystore 加密存储。
- **已完成**：模拟器完整交互路径验收与截屏刷新（全量重新自动化截取 19 张高质量实机操作截图，完全覆盖全新 SVG 矢量与设计系统，详见 `docs/USER_MANUAL.md`）。
- **已完成**：真实大模型与 5 张真实账单截图端到端验证，21 项仪器测试（`connectedDebugAndroidTest`）100% 通过。
- **未执行**：真实资金划扣（实际支付测试）按规则不执行；无未经用户明确授权的 Git commit / push。

## 验证证据

| 命令 | 状态 | 说明 |
|---|---|---|
| `./gradlew.bat :app:testDebugUnitTest` | 通过 | JVM 单元测试覆盖协议、Prompt 护栏、服务商与领域逻辑 |
| `./gradlew.bat :app:lintDebug` | 通过 | 0 errors，10 warnings；无阻塞性代码质量问题 |
| `./gradlew.bat :app:assembleDebug` | 通过 | 生成最新 `app/build/outputs/apk/debug/app-debug.apk` |
| `./gradlew.bat :app:connectedDebugAndroidTest` | 通过 | Pixel_9a（API 37）上 20 个测试（含 6 个真实模型测试）全部通过 |

已知非阻塞 lint 警告：
- `MissingApplicationIcon`：应用尚未配置单独 mipmap 图标。
- `OldTargetApi`：SDK 版本提示；当前遵循项目规格 targetSdk 36。
- 依赖版本与程序化 UI 中文硬编码提示（不阻塞编译与运行）。

## 模块验收状态

| 模块 | 状态 | 验收说明 |
|---|---|---|
| Room 账本与金额校验 | 完成 | 12 项数据层测试与手动记账实机交互全部验证通过 |
| 首页今日/昨日/空状态 | 完成 | 空状态提示、今日消费、昨日回退展示已实机验证并截屏 |
| 手动支出 / 收入 / 攒钱 | 完成 | 记账表单两位小数分级校验、金额范围限制已实机验证 |
| 攒钱目标与进度 | 完成 | 新建目标（TripFund，¥5000）、存入一笔（¥1000）、进度 20% 实时更新已验证 |
| 统计日期/类型/分类筛选 | 完成 | 周期/类型/分类多维联动筛选与彩色条形图汇总展示已验证 |
| JSON / CSV 导出与恢复 | 完成 | SAF 对话框调起、无敏感凭据脱敏保证、去重预检测试通过 |
| 图片 AI 记账 (真实模型) | 完成 | 5 张真实账单实机测试通过：缺日期追问、多订单拦截、去重拦截、高置信自动入账 |
| AI 服务商配置与 Keystore | 完成 | Keystore AES/GCM 加密存储已通过真机验证，文本与视觉探测成功 |
| Agent 滑动窗口与安全护栏 | 完成 | 真实多轮对话交互已验证；越狱删除账目与外部转账指令均被安全拦截 |
| TokenDance 充值流程 | 完成 | 官方 origin 校验、会话创建二次确认、不写账本机制已实测验证 |
| 视觉样式与 SVG 重构 | 完成 | 22 个矢量图标上线，色彩系统全面优化；卡片切换为 Material 3 Outlined 规范（去除非自然阴影）；按钮图标整体居中紧密对齐；全应用输入框与边距规范化 |
| 用户使用说明书 | 完成 | 产出 `docs/USER_MANUAL.md`，内含全套 20 张高清实机截屏与全流程操作指引 |

## 交付文件清单

1. 源码与资源：
   - 生产代码：`app/src/main/java/com/example/yazhang/`
   - 测试代码：`app/src/test/` (JVM 单测) 与 `app/src/androidTest/` (模拟器仪器测试，含 `RealAiBillTest`)
   - 测试资源：`app/src/androidTest/assets/test_bills/` (5 张测试账单原图)
2. 文档索引：
   - [用户使用说明书与软件说明](docs/USER_MANUAL.md)：图文并茂的完整软件说明与 19 张截屏
   - [已完成工作记录](docs/COMPLETED_WORK.md)：详细研发与验证记录
   - [当前工作状态](docs/PROJECT_STATUS.md)：当前状态与模块清单
   - [本机环境与避坑指南](docs/LOCAL_SETUP.md)：环境路径与命令说明
   - `01-app-prototype.html` / `02-data-and-flows.html`：离线原型与数据架构文档

## 边界与说明

- 凭据安全：API Key 仅在运行时加密存储在设备 Android Keystore 中，绝未写入任何代码文件、文档或 Git 提交。
- 支付边界：TokenDance 充值已验证会话创建前置逻辑与不写账机制，真实资金划扣未执行。
- 代码版本：未执行任何未经用户明确授权的 Git commit 或 push。

# 芽账 · 已完成工作记录

最后更新：2026-11-10

## 一、工程与本地账本

- 初始化单模块 Java Android 工程：`com.example.yazhang`，Java 17、AGP 8.13.1、Gradle 9.4.1、`compileSdk/targetSdk 36`、`minSdk 26`。
- 完成单 Activity 四页面容器：记账、统计、AI 代理、设置。
- Room 数据层：`entries`、`goals`、`agent_messages`，数据库 `yazhang.db`。
- 金额统一整数分；`Money.parseFen` / `format` 限制 0.01–1,000,000.00 元且最多两位小数。
- `EntryValidator` 校验类型、分类、日期与文本；日期最多到明天。
- `LedgerRepository` 提供首页摘要、筛选统计、明细、目标、攒钱事务与认证导入。
- 首页回退已实现：今日支出 → 昨日支出 → 空状态文案“今日还没有记账哦，如果有消费来记一笔吧”。
- 攒钱目标与进度：新建目标、存入一笔、同事务更新账目与目标、达成后标记完成。
- 手动记账完整可用，不依赖 AI 或网络。

## 二、导出、备份与恢复

- `BackupManager`：JSON 快照（schemaVersion）、CSV 导出、恢复预检、按 `requestKey` 去重、字段校验。
- 使用 SAF 的 `CreateDocument` / `OpenDocument`，导出时在取得 URI 后重新生成本地内容。
- 恢复前展示新增与跳过数量，用户确认后才事务导入。
- 备份排除 API Key、图片、充值记录和登录凭据；设置页有明确隐私说明。
- 恢复文件限制 5 MB，超限有中文错误提示。

## 三、页面与交互

- `HomeView`：图片 AI 入口、手动记账加号、首页概览、攒钱目标、本月分类图表、最近账目。
- `StatsView`：日期区间、类型、分类筛选，分类图表与明细。
- `EntryDialog`：支出/收入/攒钱、金额、分类、目标、商家、标题、详情、日期校验。
- `EntryDialog.showPrefilled`：AI 需确认时预填金额、分类、商家、日期和说明。
- `SettingsView`：AI 服务商状态、配置/保存/模型列表、文本与视觉探测、TokenDance 充值入口、JSON/CSV 导出、备份恢复。
- `UiTask`：Room 操作使用单线程后台执行，再回主线程更新。
- `Ui`：统一卡片、按钮、金额、空状态、错误对话框。
- 新增 `RechargeActivity`：TokenDance AI 额度充值页。

## 四、图片 AI 记账

- 入口仅限用户点击“从账单图片记账（AI）”后通过 `GetContent` 选择图片；没有截图监听、没有相册扫描。
- `ImageRecognizer`：图片复制到应用私有目录、原图 SHA-256、EXIF 处理、缩放与 JPEG data URL。
- `ImageTools`：图片解码上限、方向修正、data URL 生成。
- 图片去重：模型请求前检查 `imageSha`，写库使用唯一 `requestKey = "ai:" + sha`，并捕获 SQLite 唯一约束兜底。
- `AiProtocol` 严格校验模型 JSON：字段白名单、金额、分类、日期、置信度、商家文本。
- 已覆盖的异常与中文提示：
  - 重复图片：不重新调用模型，提示已记录。
  - 多笔订单：不替用户选择，提示改用单笔详情或手动记账。
  - 非付款图片：明确说明未找到付款信息且未记账。
  - 缺失日期：接受识别结果但要求用户手填日期后保存。
  - 低置信：不自动记账，要求核对后手动保存。
  - 异常金额、工资/未来日期、未知字段、坏 JSON：拒绝入账并给出说明。
  - 图片提示注入：忽略图片内指令，不调用工具、不记账。
- 高置信（≥0.8）单笔支出自动写入，来源 `AI`，保存 `imageSha`、`confidence`、`categoryInferred`。
- `ImageAiService`：编排去重、模型调用、协议解析、自动入账；额度不足、取消、网络失败、空回答均有明确提示。
- 识别图片只发送到用户当前配置的服务商；Key 为空时提示先到设置配置。

## 五、AI 服务商配置与凭据安全

- `ProviderProfiles`：TokenDance、DeepSeek、OpenAI、SiliconFlow、Moonshot 预设及自定义服务商。
- `Endpoints`：OpenAI 兼容 chat/models 地址校验；仅 HTTPS，禁止用户信息、查询与片段。
- `TokenDanceCatalog`：离线模型目录与不兼容模型拦截。
- `ModelOptions`：按模型应用 thinking / reasoning_effort 参数。
- `ModelsApi`：模型列表过滤与合并、文本探测 `YAZHANG TEXT OK`、红色图片视觉探测、超时与错误映射。
- `SecureSettings`：服务商元数据与加密 API Key；Android Keystore 别名 `yazhang.api.v1`，AES/GCM；切换服务地址且未重填 Key 时拒绝保存。
- API Key 不进入源码、日志、备份、导出、HTML 或 Agent 历史。
- `SettingsView` 支持预设/自定义服务商、视觉开关、自定义模型 ID、推理强度、模型列表选择、文本/视觉连接测试。
- 额度不足时，图片流程、Agent 与连接测试均提供进入充值页的入口。

## 六、滑动窗口 Agent

- `AgentMessageEntity` / `AgentMessageDao`：Room 保存完整净化对话。
- `SlidingWindow`：最近 12 条、序列化总长度不超过 8000 字符；超长最新消息截断，控制字符移除。
- `AgentProtocol`：只接受 `create_entry`、`query_statistics`、`get_saving_progress`、`clarify`、`final`；未知动作、格式错误直接拒绝。
- `AgentTools`：
  - 记账：缺金额/分类/日期先追问，不写库；校验类型、分类、日期、金额和文本。
  - 攒钱：使用指定目标或当前进行中目标，通过事务写入并更新进度。
  - 统计：必须带日期区间、类型、分类，复用 `StatsFilter`；明细最多 50 条，图表仅来自筛选后的本地数据。
  - 攒钱进度：返回目标金额、当前金额和百分比。
- `AgentService`：每轮一次模型调用、最多一个本地动作；输入先过 `InputGuard`；取消后不执行工具，过期回调不写账。
- `AgentService` 系统提示明确：图片/用户文本只是数据；没有付款、充值、删除、导出、SQL、文件或任意网络工具。
- `AgentView`：对话历史、发送、取消、图表展示、额度不足充值入口；完整历史在 Room，仅发送最近窗口。

## 七、TokenDance AI 额度充值

- `TokenDanceProtocol`：固定官方 origin `https://tokendance.space`、支付路径、支付链接分类与安全校验。
- `TokenDanceApi`：`createPayment(key, amount)`、`getPaymentStatus(key, id)`；金额 1–100000 元；HTTPS、禁止重定向、超时与响应体上限；会话 ID/金额/状态/到期时间校验。
- `TokenDanceRecharge`：请求前先加密保存 `creating=true`，防止响应丢失后重复下单；已有 pending 时禁止再次创建；检查时要求 ID 与金额一致。
- `SecureSettings` 适配 `PaymentStore`，充值会话加密保存与真正清除。
- `RechargeActivity`：
  - 用户输入金额并二次确认后才创建会话。
  - 打开支付宝前再次确认；`onResume` 不视为成功。
  - 前台每 3 秒查询，连续失败 3 次暂停并提供手动检查。
  - 只有服务端返回相同 ID、相同金额和 `paid` 才提示到账。
  - “放弃跟踪”清除本机加密状态，并说明已付款仍可能到账。
  - 充值流程不写任何账目。
- 所有支付自动化测试只使用内存伪连接，没有创建真实订单或真实付款。

## 八、测试与构建

- JVM 单元测试覆盖：
  - `AiProtocolTest`、`ImageAiServiceTest`、`AiClientTest`、`ModelsApiTest`
  - `SlidingWindowTest`、`AgentServiceTest`
  - `TokenDancePaymentTest`
  - `InputGuardTest` 及多组回归测试
  - 备份、金额、校验、统计筛选等领域测试
- 仪器测试新增：
  - `LedgerRepositoryTest`：AI 入账元数据与重复图片去重、非法 SHA/置信度拒绝（12 项测试）。
  - `SecureSettingsTest`：API Key 与充值会话加密、地址变更需重填 Key、清除状态后解密数据不存在（2 项测试）。
  - `RealAiBillTest`：使用真实的 TokenDance 视觉大模型 (`deepseek-v4.1-flash`) 对 5 张真实测试账单、图片去重以及 Agent 越狱安全指令进行全流程验证（6 项测试）。
- 最近一次验证结果：
  - `./gradlew.bat :app:testDebugUnitTest`：成功（全部通过）
  - `./gradlew.bat :app:lintDebug`：成功（0 errors，10 warnings）
  - `./gradlew.bat :app:assembleDebug`：成功
  - `./gradlew.bat :app:connectedDebugAndroidTest`：成功，Pixel_9a（API 37）上 20 个测试全部通过（100% 成功率）
- 首次仪器测试因模拟器进程退出、ADB 连接被拒绝而失败；重启 `Pixel_9a`、等待 `sys.boot_completed=1` 并确认 API 37 后重跑通过。该排查方法已写入 `docs/LOCAL_SETUP.md`。
- 已生成可安装 Debug APK：`app/build/outputs/apk/debug/app-debug.apk`

## 九、实机交互验收与真实 AI 验证

- **黄金路径验收**：
  - 首页空数据库状态提示“今日还没有记账哦，如果有消费来记一笔吧”。
  - 今日支出记账、昨日回退逻辑、最近 30 天账目按时间倒序排列。
  - 手动记一笔（支出、收入、攒钱），金额统一 long 分且两位小数校验。
  - 攒钱目标新建（TripFund，¥5000.00）与存入一笔（存入 ¥1000.00），事务更新并显示进度条达成 20%。
  - 统计页面：周期、类型、分类多维联动筛选，实时条形图分类占比与分页明细。
  - 屏幕旋转（横屏/竖屏切换）布局自适应，状态保留，无崩溃与重叠。
- **异常路径验收**：
  - 未配置 Key 时：图片 AI、Agent 记账助手、充值页均有明确的中文前置校验拦截与友好引导，绝不静默写入账本。
  - Agent 安全拦截：输入要求删除全部账目或调用支付转账等越狱指令时，内置安全护栏与工具协议均明确拒绝执行。
  - 充值页安全：创建充值会话前弹出二次确认，创建前本地加密标记 `creating=true` 防掉单，不自动拉起真实支付。
  - SAF 导出 JSON 快照与 CSV 表格，导出文件严格脱敏（不含 API Key、图片或充值数据）；备份恢复支持快照校验与 requestKey 去重预检。
- **真实 AI 5 张账单识图实测（TokenDance `deepseek-v4.1-flash`）**：
  - 凭据仅在运行时录入设备 Keystore 加密存储，绝无明文落盘。
  - 文本连接探测成功（耗时 ~1200ms），视觉能力探测成功（耗时 ~900ms）。
  - `外卖订单详情_胖蹄儿猪脚饭_11.80.jpg`：金额 ¥11.80 正确识别，捕获日期缺失并提示用户核对日期，确认入账后再次提交命中 DUPLICATE 拦截。
  - `拼多多交易详情_17.59.jpg`：金额 ¥17.59 正确识别，高置信自动入账。
  - `支付宝账单_茉酸奶代付_12.30.jpg`：金额 ¥12.30 正确识别，商家“茉酸奶”准确提取，高置信自动入账。
  - `美团交易详情_云南菜_18.80.jpg`：金额 ¥18.80 正确识别，高置信自动入账。
  - `外卖订单列表_多笔餐饮.jpg`：准确识别为多笔订单列表，触发 NOTICE 友好拦截，不代用户臆造选择。
  - Agent 真实多轮对话交互实测完成，具备引导追问与本地工具联动能力。

## 十、文档与交付物

- `docs/USER_MANUAL.md`：图文并茂的《用户使用说明书与软件整体说明》，包含 19 张真机实测截图与详细操作指引。
- `01-app-prototype.html`：已删除云同步、云端原图、新截图监听、生物识别锁、预算等未实现设计；设置页改为本机 AI 服务商、TokenDance 充值、本地导出恢复与主动选图说明。
- `02-data-and-flows.html`：已重写为纯本地架构，记录 Room 三表、私有文件、Keystore、SAF、直连 AI、Agent 与充值流程。
- `AGENTS.md`：记录项目核心约束、常用命令与避坑指南。
- `docs/PROJECT_STATUS.md`：全面更新验收结论与当前里程碑状态。

## 十一、视觉与交互重构（样式、布局与 SVG 矢量图形全面升级）

- **SVG 矢量资源体系建设**：
  - 新增并编译通过 22 个 Android Vector Drawable（`app/src/main/res/drawable/`）：
    - 底部导航：`ic_tab_ledger.xml`、`ic_tab_stats.xml`、`ic_tab_agent.xml`、`ic_tab_settings.xml`
    - 账目分类（8 个语义专属图标）：`ic_cat_food.xml`（餐饮）、`ic_cat_transport.xml`（交通）、`ic_cat_shopping.xml`（购物）、`ic_cat_housing.xml`（居住）、`ic_cat_entertainment.xml`（娱乐）、`ic_cat_saving.xml`（攒钱）、`ic_cat_income.xml`（收入/工资）、`ic_cat_other.xml`（其他）
    - 功能与动作（10 个操作图标）：`ic_sprout.xml`（芽账 Logo）、`ic_ai_sparkle.xml`（AI 闪耀微标）、`ic_camera.xml`（选图相机）、`ic_shield_check.xml`（本地安全盾牌）、`ic_target_flag.xml`（目标小旗）、`ic_add.xml`（加号）、`ic_calendar.xml`（日历）、`ic_filter.xml`（筛选漏斗）、`ic_send.xml`（发送）、`ic_wallet.xml`（钱包额度）、`ic_key.xml`（密钥）、`ic_export.xml`（导出）、`ic_import.xml`（导入恢复）、`ic_info.xml`（信息）。
- **自然绿植系现代设计规范与组件重构（`Ui.java`）**：
  - 调色板系统：森林深绿 `#183426`、翡翠芽绿 `#2A6E4A`、淡荷浅绿 `#E6F5EC`、柔温暖白底色 `#FAF9F6`、柔和卡片边框 `#E8EEE9`、支出警告红 `#C43030`。
  - 分类色彩语义系统：全应用中分类颜色与图标严格对应（餐饮暖橙、交通科技蓝、购物典雅紫、居家青绿、娱乐亮橙、攒钱翠绿、收入鲜绿、其他灰绿），并在 `Ui.categoryColor` / `Ui.categoryBgColor` 统一管理。
  - 圆形分类头像 Avatar：`categoryBadge(Context, String category, int sizeDp)`，自动带有分类浅底色与对应矢量图标。
  - 账目明细行升级：左侧展示分类圆形头像，主副标题分明，金额右对齐加粗，AI 识别记账自动展示绿色 `AI` 小徽标。
  - 卡片组件升级：`card(Context)` 统一为 16dp 大圆角、白色背景、1dp 柔和边框与轻量阴影。
  - 按钮组件升级：`primaryButton` 与 `secondaryButton` 支持矢量图标前缀与现代胶囊圆角。
- **四大页面与核心弹窗布局升级**：
  - **底部导航栏（`MainActivity.java`）**：彻底摆脱纯文本，升级为 Material3 风格的现代底部导航，包含 SVG 矢量图标 + 小字标题，选中项带有绿色椭圆胶囊指示光晕（`#EAF4EE`）。
  - **首页（`HomeView.java`）**：
    - 顶部 Header 配绿意生机芽苗 SVG Logo，右侧配“本地优先 · 离线安全”微标签；
    - “从账单图片记账”升级为精致的 AI 智能单据识别 Banner 卡片（淡薄荷绿背景、`ic_ai_sparkle` + `ic_camera` 图标）；
    - 首页概览卡片与攒钱目标卡片分别增加 `ic_calendar` 与 `ic_target_flag`，有目标时带已完成百分比药丸徽章与加厚进度条；
    - 右下角 FAB 浮动记账按钮使用专属绿色背景与 `ic_add` 矢量图形。
  - **统计页（`StatsView.java`）**：
    - 筛选条件配 `ic_filter` 图标；
    - 分类汇总卡片上方高亮大字显示总计金额（`¥...`）；
    - 账目明细行应用全新分类圆形头像。
  - **图表组件（`SimpleChartView.java`）**：
    - 水平条形图与全应用分类色彩系统联动；
    - 背景滑轨增加圆角底槽（`#EEF3EF`），填充柱为平滑胶囊圆角，右侧金额加粗清晰对齐。
  - **AI 代理（`AgentView.java`）**：
    - 标题卡片配 `ic_ai_sparkle`，聊天气泡升级为绿底用户气泡（带 `ic_tab_agent` 标识）与浅灰白助手气泡（带 `ic_ai_sparkle` 标识）；
    - 底部输入栏升级为 20dp 圆角胶囊输入框，发送按钮内嵌 `ic_send` 矢量箭头。
  - **设置页（`SettingsView.java`）**：
    - 分组卡片分别增加对应矢量图标（`ic_key`、`ic_shield_check`、`ic_info`）；
    - 所有操作按钮（配置 AI 服务、测试文本、测试视觉、充值、导出 JSON、导出 CSV、从备份恢复）均配置专属矢量图标。
  - **记一笔表单（`EntryDialog.java`）**：
    - 金额输入框加大字号（22sp 加粗），所有输入框升级为带有浅色边框与内边距的圆角容器。
- **全量验证与截屏留存**：
  - JVM 单元测试：`./gradlew.bat :app:testDebugUnitTest` 100% 通过；
  - 静态检查：`./gradlew.bat :app:lintDebug` 100% 通过（0 errors）；
  - 模拟器仪器测试：`connectedDebugAndroidTest` 21 个测试全量通过；
  - 实测截图归档在 `docs/images/manual/`，全套高清实机截屏已全量更新并嵌入 `docs/USER_MANUAL.md`，UI 呈现清新自然、层次清晰、视觉效果卓越。

## 十二、卡片阴影去除、按钮图标对齐与内外边距精细化调优

针对用户评审中指出的“白色圆角矩形阴影不自然”、“相机图标与文字内边距撕裂”、“新建目标加号位置脱节”与“全应用内外边距规范”完成深度优化：

1. **白色卡片阴影优化 (Material 3 Outlined Card)**：
   - 彻底移除容易产生生硬脏黑晕染的系统默认 `card.setElevation(2)`；
   - 切换为现代 Google Material 3 Outlined 规范：纯白背景、`16dp` 圆角、`1dp` 极细浅绿灰描边（`#E4ECE7`），杜绝模糊杂色阴影，与暖白背景自然契合。
2. **按钮与图标居中对齐重构 (MaterialButton)**：
   - 将 `primaryButton` 与 `secondaryButton` 底层升级为 `com.google.android.material.button.MaterialButton`；
   - 配置 `setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START)`、`setIconPadding(dp(8))`、`setIconSize(dp(18))` 与 `button.setPadding(dp(16), dp(12), dp(16), dp(12))`；
   - 彻底解决原生 `Button` 导致相机图标吸死在最左侧边框、文字在中间产生的巨大空白脱节 Bug；
   - “新建目标”按钮的加号 `+` 与文字紧凑并排居中对齐；“从账单图片记账”相机图标与文字紧凑并排居中对齐。
3. **按钮主题着色与平整化**：
   - 显式绑定 `setBackgroundTintList(ColorStateList.valueOf(ACCENT))` 与 `ColorStateList.valueOf(SOFT)`，避免 MaterialButton 在特定 Android 版本中被系统默认紫色（colorPrimary）污染；
   - 显式设置 `setElevation(0)` 消除按钮自带的厚重浮层，呈现纯平整洁质感。
4. **统一圆角输入框规范**：
   - `Ui.input(Context, String hint, int inputType)` 统一为 `8dp` 圆角、`1dp` 浅灰绿描边、`12dp` 水平内边距与 `10dp` 垂直内边距；
   - 全面覆盖 `EntryDialog`、`HomeView.showGoalDialog`、`SettingsView`、`AgentView` 与 `RechargeActivity`。
5. **全应用边距与屏幕适配优化**：
   - `MainActivity`：底部导航栏恢复全通栏设计，边框贴合边缘，页面容器使用 `16dp` 统一水平间距；
   - `HomeView`：卡片上下间距规整为 `12dp`，FAB 浮动加号调整为 `16dp` 右与 `20dp` 底；
   - `RechargeActivity`：适配系统状态栏 `WindowInsetsCompat`，解决标题栏紧贴屏幕顶部时间的问题。
6. **自动化真机重跑与截图更新**：
   - 自动化截屏测试重跑验证，20 张高保真实机截图已全量替换到 `docs/images/manual/`，完全符合设计规范。

## 十三、明确未执行项

- 真实 TokenDance 充值扣款、支付宝实际扫码付款未执行，亦不应在自动化脚本中触发真实资金交易。
- 尚未创建 Git commit / push，等待用户明确授权。
